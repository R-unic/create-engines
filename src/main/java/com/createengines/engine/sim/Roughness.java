package com.createengines.engine.sim;

/**
 * What an uneven engine costs. A Create network wants a steady torque, not the pulses of an engine whose cylinders fire
 * in bunches or only a few at a time, so the stress an engine can be rated for is held back by how much its speed
 * surges over a cycle (the {@link EngineSimulation#predictedRipple() ripple}). Mass takes the surge out of the speed,
 * which is what a flywheel is for; so do more cylinders and an even firing order.
 */
public final class Roughness {

	/** Ripple, as a fraction of mean speed, that costs nothing. */
	public static final double FREE_RIPPLE = 0.04;
	/** Share of the rating lost for each unit of ripple beyond {@link #FREE_RIPPLE}. */
	public static final double LOSS_PER_RIPPLE = 1;
	/** The most an engine can lose to roughness. */
	public static final double MAX_LOSS = 0.4;

	private Roughness() {
	}

	/** The share of an engine's rating lost to a speed ripple of {@code ripple}, 0 to {@link #MAX_LOSS}. */
	public static double loss(double ripple) {
		return Math.clamp((ripple - FREE_RIPPLE) * LOSS_PER_RIPPLE, 0, MAX_LOSS);
	}

	/** The share of the rating that is left after roughness. */
	public static double smoothness(double ripple) {
		return 1 - loss(ripple);
	}
}
