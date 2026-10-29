package com.createengines.engine.sim;

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
