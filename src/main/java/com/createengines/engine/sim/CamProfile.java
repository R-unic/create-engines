package com.createengines.engine.sim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shape of a cam lobe set: when, relative to the lobe's firing mark, it opens each valve and triggers the
 * injector. Angles are in cylinder-cycle degrees as the cam sees it (0° = TDC starting the intake stroke,
 * 360° = firing TDC). Each valve follows a half-sine lift curve between its opening and closing angles.
 *
 * @param injection cycle angle at which the injector lobe fires the unit injector
 * @param spark     cycle angle at which a spark plug on this cylinder fires
 */
public record CamProfile(double intakeOpen, double intakeClose, double exhaustOpen, double exhaustClose,
						 double injection, double spark) {

	/**
	 * Intake 10° before TDC to 40° after BDC; exhaust 40° before BDC to 10° after TDC; injection 15° before firing
	 * TDC and the spark 10° before it.
	 */
	public static final CamProfile BASIC = new CamProfile(710, 220, 500, 10, 345, 350);

	/**
	 * The ports of a Wankel housing, in a chamber's own cycle degrees (a turn and a half of the shaft takes it through
	 * 720 of them): the intake port is open longer than any valve and the exhaust port overlaps it at the top, because a
	 * rotor sweeps across fixed holes in the housing wall instead of lifting a valve.
	 */
	public static final CamProfile ROTARY = new CamProfile(700, 230, 500, 20, 340, 350);

	private static final Map<CamProfile, Curve[]> CURVES = new ConcurrentHashMap<>();

	/** The intake valve's lift, read from a table; see {@link Curve}. */
	public Curve intakeCurve() {
		return curves()[0];
	}

	/** The exhaust valve's lift, read from a table; see {@link Curve}. */
	public Curve exhaustCurve() {
		return curves()[1];
	}

	private Curve[] curves() {
		return CURVES.computeIfAbsent(this,
			profile -> new Curve[] {new Curve(profile.intakeOpen, profile.intakeClose),
				new Curve(profile.exhaustOpen, profile.exhaustClose)});
	}

	/**
	 * One valve's lift through the 720 degrees of a cycle, which every cylinder of an engine reads twice each step. The
	 * curve only depends on the profile, so it is sampled once, finely enough that reading between samples is exact to
	 * a few parts in a million.
	 */
	public static final class Curve {

		/** Samples per degree. */
		private static final int PER_DEGREE = 8;
		private static final int SIZE = 720 * PER_DEGREE;

		private final double[] lift = new double[SIZE + 2];

		private Curve(double open, double close) {
			for (int i = 0; i < lift.length; i++)
				lift[i] = lift((double) i / PER_DEGREE, open, close);
		}

		/** Lift, 0 (shut) to 1 (fully open), at a cycle angle in degrees. */
		public double at(double cycleDegrees) {
			double at = SimMath.wrap(cycleDegrees) * PER_DEGREE;
			int index = (int) at;
			double low = lift[index];
			return low + (lift[index + 1] - low) * (at - index);
		}
	}

	public double intakeLift(double cycleDegrees) {
		return lift(cycleDegrees, intakeOpen, intakeClose);
	}

	public double exhaustLift(double cycleDegrees) {
		return lift(cycleDegrees, exhaustOpen, exhaustClose);
	}

	private static double lift(double angle, double open, double close) {
		double duration = SimMath.wrap(close - open);
		double into = SimMath.wrap(angle - open);
		if (into >= duration)
			return 0;
		return Math.sin(Math.PI * into / duration);
	}
}
