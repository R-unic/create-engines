package com.createengines.content;

/**
 * The short rev-up that plays when a goal or a challenge is earned: an engine, made by {@link EngineNoteSynth}, that
 * winds up, holds for a beat and lets go. A goal is a four that climbs; a challenge is a V8 that climbs higher and
 * lasts longer. Pure Java so it can be tested and rendered to a file.
 * <p>
 * Every sample comes from the same note synthesizer the engines use, under a fade at both ends, so it never clicks and
 * always ends in silence.
 */
public final class StingSynth {

	public static final int SAMPLE_RATE = EngineNoteSynth.SAMPLE_RATE;

	/** How loud it is against a running engine: a flourish, not a roar. */
	private static final float LEVEL = 0.55f;
	/** The speed is told to the note synthesizer this often, which then follows it smoothly. */
	private static final int STEP = SAMPLE_RATE / 50;
	private static final float FADE_IN_SECONDS = 0.03f;
	private static final float FADE_OUT_SECONDS = 0.45f;

	private final boolean challenge;
	private final EngineNoteSynth synth;
	private final float[] angles;
	private final int length;
	private int at;

	public StingSynth(boolean challenge) {
		this.challenge = challenge;
		this.synth = new EngineNoteSynth(challenge ? 0x5EED : 0xBEEF);
		int cylinders = challenge ? 8 : 4;
		angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		length = Math.round(seconds(challenge) * SAMPLE_RATE);
	}

	/** How long a sting lasts. */
	public static float seconds(boolean challenge) {
		return challenge ? 2.4f : 1.5f;
	}

	/** The number of samples in the whole sting. */
	public int length() {
		return length;
	}

	/** Whether every sample has been rendered. */
	public boolean finished() {
		return at >= length;
	}

	/** The engine speed at {@code t}, 0 to 1 of the way through: up quickly, a beat at the top, then off the throttle. */
	static float rpmAt(float t, boolean challenge) {
		float low = 350;
		float high = challenge ? 2400 : 1500;
		if (t < 0.55f) {
			float eased = 1 - (float) Math.pow(1 - t / 0.55f, 2.2);
			return low + (high - low) * eased;
		}
		if (t < 0.7f)
			return high;
		float off = (t - 0.7f) / 0.3f;
		return high - (high - low * 1.5f) * off;
	}

	/** Fills {@code out[offset, offset + count)}; past the end it is silence. */
	public void render(short[] out, int offset, int count) {
		int done = 0;
		while (done < count) {
			int span = Math.min(count - done, STEP - at % STEP);
			if (at >= length) {
				java.util.Arrays.fill(out, offset + done, offset + count, (short) 0);
				return;
			}
			if (at % STEP == 0) {
				float t = Math.min(1f, at / (float) length);
				synth.set(new EngineNoteSynth.Params(rpmAt(t, challenge), angles, 1, challenge ? 0.5f : 0.35f, 1));
			}
			span = Math.min(span, length - at);
			synth.render(out, offset + done, span);
			for (int i = 0; i < span; i++)
				out[offset + done + i] = (short) Math.round(out[offset + done + i] * LEVEL * fade(at + i));
			at += span;
			done += span;
		}
	}

	private float fade(int sample) {
		float in = Math.min(1f, sample / (FADE_IN_SECONDS * SAMPLE_RATE));
		float out = Math.min(1f, (length - sample) / (FADE_OUT_SECONDS * SAMPLE_RATE));
		return Math.max(0f, Math.min(in, out));
	}
}
