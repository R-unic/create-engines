package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * One piston-cylinder: a pocket of gas whose volume is set by crank angle, moved by cam-driven valves and heated by
 * cam-triggered fuel injection.
 * <p>
 * The whole model is five rules (plus a spark plug, if the cylinder has one):
 * <ol>
 *     <li>Volume follows slider-crank geometry; the torque on the crank is the p·dV work.</li>
 *     <li>Gas changes volume adiabatically.</li>
 *     <li>An open valve relaxes cylinder pressure toward its port's pressure at a rate set by lift × flow × time.</li>
 *     <li>Injected fuel ignites after a delay, but only if the charge is hot enough, and burns only as far as fresh
 *     air allows. With a spark plug it is lit by the spark instead, if the mixture is neither too lean nor too rich.
 *     Fuel it has no air to burn only soaks up heat.</li>
 *     <li>(In {@link EngineSimulation}) the rotor's kinetic energy changes by work minus friction and load.</li>
 * </ol>
 * The piston follows the crank; the valves and injector follow the cam. Whether those two agree is entirely down to
 * how the engine was built, and every behaviour (misfires, weak cylinders, stalls, rough running) comes out of that.
 */
public final class CylinderModel {

	static final double GAMMA = 1.3;
	/** Moles of breathed-out gas it takes to replace what the exhaust port is holding. */
	private static final double EXHAUST_MEMORY = 1;
	/** Seconds for the gas in the exhaust port to leave and be replaced by ambient air. */
	private static final double EXHAUST_CLEARING = 1;
	private static final double FUEL_EPSILON = 1e-6;
	/** Fresh charge below which a cylinder counts as having no air at all, rather than too little for the shot. */
	private static final double MIN_FRESH_AIR = 0.05;
	/** Leaner or richer than this (equivalence ratio) and a spark can't get a flame going. */
	private static final double LEAN_LIMIT = 0.5;
	private static final double RICH_LIMIT = 1.8;
	/**
	 * Heat soaked up by unburned fuel, in moles of gas it is worth: fuel the air can't burn still has to be warmed, so
	 * a rich mixture makes less pressure for the fuel it wastes.
	 */
	private static final double FUEL_HEAT_CAPACITY = 1.5;
	/** Soot a plug gains per cycle that burns rich, times the share of that cycle's fuel the air couldn't burn. */
	private static final double FOULING_RATE = 0.12;
	/** Soot a plug loses each cycle it fires on a mixture that burns clean. */
	private static final double FOULING_BURN_OFF = 0.12;
	/**
	 * Soot a plug sparks off each cycle the charge was too rich to light. Soot only builds from a rich burn, and a plug
	 * that has stopped lighting is hot and dry again a few cycles later, so rich running makes an engine stutter instead
	 * of locking it out until the mixture is fixed by hand.
	 */
	private static final double FOULING_SCRUB = 0.04;
	/**
	 * How far a completely fouled plug pulls the rich limit in. It can still light a mixture a little richer than the
	 * balanced band, so a beginner's flooded engine coughs rather than refusing to fire.
	 */
	private static final double FOULED_RICH_LOSS = RICH_LIMIT - 1.4;

	public final CylinderSpec spec;
	private final CylinderSpec.Geometry geometry;
	private final CamProfile.Curve intakeCurve;
	private final CamProfile.Curve exhaustCurve;
	/**
	 * Degrees of a rotary chamber's cycle for each degree of eccentric shaft: the rotor turns at a third of the shaft,
	 * and a chamber goes through two volume cycles (four strokes) per turn of the rotor.
	 */
	public static final double ROTARY_SCALE = 2 / 3d;
	/** Eccentric shaft degrees between one chamber of a rotor reaching a given stroke and the next one doing so. */
	public static final double ROTARY_CHAMBER_SPACING = 360;
	/** Eccentric shaft degrees in a chamber's whole four-stroke cycle: one turn of the rotor. */
	public static final double ROTARY_CYCLE = 1080;

	private final CamProfile profile;
	/** Geometric crank angle (degrees) at which this piston is at top dead centre. */
	private final double tdcCrankAngle;
	private final @Nullable Camshaft camshaft;
	/** Angle of this cylinder's lobe on its camshaft, cam degrees. */
	private final double lobeAngle;
	/**
	 * A rotary chamber has no cam: the housing's ports are cut at fixed places, so its valves and its injection follow
	 * its own cycle angle. {@link #angleScale} turns shaft degrees into that angle.
	 */
	private final boolean ported;
	private final double angleScale;
	/** Which of the rotor's chambers this is: its cycle runs this far ahead of the first one's, in cycle degrees. */
	private final double cycleOffset;

