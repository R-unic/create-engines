package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * What happens to the fuel in one cylinder: the injector's shot, the spark or the heat of compression that lights it,
 * and the burn. Moved out of {@link CylinderModel}, which owns the cylinder's inputs, its {@link Charge} and its cycle
 * figures, and calls this from its step.
 */
final class Combustion {

	/** Leaner than this (equivalence ratio) and a spark can't get a flame going. */
	private static final double LEAN_LIMIT = 0.5;
	/**
	 * Heat soaked up by unburned fuel, in moles of gas it is worth: fuel the air can't burn still has to be warmed, so
	 * a rich mixture makes less pressure for the fuel it wastes.
	 */
	private static final double FUEL_HEAT_CAPACITY = 1.5;
	/** Boost, in atmospheres over ambient, at which a hot charge pre-ignites to the full; a lighter boost loses a share of it. */
	private static final double PREIGNITION_FULL_BOOST = 0.5;
	/** The most equivalence ratio that counts toward evaporative cooling: fuel past a rich burn only floods the cylinder. */
	private static final double MAX_COOLED_EQUIVALENCE = 1.2;

	private final CylinderModel owner;
	private final Charge charge;
	private final CycleStats current;
	private final Soiling soiling;

	private double fuelPending;
	private double timeSinceInjection;
	private boolean burning;
	private double burnRate;
	/** Equivalence ratio the charge had when it lit: it sets how fast it burns and how much heat it gives. */
	private double burnEquivalence = 1;
	private @Nullable FuelSpec burningFuel;
	/** How far the last shot's evaporating cooled the charge the next intake stroke draws in, ambient temperatures. */
	private double evaporativeCooling;
	/** Share of the burn's heat the charge lost by lighting itself early, from {@link FuelSpec#preignitionLoss}; 0 for most burns. */
	private double preignitionLoss;

	Combustion(CylinderModel owner, Charge charge, CycleStats current, Soiling soiling) {
		this.owner = owner;
		this.charge = charge;
		this.current = current;
		this.soiling = soiling;
	}

	/** Whether there is fuel in the cylinder still to burn. */
	boolean hasFuel() {
		return fuelPending > CylinderModel.FUEL_EPSILON;
	}

	/** A supply's fuel as the sim may use it (see {@link FuelSpec#usable}); none stays none. */
	static FuelSpec usable(FuelSpec fuel) {
		return fuel == null ? null : fuel.usable();
	}

	boolean burning() {
		return burning;
	}

	double evaporativeCooling() {
		return evaporativeCooling;
	}

	@Nullable FuelSpec burningFuel() {
		return burningFuel;
	}

	/** Nothing waiting, nothing burning, as for a cylinder full of stale gas. */
	void reset() {
		fuelPending = 0;
		burning = false;
		evaporativeCooling = 0;
		preignitionLoss = 0;
	}

	/** The cycle is over: whatever fuel was left is lost with the exhaust. */
	void endCycle() {
		fuelPending = 0;
		burning = false;
	}

	void copyFrom(Combustion other) {
		fuelPending = other.fuelPending;
		timeSinceInjection = other.timeSinceInjection;
		burning = other.burning;
		burnRate = other.burnRate;
		burnEquivalence = other.burnEquivalence;
		evaporativeCooling = other.evaporativeCooling;
		preignitionLoss = other.preignitionLoss;
		burningFuel = other.burningFuel;
	}

	/**
	 * The injector delivers what it was set to deliver, however much air the cylinder drew in. Breathing falls with
	 * speed (valve flow is per second, not per degree), so a setting that suits one speed is rich at a higher one and
	 * lean at a lower one: tuning it to the engine is the player's job. A cylinder with no air at all is still told
	 * apart from one with no fuel.
	 */
	void inject() {
		FuelSpec fuel = usable(owner.fuelSupply.fuel());
		double air = charge.fresh * charge.moles;
		double shot = (fuel == null ? 0 : owner.usualShot(fuel)) * owner.rack * owner.metering * owner.fuelTrim
			* owner.governedFuel();
		if (fuel != null && owner.airMatch > 0 && shot > 0) {
			// A MAF sensor sets the shot from the air, whichever way that moves it. The rack still says how much of it is
			// wanted, and so does a compression engine's governor; a spark engine's throttle is already in the air.
			double matched = owner.rack * (owner.ignition.installed() ? 1 : owner.governor)
				* Math.min(air / fuel.airPerMb() * owner.airMatch, owner.injectorLimit(fuel));
			if (matched < shot)
				current.mafCut = 1 - matched / shot;
			else if (matched > shot)
				current.mafAdded = 1 - shot / matched;
			shot = matched;
		}
		double delivered = fuel == null ? 0 : owner.fuelSupply.draw(shot);
		current.fuelInjected += delivered;
		current.freshAirAtInjection = air;
		current.compressionTemp = Math.max(current.compressionTemp, charge.temperature);
		if (delivered <= 0) {
			evaporativeCooling = 0;
			return;
		}
		if (air >= CylinderModel.MIN_FRESH_AIR)
			current.equivalence = delivered * fuel.airPerMb() / air;
		evaporativeCooling = fuel.chargeCooling() * Math.min(current.equivalence, MAX_COOLED_EQUIVALENCE);
		fuelPending += delivered;
		timeSinceInjection = 0;
		burningFuel = fuel;
	}

