package com.createengines.content;

/**
 * What lies between one engine and the listener: a two-pole low-pass and a level, for the air over the distance and any
 * wall in the way ({@link NoteSurroundings} says how much). With nothing in between it passes every sample through
 * untouched. The game thread says what it wants; the sound thread glides there, so a wall stepped behind closes over the
 * note instead of switching it. The first sample takes what is wanted at once: a note starting far off starts dull. Pure
 * Java, so it is tested.
 */
final class NoteAir {

	/** Per-sample share of the way to what is wanted: about a quarter of a second. */
	private static final float GLIDE = 1f / (0.25f * EngineNoteSynth.SAMPLE_RATE);
	/** Near enough to be there. Any closer and a step of the glide is lost in a float's rounding, and it never arrives. */
	private static final float CLOSE_ENOUGH = 1e-3f;

	/** The filter's share of the way per sample (1: open) and the level that are wanted; set on the game thread. */
	private volatile float wantedAmount = 1;
	private volatile float wantedLevel = 1;
	private float amount;
	private float level;
	private boolean started;
	private float one;
	private float two;

	/**
	 * @param cutoff    where the filter is to sit, in hertz; {@link NoteSurroundings#OPEN} (or anything not a number) for none
	 * @param newLevel  the share of the level that gets through, 0 to 1
	 */
	void set(float cutoff, float newLevel) {
		wantedAmount = cutoff > 0 && cutoff < NoteSurroundings.OPEN
			? (float) (1 - Math.exp(-2 * Math.PI * cutoff / EngineNoteSynth.SAMPLE_RATE))
			: 1;
		wantedLevel = Float.isFinite(newLevel) ? Math.clamp(newLevel, 0f, 1f) : 1;
	}

	/** Forgets the engine this was between, for another taking its place; on the sound thread. */
	void restart() {
		started = false;
		one = two = 0;
	}

	float next(float in) {
		float sample = Float.isFinite(in) ? in : 0;
		float toAmount = wantedAmount;
		float toLevel = wantedLevel;
		if (!started) {
			started = true;
			amount = toAmount;
			level = toLevel;
		} else {
			amount = glide(amount, toAmount);
			level = glide(level, toLevel);
		}
		if (amount >= 1) {
			one = two = sample;
			return level >= 1 ? sample : sample * level;
		}
		one = Denormals.flush(one + amount * (sample - one));
		two = Denormals.flush(two + amount * (one - two));
		return two * level;
	}

	private static float glide(float from, float to) {
		float moved = from + (to - from) * GLIDE;
		return Math.abs(to - moved) < CLOSE_ENOUGH ? to : moved;
	}
}