	// Inputs, refreshed by the owner every tick
	public double rack = 1;
	/** Share of {@link CylinderSpec#maxFuelPerShot()} the injector is set to deliver, set by hand. */
	public double metering = 1;
	public IgnitionSource ignition = IgnitionSource.NONE;
	public double intakeFactor = 1;
	/** Share of the shot a throttle body's linkage lets through; 1 where there is no throttle. */
	public double fuelTrim = 1;
	/**
	 * Equivalence ratio a MAF sensor holds the shot to, or 0 where there is none. The injector never delivers more
	 * fuel than the air the cylinder drew in can burn at this ratio; it is a cap, so it only ever cuts.
	 */
	public double airMatch = 0;
	/** A rev limiter is holding the injector shut. */
	public boolean fuelCut;
	/**
	 * Rack position a mechanical governor holds the engine at, 0 (shut) to 1 (wide open); 1 where there is none. A
	 * compression engine is governed on fuel alone. A spark engine can't be, because a lean charge won't light, so the
	 * governor throttles its air and the throttle linkage trims its fuel to follow. Not part of the engine's rating: a
	 * governor holds speed with the torque in hand, it does not take any away.
	 */
	public double governor = 1;
	public double exhaustFactor = 1;
	/** Pressure of the air the intake valve draws on, ambient (1) unless a compressor is pushing it. */
	public double intakePressure = 1;
	/** Temperature of that air: compressing it heats it, which costs some of the density the pressure bought. */
	public double intakeTemperature = 1;
	/** Pressure the exhaust valve blows down into, ambient (1) unless a turbine is in the way. */
	public double exhaustPressure = 1;
	public FuelSupply fuelSupply = FuelSupply.NONE;

	// Gas state: amount, temperature, and fraction that is still fresh (unburned) air
	private double moles;
	private double temperature;
	private double fresh;

	/** Gas pushed back into the intake manifold, and how much of it is still fresh air. */
	private double intakeBackflow;
	private double intakeBackflowFresh = 1;

	/** Temperature of the spent gas in the exhaust port, which is what reversion draws back in. Ambient until it fires. */
	private double exhaustTemp = 1;
	/** Heat carried out of the exhaust valve since the tick began, and during the last whole tick. */
	private double exhaustHeat;
	private double exhaustHeatLastTick;

	private double fuelPending;
	private double timeSinceInjection;
	private boolean burning;
	private double burnRate;
	/** Equivalence ratio the charge had when it lit: it sets how fast it burns and how much heat it gives. */
	private double burnEquivalence = 1;
	private FuelSpec burningFuel;
	/** Soot on the spark plug, 0 (clean) to 1: rich running builds it, a mixture that burns clean cleans it off. */
	private double fouling;
	private double travelSinceCycle;

	/** How the exhaust port's gas decays over the last step length, which is the same for every step but a tick's last. */
	private double clearingDt = Double.NaN;
	private double clearingDecay;

	private final CycleStats current = new CycleStats();
	private final CycleStats last = new CycleStats();

	public CylinderModel(CylinderSpec spec, CamProfile profile, double tdcCrankAngle, @Nullable Camshaft camshaft,
						 double lobeAngle) {
		this(spec, profile, tdcCrankAngle, camshaft, lobeAngle, false, 1, 0);
	}

	private CylinderModel(CylinderSpec spec, CamProfile profile, double tdcCrankAngle, @Nullable Camshaft camshaft,
						  double lobeAngle, boolean ported, double angleScale, double cycleOffset) {
		this.spec = spec;
		this.geometry = spec.geometry();
		this.profile = profile;
		this.intakeCurve = profile.intakeCurve();
		this.exhaustCurve = profile.exhaustCurve();
		this.tdcCrankAngle = tdcCrankAngle;
		this.camshaft = camshaft;
		this.lobeAngle = lobeAngle;
		this.ported = ported;
		this.angleScale = angleScale;
		this.cycleOffset = cycleOffset;
		resetGas();
	}

