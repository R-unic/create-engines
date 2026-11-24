package com.createengines.engine.sim;

/**
 * Converts between sparks of battery charge and the Forge Energy other mods' cables carry. One spark is worth
 * {@link #FE_PER_SPARK}: what an alternator at its default setting makes (2 FE per RPM a tick against 0.01 sparks per RPM a tick),
 * so charging a battery and drawing it back through a wire is break-even rather than a way to make energy.
 */
public final class BatteryEnergy {

	public static final int FE_PER_SPARK = 200;

	private BatteryEnergy() {
	}

	/** Whole FE that {@code sparks} of charge are worth, never negative and never more than an int holds. */
	public static int toEnergy(double sparks) {
		if (!(sparks > 0))
			return 0;
		return (int) Math.min(Integer.MAX_VALUE, Math.floor(sparks * FE_PER_SPARK));
	}

	/** The sparks that {@code energy} FE is worth. */
	public static double toSparks(int energy) {
		return energy <= 0 ? 0 : (double) energy / FE_PER_SPARK;
	}
}
