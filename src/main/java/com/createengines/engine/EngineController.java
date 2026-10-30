package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.config.CEConfig;
import com.createengines.content.airflow.LambdaSensorBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.limiter.RevLimiterBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.Camshaft;
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
import com.createengines.engine.sim.RevLimiter;
import com.createengines.engine.sim.Sensors;
import com.createengines.engine.sim.Throttle;
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
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
	public record Motion(double angle, double omega, int direction, double film, double dilution,
						 @Nullable EngineController source) {
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
	/** How many moving parts need oil: crank blocks, plus every piston and rod turning with the crank. */
	private final double oilParts;

	private int ticks;
	private int ticksSincePublish = PUBLISH_INTERVAL;
	private int ticksSinceCapacity = CAPACITY_INTERVAL;
	private double smoothedLoad;
	private double[] torqueCurve = new double[Dynamometer.SPEEDS.length];
	private @Nullable List<Object> ratingKey;
	private boolean cranking;
	private @Nullable BlockPos activeStarter;
	private EngineState previousState = EngineState.STALLED;
	private final Map<Milestone, Integer> milestoneTicks = new EnumMap<>(Milestone.class);
	private final RevLimiter revLimiter = new RevLimiter();
	private final Governor governor = new Governor();
	/** Ticks the governor has held the engine on its setting while carrying a load. */
	private int governedTicks;
	/** What the engine-management blocks last saw, for milestones. */
	private boolean limiting;
	private boolean mafTrimming;
	private boolean lambdaPresent;

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
		FuelSupply fuelGallery = new GalleryFuelSupply(level, layout.heads());
		OilSupply oilGallery = new GalleryOilSupply(level, layout.heads());
		// The pans are the engine's reserve and are drawn on first; the head tanks top up what they cannot give
		OilSupply pans = new SumpOilSupply(level, layout.sumps());
		oilSupply = mb -> {
			double delivered = pans.draw(mb);
			return delivered < mb ? delivered + oilGallery.draw(mb - delivered) : delivered;
		};

		IgnitionSource plugs = new BatteryIgnition(level, layout.batteries());

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
			CylinderModel model = new CylinderModel(PrecisionParts.spec(cylinder.precision()), CamProfile.BASIC,
				cylinder.tdcAngle(), camshaft, cylinder.lobeAngle());
			model.fuelSupply = CylinderFault.NO_INJECTOR.in(cylinder.faults()) ? FuelSupply.NONE : fuelGallery;
			model.ignition = cylinder.sparkPlug() ? plugs : IgnitionSource.NONE;
			model.intakeFactor = flow(cylinder.intake());
			model.exhaustFactor = flow(cylinder.exhaust());
			models.add(model);
			working.add(model);
		}

		oilParts = parts;

		double inertia = idleInertia + layout.flywheels() * CEConfig.FLYWHEEL_INERTIA.get();
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
			// Several turbines on one exhaust network split its heat between them
			long sharing = layout.turbos()
				.stream()
				.filter(other -> turbo.exhaust() >= 0 && other.exhaust() == turbo.exhaust())
				.count();
			Turbocharger unit = new Turbocharger(turbine, compressor, 1d / Math.max(1, sharing));
			turbos.add(unit);
			sim.addTurbo(unit);
		}
		for (EngineLayout.Inline placed : layout.superchargers()) {
			Supercharger blower = new Supercharger(breathingOn(placed.intake()));
			blowers.add(blower);
			sim.addSupercharger(blower);
		}
		for (EngineLayout.Inline placed : layout.intercoolers())
			sim.addIntercooler(new Intercooler(breathingOn(placed.intake())));
		// A new engine starts dry, and has to be oiled
		sim.lubrication()
			.setFilm(motion == null ? 0 : motion.film());
		sim.lubrication()
			.setDilution(motion == null ? 0 : motion.dilution());
		if (motion != null)
			sim.restore(motion.angle(), motion.omega(), motion.direction());
		for (CylinderModel model : working)
			model.resetGas(model.pistonAngle(sim.angle(), sim.direction()));
		if (motion != null && motion.source() != null)
			carryCylinders(motion.source());
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
			this);
	}

	public void tick() {
		Level level = primary.getLevel();
		if (level == null)
			return;
		ticks++;
		double torqueScale = CEConfig.STRESS_PER_TORQUE.get();

		if (layout.incomplete()) {
			sim.restore(sim.angle(), 0, sim.direction());
			publishSpeed();
			publishCapacity(torqueScale);
			if (ticks % SYNC_INTERVAL == 0)
				primary.updateSummary(summary(EngineState.INCOMPLETE, torqueScale));
			return;
		}

		float crankSpeed = primary.getTheoreticalSpeed();
		measureCamshafts(level, crankSpeed);
		readThrottles(level);
		readTurbos(level);
		readSuperchargers(level);
		readLimiters(level);
		readGovernors(level);
		if (ticks % RACK_INTERVAL == 1)
			readFuelRacks(level);
		if (ticks % RATING_INTERVAL == 1)
			updateRating();

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
			double opening = 1;
			for (BlockPos pos : layout.ports()
				.get(port)
				.throttles())
				if (level.getBlockEntity(pos) instanceof ThrottleBlockEntity throttle)
					opening = Math.min(opening, throttle.opening());
			portOpening[port] = opening;
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
			}
		}
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
			if (model != null && head != null && level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be)
				model.metering = be.meteringFraction();
			if (model != null)
				model.airMatch = airMatch(level, i);
		}
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
	 * Rev limiters on the engine: the lowest limit set governs, and every injector is held shut while the crank is at or
	 * above it until it has fallen a little below. Read every tick, because it is a safety: a changed limit counts at once.
	 */
	private void readLimiters(Level level) {
		double limit = Double.MAX_VALUE;
		for (BlockPos pos : layout.limiters())
			if (level.getBlockEntity(pos) instanceof RevLimiterBlockEntity limiter)
				limit = Math.min(limit, limiter.limit());
		boolean fitted = limit < Double.MAX_VALUE;
		if (!fitted)
			revLimiter.reset();
		boolean cut = fitted && revLimiter.update(sim.rpm(), limit);
		limiting = cut;
		for (CylinderModel model : sim.cylinders())
			model.fuelCut = cut;
		float rpm = (float) sim.rpm();
		for (BlockPos pos : layout.limiters())
			if (level.getBlockEntity(pos) instanceof RevLimiterBlockEntity limiter)
				limiter.reading(rpm, cut);
	}

	/**
	 * Governors on the engine: the lowest speed set governs, and the rack (the throttle, in a spark engine) is moved to
	 * hold the crank there. Read every tick because a governor is a feedback loop: it has to keep up with the load.
	 */
	private void readGovernors(Level level) {
		double target = Double.MAX_VALUE;
		for (BlockPos pos : layout.governors())
			if (level.getBlockEntity(pos) instanceof GovernorBlockEntity be)
				target = Math.min(target, be.target());
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
		float rpm = (float) sim.rpm();
		for (BlockPos pos : layout.governors())
			if (level.getBlockEntity(pos) instanceof GovernorBlockEntity be)
				be.reading(rpm, (float) position);
	}

	/**
	 * Tells each sensor in the engine's pipes what its network is doing. A MAF sensor gets the breath of the cylinders
	 * drawing on its intake and how much of their shot it held back; a lambda sensor gets the mixture of the cylinders
	 * that fired into its exhaust. A stopped engine reads nothing, rather than its last breath for ever.
	 */
	private void readSensors(Level level) {
		mafTrimming = false;
		lambdaPresent = false;
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
			double mixture = 0;
			int breathing = 0;
			int firing = 0;
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
				} else if (!intake && placed.exhaust() == port && last.result.fired() && last.equivalence > 0) {
					firing++;
					mixture += last.equivalence;
				}
			}
			float meanCharge = breathing == 0 ? 0 : (float) (charge / breathing);
			float meanCut = breathing == 0 ? 0 : (float) (cut / breathing);
			float meanMixture = firing == 0 ? Float.NaN : (float) (mixture / firing);
			if (intake)
				mafTrimming |= breathing > 0 && meanCut >= EngineMilestones.MAF_TRIMMING;
			else
				lambdaPresent = true;
			for (BlockPos pos : network.sensors()) {
				var be = level.getBlockEntity(pos);
				if (be instanceof MafSensorBlockEntity maf && intake)
					maf.reading(meanCharge, meanCut);
				else if (be instanceof LambdaSensorBlockEntity lambda && !intake)
					lambda.reading(meanMixture);
			}
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
		for (CylinderModel model : sim.cylinders()) {
			key.add(model.fuelSupply.fuel());
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
		if (key.equals(ratingKey))
			return;
		ratingKey = key;
		torqueCurve = Dynamometer.torqueCurve(sim);
	}

	private double ratedTorque() {
		return sim.isStopped() ? 0 : Dynamometer.peakTorque(torqueCurve, sim.rpm()) * RATING_MARGIN;
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
		firingAngles.sort(Float::compare);
		float[] angles = new float[firingAngles.size()];
		for (int i = 0; i < angles.length; i++)
			angles[i] = firingAngles.get(i);
		return new EngineSummary(state, (float) sim.rpm(), primary.publishedCapacity(),
			(float) (smoothedLoad * torqueScale), (float) (sim.fuelRate() * 60), (float) sim.ripple(), firing,
			working, (float) sim.inertia(), (float) sim.lubrication()
				.effective(),
			activeStarter != null, angles, layout.lines(), (float) sim.lubrication()
				.dilution());
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
				model == null ? 0 : (float) model.fouling()));
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
						.isEmpty()));
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
		EngineMilestones.Snapshot snapshot = new EngineMilestones.Snapshot(state, previousState, rpm,
			activeStarter != null, firing, primary.publishedCapacity() * rpm, sparkFiring, (float) sim.lubrication()
				.effective(),
			rich, boost, dumping, fouling, firing > 0 && balanced, (float) sim.lubrication()
				.dilution(),
			pushrod, firingAngles.size() == cylinders && EngineMilestones.evenFiring(toArray(firingAngles)),
			mafTrimming, lambdaPresent && firing > 0 && balanced, limiting && sim.rpm() > 0,
			governedTicks >= GOVERNED_TICKS, precision && cylinders >= EngineMilestones.EVEN_FIRING_CYLINDERS,
			layout.lines() > 1);
		previousState = state;
		for (Milestone milestone : EngineMilestones.reached(snapshot)) {
			Integer last = milestoneTicks.get(milestone);
			if (last != null && ticks - last < MILESTONE_COOLDOWN)
				continue;
			milestoneTicks.put(milestone, ticks);
			Achievements.award(level, layout.members(), milestone);
		}
	}

	/** Credits the operator with boost from superchargers, stacked stages and intercoolers. */
	private void reportInduction(Level level, EngineState state) {
		Map<CylinderModel, int[]> stages = new IdentityHashMap<>();
		for (Turbocharger turbo : turbos)
			if (turbo.boost() >= EngineMilestones.STAGE_BOOST)
				for (CylinderModel cylinder : turbo.compressor())
					stages.computeIfAbsent(cylinder, $ -> new int[2])[0]++;
		float blowerBoost = 0;
		for (Supercharger blower : blowers) {
			if (blower.boost() >= EngineMilestones.STAGE_BOOST)
				for (CylinderModel cylinder : blower.compressor())
					stages.computeIfAbsent(cylinder, $ -> new int[2])[1]++;
			if (blower.compressor()
				.stream()
				.anyMatch(cylinder -> cylinder.lastCycle().result.fired()))
				blowerBoost = Math.max(blowerBoost, (float) blower.boost());
		}
		int turboStages = 0;
		int blowerStages = 0;
		for (int[] count : stages.values()) {
			turboStages = Math.max(turboStages, count[0]);
			blowerStages = Math.max(blowerStages, count[1]);
			if (count[0] >= 1 && count[1] >= 1) {
				turboStages = Math.max(turboStages, 1);
				blowerStages = Math.max(blowerStages, 1);
			}
		}
		float cooled = 0;
		for (Intercooler cooler : sim.intercoolers())
			for (CylinderModel cylinder : cooler.charge())
				if (cylinder.lastCycle().result.fired())
					cooled = Math.max(cooled, (float) (cylinder.intakePressure - 1));
		for (Milestone milestone : EngineMilestones.reachedInduction(new EngineMilestones.Induction(
			state == EngineState.RUNNING, blowerBoost, turboStages, blowerStages, cooled))) {
			Integer last = milestoneTicks.get(milestone);
			if (last != null && ticks - last < MILESTONE_COOLDOWN)
				continue;
			milestoneTicks.put(milestone, ticks);
			Achievements.award(level, layout.members(), milestone);
		}
	}

	/** Called when this engine is being replaced or dismantled: stop driving the network. */
	public void shutDown() {
		primary.publishCapacity(0);
		primary.publishSpeed(0);
	}
}