	/**
	 * One chamber of a Wankel rotor. {@code eccentricTdc} is the shaft angle at which the rotor's eccentric points at the
	 * housing's reference mark, and {@code chamber} (0 to 2) which of the rotor's three faces this is.
	 */
	public static CylinderModel rotary(CylinderSpec spec, CamProfile profile, double eccentricTdc, int chamber) {
		return new CylinderModel(spec, profile, eccentricTdc, null, 0, true, ROTARY_SCALE,
			chamber * ROTARY_CHAMBER_SPACING * ROTARY_SCALE);
	}

	/** Crank angle of this piston past its top dead centre, for a rotor angle travelling in {@code direction}. */
	public double pistonAngle(double rotorAngle, int direction) {
		return pistonAngle(rotorAngle, rotorAngle, direction);
	}

	/**
	 * As {@link #pistonAngle(double, int)}, given the angle the shaft has turned through in the {@link #ROTARY_CYCLE} a
	 * rotary chamber repeats over. A rotary chamber's cycle angle is where it is in its four strokes: the shaft turns
	 * three times to its once.
	 */
	public double pistonAngle(double rotorAngle, double rotaryAngle, int direction) {
		if (!ported)
			return SimMath.wrap(rotorAngle - direction * tdcCrankAngle);
		double shaft = SimMath.wrap(rotaryAngle - direction * tdcCrankAngle, ROTARY_CYCLE);
		return SimMath.wrap(shaft * angleScale + cycleOffset);
	}

	/** Whether this chamber's valves and injection follow its own cycle rather than a camshaft's. */
	public boolean ported() {
		return ported;
	}

	/** How many degrees of this cylinder's own cycle go by for each degree the shaft turns. */
	public double angleScale() {
		return angleScale;
	}

	/** Whether something fixes when its valves open and its injector fires: a cam, or a rotary housing's ports. */
	private boolean timed() {
		return ported || camshaft != null;
	}

	/** Shaft degrees one four-stroke cycle of this cylinder takes: two turns for a piston, three for a rotary chamber. */
	public double cycleDegrees() {
		return ported ? ROTARY_CYCLE : SimMath.CYCLE;
	}

	/** Whether valve timing is locked to the shaft: a cam geared 1:2, or fixed ports. */
	public boolean isTimed() {
		return ported || camshaft != null && camshaft.isPhaseLocked();
	}

	/** Where the cam says this cylinder is in its cycle (0 = intake TDC, 360 = firing TDC). */
	public double camCycleAngle() {
		return camshaft == null ? 0 : camCycleAngle(camshaft.angle(), lobeAngle);
	}

	public static double camCycleAngle(double camAngle, double lobeAngle) {
		return SimMath.wrap(2 * (camAngle - lobeAngle) + 360);
	}

	/**
	 * How far the cam's firing TDC is from the piston's top dead centre, in crank degrees within ±180. A cam can be
	 * right on either crank revolution (that choice is the firing order), so this is measured modulo 360.
	 * Only meaningful when the cam is phase-locked.
	 */
	public double timingError(int direction) {
		double error = (2 * lobeAngle - direction * tdcCrankAngle) % 360;
		if (error > 180)
			error -= 360;
		if (error <= -180)
			error += 360;
		return error;
	}

	/** Rotor angle at which the cam fires this cylinder, assuming the cam is phase-locked. */
	public double firingRotorAngle() {
		return SimMath.wrap(2 * lobeAngle);
	}

	public @Nullable Camshaft camshaft() {
		return camshaft;
	}

	public double lobeAngle() {
		return lobeAngle;
	}

	public double tdcCrankAngle() {
		return tdcCrankAngle;
	}

	/** A fresh cylinder built the same way on {@code camshaft}, to test apart from the engine. */
	public CylinderModel copyOnto(@Nullable Camshaft camshaft) {
		CylinderModel copy =
			new CylinderModel(spec, profile, tdcCrankAngle, camshaft, lobeAngle, ported, angleScale, cycleOffset);
		copy.rack = rack;
		copy.metering = metering;
		copy.ignition = ignition;
		copy.intakeFactor = intakeFactor;
		copy.fuelTrim = fuelTrim;
		copy.airMatch = airMatch;
		copy.exhaustFactor = exhaustFactor;
		copy.intakePressure = intakePressure;
		copy.intakeTemperature = intakeTemperature;
		copy.exhaustPressure = exhaustPressure;
		return copy;
	}

