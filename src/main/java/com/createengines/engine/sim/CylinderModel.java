package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * One piston-cylinder: a pocket of gas whose volume is set by crank angle, moved by cam-driven valves and heated by
 * cam-triggered fuel injection.
 * <p>
 * The whole model is five rules:
 * <ol>
 *     <li>Volume follows slider-crank geometry; the torque on the crank is the p·dV work.</li>
 *     <li>Gas changes volume adiabatically.</li>
 *     <li>An open valve relaxes cylinder pressure toward its port's pressure at a rate set by lift × flow × time.</li>
 *     <li>Injected fuel ignites after a delay, but only if the charge is hot enough, and burns only as far as fresh
 *     air allows.</li>
 *     <li>(In {@link EngineSimulation}) the rotor's kinetic energy changes by work minus friction and load.</li>
 * </ol>
 * The piston follows the crank; the valves and injector follow the cam. Whether those two agree is entirely down to
 * how the engine was built, and every behaviour (misfires, weak cylinders, stalls, rough running) comes out of that.
 */
public final class CylinderModel {

	private static final double GAMMA = 1.3;
	private static final double EXHAUST_TEMP = 2.5;
	private static final double FUEL_EPSILON = 1e-6;

	public final CylinderSpec spec;
	private final CamProfile profile;
	/** Geometric crank angle (degrees) at which this piston is at top dead centre. */
	private final double tdcCrankAngle;
	private final @Nullable Camshaft camshaft;
	/** Angle of this cylinder's lobe on its camshaft, cam degrees. */
	private final double lobeAngle;

	// Inputs, refreshed by the owner every tick
	public double rack = 1;
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
		copy.intakeFactor = intakeFactor;
		copy.exhaustFactor = exhaustFactor;
		return copy;
	}

	public void resetGas() {
		resetGas(0);
	}

	/** Fills the cylinder with fresh air at ambient pressure for wherever its piston currently is. */
	public void resetGas(double pistonAngle) {
		moles = spec.volumeAt(pistonAngle);
		temperature = 1;
		fresh = 1;
		intakeBackflow = 0;
		intakeBackflowFresh = 1;
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
			exchange(v1, 1, EXHAUST_TEMP, 0, spec.exhaustFlow() * profile.exhaustLift(mid) * exhaustFactor, dt);
		}

		// Rule 4: injection (triggered by the cam), ignition, combustion
		if (camshaft != null && SimMath.crosses(camFrom, camDelta, profile.injection()))
			inject();
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

	private void exchange(double volume, double portPressure, double portTemp, double portFresh, double rate,
						  double dt) {
		if (rate <= 0)
			return;
		double p = pressure(volume);
		double target = p + (portPressure - p) * (1 - Math.exp(-rate * dt));
		if (target > p) {
			double in = (target - p) * volume / portTemp;
			fresh = (moles * fresh + in * portFresh) / (moles + in);
			temperature = (moles * temperature + in * portTemp) / (moles + in);
			moles += in;
		} else {
			moles -= (p - target) * volume / temperature;
		}
	}

	private void inject() {
		FuelSpec fuel = fuelSupply.fuel();
		double delivered = fuel == null ? 0 : fuelSupply.draw(spec.maxFuelPerShot() * rack);
		current.fuelInjected += delivered;
		current.freshAirAtInjection = fresh * moles;
		current.compressionTemp = Math.max(current.compressionTemp, temperature);
		if (delivered <= 0)
			return;
		fuelPending += delivered;
		timeSinceInjection = 0;
		burningFuel = fuel;
	}

	private void combust(double dt) {
		if (!burning) {
			timeSinceInjection += dt;
			if (timeSinceInjection < spec.ignitionDelay() || temperature < burningFuel.autoignitionTemp())
				return;
			burning = true;
			burnRate = fuelPending / spec.burnDuration();
		}
		double airLimited = fresh * moles / burningFuel.airPerMb();
		double burned = Math.min(Math.min(fuelPending, burnRate * dt), airLimited);
		if (burned <= 0)
			return;
		fuelPending -= burned;
		fresh = Math.max(0, (fresh * moles - burned * burningFuel.airPerMb()) / moles);
		temperature += burned * burningFuel.energyPerMb() * (GAMMA - 1) / moles;
		current.fuelBurned += burned;
		if (fuelPending <= FUEL_EPSILON)
			burning = false;
	}

	private void finishCycle() {
		if (current.fuelInjected <= 0)
			current.result = CycleResult.NO_FUEL;
		else if (current.fuelBurned > 0)
			current.result = CycleResult.FIRED;
		else if (current.freshAirAtInjection < 0.05)
			current.result = CycleResult.NO_AIR;
		else
			current.result = CycleResult.NO_IGNITION;

		last.copyFrom(current);
		current.reset();
		fuelPending = 0;
		burning = false;
		travelSinceCycle = 0;
	}
}
