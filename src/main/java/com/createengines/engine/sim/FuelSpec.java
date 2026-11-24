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
 * @param preignitionTemp   charge temperature past which a boosted spark engine's charge lights itself early and loses
 *                          power (see {@link #preignitionLoss}); the autoignition point for every fuel but ethanol
 * @param sulfur            how sulfurous the fuel's exhaust is, for the pollution compat only: 1 for the usual, 0 for a fuel
 *                          with none. Nothing in the simulation reads it. A fuel that does not set it takes its soot factor
 * @param flammability      how readily fuel lying in the world (a spill, never a running engine's tank) catches and how fast a
 *                          pool burns off once lit: 1 is the way every fuel burned before the field existed, above 1 lights surely
 *                          and burns off fast (gasoline, methanol), below 1 is hard to light and slow (diesel, kerosene, plant oil).
 *                          Only {@code FuelLiquidBlock} reads it; nothing in the simulation does
 */
public record FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
					   double chargeCooling, double sootFactor, double preignitionTemp, double sulfur, double flammability) {

	/** A fuel that burns in a spill like every fuel did before flammability existed. */
	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
					double chargeCooling, double sootFactor, double preignitionTemp, double sulfur) {
		this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, preignitionTemp, sulfur, 1);
	}

	/** This fuel with a different flammability in a spill; every other figure is the same. */
	public FuelSpec withFlammability(double flammability) {
		return new FuelSpec(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, preignitionTemp, sulfur,
			flammability);
	}

	/** A fuel whose sulfur is its soot factor: how every fuel behaved before the field existed. */
	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
					double chargeCooling, double sootFactor, double preignitionTemp) {
		this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, preignitionTemp, sootFactor);
	}

	/** A fuel whose charge pre-ignites at the temperature it would compression-ignite at. */
	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity,
					double chargeCooling, double sootFactor) {
		this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, chargeCooling, sootFactor, autoignitionTemp);
	}

	/** A fuel that is neither better nor worse than usual in a cold block, and cools and sooty like any other. */
	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb) {
		this(energyPerMb, autoignitionTemp, airPerMb, 1);
	}

	public FuelSpec(double energyPerMb, double autoignitionTemp, double airPerMb, double coldSensitivity) {
		this(energyPerMb, autoignitionTemp, airPerMb, coldSensitivity, 0, 1);
	}

	/**
	 * A fuel that autoignites at or below this is a compression fuel (diesel, plant oil, used oil); above it, it is a spark fuel
	 * and an engine needs a plug or a pilot shot to light it. The pilot limit and the fuel tooltips draw the same line.
	 */
	public static final double COMPRESSION_LIMIT = 2.0;

	/**
	 * Whether this fuel needs a spark plug or a pilot shot. A spark fuel is not lit by the heat of compression in an engine that
	 * has neither, however much boost has heated the charge: gasoline's "too hard to light by compression" held only until two
	 * superchargers made the charge hot enough, after which a plugless engine ran on it at several times the power.
	 */
	public boolean needsSpark() {
		return autoignitionTemp > COMPRESSION_LIMIT;
	}

	/** Fresh charge per mB of the fuel injectors are sized for: gasoline's. A thinner fuel needs a bigger shot for the same air. */
	private static final double REFERENCE_AIR_PER_MB = 1.5;

	/** Share of a spark engine's heat release lost for each ambient temperature the charge is over the fuel's autoignition point. */
	public static final double PREIGNITION_LOSS_PER_DEGREE = 1.0;
	/** Most of a spark engine's heat release a charge that lights itself early can cost. */
	public static final double PREIGNITION_MAX_LOSS = 0.35;

	/** The most energy per mB the sim will take from a fuel: a larger finite number overflows to infinity inside a burn. */
	public static final double MAX_USABLE_ENERGY = 1e300;
	/** What an air figure of 0 (or one that is not a number) becomes: a charge that needs next to no air, never a division by zero. */
	public static final double MIN_USABLE_AIR = 1e-300;

	/**
	 * This fuel as the simulation may use it. The {@code fuel} data map accepts any number and {@link FuelCheck} only reports
	 * a bad one, so a pack's fuel reaches a running engine as written; four of the figures turn an engine's figures to NaN
	 * when they are not numbers (or, for the air, are zero, or, for the energy, overflow): those are replaced here with the
	 * nearest value that cannot, and a fuel that has none of them is returned itself, so every fuel that ever ran runs the
	 * same. The record is not changed, so {@code FuelCheck} still sees what the pack wrote.
	 * <ul>
	 *     <li>energy: not a number or infinite becomes 0, an engine that cannot run on it; above {@link #MAX_USABLE_ENERGY}
	 *     is held there;</li>
	 *     <li>air: not a number, minus infinity or 0 becomes {@link #MIN_USABLE_AIR};</li>
	 *     <li>cold sensitivity and charge cooling: not a number or infinite becomes 0, no effect.</li>
	 * </ul>
	 */
	public FuelSpec usable() {
		double energy = Double.isFinite(energyPerMb) ? Math.min(energyPerMb, MAX_USABLE_ENERGY) : 0;
		double air = Double.isNaN(airPerMb) || airPerMb == 0 || airPerMb == Double.NEGATIVE_INFINITY ? MIN_USABLE_AIR : airPerMb;
		double cold = Double.isFinite(coldSensitivity) ? coldSensitivity : 0;
		double cooling = Double.isFinite(chargeCooling) ? chargeCooling : 0;
		if (energy == energyPerMb && air == airPerMb && cold == coldSensitivity && cooling == chargeCooling)
			return this;
		return new FuelSpec(energy, autoignitionTemp, air, cold, cooling, sootFactor, preignitionTemp, sulfur, flammability);
	}

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
	 * Share of a spark engine's heat release that is lost because the charge was hotter than this fuel's pre-ignition
	 * temperature when it lit, 0 for a charge below that. A charge that heats itself past its autoignition point burns before the plug
	 * and against the rising piston, so its work is partly wasted, and boost that squeezes a charge hotter makes more of it.
	 * It only costs power: no plug is fouled and nothing is damaged.
	 *
	 * @param chargeTemp the charge's temperature when it lit (ambient = 1)
	 */
	public double preignitionLoss(double chargeTemp) {
		return Math.min(PREIGNITION_MAX_LOSS, Math.max(0, chargeTemp - preignitionTemp) * PREIGNITION_LOSS_PER_DEGREE);
	}

	/**
	 * Where ethanol's charge starts to light early under boost. Its autoignition point (3.4) is kept for compression
	 * and pilot ignition, but it sat so close to what a fast precision engine reaches unboosted (3.2 at 512 RPM) that
	 * twin turbos pre-ignited it even through an intercooler (3.9 measured). This is above what a precision V16 on twin
	 * turbos and an intercooler reaches, so that build runs clean on ethanol and gasoline is the fuel that pays for heat.
	 */
	public static final double ETHANOL_PREIGNITION = 5.0;

	/** How readily a spill of each kind of fuel catches (see {@link #flammability}): the volatile ones light surely and burn off fast. */
	public static final double VOLATILE = 2.0;
	public static final double FLAMMABLE = 1.5;
	/** Diesel-class fuels: safe to store, slow to catch and slow to spread. */
	public static final double HARD_TO_LIGHT = 0.5;

	/**
	 * Pressed seed oil: the runs-on-anything fuel. Rustic, sluggish to ignite, modest energy and the dirtiest exhaust, in exchange
	 * for being tolerant: a cold block costs it less than it costs diesel (2.1: its cold sensitivity went from 1.5 to 0.9, and its
	 * soot and sulfur from 1 to 1.3).
	 */
	public static final FuelSpec PLANT_OIL = new FuelSpec(30, 1.7, 1.6, 0.9, 0, 1.3).withFlammability(HARD_TO_LIGHT);
	/** Ignites readily and carries more energy per mB. */
	public static final FuelSpec DIESEL = new FuelSpec(36, 1.55, 1.7, 1).withFlammability(HARD_TO_LIGHT);
	/**
	 * Diesel made clean: half the soot and no sulfur of diesel, set against a lower energy to the mB (2.1: before it, biodiesel was
	 * burned as diesel). It lights, starts and runs like diesel, a compression fuel.
	 */
	public static final FuelSpec BIODIESEL = new FuelSpec(33, 1.55, 1.7, 1, 0, 0.5, 1.55, 0, HARD_TO_LIGHT);
	/** Too hard to light by compression alone at any speed an engine of this size turns: it needs a spark plug. */
	public static final FuelSpec GASOLINE = new FuelSpec(40, 2.7, 1.5, 0.8).withFlammability(VOLATILE);
	/**
	 * Needs a spark like gasoline, with less energy to the mB. It will not light early under boost that makes gasoline
	 * pre-ignite, boils off so much heat that it cools the charge, and leaves no soot on a plug.
	 */
	public static final FuelSpec ETHANOL = new FuelSpec(27, 3.4, 1.0, 1.8, 0.08, 0, ETHANOL_PREIGNITION).withFlammability(FLAMMABLE);

	/**
	 * Where kerosene's charge starts to light early under boost: between gasoline's (2.7, its autoignition point) and ethanol's
	 * (5.0). A precision engine on twin turbos and an intercooler pre-ignites gasoline and does not pre-ignite kerosene, and a
	 * crude one on one turbo and no intercooler loses some of it to a hot charge, which ethanol does not.
	 */
	public static final double KEROSENE_PREIGNITION = 3.6;
	/**
	 * The all-rounder between diesel and gasoline: more energy to the mB than diesel and a little less than gasoline, a spark
	 * fuel (2.2 is past what a pilot shot can light and past what compression reaches), a touch slower to warm in a cold block
	 * than gasoline, and better than gasoline at holding out against boost.
	 */
	public static final FuelSpec KEROSENE = new FuelSpec(38, 2.2, 1.55, 1.1, 0, 1, KEROSENE_PREIGNITION).withFlammability(HARD_TO_LIGHT);

	/**
	 * Where methanol's charge starts to light early under boost: above ethanol's 5.0, so a build that pushes ethanol's limit has
	 * room left on methanol.
	 */
	public static final double METHANOL_PREIGNITION = 6.5;
	/**
	 * The boost fuel with the least in the bucket: well under ethanol's energy to the mB, so an injector has to deliver far more
	 * of it for the same air, but the hardest of any fuel to light early, the one that cools the charge most, and clean (no soot, no
	 * sulfur). It needs a spark like ethanol and is the worst of the lot in a cold block.
	 */
	public static final FuelSpec METHANOL = new FuelSpec(22, 3.8, 0.8, 1.9, 0.17, 0, METHANOL_PREIGNITION).withFlammability(VOLATILE);

	/**
	 * The light cut other mods refine (Create: The Factory Must Grow's naphtha, anything in {@code c:naphtha}): a little less energy
	 * to the mB than gasoline, harder to light by compression than it, and a touch less sensitive to a cold block. Nothing of this
	 * mod makes it; it exists so that fluid is not dead weight beside an engine.
	 */
	public static final FuelSpec NAPHTHA = new FuelSpec(37, 2.9, 1.45, 0.7).withFlammability(VOLATILE);
}
