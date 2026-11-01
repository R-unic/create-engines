package com.createengines.engine.sim;

/**
 * Combustion properties of a fuel, in the simulation's normalized units.
 *
 * @param energyPerMb       internal energy released by burning 1 mB
 * @param autoignitionTemp  charge temperature needed for compression ignition (ambient = 1)
 * @param airPerMb          fresh charge consumed by burning 1 mB (a full cylinder of air at ambient is ~1)
 */
public record FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb) {

	/** Fresh charge per mB of the fuel injectors are sized for: gasoline's. A thinner fuel needs a bigger shot for the same air. */
	private static final double REFERENCE_AIR_PER_MB = 1.5;

	/**
	 * How much more fuel by volume than the usual shot this fuel needs to match the same air: 1 for gasoline, diesel and
	 * plant oil, and a half again for ethanol, which carries less energy to the mB. The fuel metering dial and the most an
	 * injector can deliver are both a share of the usual shot times this, so a thin fuel is not starved by an injector
	 * sized for a dense one.
	 */
	public double injectorFlow() {
		return Math.max(1, REFERENCE_AIR_PER_MB / airPerMb);
	}

	/** Pressed seed oil: rustic, sluggish to ignite, modest energy. */
	public static final FuelSpec PLANT_OIL = new FuelSpec(30, 1.7, 1.6);
	/** Ignites readily and carries more energy per mB. */
	public static final FuelSpec DIESEL = new FuelSpec(36, 1.55, 1.7);
	/** Too hard to light by compression alone at any speed an engine of this size turns: it needs a spark plug. */
	public static final FuelSpec GASOLINE = new FuelSpec(40, 2.7, 1.5);
}
