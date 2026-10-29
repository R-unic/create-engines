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

	private static final double GAMMA = 1.3;
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

	public final CylinderSpec spec;
	private final CamProfile profile;
	/** Geometric crank angle (degrees) at which this piston is at top dead centre. */
	private final double tdcCrankAngle;
	private final @Nullable Camshaft camshaft;
	/** Angle of this cylinder's lobe on its camshaft, cam degrees. */
	private final double lobeAngle;

	// Inputs, refreshed by the owner every tick
	public double rack = 1;
	/** Share of {@link CylinderSpec#maxFuelPerShot()} the injector is set to deliver, set by hand. */
	public double metering = 1;
	public IgnitionSource ignition = IgnitionSource.NONE;
	public double intakeFactor = 1;
	public double exhaustFactor = 1;
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

	private double fuelPending;
	private double timeSinceInjection;
	private boolean burning;
	private double burnRate;
	private FuelSpec burningFuel;
	private double travelSinceCycle;

	private final CycleStats current = new CycleStats();
	private final CycleStats last = new CycleStats();

	public CylinderModel(CylinderSpec spec, CamProfile profile, double tdcCrankAngle, @Nullable Camshaft camshaft,
						 double lobeAngle) {
		this.spec = spec;
		this.profile = profile;
		this.tdcCrankAngle = tdcCrankAngle;
		this.camshaft = camshaft;
		this.lobeAngle = lobeAngle;
		resetGas();
	}

	/** Crank angle of this piston past its top dead centre, for a rotor angle travelling in {@code direction}. */
	public double pistonAngle(double rotorAngle, int direction) {
		return SimMath.wrap(rotorAngle - direction * tdcCrankAngle);
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
		CylinderModel copy = new CylinderModel(spec, profile, tdcCrankAngle, camshaft, lobeAngle);
		copy.rack = rack;
		copy.metering = metering;
		copy.ignition = ignition;
		copy.intakeFactor = intakeFactor;
		copy.exhaustFactor = exhaustFactor;
		return copy;
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
		fuelPending = other.fuelPending;
		timeSinceInjection = other.timeSinceInjection;
		burning = other.burning;
		burnRate = other.burnRate;
		burningFuel = other.burningFuel;
		travelSinceCycle = other.travelSinceCycle;
		current.copyFrom(other.current);
		last.copyFrom(other.last);
	}

	public CycleStats lastCycle() {
		return last;
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
		double v0 = spec.volumeAt(pistonFrom);
		double v1 = spec.volumeAt(pistonFrom + delta);

		// Rule 2: adiabatic volume change. Rule 1: work relative to the crankcase at ambient pressure.
		double p0 = pressure(v0);
		temperature *= Math.pow(v0 / v1, GAMMA - 1);
		double p1 = pressure(v1);
		double work = (0.5 * (p0 + p1) - 1) * (v1 - v0);

		// Rule 3: valves, opened by the cam
		if (camshaft != null) {
			double mid = SimMath.wrap(camFrom + camDelta / 2);
			exchangeIntake(v1, spec.intakeFlow() * profile.intakeLift(mid) * intakeFactor, dt);
			exchangeExhaust(v1, spec.exhaustFlow() * profile.exhaustLift(mid) * exhaustFactor, dt);
		}

		// Rule 4: injection (triggered by the cam), ignition, combustion
		if (camshaft != null && SimMath.crosses(camFrom, camDelta, profile.injection()))
			inject();
		if (camshaft != null && SimMath.crosses(camFrom, camDelta, profile.spark()))
			spark();
		if (fuelPending > FUEL_EPSILON)
			combust(dt);
		if (fuelPending > FUEL_EPSILON && !burning)
			current.compressionTemp = Math.max(current.compressionTemp, temperature);
		current.peakPressure = Math.max(current.peakPressure, pressure(v1));
		current.work += work;

		// A cycle ends when the cam starts the next intake stroke, or after two revolutions if the cam isn't turning
		travelSinceCycle += delta;
		boolean camWrapped = camshaft != null && SimMath.crosses(camFrom, camDelta, 0);
		if (camWrapped || travelSinceCycle >= SimMath.CYCLE)
			finishCycle();
		return work;
	}

	private double pressure(double volume) {
		return moles * temperature / volume;
	}

	/**
	 * The intake port is a manifold, not open sky: gas pushed back out through the intake valve (reversion, or all of
	 * it if the exhaust can't open) waits in the manifold, cools, and is the first thing drawn back in.
	 */
	private void exchangeIntake(double volume, double rate, double dt) {
		if (rate <= 0)
			return;
		double p = pressure(volume);
		double target = p + (1 - p) * (1 - Math.exp(-rate * dt));
		if (target < p) {
			double out = (p - target) * volume / temperature;
			intakeBackflowFresh = (intakeBackflow * intakeBackflowFresh + out * fresh) / (intakeBackflow + out);
			intakeBackflow += out;
			moles -= out;
			return;
		}
		double in = (target - p) * volume;
		double reingested = Math.min(intakeBackflow, in);
		double inFresh = in <= 0 ? 1 : (reingested * intakeBackflowFresh + (in - reingested)) / in;
		intakeBackflow -= reingested;
		fresh = (moles * fresh + in * inFresh) / (moles + in);
		temperature = (moles * temperature + in) / (moles + in);
		moles += in;
	}

	/**
	 * The exhaust port holds spent gas: it never counts as fresh air. It is hot only if this cylinder has just burned
	 * fuel, and that heat is what reversion draws back in. A cylinder that isn't burning anything can't get hot gas
	 * out of its exhaust (that would be heat from nowhere), and the gas in the port drifts back to ambient anyway.
	 */
	private void exchangeExhaust(double volume, double rate, double dt) {
		exhaustTemp = 1 + (exhaustTemp - 1) * Math.exp(-dt / EXHAUST_CLEARING);
		if (rate <= 0)
			return;
		double p = pressure(volume);
		double target = p + (1 - p) * (1 - Math.exp(-rate * dt));
		if (target > p) {
			double in = (target - p) * volume / exhaustTemp;
			fresh = moles * fresh / (moles + in);
			temperature = (moles * temperature + in * exhaustTemp) / (moles + in);
			moles += in;
		} else {
			double out = (p - target) * volume / temperature;
			if (current.fuelBurned > 0 || last.fuelBurned > 0)
				exhaustTemp += (temperature - exhaustTemp) * Math.min(1, out / EXHAUST_MEMORY);
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
		double shot = spec.maxFuelPerShot() * rack * metering;
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
		else
			startBurning();
	}

	private void startBurning() {
		burning = true;
		burnRate = fuelPending / spec.burnDuration();
	}

	private void combust(double dt) {
		if (!burning) {
			timeSinceInjection += dt;
			if (timeSinceInjection < spec.ignitionDelay() || temperature < burningFuel.autoignitionTemp())
				return;
			startBurning();
		}
		double airLimited = fresh * moles / burningFuel.airPerMb();
		double burned = Math.min(Math.min(fuelPending, burnRate * dt), airLimited);
		if (burned <= 0)
			return;
		fuelPending -= burned;
		fresh = Math.max(0, (fresh * moles - burned * burningFuel.airPerMb()) / moles);
		temperature += burned * burningFuel.energyPerMb() * (GAMMA - 1) / (moles + FUEL_HEAT_CAPACITY * fuelPending);
		current.fuelBurned += burned;
		if (fuelPending <= FUEL_EPSILON)
			burning = false;
	}

	private void finishCycle() {
		if (current.fuelInjected <= 0)
			current.result = CycleResult.NO_FUEL;
		else if (current.fuelBurned > 0)
			current.result = CycleResult.FIRED;
		else if (current.freshAirAtInjection < MIN_FRESH_AIR)
			current.result = CycleResult.NO_AIR;
		else if (current.deadSparks > 0 && current.sparks == 0)
			current.result = CycleResult.NO_SPARK;
		else if (current.flameOut < 0)
			current.result = CycleResult.TOO_LEAN;
		else if (current.flameOut > 0)
			current.result = CycleResult.TOO_RICH;
		else
			current.result = CycleResult.NO_IGNITION;

		last.copyFrom(current);
		current.reset();
		fuelPending = 0;
		burning = false;
		travelSinceCycle = 0;
	}
}
