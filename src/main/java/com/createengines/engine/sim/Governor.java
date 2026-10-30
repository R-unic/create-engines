package com.createengines.engine.sim;

/**
 * A mechanical governor: the flyweights that move the fuel rack (or, in a spark engine, the throttle) to hold the crank
 * at a set speed whatever the load does.
 * <p>
 * It is a proportional controller with a slow reset. The proportional part is the classic droop: speed has to sag a
 * little below the setting for the governor to open further, and a heavier load sags it more. The reset integrates that
 * sag away, so the engine settles back on its set speed instead of running a few percent slow under load. It frees
 * itself of the reset while fully open or fully shut, so a long start-up or a load that is too heavy for the engine does
 * not leave it overshooting afterwards.
 */
public final class Governor {

	/** How far below the set speed (as a share of it) the governor is fully open on proportional action alone. */
	public static final double BAND = 0.1;
	/** Reset rate: position gained per second per unit of relative speed error. */
	private static final double RESET_RATE = 4;
	/** Least air a governed spark engine is throttled to, so a lightly loaded one still has a charge to light. */
	public static final double MIN_OPENING = 0.08;

	private double reset;
	private double position = 1;

	/**
	 * @param rpm    the crank's speed now
	 * @param target the speed to hold, RPM
	 * @param dt     seconds since the last call
	 * @return the rack position, 0 (shut) to 1 (wide open)
	 */
	public double update(double rpm, double target, double dt) {
		double error = (target - rpm) / target;
		double raw = reset + error / BAND;
		boolean saturatedHigh = raw >= 1 && error > 0;
		boolean saturatedLow = raw <= 0 && error < 0;
		if (!saturatedHigh && !saturatedLow)
			reset = Math.max(-1, Math.min(1, reset + RESET_RATE * error * dt));
		position = Math.max(0, Math.min(1, reset + error / BAND));
		return position;
	}

	/** Where the rack is now, 0 (shut) to 1 (wide open). */
	public double position() {
		return position;
	}

	/** Forgets what it has learnt, e.g. when the governor is taken off the engine. */
	public void reset() {
		reset = 0;
		position = 1;
	}

	/** The throttle opening a governed spark engine is held at for a rack position. */
	public static double opening(double position) {
		return MIN_OPENING + (1 - MIN_OPENING) * Math.max(0, Math.min(1, position));
	}

	/** The redstone strength for a rack position: 15 wide open, 0 shut. */
	public static int signal(double position) {
		return (int) Math.round(Math.max(0, Math.min(1, position)) * Throttle.FULL_SIGNAL);
	}
}
