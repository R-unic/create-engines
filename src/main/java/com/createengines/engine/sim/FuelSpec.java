package com.createengines.engine.sim;

/**
 * Combustion properties of a fuel, in the simulation's normalized units.
 *
 * @param energyPerMb       internal energy released by burning 1 mB
 * @param autoignitionTemp  charge temperature needed for compression ignition (ambient = 1)
 * @param airPerMb          fresh charge consumed by burning 1 mB (a full cylinder of air at ambient is ~1)
 * @param coldSensitivity   how much a cold block hurts this fuel's burn, 1 for the usual: ethanol and pressed oil are
 *                          the worst at it, gasoline the best
 * @param chargeCooling     how far the fuel's evaporating cools the charge it is mixed into, in ambient temperatures, at
 *                          an equivalence of 1: a cooler charge is a denser one, so the cylinder draws more air. Ethanol
 *                          takes a lot of heat to boil; the other fuels hardly cool anything
 * @param sootFactor        how much of a rich burn's unburned fuel ends up as soot on a spark plug, 1 for the usual and 0
 *                          for a fuel that burns clean however rich it runs
 */
public record FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
					   double chargeCooling, double sootFactor) {

	/** A fuel that is neither better nor worse than usual in a cold block, and cools and sooty like any other. */
	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb) {
		this(energyPerMb, autoignitionTemp, airPerMb, 1);
	}

	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity) {
		this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, 0, 1);
	}

	/** Fresh charge per mB of the fuel injectors are sized for: gasoline's. A thinner fuel needs a bigger shot for the same air. */
	private static final double REFERENCE_AIR_PER_MB = 1.5;

	/** Share of a spark engine's heat release lost for each ambient temperature the charge is over the fuel's autoignition point. */
	public static final double PREIGNITION_LOSS_PER_DEGREE = 1.0;
	/** Most of a spark engine's heat release a charge that lights itself early can cost. */
	public static final double PREIGNITION_MAX_LOSS = 0.35;

	/**
	 * How much more fuel by volume than the usual shot this fuel needs to match the same air: 1 for gasoline, diesel and
	 * plant oil, and a half again for ethanol, which carries less energy to the mB. The fuel metering dial and the most an
	 * injector can deliver are both a share of the usual shot times this, so a thin fuel is not starved by an injector
	 * sized for a dense one.
	 */
	public double injectorFlow() {
		return Math.max(1, REFERENCE_AIR_PER_MB / airPerMb);
	}

	/**
	 * Share of a spark engine's heat release that is lost because the charge was hotter than this fuel autoignites at
	 * when it lit, 0 for a charge below that. A charge that heats itself past its autoignition point burns before the plug
	 * and against the rising piston, so its work is partly wasted, and boost that squeezes a charge hotter makes more of it.
	 * It only costs power: no plug is fouled and nothing is damaged.
	 *
	 * @param chargeTemp the charge's temperature when it lit (ambient = 1)
	 */
	public double preignitionLoss(double chargeTemp) {
		return Math.min(PREIGNITION_MAX_LOSS, Math.max(0, chargeTemp - autoignitionTemp) * PREIGNITION_LOSS_PER_DEGREE);
	}

	/** Pressed seed oil: rustic, sluggish to ignite, modest energy. */
	public static final FuelSpec PLANT_OIL = new FuelSpec(30, 1.7, 1.6, 1.5);
	/** Ignites readily and carries more energy per mB. */
	public static final FuelSpec DIESEL = new FuelSpec(36, 1.55, 1.7, 1);
	/** Too hard to light by compression alone at any speed an engine of this size turns: it needs a spark plug. */
	public static final FuelSpec GASOLINE = new FuelSpec(40, 2.7, 1.5, 0.8);
	/**
	 * Needs a spark like gasoline, with less energy to the mB. It will not light early under boost that makes gasoline
	 * pre-ignite, boils off so much heat that it cools the charge, and leaves no soot on a plug.
	 */
	public static final FuelSpec ETHANOL = new FuelSpec(27, 3.4, 1.0, 1.8, 0.08, 0);
}
