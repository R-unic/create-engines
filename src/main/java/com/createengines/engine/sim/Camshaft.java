package com.createengines.engine.sim;

/**
 * A camshaft line. It turns at {@link #ratio} times crank speed, whatever gearing the player built.
 * <p>
 * When the ratio is exactly the four-stroke 1:2, the gears hold the cam in a fixed phase with the crank
 * (cam angle = crank angle / 2). At any other ratio the cam drifts against the crank and the valve events wander
 * through the cycle: the engine runs badly or not at all, without any special rule saying so.
 */
public final class Camshaft {

	public static final double FOUR_STROKE_RATIO = 0.5;
	private static final double LOCK_TOLERANCE = 0.005;

	/** |cam speed / crank speed|, measured from the actual kinetic network. */
	public double ratio = FOUR_STROKE_RATIO;
	/** Cam rotation in degrees, [0, 360). */
	double angle;

	public boolean isPhaseLocked() {
		return Math.abs(ratio - FOUR_STROKE_RATIO) < LOCK_TOLERANCE;
	}

	void sync(double rotorAngle) {
		if (isPhaseLocked())
			angle = rotorAngle / 2;
	}

	void advance(double rotorDegrees) {
		angle = SimMath.wrap(angle + ratio * rotorDegrees, 360);
	}

	public double angle() {
		return angle;
	}
}
