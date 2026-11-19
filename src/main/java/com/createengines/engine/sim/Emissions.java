package com.createengines.engine.sim;

/**
 * What an engine puts into the air, as whole steps of gas. Plain arithmetic: the game side turns the steps into pollution when
 * a pollution mod is installed, and nothing here is read back into the simulation, so an engine behaves the same whether or not
 * anything listens.
 * <p>
 * Carbon comes in proportion to the fuel burned, and a good deal more from fuel a rich run threw out unburned (soot no trap
 * caught). Sulfur comes in proportion to how dirty the fuel is, its soot factor: crude is sulfurous and ethanol is not. The
 * rates are a first choice, not measured. The part of a step not yet whole is held, so a slow engine still releases one now and
 * then.
 */
public final class Emissions {

	/** Steps of carbon for each mB of fuel burned. */
	public static final double CARBON_PER_MB = 0.02;

	/** Steps of sulfur for each mB of fuel burned, at a soot factor of 1. */
	public static final double SULFUR_PER_MB = 0.01;

	/** How many times as much carbon a mB of unburned, soot-forming fuel makes as a mB burned. */
	public static final double SOOT_CARBON = 4;

	/** The most that is held back, in steps, so an engine with nowhere to put its gas never stores up a flood. */
	public static final double MAX_HELD = 64;

	private double carbon;
	private double sulfur;

	/**
	 * Adds a stretch of running.
	 *
	 * @param burnedMb   fuel burned, in mB
	 * @param sootMb     fuel that left the exhaust unburned, in mB
	 * @param sootFactor the fuel's soot factor (1 usual, 0 clean)
	 * @param rate       the server's multiplier; 0 emits nothing
	 */
	public void add(double burnedMb, double sootMb, double sootFactor, double rate) {
		if (Double.isNaN(rate) || Double.isNaN(burnedMb) || Double.isNaN(sootMb) || Double.isNaN(sootFactor))
			return;
		// A finite rate keeps zero fuel times an infinite rate from making a NaN
		double scale = Math.min(1000, Math.max(0, rate));
		double burned = Math.max(0, burnedMb);
		double soot = Math.max(0, sootMb);
		carbon = Math.min(MAX_HELD, carbon + (burned + soot * SOOT_CARBON) * CARBON_PER_MB * scale);
		sulfur = Math.min(MAX_HELD, sulfur + burned * SULFUR_PER_MB * Math.max(0, sootFactor) * scale);
	}

	/** Whole steps of carbon ready to release; the rest stays held. */
	public int takeCarbon() {
		int whole = (int) carbon;
		carbon -= whole;
		return whole;
	}

	/** Whole steps of sulfur ready to release; the rest stays held. */
	public int takeSulfur() {
		int whole = (int) sulfur;
		sulfur -= whole;
		return whole;
	}

	/** Drops whatever is held, for an engine that stopped or has no outlet to release it through. */
	public void clear() {
		carbon = 0;
		sulfur = 0;
	}
}
