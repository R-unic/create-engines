package com.createengines.content;

/**
 * The curves a note works out every sample, read from tables. {@link Math#tanh} is worked out in Java and, clipping every
 * sample of every note, was the largest part of what a note cost the sound thread; the sine is dear enough to be worth it
 * too. Between their entries the tables are out by less than a tenth of the smallest step of a 16 bit sample. Pure Java,
 * so it is tested.
 */
final class NoteMath {

	/** From here on the true curve is within a hundredth of a 16 bit step of 1. */
	private static final int REACH = 8;
	private static final int STEPS = 256;
	private static final float[] TANH = new float[REACH * STEPS + 1];

	/** Entries in a whole turn of the sine; a power of two, so wrapping is a mask. */
	private static final int TURN = 4096;
	private static final double PER_RADIAN = TURN / (2 * Math.PI);
	private static final float[] SINE = new float[TURN + 1];

	static {
		for (int i = 0; i < TANH.length; i++)
			TANH[i] = (float) Math.tanh(i / (double) STEPS);
		for (int i = 0; i < SINE.length; i++)
			SINE[i] = (float) Math.sin(i / PER_RADIAN);
	}

	/** {@code tanh(x)}; what is not a number stays one. */
	static float tanh(float x) {
		float place = Math.abs(x) * STEPS;
		if (!(place < REACH * STEPS))
			return Float.isNaN(x) ? x : x < 0 ? -1f : 1f;
		int below = (int) place;
		float low = TANH[below];
		float curve = low + (TANH[below + 1] - low) * (place - below);
		return x < 0 ? -curve : curve;
	}

	/** {@code sin(radians)}, for an angle from 0 to a few thousand turns; what is not a number stays one. */
	static float sin(double radians) {
		double place = radians * PER_RADIAN;
		int whole = (int) place;
		int below = whole & (TURN - 1);
		float low = SINE[below];
		return low + (SINE[below + 1] - low) * (float) (place - whole);
	}

	private NoteMath() {
	}
}
