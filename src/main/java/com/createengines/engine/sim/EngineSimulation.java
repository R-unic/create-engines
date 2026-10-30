package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A crankshaft rotor and the cylinders acting on it.
 * <p>
 * The rotor knows nothing about cylinders beyond "they do work on me". Speed comes from an energy balance:
 * ½Iω² changes by cylinder work minus friction and load work over each crank-angle step. Load is a constant
 * torque, which is exactly what Create's stress model is (every consumer's SU scales with its own speed).
 * <p>
 * Pure Java: no Minecraft classes, so this can be unit-tested directly.
 */
public final class EngineSimulation {

	public static final double TICK = 1 / 20d;
	private static final double MAX_STEP_DEGREES = 4;
	/** Below this the crank is considered stopped. */
	private static final double STALL_OMEGA = rpmToOmega(2);
	/** Oil strength lost per rotor cycle, times how far the unburned share of the fuel is past what counts as rich. */
	private static final double DILUTION_PER_CYCLE = 0.06;
	/** Oil strength regained per rotor cycle spent burning what it is given. */
	private static final double BOIL_OFF_PER_CYCLE = 0.04;

	private final List<CylinderModel> cylinders;
	private final List<Camshaft> camshafts;
	private final double baseInertia;
	private final double baseFriction;
	private final double baseViscousFriction;
	private final Lubrication lubrication = new Lubrication(1);
	private final List<Turbocharger> turbos = new ArrayList<>();

	private boolean washesOil = true;

	private double inertia;
	/** Rotor angle in [0, 720), always increasing in the direction of travel. */
	private double angle;
	/** Angular speed, rad/s, always non-negative. */
	private double omega;
	private int direction = 1;

	// Per-rotor-cycle bookkeeping
	private double cycleProgress;
	private double cycleGasWork;
	private double cycleFrictionWork;
	private double cycleOmegaMin = Double.MAX_VALUE;
	private double cycleOmegaMax;
	private double cycleFuel;
	private double cycleTime;
	private double crankingWork;
	private double crankingAngle;

	// Results of the last complete rotor cycle
	private double indicatedTorque;
	private double netTorque;
	private double ripple;
	private double fuelRate;
	private boolean hasCompletedCycle;

	public EngineSimulation(List<CylinderModel> cylinders, double baseInertia, double baseFriction) {
		this(cylinders, baseInertia, baseFriction, 0);
	}

	/**
	 * The crank starts fully oiled; see {@link #lubrication()}.
	 *
	 * @param baseInertia         inertia of crank segments, flywheels and anything else rigidly on the crank
	 * @param baseFriction        speed-independent friction of the bearings and anything else not a cylinder
	 * @param baseViscousFriction friction per rad/s of anything else not a cylinder, e.g. pistons and rods in a
	 *                            cylinder that has no head to make it work
	 */
	public EngineSimulation(List<CylinderModel> cylinders, double baseInertia, double baseFriction,
							double baseViscousFriction) {
		this.cylinders = List.copyOf(cylinders);
		this.camshafts = cylinders.stream()
			.map(CylinderModel::camshaft)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		this.baseInertia = baseInertia;
		this.baseFriction = baseFriction;
		this.baseViscousFriction = baseViscousFriction;
		this.inertia = baseInertia + cylinders.stream()
			.mapToDouble(c -> c.spec.inertia())
			.sum();
	}

	public static double rpmToOmega(double rpm) {
		return rpm * 2 * Math.PI / 60;
	}

	public static double omegaToRpm(double omega) {
		return omega * 60 / (2 * Math.PI);
	}

	/**
	 * Advances one tick with the crank free to accelerate or decelerate against {@code loadTorque}.
	 *
	 * @param maxOmega hard speed ceiling (Create's maximum rotation speed)
	 */
	public void tickFree(double loadTorque, double maxOmega) {
		tickCranked(loadTorque, maxOmega, 0, direction);
	}

	/**
	 * Advances one tick with something turning the crank through a one-way clutch: it holds the crank at at least
	 * {@code floorOmega}, but the engine is free to outrun it. This is how a starter works, and also how the crank
	 * behaves when another kinetic source drives it. The energy the clutch had to put in is reported by
	 * {@link #crankingTorque()}.
	 *
	 * @param maxOmega hard speed ceiling (Create's maximum rotation speed)
	 */
	public void tickCranked(double loadTorque, double maxOmega, double floorOmega, int floorDirection) {
		runCranked(loadTorque, maxOmega, floorOmega, floorDirection);
		endTick();
	}

