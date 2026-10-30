package com.createengines.engine.sim;

/**
 * What the engine-management blocks read off the simulation, as the 0 to 15 redstone strengths they put out.
 * Pure functions of figures the simulation already keeps, so the thresholds can be tested without a world.
 */
public final class Sensors {

	/** Strongest redstone signal. */
	public static final int FULL_SIGNAL = 15;
	/** Air charge, as a share of a full breath, that reads as full strength: a boosted cylinder can pass 1. */
	public static final double MAF_FULL_CHARGE = 1.5;
	/** Equivalence ratio that reads as full strength on a lambda sensor: twice as much fuel as the air can burn. */
	public static final double LAMBDA_FULL_RICH = 2;

	private Sensors() {
	}

	/**
	 * The air a cylinder drew in as a share of what its bore holds at bottom dead centre. Above 1 only if something
	 * is pushing it in.
	 */
	public static double airCharge(CylinderSpec spec, double freshAir) {
		return Math.max(0, freshAir) / spec.volumeAt(180);
	}

	/** A MAF sensor's output: how full a breath its cylinders are taking, 0 for none to 15 for {@value #MAF_FULL_CHARGE}×. */
	public static int mafSignal(double charge) {
		return scaled(charge / MAF_FULL_CHARGE);
	}

	/**
	 * A lambda sensor's output: the mixture the exhaust gas says it burned. Balanced (1) reads 8, so the whole
	 * balanced band (0.8 to 1.25) reads 6 to 9; lean is below that and rich above, with 15 at twice the fuel the air
	 * could burn.
	 */
	public static int lambdaSignal(double equivalence) {
		return scaled(equivalence / LAMBDA_FULL_RICH);
	}

	/** A rev limiter's output: how close the engine is to the speed it is set to cut at, 15 at the limit. */
	public static int limiterSignal(double rpm, double limit) {
		return limit <= 0 ? 0 : scaled(rpm / limit);
	}

	private static int scaled(double fraction) {
		return (int) Math.max(0, Math.min(FULL_SIGNAL, Math.round(fraction * FULL_SIGNAL)));
	}
}
