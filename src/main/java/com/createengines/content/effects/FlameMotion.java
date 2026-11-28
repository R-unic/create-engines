package com.createengines.content.effects;

/**
 * The arithmetic behind how a flame tongue and a spark are drawn: their colour as they cool, how long a streak their
 * speed draws them into, which way round the streak faces the camera, and what a spark does when it strikes something.
 * Kept free of the game so it can be tested.
 */
public final class FlameMotion {

	/** A flame from new to spent: pale yellow at the root, then yellow, orange, red and a sooty brown. */
	private static final float[][] FLAME = {{1, 0.93f, 0.66f}, {1, 0.66f, 0.14f}, {1, 0.42f, 0.05f}, {0.82f, 0.2f, 0.04f},
		{0.3f, 0.11f, 0.08f}};
	/** A spark from new to spent: white hot, yellow, orange, dull red. */
	private static final float[][] SPARK = {{1, 0.98f, 0.85f}, {1, 0.82f, 0.28f}, {1, 0.48f, 0.08f}, {0.7f, 0.14f, 0.03f}};

	/** Ticks of travel a streak is drawn over. */
	private static final double SPARK_SMEAR = 1;
	private static final double LONGEST_SPARK = 0.28;
	/** Share of its speed a spark keeps off a surface, along the way it bounced and across it. */
	private static final double BOUNCE = 0.45;
	private static final double SKID = 0.7;

	private FlameMotion() {
	}

	/** The colour of a flame {@code life} of the way through it, 0 to 1, as red, green and blue. */
	public static float[] flameColour(float life) {
		return ramp(FLAME, life);
	}

	/** The colour of a spark {@code life} of the way through it. */
	public static float[] sparkColour(float life) {
		return ramp(SPARK, life);
	}

	/**
	 * Half the length of a flame tongue of half width {@code width} moving at {@code speed} blocks a tick: a fast one is
	 * drawn out into a jet, a slow one is as round as it is wide. Never more than three times its width.
	 */
	public static double tongueHalfLength(double width, double speed) {
		return Math.max(width, Math.min(width * 3, speed * 1.3));
	}

	/** Half the length of a spark's streak: what it crosses in a moment, and at least its own width. */
	public static double sparkHalfLength(double width, double speed) {
		return Math.max(width, Math.min(LONGEST_SPARK, speed * SPARK_SMEAR) / 2);
	}

	/**
	 * The two half axes of a streak lying along {@code velocity}, seen from a camera {@code toCamera} away: first along
	 * the motion, {@code halfLength} long, then across it, {@code halfWidth} long and turned to face the camera. Null when
	 * there is no such thing, because it is still or coming straight at the camera: draw it as a plain square then.
	 */
	public static double[][] streakAxes(double[] velocity, double[] toCamera, double halfLength, double halfWidth) {
		double speed = length(velocity);
		if (speed < 1e-4)
			return null;
		double[] across = {velocity[1] * toCamera[2] - velocity[2] * toCamera[1],
			velocity[2] * toCamera[0] - velocity[0] * toCamera[2],
			velocity[0] * toCamera[1] - velocity[1] * toCamera[0]};
		double wide = length(across);
		if (wide <= 0.25 * speed * length(toCamera))
			return null;
		return new double[][] {scaled(velocity, halfLength / speed), scaled(across, halfWidth / wide)};
	}

	/**
	 * A spark's velocity after a tick in which it wanted to move {@code wanted} but only managed {@code moved}: along each
	 * way it was stopped it comes back off the surface slower, and it skids along the others.
	 */
	public static double[] bounce(double[] wanted, double[] moved) {
		boolean[] struck = new boolean[3];
		boolean any = false;
		for (int i = 0; i < 3; i++) {
			struck[i] = Math.abs(wanted[i] - moved[i]) > 1e-7;
			any |= struck[i];
		}
		if (!any)
			return wanted.clone();
		double[] out = new double[3];
		for (int i = 0; i < 3; i++)
			out[i] = struck[i] ? -wanted[i] * BOUNCE : wanted[i] * SKID;
		return out;
	}

	private static float[] ramp(float[][] stops, float life) {
		float at = Math.max(0, Math.min(1, life)) * (stops.length - 1);
		int from = Math.min(stops.length - 2, (int) at);
		float share = at - from;
		float[] out = new float[3];
		for (int i = 0; i < 3; i++)
			out[i] = stops[from][i] + (stops[from + 1][i] - stops[from][i]) * share;
		return out;
	}

	private static double length(double[] v) {
		return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	private static double[] scaled(double[] v, double by) {
		return new double[] {v[0] * by, v[1] * by, v[2] * by};
	}
}
