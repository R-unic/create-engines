package com.createengines.engine;

import java.util.Arrays;

/**
 * Firing angles as they travel to the client: whole tenths of a degree within the engine's 720 degree cycle. Pure Java so
 * the rounding can be tested.
 *
 * <p>Rounding to tenths can carry an angle just under 720 (719.96 and up, or a double a hair under 720 that became
 * 720.0f) up to 7200, which is the whole cycle, not a place in it. The engine note's synthesizer refuses any angle at or
 * past 720 as damaged and goes silent for the whole engine, and an engine whose note is playing leaves its cylinders'
 * own bangs to it, so one such angle in a big engine silenced every firing it made. Wrapping the tenths into the cycle
 * puts that angle at 0, which is where it is, and sorting keeps the list in firing order, which the synthesizer relies on.
 */
public final class FiringAngles {

	/** Tenths of a degree in the 720 degree cycle. */
	static final int CYCLE_TENTHS = 7200;

	private FiringAngles() {
	}

	/** Angles to wire form: tenths of a degree, each wrapped into the cycle, sorted. Angles that are not numbers are left out. */
	public static int[] toTenths(float[] angles) {
		int[] tenths = new int[angles.length];
		int count = 0;
		for (float angle : angles)
			if (Float.isFinite(angle))
				tenths[count++] = wrap(Math.round(angle * 10f));
		tenths = Arrays.copyOf(tenths, count);
		Arrays.sort(tenths);
		return tenths;
	}

	/** Wire form to angles, in degrees, each in [0, 720) and sorted, whatever the wire held. */
	public static float[] fromTenths(int[] tenths) {
		int[] wrapped = new int[tenths.length];
		for (int i = 0; i < wrapped.length; i++)
			wrapped[i] = wrap(tenths[i]);
		Arrays.sort(wrapped);
		float[] angles = new float[wrapped.length];
		for (int i = 0; i < angles.length; i++)
			angles[i] = wrapped[i] / 10f;
		return angles;
	}

	private static int wrap(int tenths) {
		return Math.floorMod(tenths, CYCLE_TENTHS);
	}
}
