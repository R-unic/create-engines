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
	/** Everything that puts work on the crank: the cylinders first, then any {@link #addSource added} ones. */
	private final List<TorqueSource> sources;
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
	/** Turbochargers, superchargers and intercoolers, and how they set the air; see {@link Induction}. */
	private final Induction induction = new Induction();

	private boolean washesOil = true;
	/** Measures how unevenly a driven engine turns the crank; see {@link #trackSwing()}. Null unless asked for. */
	private SwingTracker swing;
	/** Mass behind a dual-mass flywheel's spring pack, which smooths the ripple but not the crank; see {@link DualMass}. */
	private double isolatedInertia;
	private double isolationResonanceRpm;

	/**
	 * How a tick is cut into steps: the constants above in a live engine. A test sets finer ones through
	 * {@link #useStepSizes} to check that a result does not depend on how coarsely the sim looked at it.
	 */
	private double minStepDegrees = MIN_STEP_DEGREES;
	private double maxStepDegrees = MAX_STEP_DEGREES;
	private double stepsPerTick = STEPS_PER_TICK;

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
	/** Mean angular speed over the last whole cycle, rad/s, and how long it took; 0 with no whole cycle since the last stop. */
	private double cycleMeanOmega;
	private double cycleMeanSeconds;
	/** The speed seen from outside; nothing in the simulation reads it. */
	private final MeanSpeed meanSpeed = new MeanSpeed();

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
		this.sources = new ArrayList<>(this.cylinders);
		this.camshafts = cylinders.stream()
			.map(CylinderModel::camshaft)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		this.baseInertia = baseInertia;
		this.baseFriction = baseFriction;
		this.baseViscousFriction = baseViscousFriction;
		this.inertia = baseInertia + cylinders.stream()
			.mapToDouble(CylinderModel::inertia)
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
		// Every stall test is a comparison, which NaN fails: a corrupt speed would otherwise never stop
		if (!Double.isFinite(omega))
			omega = 0;
		measureFriction();
		if (floorOmega > STALL_OMEGA) {
			if (omega <= STALL_OMEGA)
				setDirection(floorDirection);
			omega = Math.max(omega, floorOmega);
		}
		double remaining = TICK;
		while (remaining > 1e-9 && omega > STALL_OMEGA) {
			double delta = Math.min(stepLimit(omega, minStepDegrees, maxStepDegrees, stepsPerTick), Math.toDegrees(omega * remaining));
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
			if (!(energy > 0)) {
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
		return stepLimit(omega, MIN_STEP_DEGREES, MAX_STEP_DEGREES, STEPS_PER_TICK);
	}

	private static double stepLimit(double omega, double min, double max, double perTick) {
		double perStep = Math.toDegrees(omega * TICK) / perTick;
		return Math.max(min, Math.min(max, perStep));
	}

	/**
	 * Cuts every following tick into steps of at most these degrees instead of the live ones, for tests that compare a
	 * result at two resolutions. {@code stepsPerTick} is how many steps a tick may take before the step grows past
	 * {@code minDegrees}, as {@link #STEPS_PER_TICK} is for the live engine.
	 */
	void useStepSizes(double minDegrees, double maxDegrees, double stepsPerTick) {
		if (!(minDegrees > 0) || maxDegrees < minDegrees || !(stepsPerTick > 0))
			throw new IllegalArgumentException("steps " + minDegrees + ".." + maxDegrees + " per tick " + stepsPerTick);
		this.minStepDegrees = minDegrees;
		this.maxStepDegrees = maxDegrees;
		this.stepsPerTick = stepsPerTick;
	}

	/** Mean torque the cranking clutch supplied during the last tick (0 if the engine was outrunning it). */
	public double crankingTorque() {
		return crankingAngle > 0 ? crankingWork / crankingAngle : 0;
	}

	/**
	 * Speed the engine is making under its own power, RPM, for milestones: 0 while the cranking clutch is putting work in to
	 * hold the crank up to speed, since a starter or another source can spin an engine far faster than it could run itself.
	 */
	public double ownRpm() {
		return crankingTorque() > 0 ? 0 : rpm();
	}

	/** Advances one tick with the crank held at exactly {@code drivenOmega}, like a dynamometer. */
	public void tickDriven(double drivenOmega, int drivenDirection) {
		setDirection(drivenDirection);
		omega = drivenOmega;
		if (omega > STALL_OMEGA) {
			measureFriction();
			double remaining = TICK;
			while (remaining > 1e-9) {
				double delta = Math.min(stepLimit(omega, minStepDegrees, maxStepDegrees, stepsPerTick), Math.toDegrees(omega * remaining));
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
		double burnHeat = 0, otherExhaust = 0;
		for (int i = 0; i < sources.size(); i++) {
			TorqueSource source = sources.get(i);
			source.endTick();
			if (source instanceof CylinderModel cylinder)
				burnHeat += cylinder.burnHeatLastTick();
			else
				otherExhaust += source.exhaustHeatLastTick();
		}
		if (thermalLive && Tuning.engineTemperature)
			thermal.update(Thermal.heatIn(burnHeat / TICK, otherExhaust / TICK, tickFrictionWork * Thermal.dryHeat(lubrication.film()) / TICK,
				sources.size()), sources.size(), TICK, coolant);
		tickFrictionWork = 0;
		// With a clutch holding the crank there is no cycle of its own to go by
		meanSpeed.update(rpm(), crankingTorque() > 0 ? 0 : omegaToRpm(cycleMeanOmega), cycleMeanSeconds, TICK);
		double cold = thermal.felt();
		double hot = thermal.hotness();
		for (int i = 0; i < sources.size(); i++) {
			sources.get(i).setColdness(cold);
			if (sources.get(i) instanceof CylinderModel cylinder)
				cylinder.hotness = hot;
		}
		induction.tick(rpm(), cylinders, thermal.chargeFactor());
		// Sources that are not cylinders breathe the engine's air as a whole; a plain engine skips this
		int first = cylinders.size();
		if (sources.size() == first)
			return;
		induction.airForSources(cylinders, thermal.chargeFactor());
		for (int i = first; i < sources.size(); i++) {
			TorqueSource source = sources.get(i);
			// One attached to a port breathes what that network gives it, not the engine's average
			if (!(source instanceof Breather breather && induction.attached.contains(breather)))
				source.setIntake(induction.sourcePressure, induction.sourceTemperature);
		}
	}

	/**
	 * How thick the air this engine breathes is, 1 at sea level (see {@link Altitude}). It scales every cylinder's intake
	 * pressure before the compressors multiply it, so boost offsets thin air.
	 */
	public void setAmbientDensity(double density) {
		induction.density = Altitude.clamp(density, Tuning.altitudeFloor);
	}

	/** How thick the air this engine breathes is, see {@link #setAmbientDensity}. */
	public double ambientDensity() {
		return induction.density;
	}

	/** Whether unburned fuel thins the oil as it runs. A dynamometer measures the oil as it is and leaves it alone. */
	public void setWashesOil(boolean washesOil) {
		this.washesOil = washesOil;
	}

	/** Adds a turbocharger, which is ticked with the engine. */
	public void addTurbo(Turbocharger turbo) {
		induction.turbos.add(turbo);
	}

	public List<Turbocharger> turbos() {
		return induction.turbos;
	}

	/** Adds a supercharger, which is ticked with the engine. */
	public void addSupercharger(Supercharger supercharger) {
		induction.superchargers.add(supercharger);
	}

	public List<Supercharger> superchargers() {
		return induction.superchargers;
	}

	/** Adds an intercooler, which cools the air whatever compressors are ahead of it. */
	public void addIntercooler(Intercooler intercooler) {
		induction.intercoolers.add(intercooler);
	}

	public List<Intercooler> intercoolers() {
		return induction.intercoolers;
	}

	private double advance(double delta, double dt) {
		for (Camshaft camshaft : camshafts)
			camshaft.sync(angle);
		double gasWork = 0;
		for (int i = 0; i < sources.size(); i++) {
			TorqueSource source = sources.get(i);
			gasWork += source.advance(angle, rotaryAngle, direction, delta, dt);
			cycleFuel += source.fuelUsedLastStep();
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
		cycleMeanOmega = cycleTime > 0 ? mean : 0;
		cycleMeanSeconds = cycleTime;
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
		for (int i = 0; i < sources.size(); i++) {
			double fraction = sources.get(i).unburnedFraction();
			if (fraction < 0)
				continue;
			injecting++;
			unburned += fraction;
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
		cycleMeanOmega = 0;
		cycleMeanSeconds = 0;
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
		for (int i = 0; i < sources.size(); i++)
			friction += sources.get(i).frictionAt(omega, film);
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
		for (int i = 0; i < sources.size(); i++) {
			TorqueSource source = sources.get(i);
			fixed += source.frictionStatic() * staticFactor;
			perOmega += source.frictionViscous() * viscousFactor;
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
	public void setRadiators(Radiator.Supply radiators) {
		thermal.setRadiators(radiators);
	}

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
		for (int i = 0; i < sources.size(); i++)
			work += sources.get(i).availableWork();
		return Math.max(0, work / Math.toRadians(SimMath.CYCLE) - frictionTorque());
	}

	/** Whether combustion alone is keeping the crank turning: it fired and made more work than friction took. */
	public boolean isSustaining() {
		if (!hasCompletedCycle || netTorque <= 0)
			return false;
		for (int i = 0; i < sources.size(); i++)
			if (sources.get(i).fired())
				return true;
		return false;
	}

	/** Which way the crank turns. Valve timing only suits one direction, so this matters. */
	public void setDirection(int direction) {
		if (direction == 0)
			return;
		this.direction = direction > 0 ? 1 : -1;
		// Cylinders on a shared cam lobe are timed relative to each other, and that depends on the way the crank turns
		for (int i = 0; i < sources.size(); i++)
			sources.get(i).setDirection(this.direction);
	}

	/** Restores motion state, e.g. after a topology rebuild or loading from disk. */
	public void restore(double angle, double omega, int direction) {
		setDirection(direction);
		this.angle = SimMath.wrap(angle);
		this.rotaryAngle = SimMath.wrap(angle, CylinderModel.ROTARY_CYCLE);
		this.omega = Double.isFinite(omega) ? Math.max(0, omega) : 0;
		for (Camshaft camshaft : camshafts)
			camshaft.angle = this.angle / 2;
		meanSpeed.update(rpm(), 0, 0, TICK);
	}

	/**
	 * Adds something that puts work on the crank besides the cylinders this simulation was built with, such as a turbine
	 * a pack supplies. Its inertia joins the crank's. It is stepped, rubbed and heated like a cylinder, but it takes no
	 * part in {@link #cylinders()} or in boost, which are cylinder things.
	 */
	public void addSource(TorqueSource source) {
		sources.add(Objects.requireNonNull(source));
		inertia += source.inertia();
	}

	/**
	 * Adds a source that breathes through the engine's own networks, like a cylinder: build the turbochargers,
	 * superchargers and intercoolers on the same networks with it in their lists, and it gets their pressures. It is added
	 * to the crank as {@link #addSource} does.
	 */
	public void addAttachedSource(Breather source) {
		addSource(source);
		induction.attached.add(source);
	}

	/** Every source on the crank: the cylinders, then any added ones. */
	public List<TorqueSource> sources() {
		return java.util.Collections.unmodifiableList(sources);
	}

	public List<CylinderModel> cylinders() {
		return cylinders;
	}

	/**
	 * Takes over the mean speed of the simulation this one replaces, after {@link #restore}: without it a rebuilt engine
	 * would be back at the instant speed, somewhere on its ripple, until it had turned a whole cycle again.
	 */
	public void carryMeanSpeed(EngineSimulation old) {
		cycleMeanOmega = old.cycleMeanOmega;
		cycleMeanSeconds = old.cycleMeanSeconds;
		meanSpeed.copyFrom(old.meanSpeed);
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

	/**
	 * The speed to show and to give a network, RPM: the mean over a firing cycle, ramped between cycles (see
	 * {@link MeanSpeed}), where {@link #rpm()} is wherever the crank is on its ripple at this instant. It is the instant
	 * speed before a first whole cycle, while a clutch holds the crank, and when stopped. As of the last tick's end.
	 */
	public double meanRpm() {
		return isStopped() ? 0 : meanSpeed.rpm();
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

	/**
	 * Fits the secondary mass of a dual-mass flywheel: {@code inertia} that counts toward {@link #predictedRipple()} but
	 * not toward how fast the crank revs, by the share {@link DualMass#isolation} gives at the speed, for a spring pack
	 * that resonates at {@code resonanceRpm}. Zero (the default) is a plain engine.
	 */
	public void setIsolatedMass(double inertia, double resonanceRpm) {
		this.isolatedInertia = Double.isFinite(inertia) ? Math.max(0, inertia) : 0;
		this.isolationResonanceRpm = resonanceRpm;
	}

	public double isolatedInertia() {
		return isolatedInertia;
	}

	public double isolationResonanceRpm() {
		return isolationResonanceRpm;
	}

	/** Starts measuring the energy swing of a driven engine, which {@link #predictedRipple()} turns into a speed ripple. */
	public void trackSwing() {
		if (swing == null)
			swing = new SwingTracker();
	}

	/**
	 * The speed ripple this engine would have if it were left to turn free at the speed it is being driven at: how far
	 * the crank's energy swings over a cycle (power strokes in, compression and friction out), as a share of the energy
	 * its inertia stores. A flywheel does not change the swing, only how much of it shows up as speed; a dual-mass one adds its isolated secondary mass to the inertia the ripple sees. Zero unless
	 * {@link #trackSwing()} was called and a whole cycle has been driven.
	 */
	public double predictedRipple() {
		if (swing == null || swing.result() <= 0 || omega <= STALL_OMEGA)
			return 0;
		// With no secondary mass this is the crank's own inertia exactly (a zero share of nothing is added)
		double seen = DualMass.effectiveInertia(inertia, isolatedInertia, omegaToRpm(omega), isolationResonanceRpm);
		return swing.result() / (seen * omega * omega);
	}

	/** Fuel injected per second over the last cycle, mB/s. */
	public double fuelRate() {
		// Engines turn faster than they once did for the same stress, and the tanks are charged less to match
		return fuelRate / CylinderSpec.SPEED_GAIN;
	}

	public boolean isStopped() {
		return omega <= STALL_OMEGA;
	}
}
