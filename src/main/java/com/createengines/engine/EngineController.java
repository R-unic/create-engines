package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.config.CEConfig;
import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.head.GalleryMember;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.Camshaft;
import com.createengines.engine.sim.CoolantSupply;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CycleStats;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.FuelSupply;
import com.createengines.engine.sim.Governor;
import com.createengines.engine.sim.IgnitionSource;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Roughness;
import com.createengines.engine.sim.Sensors;
import com.createengines.engine.sim.Throttle;
import com.createengines.engine.sim.Torsion;
import com.createengines.engine.sim.Intercooler;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.sim.OilSupply;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.CamLine;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.EngineLayout.Turbo;
import com.createengines.engine.topology.PrecisionParts;
import com.createengines.registry.CEFluids;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * Runs one engine: owns its simulation and couples it to Create's kinetic network through the primary crankshaft
 * block entity, which is a Create generator.
 * <p>
 * Create sees a generator like any other. Its speed is the crank's simulated RPM (rounded, and only republished when
 * it has moved enough, because every speed change makes Create re-propagate the network). Its capacity is the
 * engine's rated torque: the peak of its {@link Dynamometer} curve at or below its current speed, because a loaded
 * engine slows down until its torque matches the load. The load it feels is the network's total stress divided by
 * its speed, so gearing downstream changes the load exactly as it should.
 */
public final class EngineController {

	/**
	 * What carries over when an engine is rebuilt: the crank's motion, and (if the engine was running in memory)
	 * the controller it was running in, so each surviving cylinder keeps its gas.
	 */
	public record Motion(double angle, double omega, int direction, double film, double dilution, double condition,
						 double temperature, @Nullable EngineController source) {
	}

	private static final int PUBLISH_INTERVAL = 10;
	private static final int CAPACITY_INTERVAL = 5;
	private static final int SYNC_INTERVAL = 10;
	/** Crank speed below which a blower's gearing to it is not worked out. */
	private static final double MIN_RATIO_RPM = 8;
	private static final double MAX_DRIVE_RATIO = 16;
	private static final int RACK_INTERVAL = 10;
	private static final int RATING_INTERVAL = 20;
	/** Rated torque is held back a little from the dyno peak, so a load Create accepts never quite stalls the engine. */
	private static final double RATING_MARGIN = 0.9;
	/** Ticks before the same milestone is reported again, for an operator who was offline when it was first reached. */
	private static final int MILESTONE_COOLDOWN = 200;
	/**
	 * Mixture {@link #tuneFuel} aims for. A little lean of balanced, because an engine that burns cleaner speeds up, and
	 * breathes less as it does, so it drifts richer after the tune.
	 */
	private static final double TUNE_MIXTURE = 0.9;
	/** How close to its setting, as a share of it, a governed engine has to be held to count as on speed. */
	private static final double GOVERNED_BAND = 0.03;
	/** Ticks a governor has to hold an engine on its setting under load for it to count, ten seconds. */
	private static final int GOVERNED_TICKS = 200;

	private final CrankshaftBlockEntity primary;
	private final EngineLayout layout;
	private final EngineSimulation sim;
	/** Parallel to {@link EngineLayout#cylinders()}; null where the cylinder can't work at all. */
	private final List<@Nullable CylinderModel> models;
	/** Parallel to {@link EngineLayout#camLines()}. */
	private final List<Camshaft> camshafts;
	private final double[] racks;
	/** Parallel to {@link EngineLayout#ports()}: how open the throttling in each is, 1 where there is none. */
	private final double[] portOpening;
	/** Parallel to {@link EngineLayout#turbos()}. */
	private final List<Turbocharger> turbos = new ArrayList<>();
	/** Parallel to {@link EngineLayout#superchargers()}. */
	private final List<Supercharger> blowers = new ArrayList<>();
	private final OilSupply oilSupply;
	/** Whether an oil pan swapped oil during the last tick, and whether any pan has a filter fitted: for achievements and goggles. */
	private boolean exchanging;
	private boolean filtered;
	/** How many moving parts need oil: crank blocks, plus every piston and rod turning with the crank. */
	private final double oilParts;
	/** Share of the rating this engine's crank lines lose to torsional flex at the shipped balance, and the throws on its longest line. */
	private final double flexLoss;
	private final int longestLine;

	private int ticks;
	/** The tick a dynamometer block last read this engine's curve. */
	private int dynoReadTick = -1000;
	private int ticksSincePublish = PUBLISH_INTERVAL;
	private int ticksSinceCapacity = CAPACITY_INTERVAL;
	private double smoothedLoad;
	private double[] torqueCurve = new double[Dynamometer.SPEEDS.length];
	/** The free-running speed ripple at each speed of {@link #torqueCurve}, which roughness holds the rating back by. */
	private double[] rippleCurve = new double[Dynamometer.SPEEDS.length];
	private @Nullable List<Object> ratingKey;
	/** The torque curve being measured, a speed a tick, and whether {@link #torqueCurve} has ever been a whole one. */
	private Dynamometer.@Nullable Measurement rating;
	private boolean rated;
	private boolean cranking;
	private @Nullable BlockPos activeStarter;
	private EngineState previousState = EngineState.STALLED;
	private final Map<Milestone, Integer> milestoneTicks = new EnumMap<>(Milestone.class);
	private final Governor governor = new Governor();
	/** Ticks the governor has held the engine on its setting while carrying a load. */
	private int governedTicks;
	/** What the engine-management blocks last saw, for milestones. */
	private boolean mafTrimming;
	private boolean mafEnriching;
	private boolean mafReading;
	/** Heat level of the hottest exhaust heat exchanger on the engine, {@code HeatRecovery.NONE} to {@code SEETHE}. */
	private int exchangerLevel;
	private boolean governing;
	private boolean commanded;
	/** The fuel gallery shared by every head, for telling which fuel the engine burns. */
	private final GalleryFuelSupply fuels;

