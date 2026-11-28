package com.createengines.content;

/**
 * What an engine's own stream plays: its {@link EngineNoteSynth} at a gain applied sample by sample, so a fade, the share
 * the chorus gives it and its casing glide instead of moving in 20 steps a second as a source's volume does, and the
 * whistle and whine of its blowers ({@link BoostWhine}) mixed in at levels of their own. The whole then goes through the
 * air between it and the listener ({@link NoteAir}). The game thread sets the gains each tick; the sound thread glides to
 * them. Pure Java, so it is tested.
 */
final class EngineNoteMix implements EngineNotePcm.Renderer {

	/** At most this many blowers are carried by one engine's note; any more keep their own loops. */
	static final int WHINES = 4;
	/** Per-sample share of the way a gain moves to where it is asked to be: about four hundredths of a second. */
	private static final float GLIDE = 1f / (0.04f * EngineNoteSynth.SAMPLE_RATE);
	/** The same for a whine's pitch, which a heavy rotor changes slowly: about a tenth of a second. */
	private static final float BEND = 1f / (0.1f * EngineNoteSynth.SAMPLE_RATE);

	private final EngineNoteSynth synth;
	private volatile float wanted;
	private float gain;
	private final NoteAir air = new NoteAir();

	/** One blower: what the game thread asks, and where the sound thread has got to. */
	private static final class Slot {
		final BoostWhine voice;
		volatile float wantedLevel;
		volatile float wantedPitch = 1;
		float level;
		float pitch;

		Slot(BoostWhine voice, float pitch) {
			this.voice = voice;
			this.wantedPitch = pitch;
			this.pitch = pitch;
		}
	}

	private final Slot[] slots = new Slot[WHINES];

	EngineNoteMix(EngineNoteSynth synth) {
		this.synth = synth;
	}

	/** Where the air over the distance puts the filter; on the game thread. */
	void setAir(float cutoff) {
		air.set(cutoff);
	}

	/** The share of the synthesizer to play, 0 to 1; on the game thread. */
	void setGain(float share) {
		wanted = unit(share);
	}

	/**
	 * Plays the blower in {@code slot} at {@code level} (the amplitude of its recording to play, 0 to 1) and
	 * {@code pitch}; on the game thread. The first call for a slot brings its voice in from nothing.
	 */
	void setWhine(int slot, BoostWhine.Kind kind, float level, float pitch) {
		float bent = Float.isFinite(pitch) ? Math.clamp(pitch, BoostWhine.LOWEST_PITCH, BoostWhine.HIGHEST_PITCH) : 1;
		synchronized (slots) {
			if (slots[slot] == null || slots[slot].voice.kind != kind)
				slots[slot] = new Slot(new BoostWhine(kind, 0x6C8E9CF5 + slot), bent);
			slots[slot].wantedPitch = bent;
			slots[slot].wantedLevel = unit(level);
		}
	}

	/** Lets the blower in {@code slot} fade away. */
	void releaseWhine(int slot) {
		synchronized (slots) {
			if (slots[slot] != null)
				slots[slot].wantedLevel = 0;
		}
	}

	private static float unit(float value) {
		return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0;
	}

	@Override
	public void render(short[] out, int offset, int count) {
		synth.applyLock(System.nanoTime());
		for (int i = 0; i < count; i++) {
			gain += (wanted - gain) * GLIDE;
			out[offset + i] = (short) Math.round(Math.clamp(synth.next(), -1f, 1f) * gain * 30000);
		}
		for (int s = 0; s < WHINES; s++) {
			Slot slot;
			synchronized (slots) {
				slot = slots[s];
			}
			if (slot != null)
				add(slot, out, offset, count);
		}
		// Near by, the air hands each sample back as it was
		for (int i = offset; i < offset + count; i++)
			out[i] = (short) Math.clamp(Math.round(air.next(out[i])), Short.MIN_VALUE, Short.MAX_VALUE);
	}

	private void add(Slot slot, short[] out, int offset, int count) {
		float level = slot.level;
		float pitch = slot.pitch;
		float toLevel = slot.wantedLevel;
		float toPitch = slot.wantedPitch;
		if (level < 1e-5f && toLevel <= 0)
			return;
		for (int i = 0; i < count; i++) {
			level += (toLevel - level) * GLIDE;
			pitch += (toPitch - pitch) * BEND;
			int sum = out[offset + i] + Math.round(slot.voice.next(pitch) * level * 32767);
			out[offset + i] = (short) Math.clamp(sum, Short.MIN_VALUE, Short.MAX_VALUE);
		}
		slot.level = level;
		slot.pitch = pitch;
	}
}
