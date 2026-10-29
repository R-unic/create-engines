package com.createengines.engine.sim;

/**
 * Combustion properties of a fuel, in the simulation's normalized units.
 *
 * @param energyPerMb       internal energy released by burning 1 mB
 * @param autoignitionTemp  charge temperature needed for compression ignition (ambient = 1)
 * @param airPerMb          fresh charge consumed by burning 1 mB (a full cylinder of air at ambient is ~1)
 */
public record FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb) {

	/** Pressed seed oil: rustic, sluggish to ignite, modest energy. */
	public static final FuelSpec PLANT_OIL = new FuelSpec(30, 1.7, 1.6);
	/** Ignites readily and carries more energy per mB. */
	public static final FuelSpec DIESEL = new FuelSpec(36, 1.55, 1.7);
}