	/**
	 * Closes a tick's accounting of the heat this cylinder sent out of its exhaust valve, in moles of gas times how far
	 * hotter than ambient it was. That is what a turbine has to work with: gas that was never heated carries nothing.
	 */
	void endTick() {
		exhaustHeatLastTick = exhaustHeat;
		exhaustHeat = 0;
	}

	/** Heat this cylinder put into its exhaust during the last whole tick. */
	public double exhaustHeatLastTick() {
		return exhaustHeatLastTick;
	}

	public void resetGas() {
		resetGas(0);
	}

	/**
	 * Fills the cylinder with stale gas at ambient pressure for wherever its piston currently is. It holds no fresh
	 * air to burn: a cylinder has to breathe in through its intake before it can fire, so one with no intake never
	 * does.
	 */
	public void resetGas(double pistonAngle) {
		moles = spec.volumeAt(pistonAngle);
		temperature = 1;
		fresh = 0;
		intakeBackflow = 0;
		intakeBackflowFresh = 1;
		exhaustTemp = 1;
		exhaustHeat = 0;
		exhaustHeatLastTick = 0;
		fuelPending = 0;
		burning = false;
	}

	/** Takes over another model's gas and cycle state, e.g. when the engine is rebuilt around this cylinder. */
	public void copyStateFrom(CylinderModel other) {
		moles = other.moles;
		temperature = other.temperature;
		fresh = other.fresh;
		intakeBackflow = other.intakeBackflow;
		intakeBackflowFresh = other.intakeBackflowFresh;
		exhaustTemp = other.exhaustTemp;
		exhaustHeat = other.exhaustHeat;
		exhaustHeatLastTick = other.exhaustHeatLastTick;
		fuelPending = other.fuelPending;
		timeSinceInjection = other.timeSinceInjection;
		burning = other.burning;
		burnRate = other.burnRate;
		burnEquivalence = other.burnEquivalence;
		fouling = other.fouling;
		burningFuel = other.burningFuel;
		travelSinceCycle = other.travelSinceCycle;
		current.copyFrom(other.current);
		last.copyFrom(other.last);
	}

	public CycleStats lastCycle() {
		return last;
	}

	/** Soot on the spark plug from 0 to 1. A fouled plug can't light a rich mixture it otherwise could. */
	public double fouling() {
		return fouling;
	}

	public CycleStats currentCycle() {
		return current;
	}

	/**
	 * Advances this cylinder by {@code delta} crank degrees taking {@code dt} seconds.
	 *
	 * @param pistonFrom  piston crank angle past TDC at the start of the step
	 * @param camFrom     cam-cycle angle at the start of the step
	 * @param camDelta    how far the cam-cycle angle moves during the step
	 * @return p·dV work done on the crank during the step
	 */
	double step(double pistonFrom, double delta, double camFrom, double camDelta, double dt) {
		double v0 = geometry.volume(pistonFrom);
		double v1 = geometry.volume(pistonFrom + delta);

		// Rule 2: adiabatic volume change (T times V^(γ-1) stays put). Rule 1: work relative to the crankcase at ambient pressure.
		double p0 = pressure(v0);
		temperature *= geometry.heat(pistonFrom) / geometry.heat(pistonFrom + delta);
		double p1 = pressure(v1);
		double work = (0.5 * (p0 + p1) - 1) * (v1 - v0);

		// Rule 3: valves, opened by the cam
		if (timed()) {
			double mid = SimMath.wrap(camFrom + camDelta / 2);
			exchangeIntake(v1, spec.intakeFlow() * intakeCurve.at(mid) * intakeFactor * governedAir(), dt);
			exchangeExhaust(v1, spec.exhaustFlow() * exhaustCurve.at(mid) * exhaustFactor, dt);
		}

		// Rule 4: injection (triggered by the cam), ignition, combustion
		if (timed() && SimMath.crosses(camFrom, camDelta, profile.injection()))
			inject();
		if (timed() && SimMath.crosses(camFrom, camDelta, profile.spark()))
			spark();
		if (fuelPending > FUEL_EPSILON)
			combust(dt);
		if (fuelPending > FUEL_EPSILON && !burning)
			current.compressionTemp = Math.max(current.compressionTemp, temperature);
		current.peakPressure = Math.max(current.peakPressure, pressure(v1));
		current.work += work;

		// A cycle ends when the cam starts the next intake stroke, or after two revolutions if the cam isn't turning
		travelSinceCycle += delta;
		boolean camWrapped = timed() && SimMath.crosses(camFrom, camDelta, 0);
		if (camWrapped || travelSinceCycle >= SimMath.CYCLE)
			finishCycle();
		return work;
	}

