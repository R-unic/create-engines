package com.createengines.content;

/**
 * What the air over the distance does to one engine on its way to the listener: a two-pole low-pass, open near by and
 * closing farther off. Walls and rooms are left to the game and to mods made for them, such as Sound Physics Remastered.
 * With nothing to take away it passes every sample through untouched. The game thread says what it wants; the sound
 * thread glides there. The first sample takes what is wanted at once: a note starting far off starts dull. Pure Java, so
 * it is tested.
 */
final class NoteAir {

	/** A cutoff meaning no filter at all. */
	static final float OPEN = Float.POSITIVE_INFINITY;
	/** Blocks within which the air takes nothing away. */
	static final double CLEAR_AIR = 8;
	private static final float NEAR_HZ = 12000;
	private static final float FAR_HZ = 700;
	/** Per-sample share of the way to what is wanted: about a quarter of a second. */
	private static final float GLIDE = 1f / (0.25f * EngineNoteSynth.SAMPLE_RATE);
	/** Near enough to be there. Any closer and a step of the glide is lost in a float's rounding, and it never arrives. */
	private static final float CLOSE_ENOUGH = 1e-3f;

	/** The filter's share of the way per sample (1: open) that is wanted; set on the game thread. */
	private volatile float wantedAmount = 1;
	private float amount;
	private boolean started;
	private float one;
	private float two;

	/** Where the air puts the filter for an engine {@code blocks} away: open near by, only the boom far off. */
	static float cutoff(double blocks) {
		if (!(blocks > CLEAR_AIR))
			return OPEN;
		double share = Math.min(1, (blocks - CLEAR_AIR) / (EngineChorus.REACH - CLEAR_AIR));
		return (float) (NEAR_HZ * Math.pow(FAR_HZ / NEAR_HZ, share));
	}

	/** @param cutoff where the filter is to sit, in hertz; {@link #OPEN} (or anything not a number) for none */
	void set(float cutoff) {
		wantedAmount = cutoff > 0 && cutoff < OPEN
			? (float) (1 - Math.exp(-2 * Math.PI * cutoff / EngineNoteSynth.SAMPLE_RATE))
			: 1;
	}

	/** Forgets the engine this was between, for another taking its place; on the sound thread. */
	void restart() {
		started = false;
		one = two = 0;
	}

	float next(float in) {
		float sample = Float.isFinite(in) ? in : 0;
		float toAmount = wantedAmount;
		if (!started) {
			started = true;
			amount = toAmount;
		} else
			amount = glide(amount, toAmount);
		if (amount >= 1) {
			one = two = sample;
			return sample;
		}
		one = Denormals.flush(one + amount * (sample - one));
		two = Denormals.flush(two + amount * (one - two));
		return two;
	}

	private static float glide(float from, float to) {
		float moved = from + (to - from) * GLIDE;
		return Math.abs(to - moved) < CLOSE_ENOUGH ? to : moved;
	}
}