	public EngineController(CrankshaftBlockEntity primary, EngineLayout layout, @Nullable Motion motion) {
		this.primary = primary;
		this.layout = layout;
		Level level = primary.getLevel();

		camshafts = new ArrayList<>();
		for (int i = 0; i < layout.camLines()
			.size(); i++)
			camshafts.add(new Camshaft());
		racks = new double[camshafts.size()];
		Arrays.fill(racks, 1);
		portOpening = new double[layout.ports()
			.size()];
		Arrays.fill(portOpening, 1);

		// Every head's tank feeds every injector, so fuel piped into any one head runs the whole engine
		GalleryFuelSupply fuelGallery = new GalleryFuelSupply(level, layout.heads());
		fuels = fuelGallery;
		OilSupply oilGallery = new GalleryOilSupply(level, layout.heads());
		// The pans are the engine's reserve and are drawn on first; the head tanks top up what they cannot give
		OilSupply pans = new SumpOilSupply(level, layout.sumps());
		oilSupply = mb -> {
			double delivered = pans.draw(mb);
			return delivered < mb ? delivered + oilGallery.draw(mb - delivered) : delivered;
		};

		IgnitionSource plugs = new BatteryIgnition(level, layout.batteries());
		// The oil pans also hold the engine's coolant and its pilot fuel
		CoolantSupply coolant = layout.sumps()
			.isEmpty() ? CoolantSupply.NONE : new SumpCoolantSupply(level, layout.sumps());
		FuelSupply pilots = new SumpPilotSupply(level, layout.sumps());

		models = new ArrayList<>();
		List<CylinderModel> working = new ArrayList<>();
		// A piston and rod that turn with the crank but aren't part of a working cylinder (no head, say) still have
		// mass and rub against their bore, so the crank can't spin freely just because nothing is burning
		double idleInertia = 0;
		double idleStatic = 0;
		double idleViscous = 0;
		double parts = layout.segments()
			.size();
		for (Cylinder cylinder : layout.cylinders()) {
			if (CylinderFault.disables(cylinder.faults())) {
				models.add(null);
				double weight = CylinderFault.dragOnCrank(cylinder.faults());
				idleInertia += weight * CylinderSpec.CRUDE.inertia();
				idleStatic += weight * CylinderSpec.CRUDE.frictionStatic();
				idleViscous += weight * CylinderSpec.CRUDE.frictionViscous();
				parts += weight;
				continue;
			}
			parts++;
			Camshaft camshaft = cylinder.camLine() >= 0 ? camshafts.get(cylinder.camLine()) : null;
			// A rotor's ports are cut in its housing, so it needs no camshaft to time them
			CylinderModel model = cylinder.rotary()
				? CylinderModel.rotary(PrecisionParts.rotarySpec(cylinder.precision()), CamProfile.ROTARY,
					cylinder.tdcAngle(), cylinder.chamber())
				: new CylinderModel(PrecisionParts.spec(cylinder.precision()), CamProfile.BASIC, cylinder.tdcAngle(),
					camshaft, cylinder.lobeAngle());
			model.fuelSupply = CylinderFault.NO_INJECTOR.in(cylinder.faults()) ? FuelSupply.NONE : fuelGallery;
			model.ignition = cylinder.sparkPlug() ? plugs : IgnitionSource.NONE;
			model.pilotSupply = pilots;
			model.intakeFactor = flow(cylinder.intake());
			model.exhaustFactor = flow(cylinder.exhaust());
			if (!Double.isNaN(cylinder.lobeLeaderTdc()))
				model.shareLobe(cylinder.lobeLeaderTdc());
			models.add(model);
			working.add(model);
		}

		oilParts = parts;
		int[] lineThrows = throwsPerLine(layout);
		flexLoss = Torsion.loss(lineThrows);
		longestLine = Arrays.stream(lineThrows)
			.max()
			.orElse(0);

		double inertia = idleInertia + layout.flywheelInertia();
		for (EngineLayout.Segment segment : layout.segments())
			inertia += segment.isThrow() ? CEConfig.THROW_INERTIA.get() : CEConfig.JOURNAL_INERTIA.get();
		double friction = idleStatic + layout.segments()
			.size() * CEConfig.BEARING_FRICTION.get();
		sim = new EngineSimulation(working, inertia, friction, idleViscous);
		for (Turbo turbo : layout.turbos()) {
			List<CylinderModel> turbine = new ArrayList<>();
			List<CylinderModel> compressor = new ArrayList<>();
			for (int i = 0; i < models.size(); i++) {
				CylinderModel model = models.get(i);
				Cylinder cylinder = layout.cylinders()
					.get(i);
				if (model == null)
					continue;
				if (turbo.exhaust() >= 0 && cylinder.exhaust() == turbo.exhaust())
					turbine.add(model);
				if (turbo.intake() >= 0 && cylinder.intake() == turbo.intake())
					compressor.add(model);
			}
			// Turbines side by side on one exhaust network split its heat between them; one the exhaust has to go
			// through gets all of it
			long sharing = turbo.exhaustParallel() ? layout.turbos()
				.stream()
				.filter(other -> other.exhaust() == turbo.exhaust() && other.exhaustParallel())
				.count() : 1;
			Turbocharger unit = new Turbocharger(turbine, compressor, 1d / Math.max(1, sharing));
			unit.variableGeometry = level.getBlockState(turbo.pos())
				.getBlock() instanceof TurbochargerBlock block && block.variableGeometry();
			unit.antiLagFuel = fuelGallery;
			unit.parallelTurbine = turbo.exhaustParallel();
			unit.parallelCompressor = turbo.intakeParallel();
			if (turbo.intakeParallel())
				unit.flow = 1d / Math.max(1, banked(turbo.intake()));
			turbos.add(unit);
			sim.addTurbo(unit);
		}
		for (EngineLayout.Inline placed : layout.superchargers()) {
			Supercharger blower = new Supercharger(breathingOn(placed.intake()));
			blower.parallel = placed.parallel();
			blowers.add(blower);
			sim.addSupercharger(blower);
		}
		for (EngineLayout.Inline placed : layout.intercoolers())
			sim.addIntercooler(new Intercooler(breathingOn(placed.intake())));
		// A new engine starts dry, and has to be oiled; its oil is new and its block cold
		sim.lubrication()
			.setFilm(motion == null ? 0 : motion.film());
		sim.lubrication()
			.setDilution(motion == null ? 0 : motion.dilution());
		sim.lubrication()
			.setCondition(motion == null ? 1 : motion.condition());
		sim.thermal()
			.setTemperature(motion == null ? 0 : motion.temperature());
		sim.setCoolant(coolant);
		sim.setThermalLive(true);
		if (motion != null) {
			sim.restore(motion.angle(), motion.omega(), motion.direction());
			// Rotor chambers repeat every three turns, so carrying only the angle could swap their gas between them
			if (motion.source() != null)
				sim.restoreRotary(motion.source().sim.rotaryAngle());
		}
		for (CylinderModel model : working)
			model.resetGas(model.pistonAngle(sim.angle(), sim.rotaryAngle(), sim.direction()));
		if (motion != null && motion.source() != null) {
			carryCylinders(motion.source());
			// A rebuilt engine is re-rated from scratch, but until that is done it is still the engine it was: dropping
			// its rating to nothing for the moment that takes would overstress everything it was driving
			torqueCurve = motion.source().torqueCurve;
			rippleCurve = motion.source().rippleCurve;
			rated = motion.source().rated;
		}
	}

	/** How many compressors, turbo or blower, sit side by side on intake network {@code port} and share its air. */
	private long banked(int port) {
		return layout.turbos()
			.stream()
			.filter(other -> other.intake() == port && other.intakeParallel())
			.count()
			+ layout.superchargers()
				.stream()
				.filter(other -> other.intake() == port && other.parallel())
				.count();
	}

	/** The working cylinders that draw their air from intake network {@code port}. */
	private List<CylinderModel> breathingOn(int port) {
		List<CylinderModel> found = new ArrayList<>();
		for (int i = 0; i < models.size(); i++)
			if (models.get(i) != null && layout.cylinders()
				.get(i)
				.intake() == port)
				found.add(models.get(i));
		return found;
	}

