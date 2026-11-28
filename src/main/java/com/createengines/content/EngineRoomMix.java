package com.createengines.content;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The shared "engine room" voice: the engines past the nearest few, each its own {@link EngineNoteSynth}, mixed into one
 * stream at the gains the game thread sets (their reach, settling and the ceiling, worked out by {@link EngineChorus}). A slot
 * whose engine changes fades the new one in from nothing, and a gain glides over a few hundredths of a second, so nothing
 * clicks. The game thread writes slots and gains; the sound thread renders; neither allocates on the way. Pure Java, so it
 * is tested.
 */
final class EngineRoomMix implements EngineNotePcm.Renderer {

	static final int SLOTS = EngineChorus.ROOM_ENGINES;
	/** Per-sample share of the way a gain moves to where it is asked to be: about three hundredths of a second. */
	private static final float GLIDE = 1f / (0.03f * EngineNoteSynth.SAMPLE_RATE);

	private final AtomicReferenceArray<EngineNoteSynth> slots = new AtomicReferenceArray<>(SLOTS);
	private final AtomicIntegerArray wanted = new AtomicIntegerArray(SLOTS);
	/** What the sound thread last rendered in each slot, and at what gain. */
	private final EngineNoteSynth[] playing = new EngineNoteSynth[SLOTS];
	private final float[] gain = new float[SLOTS];
	/** The air between each slot's engine and the listener. */
	private final NoteAir[] airs = new NoteAir[SLOTS];

	EngineRoomMix() {
		for (int s = 0; s < SLOTS; s++)
			airs[s] = new NoteAir();
	}
	private final float[] mix = new float[EngineNotePcm.CHUNK];
	/** Samples rendered, to tell an engine joining a stream already going from one there from its start. */
	private long rendered;
	/** Below this the mix is left as it is, so an engine here sounds as its own note does; above, it is held under full scale. */
	private static final float KNEE = 0.8f;

	/** Puts {@code synth} (or nothing) in {@code slot}, heard at {@code level}; on the game thread. */
	void set(int slot, EngineNoteSynth synth, float level) {
		slots.set(slot, synth);
		wanted.set(slot, Float.floatToIntBits(Float.isFinite(level) ? Math.clamp(level, 0f, 2f) : 0f));
	}

	/** Where the air over its distance puts the filter for the engine in {@code slot}; on the game thread. */
	void setAir(int slot, float cutoff) {
		airs[slot].set(cutoff);
	}

	/** Whether nothing is in any slot and every slot has faded out. */
	boolean isSilent() {
		for (int s = 0; s < SLOTS; s++)
			if (slots.get(s) != null || gain[s] > 0.001f)
				return false;
		return true;
	}

	@Override
	public void render(short[] out, int offset, int count) {
		int samples = Math.min(count, mix.length);
		java.util.Arrays.fill(mix, 0, samples, 0f);
		for (int s = 0; s < SLOTS; s++) {
			EngineNoteSynth synth = slots.get(s);
			if (synth != playing[s]) {
				// A new engine in the slot comes in from nothing; the one it replaces was faded by the game thread first
				playing[s] = synth;
				gain[s] = 0;
				airs[s].restart();
				if (synth != null && rendered >= EngineNoteClock.PRIMED_SAMPLES)
					synth.joinRunningStream();
			}
			if (synth == null)
				continue;
			float target = Float.intBitsToFloat(wanted.get(s));
			synth.applyLock(System.nanoTime());
			float g = gain[s];
			for (int i = 0; i < samples; i++) {
				g += (target - g) * GLIDE;
				mix[i] += airs[s].next(synth.next()) * g;
			}
			gain[s] = g;
		}
		for (int i = 0; i < samples; i++)
			out[offset + i] = (short) Math.round(limit(mix[i]) * 30000);
		for (int i = samples; i < count; i++)
			out[offset + i] = 0;
		rendered += samples;
	}

	/** Unchanged up to the {@link #KNEE}, then eased toward full scale. */
	static float limit(float sample) {
		float size = Math.abs(sample);
		if (size <= KNEE)
			return sample;
		return Math.copySign(KNEE + (1 - KNEE) * (float) Math.tanh((size - KNEE) / (1 - KNEE)), sample);
	}
}
