package com.createengines.engine.sim;

final class SimMath {

	static final double CYCLE = 720;

	private SimMath() {
	}

	/** Wraps an angle into [0, 720). */
	static double wrap(double cycleDegrees) {
		double wrapped = cycleDegrees % CYCLE;
		return wrapped < 0 ? wrapped + CYCLE : wrapped;
	}

	/** Whether moving forward from {@code from} by {@code delta} degrees passes {@code mark} (excluding the start). */
	static boolean crosses(double from, double delta, double mark) {
		double ahead = wrap(mark - from);
		return ahead > 0 && ahead <= delta;
	}
}