	private void runCranked(double loadTorque, double maxOmega, double floorOmega, int floorDirection) {
		crankingWork = 0;
		crankingAngle = 0;
		if (floorOmega > STALL_OMEGA) {
			if (omega <= STALL_OMEGA)
				setDirection(floorDirection);
			omega = Math.max(omega, floorOmega);
		}
		double remaining = TICK;
		while (remaining > 1e-9 && omega > STALL_OMEGA) {
			double delta = Math.min(MAX_STEP_DEGREES, Math.toDegrees(omega * remaining));
			double radians = Math.toRadians(delta);
			double dt = radians / omega;
			double gasWork = advance(delta, dt);
			double friction = frictionTorque() * radians;
			double energy = 0.5 * inertia * omega * omega + gasWork - friction - loadTorque * radians;
			cycleFrictionWork += friction;
			double floorEnergy = 0.5 * inertia * floorOmega * floorOmega;
			if (floorOmega > STALL_OMEGA && energy < floorEnergy) {
				crankingWork += floorEnergy - energy;
				energy = floorEnergy;
			}
			crankingAngle += radians;
			remaining -= dt;
			if (energy <= 0) {
				stall();
				return;
			}
			omega = Math.min(Math.sqrt(2 * energy / inertia), maxOmega);
		}
		if (omega <= STALL_OMEGA)
			stall();
	}

	/** Mean torque the cranking clutch supplied during the last tick (0 if the engine was outrunning it). */
	public double crankingTorque() {
		return crankingAngle > 0 ? crankingWork / crankingAngle : 0;
	}

	/** Advances one tick with the crank held at exactly {@code drivenOmega}, like a dynamometer. */
	public void tickDriven(double drivenOmega, int drivenDirection) {
		setDirection(drivenDirection);
		omega = drivenOmega;
		if (omega > STALL_OMEGA) {
			double remaining = TICK;
			while (remaining > 1e-9) {
				double delta = Math.min(MAX_STEP_DEGREES, Math.toDegrees(omega * remaining));
				double dt = Math.toRadians(delta) / omega;
				advance(delta, dt);
				cycleFrictionWork += frictionTorque() * Math.toRadians(delta);
				remaining -= dt;
			}
		}
		endTick();
	}

	/** Closes the tick's heat accounting and lets each turbo spool on it; they spin down even while the crank is stopped. */
	private void endTick() {
		for (CylinderModel cylinder : cylinders)
			cylinder.endTick();
		for (Turbocharger turbo : turbos)
			turbo.tick(TICK);
	}

	/** Whether unburned fuel thins the oil as it runs. A dynamometer measures the oil as it is and leaves it alone. */
	public void setWashesOil(boolean washesOil) {
		this.washesOil = washesOil;
	}

	/** Adds a turbocharger, which is ticked with the engine. */
	public void addTurbo(Turbocharger turbo) {
		turbos.add(turbo);
	}

	public List<Turbocharger> turbos() {
		return turbos;
	}

	private double advance(double delta, double dt) {
		for (Camshaft camshaft : camshafts)
			camshaft.sync(angle);
		double gasWork = 0;
		for (CylinderModel cylinder : cylinders) {
			Camshaft camshaft = cylinder.camshaft();
			double camDelta = camshaft == null ? 0 : 2 * camshaft.ratio * delta;
			double before = cylinder.currentCycle().fuelInjected;
			gasWork += cylinder.step(cylinder.pistonAngle(angle, direction), delta, cylinder.camCycleAngle(), camDelta,
				dt);
			cycleFuel += Math.max(0, cylinder.currentCycle().fuelInjected - before);
		}
		for (Camshaft camshaft : camshafts)
			camshaft.advance(delta);
		angle = SimMath.wrap(angle + delta);
		cycleGasWork += gasWork;
		cycleTime += dt;
		cycleOmegaMin = Math.min(cycleOmegaMin, omega);
		cycleOmegaMax = Math.max(cycleOmegaMax, omega);
		cycleProgress += delta;
		if (cycleProgress >= SimMath.CYCLE)
			finishRotorCycle();
		return gasWork;
	}

	private void finishRotorCycle() {
		double radians = Math.toRadians(cycleProgress);
		indicatedTorque = cycleGasWork / radians;
		netTorque = (cycleGasWork - cycleFrictionWork) / radians;
		double mean = cycleTime > 0 ? radians / cycleTime : omega;
		ripple = mean > 0 ? (cycleOmegaMax - cycleOmegaMin) / mean : 0;
		fuelRate = cycleTime > 0 ? cycleFuel / cycleTime : 0;
		hasCompletedCycle = true;
		washOil();

		cycleProgress = 0;
		cycleGasWork = 0;
		cycleFrictionWork = 0;
		cycleOmegaMin = Double.MAX_VALUE;
		cycleOmegaMax = 0;
		cycleFuel = 0;
		cycleTime = 0;
	}