	private double pressure(double volume) {
		return moles * temperature / volume;
	}

	/** How open the governor leaves the intake: a spark engine is throttled, a compression engine breathes freely. */
	private double governedAir() {
		return ignition.installed() ? Governor.opening(governor) : 1;
	}

	/** Share of the shot the governor lets through. */
	private double governedFuel() {
		return ignition.installed() ? Throttle.fuelTrim(Governor.opening(governor)) : governor;
	}

	/**
	 * The intake port is a manifold, not open sky: gas pushed back out through the intake valve (reversion, or all of
	 * it if the exhaust can't open) waits in the manifold, cools, and is the first thing drawn back in.
	 */
	private void exchangeIntake(double volume, double rate, double dt) {
		if (rate <= 0)
			return;
		double p = pressure(volume);
		double target = p + (intakePressure - p) * SimMath.approach(rate * dt);
		if (target < p) {
			double out = (p - target) * volume / temperature;
			intakeBackflowFresh = (intakeBackflow * intakeBackflowFresh + out * fresh) / (intakeBackflow + out);
			intakeBackflow += out;
			moles -= out;
			return;
		}
		// Gas at the intake's temperature is what raises the pressure, so a hotter charge means fewer moles for it
		double in = (target - p) * volume / intakeTemperature;
		double reingested = Math.min(intakeBackflow, in);
		double inFresh = in <= 0 ? 1 : (reingested * intakeBackflowFresh + (in - reingested)) / in;
		intakeBackflow -= reingested;
		fresh = (moles * fresh + in * inFresh) / (moles + in);
		temperature = (moles * temperature + in * intakeTemperature) / (moles + in);
		moles += in;
	}

	/**
	 * The exhaust port holds spent gas: it never counts as fresh air. It is hot only if this cylinder has just burned
	 * fuel, and that heat is what reversion draws back in. A cylinder that isn't burning anything can't get hot gas
	 * out of its exhaust (that would be heat from nowhere), and the gas in the port drifts back to ambient anyway.
	 */
	private void exchangeExhaust(double volume, double rate, double dt) {
		if (dt != clearingDt) {
			clearingDt = dt;
			clearingDecay = Math.exp(-dt / EXHAUST_CLEARING);
		}
		exhaustTemp = 1 + (exhaustTemp - 1) * clearingDecay;
		if (rate <= 0)
			return;
		double p = pressure(volume);
		double target = p + (exhaustPressure - p) * SimMath.approach(rate * dt);
		if (target > p) {
			double in = (target - p) * volume / exhaustTemp;
			fresh = moles * fresh / (moles + in);
			temperature = (moles * temperature + in * exhaustTemp) / (moles + in);
			moles += in;
		} else {
			double out = (p - target) * volume / temperature;
			if (current.fuelBurned > 0 || last.fuelBurned > 0) {
				exhaustTemp += (temperature - exhaustTemp) * Math.min(1, out / EXHAUST_MEMORY);
				// Only burning makes heat: a turbine can't be spun by compressed air that was never lit
				exhaustHeat += out * Math.max(0, temperature - 1);
			}
			moles -= out;
		}
	}

	/**
	 * The injector delivers what it was set to deliver, however much air the cylinder drew in. Breathing falls with
	 * speed (valve flow is per second, not per degree), so a setting that suits one speed is rich at a higher one and
	 * lean at a lower one: tuning it to the engine is the player's job. A cylinder with no air at all is still told
	 * apart from one with no fuel.
	 */
	private void inject() {
		FuelSpec fuel = fuelSupply.fuel();
		double air = fresh * moles;
		double shot = spec.maxFuelPerShot() * rack * metering * fuelTrim * governedFuel();
		if (fuel != null && airMatch > 0 && shot > 0) {
			double matched = air / fuel.airPerMb() * airMatch;
			if (matched < shot) {
				current.mafCut = 1 - matched / shot;
				shot = matched;
			}
		}
		if (fuelCut) {
			current.limited = true;
			shot = 0;
		}
		double delivered = fuel == null ? 0 : fuelSupply.draw(shot);
		current.fuelInjected += delivered;
		current.freshAirAtInjection = air;
		current.compressionTemp = Math.max(current.compressionTemp, temperature);
		if (delivered <= 0)
			return;
		if (air >= MIN_FRESH_AIR)
			current.equivalence = delivered * fuel.airPerMb() / air;
		fuelPending += delivered;
		timeSinceInjection = 0;
		burningFuel = fuel;
	}

