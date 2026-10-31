package com.createengines.engine.sim;

final class SimMath {

	static final double CYCLE = 720;

	private SimMath() {
	}

	/** Wraps an angle into [0, 720). */
	static double wrap(double cycleDegrees) {
		return wrap(cycleDegrees, CYCLE);
	}

	/**
	 * Wraps an angle into [0, period). Nearly every angle is within a period of where it should be, and a floating-point
	 * remainder is far slower than the comparisons that spot that, so those cases skip it (with the same result).
	 */
	static double wrap(double degrees, double period) {
		if (degrees >= 0) {
			if (degrees < period)
				return degrees;
			if (degrees < 2 * period)
				return degrees - period;
		}
		double wrapped = degrees % period;
		return wrapped < 0 ? wrapped + period : wrapped;
	}

	/**
	 * {@code 1 - e^-x}: how far something relaxing at rate {@code x} (rate times time) gets to where it is going. In a step
	 * of a few degrees {@code x} is nearly always tiny, where a series is as exact as the exponential (to a few parts in a
	 * thousand million) and far cheaper.
	 */
	static double approach(double x) {
		if (x > 0.25)
			return 1 - Math.exp(-x);
		return x * (1 - x * (1 / 2d - x * (1 / 6d - x * (1 / 24d - x * (1 / 120d - x * (1 / 720d - x / 5040d))))));
	}

	/** Rounding error below which a mark is taken to be exactly on a step's edge. */
	private static final double EDGE = 1e-9;

	/**
	 * Whether moving forward from {@code from} by {@code delta} degrees passes {@code mark} (excluding the start). A mark
	 * that falls on the boundary between two steps is passed by exactly one of them, however the arithmetic rounds: a
	 * rotary chamber, whose angle moves by thirds, meets its marks on the boundary often.
	 */
	static boolean crosses(double from, double delta, double mark) {
		double ahead = wrap(mark - from);
		return ahead > EDGE && ahead <= delta + EDGE;
	}
}
