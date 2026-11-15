package com.createengines.content.effects;

/**
 * The smoke ring a slow single-cylinder engine puffs from its stack at idle. Kept free of the game so it can be tested:
 * whether a firing makes one, and the velocity of each puff in the ring.
 */
public final class SmokeRing {

	/** Speed, RPM, up to which a single's firings are slow enough to leave in rings. */
	public static final float SLOW_RPM = 48;
	/** Puffs in a ring. */
	public static final int POINTS = 10;

	private SmokeRing() {
	}

	/**
	 * Whether one firing leaves the pipe as a ring: only the lone cylinder of a single, turning slowly, burning cleanly
	 * and warm enough that the smoke is not vapour. A rich or loaded engine pours out a plume and a ring would be lost in it.
	 */
	public static boolean rings(int cylinders, float rpm, boolean rich, float load) {
		return cylinders == 1 && Math.abs(rpm) > 1 && Math.abs(rpm) <= SLOW_RPM && !rich && load < 0.6f;
	}

	/**
	 * The velocities of a ring's puffs: all go {@code forward} along {@code normal} (a unit vector), and each strays
	 * {@code spread} outward from it, at {@link #POINTS} evenly spaced angles about it, so the ring grows as it travels.
	 * Each row is x, y, z.
	 */
	public static double[][] velocities(double[] normal, double forward, double spread) {
		// Any two unit vectors across the normal and each other: pick the axis it is least aligned with to cross against
		double[] helper = Math.abs(normal[0]) <= Math.abs(normal[1]) && Math.abs(normal[0]) <= Math.abs(normal[2])
			? new double[] {1, 0, 0} : Math.abs(normal[1]) <= Math.abs(normal[2]) ? new double[] {0, 1, 0} : new double[] {0, 0, 1};
		double[] u = unit(cross(normal, helper));
		double[] v = cross(normal, u);
		double[][] out = new double[POINTS][3];
		for (int i = 0; i < POINTS; i++) {
			double angle = 2 * Math.PI * i / POINTS;
			double c = Math.cos(angle) * spread;
			double s = Math.sin(angle) * spread;
			for (int axis = 0; axis < 3; axis++)
				out[i][axis] = normal[axis] * forward + u[axis] * c + v[axis] * s;
		}
		return out;
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] unit(double[] a) {
		double length = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		return new double[] {a[0] / length, a[1] / length, a[2] / length};
	}
}