	/**
	 * Fuel the cylinders failed to burn washes the oil off their walls and into the sump, thinning it for every part of
	 * the engine however well it is supplied; an engine that burns what it is given boils the fuel back out. Fuel is
	 * only counted from cylinders that took some in, so one with nothing injected neither washes nor cleans.
	 */
	private void washOil() {
		if (!washesOil)
			return;
		double unburned = 0;
		int injecting = 0;
		for (CylinderModel cylinder : cylinders) {
			CycleStats last = cylinder.lastCycle();
			if (last.fuelInjected <= 0)
				continue;
			injecting++;
			unburned += last.unburnedFraction();
		}
		if (injecting == 0)
			return;
		double mean = unburned / injecting;
		if (mean > Mixture.RICH_UNBURNED)
			lubrication.dilute((mean - Mixture.RICH_UNBURNED) * DILUTION_PER_CYCLE);
		else
			lubrication.boilOff(BOIL_OFF_PER_CYCLE);
	}

	private void stall() {
		omega = 0;
		netTorque = 0;
		indicatedTorque = 0;
		fuelRate = 0;
		ripple = 0;
		hasCompletedCycle = false;
		cycleProgress = 0;
		cycleGasWork = 0;
		cycleFrictionWork = 0;
		cycleFuel = 0;
		cycleTime = 0;
	}

	public double frictionTorque() {
		double film = lubrication.effective();
		double friction = baseFriction * Lubrication.staticFactor(film)
			+ baseViscousFriction * omega * Lubrication.viscousFactor(film);
		for (CylinderModel cylinder : cylinders)
			friction += cylinder.spec.frictionAt(omega, film);
		return friction;
	}

	/** How well oiled the moving parts are; the owner keeps it up to date from the oil supply. */
	public Lubrication lubrication() {
		return lubrication;
	}

	/** Crank revolutions turned during the last tick. */
	public double revolutionsLastTick() {
		return crankingAngle / (2 * Math.PI);
	}

	/**
	 * Net torque the engine could deliver at its current speed with every injector's rack fully open. Work from
	 * cylinders that fired scales with the fuel they burned, up to the air they had; everything else (pumping and
	 * friction losses) is what it is.
	 */
	public double availableTorque() {
		if (!hasCompletedCycle)
			return 0;
		double work = 0;
		for (CylinderModel cylinder : cylinders) {
			CycleStats last = cylinder.lastCycle();
			double scale = 1;
			FuelSpec fuel = cylinder.fuelSupply.fuel();
			if (last.result.fired() && last.fuelBurned > 0 && fuel != null) {
				double fullShot = cylinder.spec.maxFuelPerShot() * cylinder.metering * cylinder.fuelTrim;
				double airLimit = last.freshAirAtInjection / fuel.airPerMb();
				// A MAF sensor holds the shot below what the air could burn if it is set to a leaner mixture
				double matched = cylinder.airMatch > 0 ? airLimit * cylinder.airMatch : airLimit;
				scale = Math.max(1, Math.min(fullShot, Math.min(airLimit, matched)) / last.fuelBurned);
			}
			work += last.work * scale;
		}
		return Math.max(0, work / Math.toRadians(SimMath.CYCLE) - frictionTorque());
	}

	/** Whether combustion alone is keeping the crank turning: it fired and made more work than friction took. */
	public boolean isSustaining() {
		if (!hasCompletedCycle || netTorque <= 0)
			return false;
		for (CylinderModel cylinder : cylinders)
			if (cylinder.lastCycle().result.fired())
				return true;
		return false;
	}

	/** Which way the crank turns. Valve timing only suits one direction, so this matters. */
	public void setDirection(int direction) {
		if (direction != 0)
			this.direction = direction > 0 ? 1 : -1;
	}

	/** Restores motion state, e.g. after a topology rebuild or loading from disk. */
	public void restore(double angle, double omega, int direction) {
		setDirection(direction);
		this.angle = SimMath.wrap(angle);
		this.omega = Math.max(0, omega);
		for (Camshaft camshaft : camshafts)
			camshaft.angle = this.angle / 2;
	}

	public List<CylinderModel> cylinders() {
		return cylinders;
	}

	public List<Camshaft> camshafts() {
		return camshafts;
	}

	public double angle() {
		return angle;
	}

	public double omega() {
		return omega;
	}

	public double rpm() {
		return omegaToRpm(omega);
	}

	public int direction() {
		return direction;
	}

	public double inertia() {
		return inertia;
	}

	public double baseInertia() {
		return baseInertia;
	}

	public double baseFriction() {
		return baseFriction;
	}

	public double baseViscousFriction() {
		return baseViscousFriction;
	}

	public double indicatedTorque() {
		return indicatedTorque;
	}

	public double netTorque() {
		return netTorque;
	}

	/** Speed variation over the last cycle as a fraction of mean speed. */
	public double ripple() {
		return ripple;
	}

	/** Fuel injected per second over the last cycle, mB/s. */
	public double fuelRate() {
		return fuelRate;
	}

	public boolean isStopped() {
		return omega <= STALL_OMEGA;
	}
}
