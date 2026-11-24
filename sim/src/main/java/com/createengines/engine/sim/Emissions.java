package com.createengines.engine.sim;

/**
 * What an engine puts into the air, as whole steps of gas. Plain arithmetic: the game side turns the steps into pollution when
 * a pollution mod is installed, and nothing here is read back into the simulation, so an engine behaves the same whether or not
 * anything listens.
 * <p>
 * Carbon comes in proportion to the fuel burned, and a good deal more from fuel a rich run threw out unburned (soot no trap
 * caught; what a Soot Trap holds is not released). Sulfur comes in proportion to how sulfurous the fuel is, the optional
 * {@code sulfur} of its fuel data (its soot factor when it sets none): crude is sulfurous and ethanol is not. A trap does not
 * touch the base carbon or the sulfur. The
 * rates are a first choice, not measured. The part of a step not yet whole is held, so a slow engine still releases one now and
 * then.
 */
public final class Emissions {

	/** Steps of carbon for each mB of fuel burned. */
	public static final double CARBON_PER_MB = 0.5;

	/** Steps of sulfur for each mB of fuel burned, at a soot factor of 1. */
	public static final double SULFUR_PER_MB = 0.25;

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
	 * @param sootMb     fuel that left the exhaust unburned and that no trap caught, in mB
	 * @param sootFactor the fuel's sulfur (1 usual, 0 none)
	 * @param rate       the server's multiplier; 0 emits nothing
	 */
	public void add(double burnedMb, double sootMb, double sootFactor, double rate) {
		add(burnedMb, sootMb, sootFactor, rate, 1, 1);
	}

	/**
	 * As {@link #add(double, double, double, double)}, with the server's separate multipliers for each gas, applied on top of
	 * {@code rate}.
	 *
	 * @param carbonRate the server's multiplier for carbon alone; 0 emits none
	 * @param sulfurRate the server's multiplier for sulfur alone; 0 emits none
	 */
	public void add(double burnedMb, double sootMb, double sootFactor, double rate, double carbonRate, double sulfurRate) {
		add(burnedMb, sootMb, sootFactor, rate, carbonRate, sulfurRate, 0, 0);
	}

	/**
	 * As the six-rate form, with a ceiling on what one engine may add: no more than {@code ceilingPerMinute} steps of each gas
	 * for every minute of running, so a big engine cannot flood its surroundings however much it burns.
	 *
	 * @param ceilingPerMinute the most steps of each gas added per minute; 0 or less means no ceiling
	 * @param seconds          how long the stretch being added lasted
	 */
	public void add(double burnedMb, double sootMb, double sootFactor, double rate, double carbonRate, double sulfurRate,
		double ceilingPerMinute, double seconds) {
		if (Double.isNaN(rate) || Double.isNaN(burnedMb) || Double.isNaN(sootMb) || Double.isNaN(sootFactor)
			|| Double.isNaN(carbonRate) || Double.isNaN(sulfurRate))
			return;
		// A finite rate keeps zero fuel times an infinite rate from making a NaN
		double scale = Math.min(1000, Math.max(0, rate));
		double carbonScale = scale * Math.min(1000, Math.max(0, carbonRate));
		double sulfurScale = scale * Math.min(1000, Math.max(0, sulfurRate));
		double burned = Math.max(0, burnedMb);
		double soot = Math.max(0, sootMb);
		double addCarbon = (burned + soot * SOOT_CARBON) * CARBON_PER_MB * carbonScale;
		double addSulfur = burned * SULFUR_PER_MB * Math.max(0, sootFactor) * sulfurScale;
		if (ceilingPerMinute > 0 && !Double.isNaN(ceilingPerMinute) && !Double.isNaN(seconds)) {
			double allowed = ceilingPerMinute * Math.max(0, seconds) / 60;
			addCarbon = Math.min(addCarbon, allowed);
			addSulfur = Math.min(addSulfur, allowed);
		}
		carbon = Math.min(MAX_HELD, carbon + addCarbon);
		sulfur = Math.min(MAX_HELD, sulfur + addSulfur);
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
