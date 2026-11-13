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
	public static final double MIN_OPENING = 0.03;
	/** Slowest speed a redstone signal turns a governor down to, RPM: below this an engine only stalls. */
	public static final double MIN_COMMANDED = 20;

	/** Time constant of the flyweights' smoothing, seconds: a crank's speed pulses with every firing, and the rack should not follow that. */
	private static final double SMOOTHING = 0.25;

	private double reset;
	private double position = 1;
	private double smoothed = Double.NaN;

	/**
	 * Picks the rack up where it already is, e.g. after a reload or when the engine is rebuilt, so the governor takes
	 * over from the position it was holding instead of throwing the rack wide open.
	 *
	 * @param held   the rack position to continue from, 0 to 1
	 * @param rpm    the crank's speed now
	 * @param target the speed being held, RPM
	 */
	public void seed(double held, double rpm, double target) {
		if (!(target > 0) || !Double.isFinite(held) || !Double.isFinite(rpm))
			return;
		double clamped = Math.max(0, Math.min(1, held));
		double error = (target - rpm) / target;
		reset = Math.max(-1, Math.min(1, clamped - error / BAND));
		position = clamped;
		smoothed = rpm;
	}

	/**
	 * @param rpm    the crank's speed now
	 * @param target the speed to hold, RPM
	 * @param dt     seconds since the last call
	 * @return the rack position, 0 (shut) to 1 (wide open)
	 */
	public double update(double rpm, double target, double dt) {
		if (!(target > 0) || !Double.isFinite(rpm))
			return position;
		smoothed = Double.isNaN(smoothed) ? rpm : smoothed + (rpm - smoothed) * (dt / (SMOOTHING + dt));
		// A speed well off the setting is acted on as it is: smoothing must never hold back the rack in an overspeed
		double measured = rpm > target * (1 + BAND) ? rpm : smoothed;
		double error = (target - measured) / target;
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
		smoothed = Double.NaN;
	}

	/**
	 * The speed a governor holds once a redstone signal has had its say: none leaves the setting alone, and a signal of
	 * 1 to 15 asks for that share of it (15 being all of it), but never less than {@link #MIN_COMMANDED}.
	 *
	 * @param setting the speed the governor is set to, RPM
	 * @param signal  the redstone strength at the governor
	 */
	public static double commanded(double setting, int signal) {
		if (signal <= 0)
			return setting;
		double asked = setting * Math.min(signal, Throttle.FULL_SIGNAL) / Throttle.FULL_SIGNAL;
		return Math.min(setting, Math.max(MIN_COMMANDED, asked));
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