	/**
	 * The plug fires once per cycle whether or not there is anything to light. It lights the charge only if there is
	 * fuel waiting and the mixture can carry a flame: a flat battery, or air and fuel too far out of proportion either
	 * way, leaves the fuel to waste.
	 */
	private void spark() {
		if (!ignition.installed())
			return;
		if (!ignition.spark()) {
			current.deadSparks++;
			return;
		}
		current.sparks++;
		if (fuelPending <= FUEL_EPSILON || burning || burningFuel == null)
			return;
		double air = fresh * moles;
		double equivalence = air < MIN_FRESH_AIR ? Double.MAX_VALUE : fuelPending * burningFuel.airPerMb() / air;
		if (equivalence < LEAN_LIMIT)
			current.flameOut = -1;
		else if (equivalence > RICH_LIMIT)
			current.flameOut = 1;
		else if (equivalence > RICH_LIMIT - FOULED_RICH_LOSS * fouling)
			current.flameOut = 2;
		else
			startBurning();
	}

	private void startBurning() {
		burning = true;
		double air = fresh * moles;
		burnEquivalence = air < MIN_FRESH_AIR ? RICH_LIMIT : fuelPending * burningFuel.airPerMb() / air;
		burnRate = fuelPending / spec.burnDuration() * Mixture.flameSpeed(burnEquivalence);
	}

	private void combust(double dt) {
		if (!burning) {
			timeSinceInjection += dt;
			// A rotor's long, thin chamber loses too much heat to its walls for the charge to light itself: only a plug will
			if (ported || timeSinceInjection < spec.ignitionDelay() || temperature < burningFuel.autoignitionTemp())
				return;
			startBurning();
		}
		double airLimited = fresh * moles / burningFuel.airPerMb();
		double burned = Math.min(Math.min(fuelPending, burnRate * dt), airLimited);
		if (burned <= 0)
			return;
		fuelPending -= burned;
		fresh = Math.max(0, (fresh * moles - burned * burningFuel.airPerMb()) / moles);
		double heat = burned * burningFuel.energyPerMb() * Mixture.efficiency(burnEquivalence);
		temperature += heat * (GAMMA - 1) / (moles + FUEL_HEAT_CAPACITY * fuelPending);
		current.fuelBurned += burned;
		if (fuelPending <= FUEL_EPSILON)
			burning = false;
	}

	private void finishCycle() {
		if (current.fuelInjected <= 0)
			current.result = current.limited ? CycleResult.FUEL_CUT
				// A MAF sensor that cut the shot to nothing did so because there was nothing to burn it with
				: current.mafCut > 0 && current.freshAirAtInjection < MIN_FRESH_AIR ? CycleResult.NO_AIR
					: CycleResult.NO_FUEL;
		else if (current.fuelBurned > 0)
			current.result = CycleResult.FIRED;
		else if (current.freshAirAtInjection < MIN_FRESH_AIR)
			current.result = CycleResult.NO_AIR;
		else if (current.deadSparks > 0 && current.sparks == 0)
			current.result = CycleResult.NO_SPARK;
		else if (current.flameOut < 0)
			current.result = CycleResult.TOO_LEAN;
		else if (current.flameOut == 2)
			current.result = CycleResult.FOULED;
		else if (current.flameOut > 0)
			current.result = CycleResult.TOO_RICH;
		else
			current.result = CycleResult.NO_IGNITION;

		if (ignition.installed() && current.fuelInjected > 0) {
			double unburned = current.unburnedFraction();
			if (current.fuelBurned <= 0) {
				if (current.sparks > 0)
					fouling = Math.max(0, fouling - FOULING_SCRUB);
			} else if (unburned > Mixture.RICH_UNBURNED)
				fouling = Math.min(1, fouling + FOULING_RATE * unburned);
			else
				fouling = Math.max(0, fouling - FOULING_BURN_OFF);
		}

		last.copyFrom(current);
		current.reset();
		fuelPending = 0;
		burning = false;
		travelSinceCycle = 0;
	}
}