	/**
	 * The plug fires once per cycle whether or not there is anything to light. It lights the charge only if there is
	 * fuel waiting and the mixture can carry a flame: a flat battery, or air and fuel too far out of proportion either
	 * way, leaves the fuel to waste.
	 */
	void spark() {
		if (!owner.ignition.installed())
			return;
		if (!owner.ignition.spark()) {
			current.deadSparks++;
			return;
		}
		current.sparks++;
		if (fuelPending <= CylinderModel.FUEL_EPSILON || burning || burningFuel == null)
			return;
		double air = charge.fresh * charge.moles;
		double equivalence = air < CylinderModel.MIN_FRESH_AIR ? Double.MAX_VALUE
			: fuelPending * burningFuel.airPerMb() / air;
		if (equivalence < LEAN_LIMIT)
			current.flameOut = -1;
		else if (equivalence > CylinderModel.RICH_LIMIT)
			current.flameOut = 1;
		else if (equivalence > soiling.richLimit())
			current.flameOut = 2;
		else
			startBurning();
	}

	/**
	 * Whether a pilot shot lights the charge now: the main fuel is too hard to light by compression at this temperature, but
	 * the pilot fuel isn't, and the supply has some to give. A cylinder with a plug uses its plug instead, and a rotor
	 * chamber never lights itself.
	 */
	private boolean pilotLights() {
		if (!Tuning.pilotInjection || owner.ignition.installed())
			return false;
		FuelSpec pilot = usable(owner.pilotSupply.fuel());
		if (pilot == null || charge.temperature < pilot.autoignitionTemp())
			return false;
		double shot = CylinderModel.PILOT_SHARE * current.fuelInjected;
		if (shot <= 0 || owner.pilotSupply.draw(shot) < shot * 0.99)
			return false;
		current.pilotShot += shot;
		return true;
	}

	private void startBurning() {
		burning = true;
		double air = charge.fresh * charge.moles;
		burnEquivalence = air < CylinderModel.MIN_FRESH_AIR ? CylinderModel.RICH_LIMIT
			: fuelPending * burningFuel.airPerMb() / air;
		burnRate = fuelPending / owner.spec.burnDuration() * Mixture.flameSpeed(burnEquivalence);
		// A spark engine's charge that the boost has already squeezed past its autoignition point burns early and loses
		// power for it. A compression engine lights itself on purpose, so it loses nothing, and neither does an unboosted
		// engine: a charge runs hotter with speed alone, and that is the engine's business, not the boost's.
		double boost = Math.min(1, Math.max(0, (owner.intakePressure - 1) / PREIGNITION_FULL_BOOST));
		preignitionLoss = owner.ignition.installed() && boost > 0
			? burningFuel.preignitionLoss(charge.temperature) * boost : 0;
		current.preignition = Math.max(current.preignition, preignitionLoss);
	}

	void combust(double dt) {
		if (!burning) {
			timeSinceInjection += dt;
			// A rotor's long, thin chamber loses too much heat to its walls for the charge to light itself: only a plug will
			if (owner.ported() || timeSinceInjection < owner.spec.ignitionDelay())
				return;
			boolean piloted = false;
			// A spark fuel in an engine with no plug is lit by a pilot shot or not at all, however hot boost has made the
			// charge: a plug-fitted engine's charge past the autoignition point is pre-ignition, which costs power but is
			// still the plug's burn
			boolean selfLit = charge.temperature >= burningFuel.autoignitionTemp()
				&& (owner.ignition.installed() || !burningFuel.needsSpark());
			if (!selfLit) {
				if (!pilotLights())
					return;
				piloted = true;
			}
			startBurning();
			if (piloted)
				burnRate /= CylinderModel.PILOT_BURN_SLOWDOWN;
		}
		double airLimited = charge.fresh * charge.moles / burningFuel.airPerMb();
		double burned = Math.min(Math.min(fuelPending, burnRate * dt), airLimited);
		if (burned <= 0)
			return;
		fuelPending -= burned;
		charge.fresh = Math.max(0, (charge.fresh * charge.moles - burned * burningFuel.airPerMb()) / charge.moles);
		double heat = burned * burningFuel.energyPerMb() * Mixture.efficiency(burnEquivalence)
			* Thermal.burnFactor(owner.coldness, burningFuel.coldSensitivity()) * (1 - preignitionLoss);
		charge.temperature += heat * (CylinderModel.GAMMA - 1) / (charge.moles + FUEL_HEAT_CAPACITY * fuelPending);
		current.fuelBurned += burned;
		if (fuelPending <= CylinderModel.FUEL_EPSILON)
			burning = false;
	}
}
