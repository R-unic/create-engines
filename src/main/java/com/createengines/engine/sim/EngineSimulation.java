package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
	/**
	 * A tick is cut into crank-angle steps of at most this many degrees, which at Create's usual speeds is as fine as the
	 * model is calibrated to. Faster than {@link #STEPS_PER_TICK} of them would take, the step grows to keep a tick's
	 * cost from climbing with speed, up to {@link #MAX_STEP_DEGREES}: gas exchange and burning are timed in seconds, so
	 * a fast engine changes little over a few more degrees, and a step this coarse measures the same torque to within
	 * a percent from 1000 RPM up.
	 */
	static final double MIN_STEP_DEGREES = 4;
	static final double MAX_STEP_DEGREES = 16;
	static final double STEPS_PER_TICK = 64;
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
	/** A new simulation is warm; a live engine's owner starts it cold and switches {@link #setThermalLive} on. */
	private final Thermal thermal = new Thermal(Thermal.NORMAL);
	private CoolantSupply coolant = CoolantSupply.NONE;
	/**
	 * Whether the block warms and cools as the engine runs. Off for a dynamometer's copy, which holds the temperature of
	 * the engine it is measuring, and for a bare simulation, which stays at the warm temperature that costs and earns nothing.
	 */
	private boolean thermalLive;
	private final List<Turbocharger> turbos = new ArrayList<>();
	private final List<Supercharger> superchargers = new ArrayList<>();
	private final List<Intercooler> intercoolers = new ArrayList<>();

	private boolean washesOil = true;
	/** Measures how unevenly a driven engine turns the crank; see {@link #trackSwing()}. Null unless asked for. */
	private Swing swing;

	private double inertia;
	/** Friction torque not tied to speed, and per rad/s, at the oil the tick began with; see {@link #measureFriction()}. */
	private double stepFriction;
	private double stepViscousFriction;
	/** Rotor angle in [0, 720), always increasing in the direction of travel. */
	private double angle;
	/**
	 * The same turning as {@link #angle}, kept in [0, 1080): the angle a rotary chamber repeats over. It only differs from
	 * {@link #angle} by a multiple of 360, which is as far apart as the chambers of a rotor are.
	 */
	private double rotaryAngle;
	/** Angular speed, rad/s, always non-negative. */
	private double omega;
	private int direction = 1;

	// Per-rotor-cycle bookkeeping
	private double cycleProgress;
	private double cycleGasWork;
	private double cycleFrictionWork;
	/** Friction work done over the tick so far, which warms the block alongside the exhaust. */
	private double tickFrictionWork;
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
		measureFriction();
		if (floorOmega > STALL_OMEGA) {
			if (omega <= STALL_OMEGA)
				setDirection(floorDirection);
			omega = Math.max(omega, floorOmega);
		}
		double remaining = TICK;
		while (remaining > 1e-9 && omega > STALL_OMEGA) {
			double delta = Math.min(stepLimit(omega), Math.toDegrees(omega * remaining));
			double radians = Math.toRadians(delta);
			double dt = radians / omega;
			double gasWork = advance(delta, dt);
			double friction = stepFrictionTorque() * radians;
			double energy = 0.5 * inertia * omega * omega + gasWork - friction - loadTorque * radians;
			cycleFrictionWork += friction;
			tickFrictionWork += friction;
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

	/** The most crank degrees one step may cover at {@code omega}; see {@link #MIN_STEP_DEGREES}. */
	static double stepLimit(double omega) {
		double perStep = Math.toDegrees(omega * TICK) / STEPS_PER_TICK;
		return Math.max(MIN_STEP_DEGREES, Math.min(MAX_STEP_DEGREES, perStep));
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
			measureFriction();
			double remaining = TICK;
			while (remaining > 1e-9) {
				double delta = Math.min(stepLimit(omega), Math.toDegrees(omega * remaining));
				double dt = Math.toRadians(delta) / omega;
				double gasWork = advance(delta, dt);
				double friction = stepFrictionTorque() * Math.toRadians(delta);
				cycleFrictionWork += friction;
				tickFrictionWork += friction;
				if (swing != null)
					swing.add(gasWork - friction, Math.toRadians(delta));
				remaining -= dt;
			}
		}
		endTick();
	}

	/** Closes the tick's heat accounting and lets each turbo spool on it; they spin down even while the crank is stopped. */
	private void endTick() {
		double exhaustHeat = 0;
		for (CylinderModel cylinder : cylinders) {
			cylinder.endTick();
			exhaustHeat += cylinder.exhaustHeatLastTick();
		}
		if (thermalLive && Tuning.engineTemperature)
			thermal.update(Thermal.heatIn(exhaustHeat / TICK, tickFrictionWork / TICK), cylinders.size(), TICK, coolant);
		tickFrictionWork = 0;
		for (Turbocharger turbo : turbos)
			turbo.tick(TICK);
		for (Supercharger blower : superchargers) {
			if (blower.followsCrank())
				blower.rpm = blower.driveRatio * rpm();
			blower.tick(TICK);
		}
		applyInduction();
	}

	/**
	 * Sets the air every cylinder breathes. The compressors on a cylinder's intake run in turn, so their pressure and
	 * temperature ratios multiply; the intercoolers then take heat out of the result; and the turbines on its exhaust
	 * run each hold it up, so their back-pressures add.
	 */
	private void applyInduction() {
		double cool = thermal.chargeFactor();
		if (turbos.isEmpty() && superchargers.isEmpty() && intercoolers.isEmpty()) {
			// No compressors, but a cool engine still draws a denser charge
			for (CylinderModel cylinder : cylinders)
				cylinder.intakeTemperature = cool;
			return;
		}
		for (CylinderModel cylinder : cylinders) {
			cylinder.intakePressure = 1;
			cylinder.intakeTemperature = 1;
			cylinder.exhaustPressure = 1;
		}
		// Stages in series multiply (or add) straight onto the cylinder; stages in parallel each handle a share of the
		// air, so the cylinder gets their average, whatever the number of them
		Parallel intake = new Parallel();
		Parallel exhaust = new Parallel();
		// The server can scale boost, or switch forced induction off, in which case the turbines add no back-pressure either
		double strength = Tuning.forcedInduction ? Tuning.boostStrength : 0;
		double turbine = Tuning.forcedInduction ? 1 : 0;
		for (Turbocharger turbo : turbos) {
			double pressure = 1 + turbo.boost() * strength;
			double temperature = Tuning.blend(turbo.chargeTemperature(), strength);
			for (CylinderModel cylinder : turbo.compressor()) {
				if (turbo.parallelCompressor)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.intakePressure *= pressure;
					cylinder.intakeTemperature *= temperature;
				}
			}
			double back = (turbo.backPressure() - 1) * turbine;
			for (CylinderModel cylinder : turbo.turbine()) {
				if (turbo.parallelTurbine)
					exhaust.add(cylinder, back, 0);
				else
					cylinder.exhaustPressure += back;
			}
		}
		for (Supercharger blower : superchargers) {
			double pressure = 1 + blower.boost() * strength;
			double temperature = Tuning.blend(blower.chargeTemperature(), strength);
			for (CylinderModel cylinder : blower.compressor()) {
				if (blower.parallel)
					intake.add(cylinder, pressure, temperature);
				else {
					cylinder.intakePressure *= pressure;
					cylinder.intakeTemperature *= temperature;
				}
			}
		}
		intake.forEach((cylinder, pressure, temperature) -> {
			cylinder.intakePressure *= pressure;
			cylinder.intakeTemperature *= temperature;
		});
		exhaust.forEach((cylinder, back, $) -> cylinder.exhaustPressure += back);
		for (Intercooler cooler : intercoolers)
			cooler.cool();
		if (cool != 1)
			for (CylinderModel cylinder : cylinders)
				cylinder.intakeTemperature *= cool;
	}

	/** Running averages of what several parallel stages make for each cylinder they serve. */
	private static final class Parallel {
		/** Made when the first stage in parallel is added: most engines have none, and this is built every tick. */
		private Map<CylinderModel, double[]> sums;

		void add(CylinderModel cylinder, double first, double second) {
			if (sums == null)
				sums = new IdentityHashMap<>();
			double[] sum = sums.computeIfAbsent(cylinder, $ -> new double[3]);
			sum[0] += first;
			sum[1] += second;
			sum[2]++;
		}

		void forEach(Average average) {
			if (sums != null)
				sums.forEach((cylinder, sum) -> average.accept(cylinder, sum[0] / sum[2], sum[1] / sum[2]));
		}

		interface Average {
			void accept(CylinderModel cylinder, double first, double second);
		}
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

	/** Adds a supercharger, which is ticked with the engine. */
	public void addSupercharger(Supercharger supercharger) {
		superchargers.add(supercharger);
	}

	public List<Supercharger> superchargers() {
		return superchargers;
	}

	/** Adds an intercooler, which cools the air whatever compressors are ahead of it. */
	public void addIntercooler(Intercooler intercooler) {
		intercoolers.add(intercooler);
	}

	public List<Intercooler> intercoolers() {
		return intercoolers;
	}

	private double advance(double delta, double dt) {
		for (Camshaft camshaft : camshafts)
			camshaft.sync(angle);
		double gasWork = 0;
		for (CylinderModel cylinder : cylinders) {
			Camshaft camshaft = cylinder.camshaft();
			double before = cylinder.currentCycle().fuelInjected;
			if (cylinder.ported()) {
				// A rotary chamber is in step with the shaft by the housing's gears: its ports follow its own cycle
				double cycle = cylinder.pistonAngle(angle, rotaryAngle, direction);
				double cycleDelta = delta * cylinder.angleScale();
				gasWork += cylinder.step(cycle, cycleDelta, cycle, cycleDelta, dt);
			} else {
				double camDelta = camshaft == null ? 0 : 2 * camshaft.ratio * delta;
				gasWork += cylinder.step(cylinder.pistonAngle(angle, direction), delta, cylinder.camCycleAngle(), camDelta,
					dt);
			}
			cycleFuel += Math.max(0, cylinder.currentCycle().fuelInjected - before);
		}
		for (Camshaft camshaft : camshafts)
			camshaft.advance(delta);
		angle = SimMath.wrap(angle + delta);
		rotaryAngle = SimMath.wrap(rotaryAngle + delta, CylinderModel.ROTARY_CYCLE);
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
		if (!Tuning.oilDilution) {
			lubrication.setDilution(0);
			return;
		}
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
		return friction * drag();
	}

	/**
	 * Works out what friction is made of at the oil the engine has now, so the many steps of a tick can each find it from
	 * the speed alone: the oil only changes between ticks, but summing every cylinder's share is a step's costliest loop.
	 */
	private void measureFriction() {
		double film = lubrication.effective();
		double drag = drag();
		double staticFactor = Lubrication.staticFactor(film) * drag;
		double viscousFactor = Lubrication.viscousFactor(film) * drag;
		double fixed = baseFriction * staticFactor;
		double perOmega = baseViscousFriction * viscousFactor;
		for (CylinderModel cylinder : cylinders) {
			fixed += cylinder.spec.frictionStatic() * staticFactor;
			perOmega += cylinder.spec.frictionViscous() * viscousFactor;
		}
		stepFriction = fixed;
		stepViscousFriction = perOmega;
	}

	/** Friction torque at {@code omega}, from the figures {@link #measureFriction()} took at the start of the tick. */
	private double stepFrictionTorque() {
		return stepFriction + stepViscousFriction * omega;
	}

	/** How well oiled the moving parts are; the owner keeps it up to date from the oil supply. */
	public Lubrication lubrication() {
		return lubrication;
	}

	/** How hot the block is; see {@link Thermal}. */
	public Thermal thermal() {
		return thermal;
	}

	/** Where the coolant comes from; none by default, in which case the engine only loses heat to the air. */
	public void setCoolant(CoolantSupply coolant) {
		this.coolant = coolant;
	}

	/** Lets the block warm and cool as the engine runs. */
	public void setThermalLive(boolean live) {
		this.thermalLive = live;
	}

	/** Multiplier on all friction from the state of the oil and how warm the block is. */
	private double drag() {
		return lubrication.drag() * thermal.drag();
	}

	/**
	 * Spends the oil's condition for the turning done this tick. The owner calls it once a tick on a live engine, with
	 * whether a filter is fitted; a dynamometer copy never does.
	 */
	public void ageOil(boolean filtered) {
		lubrication.age(revolutionsLastTick(), lubrication.stress(thermal.temperature(), filtered));
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
				double airLimit = last.freshAirAtInjection / fuel.airPerMb();
				// A MAF sensor sets the shot from the air, up to what the injector can deliver; without one it is
				// whatever Fuel Metering says
				double fullShot = cylinder.airMatch > 0
					? Math.min(airLimit * cylinder.airMatch, cylinder.injectorLimit(fuel))
					: cylinder.usualShot(fuel) * cylinder.metering * cylinder.fuelTrim;
				scale = Math.max(1, Math.min(fullShot, airLimit) / last.fuelBurned);
			}
			// A rotary chamber takes a turn and a half of the shaft to do what a piston does in two
			work += last.work * scale * SimMath.CYCLE / cylinder.cycleDegrees();
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
		if (direction == 0)
			return;
		this.direction = direction > 0 ? 1 : -1;
		// Cylinders on a shared cam lobe are timed relative to each other, and that depends on the way the crank turns
		for (CylinderModel cylinder : cylinders)
			cylinder.setDirection(this.direction);
	}

	/** Restores motion state, e.g. after a topology rebuild or loading from disk. */
	public void restore(double angle, double omega, int direction) {
		setDirection(direction);
		this.angle = SimMath.wrap(angle);
		this.rotaryAngle = SimMath.wrap(angle, CylinderModel.ROTARY_CYCLE);
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

	/** The shaft angle in [0, 1080) that rotary chambers are timed from; see {@link #angle()}. */
	public double rotaryAngle() {
		return rotaryAngle;
	}

	/**
	 * Takes the rotary angle from an engine that was running the same rotors, which keeps each chamber's gas in step
	 * with the shaft. Only the part beyond {@link #angle()}'s own 720 is carried, so it must agree with it modulo 360.
	 */
	public void restoreRotary(double rotaryAngle) {
		this.rotaryAngle = SimMath.wrap(rotaryAngle, CylinderModel.ROTARY_CYCLE);
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

	/** Starts measuring the energy swing of a driven engine, which {@link #predictedRipple()} turns into a speed ripple. */
	public void trackSwing() {
		if (swing == null)
			swing = new Swing();
	}

	/**
	 * The speed ripple this engine would have if it were left to turn free at the speed it is being driven at: how far
	 * the crank's energy swings over a cycle (power strokes in, compression and friction out), as a share of the energy
	 * its inertia stores. A flywheel does not change the swing, only how much of it shows up as speed. Zero unless
	 * {@link #trackSwing()} was called and a whole cycle has been driven.
	 */
	public double predictedRipple() {
		if (swing == null || swing.result <= 0 || omega <= STALL_OMEGA)
			return 0;
		return swing.result / (inertia * omega * omega);
	}

	/**
	 * Net energy the crank gains over a 720 degree cycle, less what an even torque would have given by the same angle:
	 * the biggest excess minus the biggest shortfall is how far the energy swings.
	 */
	private static final class Swing {
		private double[] angles = new double[128];
		private double[] energies = new double[128];
		private int count;
		private double totalAngle;
		private double totalEnergy;
		double result;

		void add(double work, double radians) {
			totalAngle += radians;
			totalEnergy += work;
			if (count == angles.length) {
				angles = Arrays.copyOf(angles, count * 2);
				energies = Arrays.copyOf(energies, count * 2);
			}
			angles[count] = totalAngle;
			energies[count] = totalEnergy;
			count++;
			if (totalAngle >= Math.toRadians(SimMath.CYCLE) - 1e-9) {
				double slope = totalEnergy / totalAngle;
				double low = 0;
				double high = 0;
				for (int i = 0; i < count; i++) {
					double excess = energies[i] - slope * angles[i];
					low = Math.min(low, excess);
					high = Math.max(high, excess);
				}
				result = high - low;
				count = 0;
				totalAngle = 0;
				totalEnergy = 0;
			}
		}
	}

	/** Fuel injected per second over the last cycle, mB/s. */
	public double fuelRate() {
		return fuelRate;
	}

	public boolean isStopped() {
		return omega <= STALL_OMEGA;
	}
}