	private void carryCylinders(EngineController old) {
		for (int i = 0; i < turbos.size(); i++)
			for (int j = 0; j < old.turbos.size(); j++)
				if (old.layout.turbos()
					.get(j)
					.pos()
					.equals(layout.turbos()
						.get(i)
						.pos())) {
					turbos.get(i)
						.copyStateFrom(old.turbos.get(j));
					break;
				}
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			if (model == null)
				continue;
			BlockPos pos = layout.cylinders()
				.get(i)
				.pos();
			for (int j = 0; j < old.models.size(); j++) {
				CylinderModel previous = old.models.get(j);
				if (previous != null && old.layout.cylinders()
					.get(j)
					.pos()
					.equals(pos)) {
					model.copyStateFrom(previous);
					break;
				}
			}
		}
	}

	private double flow(int port) {
		return port < 0 ? 0 : layout.ports()
			.get(port)
			.flowFactor();
	}

	public EngineLayout layout() {
		return layout;
	}

	public Motion motion() {
		return new Motion(sim.angle(), sim.omega(), sim.direction(), sim.lubrication()
			.film(), sim.lubrication()
				.dilution(),
			sim.lubrication()
				.condition(),
			sim.thermal()
				.temperature(),
			this);
	}

	public void tick() {
		Level level = primary.getLevel();
		if (level == null)
			return;
		ticks++;
		double torqueScale = CEConfig.STRESS_PER_TORQUE.get();

		if (layout.incomplete() || layout.broken()) {
			sim.restore(sim.angle(), 0, sim.direction());
			publishSpeed();
			publishCapacity(torqueScale);
			if (ticks % SYNC_INTERVAL == 0)
				primary.updateSummary(summary(layout.incomplete() ? EngineState.INCOMPLETE : EngineState.BROKEN, torqueScale));
			return;
		}

		float crankSpeed = primary.getTheoreticalSpeed();
		measureCamshafts(level, crankSpeed);
		readThrottles(level);
		readTurbos(level);
		readSuperchargers(level);
		readGovernors(level);
		if (ticks % RACK_INTERVAL == 1)
			readFuelRacks(level);
		if (ticks % RATING_INTERVAL == 1)
			updateRating();
		continueRating();

		// Anything turning the crank harder than the engine itself: another kinetic source, or a starter
		double floor = 0;
		int floorDirection = sim.direction();
		if (primary.hasSource() && crankSpeed != 0) {
			floor = EngineSimulation.rpmToOmega(Math.abs(crankSpeed));
			floorDirection = (int) Math.signum(crankSpeed);
		}
		activeStarter = null;
		for (BlockPos pos : layout.starters()) {
			if (!(level.getBlockEntity(pos) instanceof StarterBlockEntity starter) || !starter.wantsToCrank())
				continue;
			double omega = EngineSimulation.rpmToOmega(Math.abs(starter.getSpeed()));
			if (omega > floor) {
				floor = omega;
				floorDirection = starter.crankDirection() * senseAt(pos);
				activeStarter = pos;
			}
		}

		// An overstressed network has stopped everything on it, so nothing is drawing on the engine
		double load = crankSpeed == 0 || primary.isOverStressed() ? 0
			: primary.networkStress() / Math.abs(crankSpeed) / torqueScale;
		smoothedLoad += (load - smoothedLoad) * 0.1;
		double maxOmega = EngineSimulation.rpmToOmega(AllConfigs.server().kinetics.maxRotationSpeed.get());
		sim.tickCranked(load, maxOmega, floor, floorDirection);
		sim.lubrication()
			.update(sim.revolutionsLastTick(), EngineSimulation.TICK, oilParts, oilSupply);
		serviceOil(level);
		cranking = floor > 0 && !sim.isSustaining();
		readSensors(level);

		// The starter feels the effort of turning the engine as stress on its own input shaft
		double crankingStress = sim.crankingTorque() * torqueScale;
		for (BlockPos pos : layout.starters())
			if (level.getBlockEntity(pos) instanceof StarterBlockEntity starter) {
				boolean active = pos.equals(activeStarter);
				starter.updateCranking(layout.primary(), active ? crankingStress : 0, active);
			}

		publishSpeed();
		publishCapacity(torqueScale);
		if (ticks % SYNC_INTERVAL == 0)
			syncDiagnostics(level, torqueScale);
	}

	/**
	 * Which way the crank line a starter turns is geared, relative to the engine's own direction: -1 if it turns the
	 * other way, so that cranking it forwards turns the engine backwards.
	 */
	private int senseAt(BlockPos starter) {
		for (EngineLayout.Segment segment : layout.segments())
			if (segment.pos()
				.distManhattan(starter) == 1)
				return segment.sense();
		return 1;
	}

	private void measureCamshafts(Level level, float crankSpeed) {
		for (int i = 0; i < camshafts.size(); i++) {
			CamLine line = layout.camLines()
				.get(i);
			Camshaft camshaft = camshafts.get(i);
			if (!(level.getBlockEntity(line.segments()
				.getFirst()) instanceof CamshaftBlockEntity cam)) {
				camshaft.ratio = 0;
				continue;
			}
			if (crankSpeed != 0)
				camshaft.ratio = Math.abs(cam.getTheoreticalSpeed() / crankSpeed);
		}
	}

	/**
	 * Throttle bodies in an intake network are in series, so the most closed one governs what every cylinder on that
	 * network can breathe. Read every tick because it is a pedal: it follows the redstone signal as it moves.
	 */
	private void readThrottles(Level level) {
		for (int port = 0; port < portOpening.length; port++) {
			// Starting from 1 would cap a bore opened past the intake right back at it
			List<Double> openings = new ArrayList<>();
			for (BlockPos pos : layout.ports()
				.get(port)
				.throttles())
				if (level.getBlockEntity(pos) instanceof ThrottleBlockEntity throttle)
					openings.add(throttle.opening());
			portOpening[port] = Throttle.inSeries(openings);
		}
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			int port = layout.cylinders()
				.get(i)
				.intake();
			if (model != null && port >= 0) {
				model.intakeFactor = flow(port) * portOpening[port];
				model.fuelTrim = Throttle.fuelTrim(portOpening[port]);
			}
		}
	}

	/** The wastegate settings, read every tick: a redstone signal on a turbo dumps its boost at once. */
	private void readTurbos(Level level) {
		for (int i = 0; i < turbos.size(); i++) {
			Turbocharger turbo = turbos.get(i);
			if (level.getBlockEntity(layout.turbos()
				.get(i)
				.pos()) instanceof TurbochargerBlockEntity be) {
				turbo.limit = be.boostLimit();
				turbo.dump = be.dumping();
				turbo.antiLag = be.antiLagLevel();
				turbo.offThrottle = !sim.isStopped() && offThrottle(turbo);
			}
		}
	}

	/**
	 * Whether every cylinder feeding a turbo's turbine is off the throttle: its fuel cut by a limiter, its rack shut by
	 * redstone, its throttle closed or its governor holding it back. Then there is hardly any heat in the exhaust, and
	 * anti-lag has something to do.
	 */
	private static boolean offThrottle(Turbocharger turbo) {
		if (turbo.turbine()
			.isEmpty())
			return false;
		for (CylinderModel cylinder : turbo.turbine())
			if (!(cylinder.rack < 0.3 || cylinder.fuelTrim < 0.3 || cylinder.governor < 0.3))
				return false;
		return true;
	}

	/**
	 * Each blower turns at the speed of its shaft, whatever is turning it. The ratio of that to the crank's speed is
	 * kept for the dynamometer, which has no shaft to read and runs it with the crank at the same ratio.
	 */
	private void readSuperchargers(Level level) {
		double crankRpm = sim.rpm();
		for (int i = 0; i < blowers.size(); i++) {
			if (!(level.getBlockEntity(layout.superchargers()
				.get(i)
				.pos()) instanceof SuperchargerBlockEntity be))
				continue;
			Supercharger blower = blowers.get(i);
			blower.limit = be.boostLimit();
			blower.dump = be.dumping();
			blower.rpm = be.shaftRpm();
			if (crankRpm >= MIN_RATIO_RPM)
				blower.driveRatio = Math.min(MAX_DRIVE_RATIO, blower.rpm / crankRpm);
		}
	}

	/**
	 * A redstone signal anywhere on a camshaft closes its fuel rack, as on an inline injection pump. The metering
	 * each injector is set to is read here too.
	 */
	private void readFuelRacks(Level level) {
		for (int i = 0; i < racks.length; i++) {
			int signal = 0;
			for (BlockPos pos : layout.camLines()
				.get(i)
				.segments())
				signal = Math.max(signal, level.getBestNeighborSignal(pos));
			racks[i] = 1 - signal / 15d;
		}
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			int line = layout.cylinders()
				.get(i)
				.camLine();
			if (model != null && line >= 0)
				model.rack = racks[line];
			BlockPos head = layout.cylinders()
				.get(i)
				.head();
			if (model != null && head != null && level.getBlockEntity(head) instanceof GalleryMember be)
				model.metering = be.meteringFraction();
			if (model != null)
				model.airMatch = airMatch(level, i);
		}
	}

	/** How many injectors {@link #tuneFuel} set, how many it left alone, and the speed it tuned them at. */
	public record Tuning(int tuned, int skipped, int rpm) {
	}

	/**
	 * Sets every injector's metering to what gave a balanced mixture over the last cycle at the speed the engine is
	 * turning now, so that a beginner does not have to dial each head in by hand. Heads whose shot is already held by a
	 * MAF sensor or a closed rack, and cylinders that did not inject, are left alone. A stopped engine tunes nothing.
	 * Breathing falls with speed, so the result is right at this speed and drifts richer above it.
	 */
	public Tuning tuneFuel(Level level) {
		if (sim.isStopped())
			return new Tuning(0, 0, 0);
		Map<BlockPos, double[]> readings = new LinkedHashMap<>();
		int skipped = 0;
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			BlockPos head = layout.cylinders()
				.get(i)
				.head();
			if (model == null || head == null)
				continue;
			CycleStats last = model.lastCycle();
			if (last.fuelInjected <= 0 || last.equivalence <= 0 || model.airMatch > 0 || model.rack < 0.99) {
				skipped++;
				continue;
			}
			double[] sum = readings.computeIfAbsent(head, $ -> new double[2]);
			sum[0] += last.equivalence;
			sum[1]++;
		}
		int tuned = 0;
		for (Map.Entry<BlockPos, double[]> entry : readings.entrySet()) {
			ScrollValueBehaviour metering =
				switch (level.getBlockEntity(entry.getKey())) {
					case CylinderHeadBlockEntity head -> head.metering;
					case RotorHousingBlockEntity housing -> housing.metering;
					case null, default -> null;
				};
			if (metering == null)
				continue;
			double mixture = entry.getValue()[0] / entry.getValue()[1];
			int steps = (int) Math.round(Math.max(1, metering.getValue()) * TUNE_MIXTURE / mixture);
			metering.setValue(Math.clamp(steps, CylinderHeadBlockEntity.MIN_METERING,
				CylinderHeadBlockEntity.MAX_METERING));
			tuned++;
		}
		return new Tuning(tuned, skipped + readings.size() - tuned, (int) Math.round(sim.rpm()));
	}

	/**
	 * The mixture the leanest MAF sensor in a cylinder's intake holds its shot to, as an equivalence ratio, or 0 where
	 * there is none.
	 */
	private double airMatch(Level level, int index) {
		int port = layout.cylinders()
			.get(index)
			.intake();
		double target = 0;
		if (port >= 0)
			for (BlockPos pos : layout.ports()
				.get(port)
				.sensors())
				if (level.getBlockEntity(pos) instanceof MafSensorBlockEntity maf)
					target = target == 0 ? maf.targetMixture() : Math.min(target, maf.targetMixture());
		return target;
	}

	/**
	 * Governors on the engine: the lowest speed set governs, and the rack (the throttle, in a spark engine) is moved to
	 * hold the crank there. Read every tick because a governor is a feedback loop: it has to keep up with the load.
	 */
	private void readGovernors(Level level) {
		double target = Double.MAX_VALUE;
		double setting = Double.MAX_VALUE;
		for (BlockPos pos : layout.governors())
			if (level.getBlockEntity(pos) instanceof GovernorBlockEntity be) {
				target = Math.min(target, be.governedSpeed());
				setting = Math.min(setting, be.target());
			}
		boolean fitted = target < Double.MAX_VALUE;
		double position = 1;
		if (fitted)
			position = governor.update(sim.rpm(), target, EngineSimulation.TICK);
		else
			governor.reset();
		for (CylinderModel model : sim.cylinders())
			model.governor = position;
		boolean holding = fitted && !sim.isStopped() && Math.abs(sim.rpm() - target) <= GOVERNED_BAND * target
			&& position > 0.05 && position < 0.95;
		governedTicks = holding ? governedTicks + 1 : 0;
		governing = fitted && !sim.isStopped() && position < 0.95;
		// A redstone signal has turned the governed speed down
		commanded = governing && target < setting - 0.5;
		float rpm = (float) sim.rpm();
		for (BlockPos pos : layout.governors())
			if (level.getBlockEntity(pos) instanceof GovernorBlockEntity be)
				be.reading(rpm, (float) position, (float) be.governedSpeed());
	}

	/**
	 * Tells each sensor in the engine's pipes what its network is doing. A MAF sensor gets the breath of the cylinders
	 * drawing on its intake and how much of their shot it held back or added; an exhaust heat exchanger gets the heat of
	 * the cylinders that fired into its exhaust. A stopped engine reads nothing, rather than its last breath for ever.
	 */
	private void readSensors(Level level) {
		mafTrimming = false;
		mafEnriching = false;
		mafReading = false;
		exchangerLevel = 0;
		boolean running = !sim.isStopped();
		for (int port = 0; port < layout.ports()
			.size(); port++) {
			EngineLayout.PortNetwork network = layout.ports()
				.get(port);
			if (network.sensors()
				.isEmpty())
				continue;
			boolean intake = network.kind() == PortKind.INTAKE;
			double charge = 0;
			double cut = 0;
			double added = 0;
			double heat = 0;
			int breathing = 0;
			for (int i = 0; running && i < models.size(); i++) {
				CylinderModel model = models.get(i);
				if (model == null)
					continue;
				CycleStats last = model.lastCycle();
				EngineLayout.Cylinder placed = layout.cylinders()
					.get(i);
				if (intake && placed.intake() == port && last.result != CycleResult.NONE) {
					breathing++;
					charge += Sensors.airCharge(model.spec, last.freshAirAtInjection);
					cut += last.mafCut;
					added += last.mafAdded;
				}
				if (!intake && placed.exhaust() == port)
					heat += model.exhaustHeatLastTick();
			}
			float meanCharge = breathing == 0 ? 0 : (float) (charge / breathing);
			float meanCut = breathing == 0 ? 0 : (float) (cut / breathing);
			float meanAdded = breathing == 0 ? 0 : (float) (added / breathing);
			// Exchangers on one network split its heat between them
			int exchangers = 0;
			for (BlockPos pos : network.sensors())
				if (!intake && level.getBlockEntity(pos) instanceof HeatExchangerBlockEntity)
					exchangers++;
			float heatShare = exchangers == 0 ? 0 : (float) (heat / EngineSimulation.TICK / exchangers);
			if (intake) {
				mafReading |= breathing > 0;
				mafTrimming |= breathing > 0 && meanCut >= EngineMilestones.MAF_TRIMMING;
				mafEnriching |= breathing > 0 && meanAdded >= EngineMilestones.MAF_ENRICHING;
			}
			for (BlockPos pos : network.sensors()) {
				var be = level.getBlockEntity(pos);
				if (be instanceof MafSensorBlockEntity maf && intake)
					maf.reading(meanCharge, meanCut, meanAdded);
				else if (be instanceof HeatExchangerBlockEntity exchanger && !intake) {
					exchanger.reading(heatShare);
					exchangerLevel = Math.max(exchangerLevel, exchanger.heatLevel());
				}
			}
		}
	}

	/**
	 * Swaps oil in the pans that have an exchange running, so fresh oil takes the place of spent oil, and spends some of
	 * what condition is left for the turning done. An engine that is not turning neither ages its oil nor flushes it.
	 */
	private void serviceOil(Level level) {
		exchanging = false;
		if (sim.revolutionsLastTick() <= 0)
			return;
		double swapped = 0;
		boolean fitted = false;
		for (BlockPos pos : layout.sumps()) {
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SumpBlockEntity sump))
				continue;
			fitted |= sump.filtered();
			swapped += sump.exchange(EngineSimulation.TICK);
		}
		filtered = fitted;
		sim.ageOil(fitted);
		if (swapped > 0) {
			sim.lubrication()
				.refresh(swapped);
			exchanging = true;
		}
	}

	private void publishSpeed() {
		float maxSpeed = AllConfigs.server().kinetics.maxRotationSpeed.get();
		float target = 0;
		if (!sim.isStopped())
			target = sim.direction() * Math.min(maxSpeed, Math.max(1, Math.round(sim.rpm())));
		float current = primary.getGeneratedSpeed();
		ticksSincePublish++;
		if (target == current)
			return;
		boolean startOrStop = target == 0 || current == 0 || Math.signum(target) != Math.signum(current);
		boolean significant = Math.abs(target - current) >= Math.max(1, Math.abs(current) * 0.04f);
		if (startOrStop || significant && ticksSincePublish >= PUBLISH_INTERVAL) {
			primary.publishSpeed(target);
			ticksSincePublish = 0;
		}
	}

	/**
	 * Re-measures the torque curve when something it depends on has changed: which cylinders have fuel and of what
	 * kind and metering, which have a spark to burn it by, which cams are geared 1:2, and how well oiled the
	 * engine is.
	 */
	private void updateRating() {
		List<Object> key = new ArrayList<>();
		key.add((int) Math.round(sim.lubrication()
			.effective() * 5));
		// Spent oil and a cold or cool block change what the engine can deliver, in steps coarse enough not to re-rate it constantly
		key.add((int) Math.round(sim.lubrication()
			.condition() * 8));
		key.add((int) Math.round(sim.thermal()
			.cold() * 4));
		key.add((int) Math.round(sim.thermal()
			.cool() * 4));
		for (CylinderModel model : sim.cylinders()) {
			key.add(model.fuelSupply.fuel());
			key.add(model.pilotSupply.fuel());
			key.add((int) Math.round(model.metering * 20));
			key.add((int) Math.round(model.intakeFactor * 20));
			key.add((int) Math.round(model.fuelTrim * 20));
			key.add((int) Math.round(model.airMatch * 20));
			key.add(model.ignition.installed() && model.ignition.powered());
			Camshaft camshaft = model.camshaft();
			key.add(camshaft != null && camshaft.isPhaseLocked());
		}
		for (Turbocharger turbo : sim.turbos())
			key.add((int) Math.round(turbo.limit * 20));
		for (Supercharger blower : sim.superchargers()) {
			key.add((int) Math.round(blower.limit * 20));
			key.add((int) Math.round(blower.driveRatio * 4));
		}
		key.add(sim.intercoolers()
			.size());
		key.add(sim.direction());
		// The curve runs as fast as the network is allowed to turn, so a raised limit has to be measured for
		float maxRpm = AllConfigs.server().kinetics.maxRotationSpeed.get();
		key.add(maxRpm);
		if (key.equals(ratingKey))
			return;
		ratingKey = key;
		rating = new Dynamometer.Measurement(sim, maxRpm);
	}

	/**
	 * Measures one more speed of the torque curve being re-measured. The curve in use stays the old one until the new
	 * one is whole, except for an engine that has none yet, which uses its new one as it fills: it is measured from the
	 * slowest speed up, so the speeds it is turning at are there first.
	 */
	private void continueRating() {
		if (rating == null)
			return;
		boolean done = rating.advance();
		if (done || !rated) {
			torqueCurve = rating.curve();
			rippleCurve = rating.ripples();
		}
		if (done) {
			rating = null;
			rated = true;
		}
	}

	/**
	 * The engine's torque curve as the network can use it.
	 *
	 * @param rpm      the speeds it was measured at
	 * @param torque   stress capacity per RPM the engine can be rated for at each, after roughness and the rating margin
	 * @param current  the engine's speed now
	 * @param complete the whole curve has been measured
	 */
	public record Curve(float[] rpm, float[] torque, float current, boolean complete) {
	}

	/** What a dynamometer block shows; asking for it counts as a dynamometer reading this engine. */
	public Curve curve() {
		dynoReadTick = ticks;
		float maxRpm = AllConfigs.server().kinetics.maxRotationSpeed.get();
		int points = Dynamometer.pointCount(maxRpm);
		float[] rpm = new float[points];
		float[] torque = new float[points];
		double scale = CEConfig.STRESS_PER_TORQUE.get() * RATING_MARGIN * Torsion.stiffness(flexLoss);
		for (int i = 0; i < points; i++) {
			rpm[i] = (float) Dynamometer.speed(i);
			torque[i] = (float) (Math.max(0, torqueCurve[i]) * Roughness.smoothness(rippleCurve[i]) * scale);
		}
		return new Curve(rpm, torque, (float) sim.rpm(), rated && rating == null);
	}

	private double ratedTorque() {
		return sim.isStopped() ? 0
			: Dynamometer.ratedTorque(torqueCurve, rippleCurve, sim.rpm()) * RATING_MARGIN * Torsion.stiffness(flexLoss);
	}

	/**
	 * How many throws sit on each of the engine's crank lines. The scanner lists the crank blocks line by line, and a
	 * line is unbroken, so a block that is not the next one along the axis from the one before it begins a new line.
	 */
	private static int[] throwsPerLine(EngineLayout layout) {
		List<EngineLayout.Segment> segments = layout.segments();
		boolean[] isThrow = new boolean[segments.size()];
		boolean[] startsLine = new boolean[segments.size()];
		Direction up = Direction.fromAxisAndDirection(layout.axis(), Direction.AxisDirection.POSITIVE);
		for (int i = 0; i < isThrow.length; i++) {
			isThrow[i] = segments.get(i)
				.isThrow();
			if (i == 0)
				continue;
			BlockPos before = segments.get(i - 1)
				.pos();
			BlockPos here = segments.get(i)
				.pos();
			startsLine[i] = !before.relative(up)
				.equals(here) && !before.relative(up.getOpposite())
					.equals(here);
		}
		return Torsion.throwsPerLine(isThrow, startsLine);
	}

	private void publishCapacity(double torqueScale) {
		double target = ratedTorque() * torqueScale;
		float current = primary.publishedCapacity();
		ticksSinceCapacity++;
		boolean dropped = target == 0 && current != 0;
		boolean significant = Math.abs(target - current) > Math.max(0.5, 0.05 * current);
		if (dropped || significant && ticksSinceCapacity >= CAPACITY_INTERVAL) {
			primary.publishCapacity((float) target);
			ticksSinceCapacity = 0;
		}
	}

	private EngineState state() {
		if (sim.isStopped())
			return EngineState.STALLED;
		if (cranking)
			return EngineState.CRANKING;
		if (primary.isOverStressed())
			return EngineState.OVERLOADED;
		for (CylinderModel model : models)
			if (model != null && model.lastCycle().result.fired())
				return EngineState.RUNNING;
		// Still turning, but nothing is burning fuel: it will run down
		return EngineState.COASTING;
	}

	private EngineSummary summary(EngineState state, double torqueScale) {
		int firing = 0;
		int working = 0;
		List<Float> firingAngles = new ArrayList<>();
		for (CylinderModel model : models) {
			if (model == null)
				continue;
			working++;
			if (model.lastCycle().result.fired())
				firing++;
			Camshaft camshaft = model.camshaft();
			if (camshaft != null && camshaft.isPhaseLocked())
				firingAngles.add((float) model.firingRotorAngle());
		}
		// A rotor fires once a turn of the shaft, which in the engine's 720 degrees is twice; its three chambers are the
		// same event, so only the first is listed
		for (int i = 0; i < models.size(); i++) {
			Cylinder placed = layout.cylinders()
				.get(i);
			if (models.get(i) == null || !placed.rotary() || placed.chamber() != 0)
				continue;
			float angle = (float) (((sim.direction() * placed.tdcAngle() + 180) % 360 + 360) % 360);
			firingAngles.add(angle);
			firingAngles.add(angle + 360);
		}
		firingAngles.sort(Float::compare);
		float[] angles = new float[firingAngles.size()];
		for (int i = 0; i < angles.length; i++)
			angles[i] = firingAngles.get(i);
		return new EngineSummary(state, (float) sim.rpm(), primary.publishedCapacity(),
			(float) (smoothedLoad * torqueScale), (float) (sim.fuelRate() * 60), (float) sim.ripple(), firing,
			working, (float) sim.inertia(), (float) sim.lubrication()
				.effective(),
			activeStarter != null, angles, layout.lines(), (float) sim.lubrication()
				.dilution(),
			(float) (sim.isStopped() ? 0 : Dynamometer.roughnessLoss(torqueCurve, rippleCurve, sim.rpm())),
			(float) sim.lubrication()
				.condition(),
			(float) sim.thermal()
				.temperature(),
			(float) sim.thermal()
				.coolantRate(),
			pilotLit(), filtered, (float) Torsion.scaled(flexLoss), longestLine);
	}

	/** Whether a pilot shot, and not compression or a spark, lit the charge in a cylinder on its last cycle. */
	private boolean pilotLit() {
		for (CylinderModel model : models)
			if (model != null && model.lastCycle().pilotShot > 0 && model.lastCycle().result.fired())
				return true;
		return false;
	}

	private void syncDiagnostics(Level level, double torqueScale) {
		EngineState state = state();
		primary.updateSummary(summary(state, torqueScale));
		reportMilestones(level, state);
		syncTurbos(level);
		syncSuperchargers(level);

		for (EngineLayout.Segment segment : layout.segments())
			if (level.getBlockEntity(segment.pos()) instanceof CrankshaftBlockEntity crank)
				crank.setPrimary(layout.primary(), segment.sense());

		for (int i = 0; i < models.size(); i++) {
			Cylinder cylinder = layout.cylinders()
				.get(i);
			if (cylinder.rotary()) {
				if (level.getBlockEntity(cylinder.pos()) instanceof RotorHousingBlockEntity housing)
					housing.updateChamber(cylinder.chamber(), status(cylinder, models.get(i)));
				continue;
			}
			if (!(level.getBlockEntity(cylinder.pos()) instanceof CylinderBlockEntity be))
				continue;
			CylinderModel model = models.get(i);
			CycleStats last = model == null ? null : model.lastCycle();
			float timing = Float.NaN;
			if (model != null && model.camshaft() != null && model.camshaft()
				.isPhaseLocked())
				timing = (float) model.timingError(sim.direction());
			FuelSpec fuel = model == null ? null : model.fuelSupply.fuel();
			float charge = last == null || last.fuelInjected <= 0 ? Float.NaN : (float) last.compressionTemp;
			List<BlockPos> outlets = cylinder.exhaust() < 0 ? List.of() : layout.ports()
				.get(cylinder.exhaust())
				.outlets();
			be.updateStatus(new CylinderStatus(layout.primary(), cylinder.faults(),
				last == null ? com.createengines.engine.sim.CycleResult.NONE : last.result, last != null && last.rich(),
				(float) cylinder.tdcAngle(), timing, charge, fuel == null ? Float.NaN : (float) fuel.autoignitionTemp(),
				cylinder.lobe(), outlets, last != null && last.lean(),
				last == null || last.equivalence <= 0 ? Float.NaN : (float) last.equivalence, cylinder.sparkPlug(),
				model == null ? 0 : (float) model.fouling(), model == null ? 0 : (float) model.lobeShift()));
		}

		for (BlockPos pos : layout.batteries())
			if (level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)
				battery.attachToEngine(layout.primary());
		for (BlockPos pos : layout.sumps())
			if (level.getBlockEntity(pos) instanceof SumpBlockEntity sump)
				sump.attachToEngine(layout.primary());
		for (BlockPos pos : layout.alternators())
			if (level.getBlockEntity(pos) instanceof AlternatorBlockEntity alternator)
				alternator.attachToEngine(layout.primary(), layout.batteries());

		for (int line = 0; line < camshafts.size(); line++) {
			Camshaft camshaft = camshafts.get(line);
			for (BlockPos pos : layout.camLines()
				.get(line)
				.segments()) {
				if (!(level.getBlockEntity(pos) instanceof CamshaftBlockEntity cam))
					continue;
				int actuated = 0;
				float timing = Float.NaN;
				for (int i = 0; i < models.size(); i++) {
					Cylinder cylinder = layout.cylinders()
						.get(i);
					if (!pos.equals(cylinder.lobe()))
						continue;
					actuated++;
					CylinderModel model = models.get(i);
					if (model != null && camshaft.isPhaseLocked() && Float.isNaN(timing))
						timing = (float) model.timingError(sim.direction());
				}
				cam.updateStatus(layout.primary(), (float) camshaft.ratio, camshaft.isPhaseLocked(),
					(float) racks[line], actuated, timing);
			}
		}
	}

	/** What a rotor chamber shows through goggles and effects. */
	private CylinderStatus status(Cylinder cylinder, @Nullable CylinderModel model) {
		CycleStats last = model == null ? null : model.lastCycle();
		FuelSpec fuel = model == null ? null : model.fuelSupply.fuel();
		List<BlockPos> outlets = cylinder.exhaust() < 0 ? List.of() : layout.ports()
			.get(cylinder.exhaust())
			.outlets();
		return new CylinderStatus(layout.primary(), cylinder.faults(),
			last == null ? CycleResult.NONE : last.result, last != null && last.rich(), (float) cylinder.tdcAngle(),
			Float.NaN, last == null || last.fuelInjected <= 0 ? Float.NaN : (float) last.compressionTemp,
			fuel == null ? Float.NaN : (float) fuel.autoignitionTemp(), null, outlets, last != null && last.lean(),
			last == null || last.equivalence <= 0 ? Float.NaN : (float) last.equivalence, cylinder.sparkPlug(),
			model == null ? 0 : (float) model.fouling(), 0);
	}

	private void syncSuperchargers(Level level) {
		for (int i = 0; i < blowers.size(); i++) {
			Supercharger blower = blowers.get(i);
			if (level.getBlockEntity(layout.superchargers()
				.get(i)
				.pos()) instanceof SuperchargerBlockEntity be)
				be.updateBlower(layout.primary(), blower.compressor()
					.size(), (float) blower.boost(), (float) blower.bypass());
		}
	}

	private void syncTurbos(Level level) {
		for (int i = 0; i < turbos.size(); i++) {
			Turbo placed = layout.turbos()
				.get(i);
			Turbocharger turbo = turbos.get(i);
			if (level.getBlockEntity(placed.pos()) instanceof TurbochargerBlockEntity be)
				be.updateStatus(new TurboStatus((float) turbo.spool(), (float) turbo.boost(), (float) turbo.wastegate(),
					(float) turbo.backPressure(), (float) charge(turbo), !turbo.compressor()
						.isEmpty(),
					!turbo.turbine()
						.isEmpty(),
					(float) turbo.vanes(), turbo.antiLagging()));
		}
	}

	/** The boost on the air a turbo's compressor feeds, all stages and coolers counted. */
	private static double charge(Turbocharger turbo) {
		double charge = 0;
		for (CylinderModel cylinder : turbo.compressor())
			charge = Math.max(charge, cylinder.intakePressure - 1);
		return charge;
	}

	private static float[] toArray(List<Float> values) {
		float[] array = new float[values.size()];
		for (int i = 0; i < array.length; i++)
			array[i] = values.get(i);
		return array;
	}

	/** Credits the engine's operator with anything notable it is doing, no more than once every ten seconds. */
	private void reportMilestones(Level level, EngineState state) {
		boolean sparkFiring = false;
		boolean rich = false;
		boolean balanced = true;
		float fouling = 0;
		boolean pushrod = false;
		boolean precision = true;
		int firing = 0;
		int cylinders = 0;
		List<Float> firingAngles = new ArrayList<>();
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			cylinders++;
			if (model == null) {
				precision = false;
				continue;
			}
			precision &= layout.cylinders()
				.get(i)
				.precision() == PrecisionParts.ALL;
			fouling = Math.max(fouling, (float) model.fouling());
			if (!model.lastCycle().result.fired())
				continue;
			firing++;
			if (model.camshaft() != null && model.camshaft()
				.isPhaseLocked())
				firingAngles.add((float) model.firingRotorAngle());
			Cylinder placed = layout.cylinders()
				.get(i);
			pushrod |= placed.lobe() != null && placed.lobe()
				.distManhattan(placed.pos()) == 1;
			sparkFiring |= model.ignition.installed();
			CycleStats cycle = model.lastCycle();
			rich |= cycle.rich();
			balanced &= !cycle.rich() && Mixture.balanced(cycle.equivalence);
		}
		float boost = 0;
		boolean dumping = false;
		for (Turbocharger turbo : turbos) {
			boost = Math.max(boost, (float) turbo.boost());
			dumping |= turbo.dump;
		}
		float rpm = (float) sim.rpm();
		reportInduction(level, state);
		reportRotary(level, state);
		reportRefining(level, state);
		reportService(level, state);
		award(level, EngineMilestones.reachedFlex(state == EngineState.RUNNING, firing, (float) Torsion.scaled(flexLoss)));
		reportMechanics(level, state);
		reportParts(level, state, firing);
		EngineMilestones.Snapshot snapshot = new EngineMilestones.Snapshot(state, previousState, rpm,
			activeStarter != null, firing, primary.publishedCapacity() * rpm, sparkFiring, (float) sim.lubrication()
				.effective(),
			rich, boost, dumping, fouling, firing > 0 && balanced, (float) sim.lubrication()
				.dilution(),
			pushrod, firingAngles.size() == cylinders && EngineMilestones.evenFiring(toArray(firingAngles)),
			mafTrimming, mafEnriching,
			governedTicks >= GOVERNED_TICKS, precision && cylinders >= EngineMilestones.EVEN_FIRING_CYLINDERS,
			layout.lines() > 1);
		previousState = state;
		award(level, EngineMilestones.reached(snapshot));
	}

	/** Credits the operator with each milestone, skipping any reported within the last {@link #MILESTONE_COOLDOWN} ticks. */
	private void award(Level level, Set<Milestone> reached) {
		for (Milestone milestone : reached) {
			Integer last = milestoneTicks.get(milestone);
			if (last != null && ticks - last < MILESTONE_COOLDOWN)
				continue;
			milestoneTicks.put(milestone, ticks);
			Achievements.award(level, layout.members(), milestone);
		}
	}

	/**
	 * Credits the operator with the parts the engine is putting to work: the camshaft that times it, the pipes it
	 * breathes through, the throttle, the fuel and oil it burns, its sensors and governor, and its precision parts.
	 */
	private void reportParts(Level level, EngineState state, int firing) {
		boolean camTimed = false;
		boolean piped = false;
		boolean throttled = false;
		int precision = 0;
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			if (model == null || !model.lastCycle().result.fired())
				continue;
			Cylinder placed = layout.cylinders()
				.get(i);
			camTimed |= model.camshaft() != null && model.camshaft()
				.isPhaseLocked();
			piped |= placed.intake() >= 0 && placed.exhaust() >= 0;
			throttled |= placed.intake() >= 0 && portOpening[placed.intake()] < EngineMilestones.THROTTLING;
			precision |= placed.precision();
		}
		Fluid fuel = fuels.fluid();
		boolean rotorHousing = false;
		for (Cylinder placed : layout.cylinders())
			rotorHousing |= placed.rotary();
		award(level, EngineMilestones.reachedParts(new EngineMilestones.Parts(state == EngineState.RUNNING && firing > 0,
			camTimed, piped, throttled, fuel != null && fuel.isSame(CEFluids.DIESEL.get()),
			fuel != null && fuel.isSame(CEFluids.GASOLINE.get()), (float) sim.lubrication()
				.effective(),
			!layout.sumps()
				.isEmpty(),
			mafReading, governing, commanded, precision, rotorHousing && !sim.isStopped())));
	}

	/** Credits the operator with a flywheel that smooths a running engine, variable vanes at work, and anti-lag. */
	private void reportMechanics(Level level, EngineState state) {
		boolean vaneBoost = false;
		boolean antiLagBoost = false;
		for (Turbocharger turbo : turbos) {
			vaneBoost |= turbo.variableGeometry && turbo.boost() >= EngineMilestones.STAGE_BOOST
				&& turbo.vanes() >= EngineMilestones.VANES_CLOSING;
			antiLagBoost |= turbo.antiLagging() && turbo.boost() >= EngineMilestones.STAGE_BOOST;
		}
		float roughness = (float) (sim.isStopped() ? 0 : Dynamometer.roughnessLoss(torqueCurve, rippleCurve, sim.rpm()));
		// Banks: firing pistons that share a throw, on sides a quarter turn apart (a vee) or opposite (flat)
		Map<BlockPos, List<Direction>> bores = new java.util.HashMap<>();
		int firing = 0;
		boolean sharedLobe = false;
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			Cylinder placed = layout.cylinders()
				.get(i);
			if (model == null || placed.rotary() || !model.lastCycle().result.fired())
				continue;
			firing++;
			// A cylinder on a shared lobe is timed at an offset from the cylinder the lobe's angle sets
			sharedLobe |= !Double.isNaN(placed.lobeLeaderTdc()) && model.lobeShift() > 0.5;
			bores.computeIfAbsent(placed.throwPos(), $ -> new ArrayList<>())
				.add(placed.bore());
		}
		boolean vee = false;
		boolean flat = false;
		for (List<Direction> sides : bores.values())
			for (int a = 0; a < sides.size(); a++)
				for (int b = a + 1; b < sides.size(); b++) {
					flat |= sides.get(a) == sides.get(b)
						.getOpposite();
					vee |= sides.get(a)
						.getAxis() != sides.get(b)
							.getAxis();
				}
		award(level, EngineMilestones.reachedMechanics(new EngineMilestones.Mechanics(state == EngineState.RUNNING,
			(float) sim.rpm(), layout.flywheelInertia(), roughness, vaneBoost, antiLagBoost, firing, vee, flat,
			ticks - dynoReadTick < 2 * SYNC_INTERVAL && rated && rating == null, sharedLobe)));
	}

	/** Credits the operator with an engine burning ethanol, and one whose exhaust heat is cooking a basin's recipe. */
	private void reportRefining(Level level, EngineState state) {
		Fluid fuel = fuels.fluid();
		award(level, EngineMilestones.reachedRefining(state == EngineState.RUNNING,
			fuel != null && fuel.isSame(CEFluids.ETHANOL.get()), exchangerLevel));
	}

	/** Credits the operator with oil that is kept fresh, an engine that has warmed up or is cooled, and a pilot shot at work. */
	private void reportService(Level level, EngineState state) {
		Fluid fuel = fuels.fluid();
		award(level, EngineMilestones.reachedService(new EngineMilestones.Service(state == EngineState.RUNNING,
			(float) sim.rpm(), exchanging, filtered, (float) sim.lubrication()
				.condition(),
			(float) sim.thermal()
				.temperature(),
			(float) sim.thermal()
				.cool(),
			sim.thermal()
				.coolantRate() > 0,
			pilotLit(), fuel != null && fuel.isSame(CEFluids.USED_OIL.get()))));
	}

	/** Credits the operator with a running rotary engine, a high-revving one, and one with several rotors. */
	private void reportRotary(Level level, EngineState state) {
		Set<BlockPos> firing = new HashSet<>();
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			Cylinder placed = layout.cylinders()
				.get(i);
			if (model != null && placed.rotary() && model.lastCycle().result.fired())
				firing.add(placed.pos());
		}
		award(level, EngineMilestones.reachedRotary(
			new EngineMilestones.Rotary(state == EngineState.RUNNING, (float) sim.rpm(), firing.size())));
	}

	/** Credits the operator with boost from superchargers, stacked stages and intercoolers. */
	private void reportInduction(Level level, EngineState state) {
		Map<CylinderModel, int[]> stages = new IdentityHashMap<>();
		// Per cylinder: turbos in series, blowers, turbos side by side
		for (Turbocharger turbo : turbos)
			if (turbo.boost() >= EngineMilestones.STAGE_BOOST)
				for (CylinderModel cylinder : turbo.compressor())
					stages.computeIfAbsent(cylinder, $ -> new int[3])[turbo.parallelCompressor ? 2 : 0]++;
		float blowerBoost = 0;
		for (Supercharger blower : blowers) {
			if (blower.boost() >= EngineMilestones.STAGE_BOOST)
				for (CylinderModel cylinder : blower.compressor())
					stages.computeIfAbsent(cylinder, $ -> new int[3])[1]++;
			if (blower.compressor()
				.stream()
				.anyMatch(cylinder -> cylinder.lastCycle().result.fired()))
				blowerBoost = Math.max(blowerBoost, (float) blower.boost());
		}
		int turboStages = 0;
		int blowerStages = 0;
		int twinStages = 0;
		for (int[] count : stages.values()) {
			turboStages = Math.max(turboStages, count[0]);
			blowerStages = Math.max(blowerStages, count[1]);
			twinStages = Math.max(twinStages, count[2]);
			if (count[0] + count[2] >= 1 && count[1] >= 1) {
				turboStages = Math.max(turboStages, 1);
				blowerStages = Math.max(blowerStages, 1);
			}
		}
		float cooled = 0;
		for (Intercooler cooler : sim.intercoolers())
			for (CylinderModel cylinder : cooler.charge())
				if (cylinder.lastCycle().result.fired())
					cooled = Math.max(cooled, (float) (cylinder.intakePressure - 1));
		award(level, EngineMilestones.reachedInduction(new EngineMilestones.Induction(
			state == EngineState.RUNNING, blowerBoost, turboStages, blowerStages, twinStages, cooled)));
	}

	/** Called when this engine is being replaced or dismantled: stop driving the network. */
	public void shutDown() {
		primary.publishCapacity(0);
		primary.publishSpeed(0);
	}
}
