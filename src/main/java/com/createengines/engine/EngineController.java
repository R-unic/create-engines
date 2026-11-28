package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.engine.sim.CamProfiles;
import com.createengines.engine.sim.Camshaft;
import com.createengines.engine.sim.CoolantSupply;

import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.Breather;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.TorqueSource;
import com.createengines.engine.sim.FuelSupply;
import com.createengines.engine.sim.IgnitionSource;
import com.createengines.engine.sim.IgnitionCoil;
import com.createengines.engine.sim.InjectorAssist;
import com.createengines.engine.sim.Torsion;
import com.createengines.engine.sim.Intercooler;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.sim.OilSupply;
import com.createengines.engine.sim.OutputGearing;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.CamLine;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.EngineLayout.Turbo;
import com.createengines.engine.topology.PrecisionParts;
import com.createengines.registry.CEDataMaps;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
	public record Motion(double angle, double omega, int direction, double film, double dilution, double condition,
						 double temperature, @Nullable EngineController source) {
	}

	private static final int PUBLISH_INTERVAL = 10;
	static final int SYNC_INTERVAL = 10;
	/** How long a heavy starter goes on choking an engine after it lets go, ticks, and the last stretch over which that fades. */
	private static final int COLD_ASSIST_HOLD = 160;
	private static final int COLD_ASSIST_FADE = 60;
	private static final int RACK_INTERVAL = 10;
	private static final int RATING_INTERVAL = 20;

	/** The engine's work, split by responsibility; each holds the state only it changes. */
	final EngineAmbient ambient = new EngineAmbient(this);
	final EngineInputs inputs = new EngineInputs(this);
	final EngineRating ratings = new EngineRating(this);
	final EngineExhaust exhaust = new EngineExhaust(this);
	final EngineReadouts readouts = new EngineReadouts(this);
	final EngineMilestoneReporter reporter = new EngineMilestoneReporter(this);

	final CrankshaftBlockEntity primary;
	final EngineLayout layout;
	final EngineSimulation sim;
	/** Parallel to {@link EngineLayout#cylinders()}; null where the cylinder can't work at all. */
	final List<@Nullable CylinderModel> models;
	/** Parallel to {@link EngineLayout#camLines()}. */
	final List<Camshaft> camshafts;
	final double[] racks;
	/** Parallel to {@link EngineLayout#ports()}: how open the throttling in each is, 1 where there is none. */
	final double[] portOpening;
	/** Parallel to {@link EngineLayout#turbos()}. */
	final List<Turbocharger> turbos = new ArrayList<>();
	/** Parallel to {@link EngineLayout#superchargers()}. */
	final List<Supercharger> blowers = new ArrayList<>();
	private final OilSupply oilSupply;
	/** How many moving parts need oil: crank blocks, plus every piston and rod turning with the crank. */
	private final double oilParts;
	/** The wear mode's reader and writer for this engine's cylinders (see {@link WearKeeper}); idle with the mode off. */
	private final WearKeeper wear;
	/** The wear mode as this engine was built: changing it has the engine built again, since its failures are read then. */
	private final boolean builtWithWear = com.createengines.engine.sim.Tuning.wearMode;
	/** The wear mode's faults by cylinder (blown gasket, broken head, thrown rod), when the mode is on; read when the engine is built, and a failure has it rebuilt. */
	private final Map<BlockPos, Integer> wearFaults;
	/** The wear mode's total for the whole build (see {@code WearTotal}), refreshed with each wearing step; none with the mode off. */
	final com.createengines.engine.sim.WearTotal.Held wearTotal = new com.createengines.engine.sim.WearTotal.Held();
	/** How many parts of each kind have failed in this build (see {@code WearFailures}), counted when the engine is built: a failure has it rebuilt. */
	final long wearFailures;
	/** Oil drawn from the pans and heads during the current tick, mB, and its smoothed rate in mB per minute: for goggles. */
	private double oilDrawn;
	double oilRate;
	/** Share of the rating this engine's crank lines lose to torsional flex at the shipped balance, and the throws on its longest line. */
	final double flexLoss;
	final int longestLine;

	int ticks;
	/** The crank speed an engine had when part of it left the loaded world, kept so it can carry on when that part returns. */
	private double heldOmega;
	private int ticksSincePublish = PUBLISH_INTERVAL;
	double smoothedLoad;
	boolean cranking;
	/** Ticks left that a heavy starter is still choking the engine; full strength until {@link #COLD_ASSIST_FADE} remain. */
	int coldAssistTicks;
	@Nullable BlockPos activeStarter;
	private final EngineStarters starters;
	/** What the spark plugs draw from, told how cold the engine is each tick and drawn on by a starter; null before the first build. */
	private @Nullable BatteryIgnition ignitionSupply;
	/** Whether a working cylinder has a spark plug: only a spark engine's starter draws on the batteries. */
	private boolean sparkEngine;
	/** The fuel gallery shared by every head, for telling which fuel the engine burns. */
	final GalleryFuelSupply fuels;

	/** Everything on this engine's crank: its cylinders, then whatever packs and addons added. */
	public List<TorqueSource> sources() {
		return sim.sources();
	}

	public EngineController(CrankshaftBlockEntity primary, EngineLayout layout, @Nullable Motion motion) {
		this.primary = primary;
		this.layout = layout;
		starters = new EngineStarters(layout);
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
		oilSupply = new OilSupply() {
			private double lubricity = 1;

			@Override
			public double draw(double mb) {
				double fromPans = pans.draw(mb);
				double fromGallery = fromPans < mb ? oilGallery.draw(mb - fromPans) : 0;
				double total = fromPans + fromGallery;
				oilDrawn += total;
				if (total > 0)
					lubricity = (fromPans * pans.lubricity() + fromGallery * oilGallery.lubricity()) / total;
				return total;
			}

			@Override
			public double lubricity() {
				return lubricity;
			}
		};

		// Wear (opt-in): a head whose gasket has blown stops its cylinder, and every other reading of the layout's faults sees that too
		wearFaults = com.createengines.engine.sim.Tuning.wearMode ? WearKeeper.wearFaults(level, layout) : Map.of();
		wearFailures = WearKeeper.failureCounts(wearFaults, com.createengines.engine.sim.Tuning.wearMode ? WearKeeper.failedTurbos(level, layout) : 0);
		BatteryIgnition plugs = new BatteryIgnition(level, layout.batteries());
		ignitionSupply = plugs;
		sparkEngine = layout.cylinders()
			.stream()
			.anyMatch(cylinder -> cylinder.sparkPlug() && !CylinderFault.disables(faultsOf(cylinder)));
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
			if (CylinderFault.disables(faultsOf(cylinder))) {
				models.add(null);
				double weight = CylinderFault.dragOnCrank(faultsOf(cylinder));
				idleInertia += weight * CylinderSpec.CRUDE.inertia();
				idleStatic += weight * CylinderSpec.CRUDE.frictionStatic() * CylinderFault.gripOnCrank(faultsOf(cylinder));
				idleViscous += weight * CylinderSpec.CRUDE.frictionViscous();
				parts += weight;
				continue;
			}
			parts++;
			Camshaft camshaft = cylinder.camLine() >= 0 ? camshafts.get(cylinder.camLine()) : null;
			// A rotor's ports are cut in its housing, so it needs no camshaft to time them
			CylinderModel model = cylinder.rotary()
				? CylinderModel.rotary(CEDataMaps.rotaryStats().apply(PrecisionParts.rotarySpec(cylinder.precision())), CamProfiles.rotary(),
					cylinder.tdcAngle(), cylinder.chamber())
				: new CylinderModel(CEDataMaps.partStats(PrecisionParts.has(cylinder.precision(), PrecisionParts.BORE))
						.apply(PrecisionParts.spec(cylinder.precision(), cylinder.effects())), CamProfiles.basic(), cylinder.tdcAngle(),
					camshaft, cylinder.lobeAngle());
			model.fuelSupply = CylinderFault.NO_INJECTOR.in(cylinder.faults()) ? FuelSupply.NONE : fuelGallery;
			model.ignition = cylinder.sparkPlug() ? plugs : IgnitionSource.NONE;
			model.assist = cylinder.injectorAssist() && !CylinderFault.NO_INJECTOR.in(cylinder.faults()) ? plugs.assist()
				: InjectorAssist.NONE;
			model.coil = cylinder.ignitionCoil() && cylinder.sparkPlug() ? plugs.coil() : IgnitionCoil.NONE;
			model.pilotSupply = pilots;
			model.ringSeal = PrecisionParts.ringSeal(cylinder.precision());
			model.lightingHeat = cylinder.rotary() ? 1 : PrecisionParts.lightingHeat(cylinder.precision());
			if (cylinder.rotary()) {
				model.rotorEarlyLoss = PrecisionParts.rotorEarlyLoss(cylinder.precision());
				model.rotorBoostGain = PrecisionParts.rotorBoostGain(cylinder.precision());
			}
			// Underwater, nothing gets in or out, wherever the pipes lead
			boolean submerged = CylinderFault.SUBMERGED.in(cylinder.faults());
			model.intakeFactor = submerged ? 0 : flow(cylinder.intake());
			model.exhaustFactor = submerged ? 0 : flow(cylinder.exhaust());
			if (!Double.isNaN(cylinder.lobeLeaderTdc()))
				model.shareLobe(cylinder.lobeLeaderTdc());
			models.add(model);
			working.add(model);
		}

		oilParts = parts;
		wear = new WearKeeper(layout, models);
		if (com.createengines.engine.sim.Tuning.wearMode)
			wear.refresh(level);
		else
			wear.applyFitted(level);
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
		sim.setIsolatedMass(layout.isolatedInertia(), CEConfig.DUAL_MASS_RESONANCE.get() * layout.isolatedResonanceFactor());
		// What packs and addons drive the crank with besides cylinders, and the networks any of it breathes from
		List<EngineProviders.Provided> provided = EngineProviders.sourcesFor(level, layout);
		int[] providedIntake = new int[provided.size()];
		int[] providedExhaust = new int[provided.size()];
		for (int i = 0; i < provided.size(); i++) {
			boolean breathes = provided.get(i)
				.source() instanceof Breather;
			providedIntake[i] = breathes ? layout.networkAt(provided.get(i)
				.intakeAt(), PortKind.INTAKE) : -1;
			providedExhaust[i] = breathes ? layout.networkAt(provided.get(i)
				.exhaustAt(), PortKind.EXHAUST) : -1;
		}
		for (Turbo turbo : layout.turbos()) {
			List<Breather> turbine = new ArrayList<>();
			List<Breather> compressor = new ArrayList<>();
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
			for (int i = 0; i < provided.size(); i++) {
				if (turbo.exhaust() >= 0 && providedExhaust[i] == turbo.exhaust())
					turbine.add((Breather) provided.get(i)
						.source());
				if (turbo.intake() >= 0 && providedIntake[i] == turbo.intake())
					compressor.add((Breather) provided.get(i)
						.source());
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
			Supercharger blower = new Supercharger(breathingOn(placed.intake(), provided, providedIntake));
			blower.parallel = placed.parallel();
			blower.variableDrive = level.getBlockState(placed.pos())
				.getBlock() instanceof SuperchargerBlock block && block.variableDrive();
			blowers.add(blower);
			sim.addSupercharger(blower);
		}
		for (EngineLayout.Inline placed : layout.intercoolers())
			sim.addIntercooler(new Intercooler(breathingOn(placed.intake(), provided, providedIntake)));
		for (int i = 0; i < provided.size(); i++) {
			TorqueSource source = provided.get(i)
				.source();
			if (source instanceof Breather breather && (providedIntake[i] >= 0 || providedExhaust[i] >= 0))
				sim.addAttachedSource(breather);
			else
				sim.addSource(source);
		}
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
		sim.setRadiators(layout.radiators()
			.isEmpty() ? com.createengines.engine.sim.Radiator.NONE : new RadiatorBank(level, layout.radiators()));
		sim.setThermalLive(true);
		sim.setAmbientDensity(ambient.airDensity());
		if (motion != null) {
			sim.restore(motion.angle(), motion.omega(), motion.direction());
			heldOmega = motion.omega();
			// Rotor chambers repeat every three turns, so carrying only the angle could swap their gas between them
			if (motion.source() != null)
				sim.restoreRotary(motion.source().sim.rotaryAngle());
		}
		for (CylinderModel model : working)
			model.resetGas(model.pistonAngle(sim.angle(), sim.rotaryAngle(), sim.direction()));
		if (motion != null && motion.source() != null) {
			carryCylinders(motion.source());
			sim.carryMeanSpeed(motion.source().sim);
			// A rebuilt engine is re-rated from scratch, but until that is done it is still the engine it was: dropping
			// its rating to nothing for the moment that takes would overstress everything it was driving
			ratings.torqueCurve = motion.source().ratings.torqueCurve;
			ratings.rippleCurve = motion.source().ratings.rippleCurve;
			ratings.rated = motion.source().ratings.rated;
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

	/** The working cylinders, and the sources a pack attached, that draw their air from intake network {@code port}. */
	private List<Breather> breathingOn(int port, List<EngineProviders.Provided> provided, int[] providedIntake) {
		List<Breather> found = new ArrayList<>();
		for (int i = 0; i < provided.size(); i++)
			if (providedIntake[i] == port && port >= 0)
				found.add((Breather) provided.get(i)
					.source());
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
			// A rotor's three chambers share one housing, so matching the block alone hands every chamber the first one's gas
			int chamber = layout.cylinders()
				.get(i)
				.chamber();
			for (int j = 0; j < old.models.size(); j++) {
				CylinderModel previous = old.models.get(j);
				if (previous != null && old.layout.cylinders()
					.get(j)
					.pos()
					.equals(pos)
					&& old.layout.cylinders()
						.get(j)
						.chamber() == chamber) {
					model.copyStateFrom(previous);
					break;
				}
			}
		}
	}

	double flow(int port) {
		return port < 0 ? 0 : layout.ports()
			.get(port)
			.flowFactor();
	}

	public EngineLayout layout() {
		return layout;
	}

	public Motion motion() {
		return new Motion(sim.angle(), layout.incomplete() ? heldOmega : sim.omega(), sim.direction(), sim.lubrication()
			.film(), sim.lubrication()
				.dilution(),
			sim.lubrication()
				.condition(),
			sim.thermal()
				.temperature(),
			this);
	}

	private static final int AIR_INTERVAL = 20;
	/** The six sides of a block, once: {@code Direction.values()} makes a new array every call. */
	static final Direction[] SIDES = Direction.values();

	public void tick() {
		Level level = primary.getLevel();
		if (level == null)
			return;
		ticks++;
		if (builtWithWear != com.createengines.engine.sim.Tuning.wearMode) {
			// The faults saved parts carry were read, or left unread, when this engine was built: it does nothing more, and the one
			// built in its place at the end of this tick carries on
			com.createengines.engine.topology.EngineTopology.rebuild(level, primary.getBlockPos());
			return;
		}
		if (ticks % AIR_INTERVAL == 0 && com.createengines.compat.sable.SableCompat.installed()) {
			sim.setAmbientDensity(ambient.airDensity());
			ambient.watchWorldWater(level);
		} else if (ticks % AIR_INTERVAL == 0 && (ambient.intakeLoss > 0 || com.createengines.compat.pollution.PollutionCompat.pollutedIntake()))
			sim.setAmbientDensity(ambient.airDensity());
		// SU per RPM of the shaft for each unit of torque: a faster shaft carries the same SU in less per RPM
		double torqueScale = CEConfig.STRESS_PER_TORQUE.get() / speedScale();

		if (layout.incomplete() || layout.broken()) {
			// An engine split by an unloaded chunk stops while it is split, but remembers how fast it was turning, as an engine
			// wholly unloaded does, so it picks up again when the chunk returns instead of coming back dead
			if (layout.incomplete() && sim.omega() != 0)
				heldOmega = sim.omega();
			else if (layout.broken())
				heldOmega = 0;
			sim.restore(sim.angle(), 0, sim.direction());
			// Nothing is turning over, so no starter is pushing: left alone its last cranking stress would stay on its input
			// network (and its goggles would still say cranking) for as long as the engine stays split
			activeStarter = null;
			cranking = false;
			for (BlockPos pos : layout.starters())
				if (LoadedBlocks.blockEntity(level, pos) instanceof StarterBlockEntity starter)
				{
					starter.updateCranking(layout.primary(), 0, false);
					starter.setElectricReady(false);
				}
			publishSpeed();
			ratings.publishCapacity(torqueScale);
			if (ticks % SYNC_INTERVAL == 0)
			{
				primary.updateSummary(readouts.summary(layout.incomplete() ? EngineState.INCOMPLETE : EngineState.BROKEN, torqueScale));
				readouts.glow(level, false);
				// Nothing is simulated, so the cylinders' last results are stale: left in place they keep firing on the
				// client for as long as anything turns a cam
				for (Cylinder cylinder : layout.cylinders())
					if (!cylinder.rotary() && LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof CylinderBlockEntity be)
						be.detachFromEngine();
			}
			return;
		}

		float crankSpeed = primary.getTheoreticalSpeed();
		measureCamshafts(level, crankSpeed);
		inputs.readThrottles(level);
		inputs.readTurbos(level);
		inputs.readSuperchargers(level);
		inputs.readGovernors(level);
		if (ticks % RACK_INTERVAL == 1)
			inputs.readFuelRacks(level);
		if (ticks % RATING_INTERVAL == 1)
			ratings.updateRating();
		ratings.continueRating();

		// Anything turning the crank harder than the engine itself: another kinetic source, or a starter
		double floor = 0;
		int floorDirection = sim.direction();
		if (primary.hasSource() && crankSpeed != 0 && drivenFromOutside()) {
			floor = EngineSimulation.rpmToOmega(Math.abs(crankSpeed) / speedScale());
			floorDirection = (int) Math.signum(crankSpeed);
		}
		activeStarter = null;
		// A cold engine makes each spark cost more of the battery. A starter turning a spark engine over draws on the same batteries
		boolean drawsBattery = false;
		if (ignitionSupply != null) {
			ignitionSupply.setEngineCold(sim.thermal()
				.felt());
			drawsBattery = com.createengines.engine.sim.Tuning.crankingDraw && sparkEngine && ignitionSupply.hasBatteries();
		}
		// A spark engine with charge in its batteries can be cranked by a starter with nothing on its input; a compression engine cannot
		boolean electricStart = drawsBattery && ignitionSupply.deliverableShare() > 0;
		starters.resolve(level);
		starters.setElectricReady(electricStart);
		activeStarter = starters.choose(floor, floorDirection);
		floor = starters.floor;
		floorDirection = starters.floorDirection;
		// A heavy starter keeps choking a cold engine for a few seconds after it lets go, so it can catch and settle
		boolean warming = starters.warming();
		coldAssistTicks = warming ? COLD_ASSIST_HOLD : Math.max(0, coldAssistTicks - 1);
		sim.thermal()
			.setCrankAssist(Math.min(1, coldAssistTicks / (double) COLD_ASSIST_FADE));

		// An overstressed network has stopped everything on it, so nothing is drawing on the engine
		double load = crankSpeed == 0 || primary.isOverStressed() ? 0
			: primary.networkStress() / Math.abs(crankSpeed) / torqueScale;
		smoothedLoad += (load - smoothedLoad) * 0.1;
		double maxOmega = EngineSimulation.rpmToOmega(engineLimit());
		sim.tickCranked(load, maxOmega, floor, floorDirection);
		oilDrawn = 0;
		sim.lubrication()
			.update(sim.revolutionsLastTick(), EngineSimulation.TICK, oilParts, oilSupply);
		oilRate += (oilDrawn / EngineSimulation.TICK * 60 - oilRate) * 0.05;
		inputs.serviceOil(level);
		if (wearTick(level))
			return;
		cranking = floor > 0 && !sim.isSustaining();
		if (drawsBattery && cranking && activeStarter != null)
			ignitionSupply.drawCranking();
		inputs.readSensors(level);

		// The starter feels the effort of turning the engine as stress on its own input shaft
		// (the starter's own input is not geared by rpmScale, so its stress is not either)
		double crankingStress = sim.crankingTorque() * CEConfig.STRESS_PER_TORQUE.get();
		// A stronger starter takes more of the effort, and gears it down: every starter pushing feels the same stress
		// on its input, however many there are and however strong, so a heavy one runs a big engine off a weak source
		double share = crankingStress / Math.max(1, starters.strength());
		starters.report(layout.primary(), share, drawsBattery, cranking, ignitionSupply);

		publishSpeed();
		ratings.publishCapacity(torqueScale);
		if (ticks % SYNC_INTERVAL == 0)
			readouts.syncDiagnostics(level, torqueScale);
	}

	/**
	 * The wear mode's share of a tick (see {@link WearKeeper}); nothing with the mode off, and an engine never sees the mode change, since
	 * {@link #tick} has it built again first. Returns whether a part failed, which has the engine rebuilt around the gap, so the caller
	 * leaves the rest of the tick.
	 */
	private boolean wearTick(Level level) {
		if (!com.createengines.engine.sim.Tuning.wearMode)
			return false;
		boolean step = ticks % WearKeeper.INTERVAL == 0;
		// How hard the rods are pulling: what the network asks of the engine against all it can give
		double rated = ratings.ratedTorque();
		double pull = rated > 0 ? Math.min(1, Math.max(0, smoothedLoad / rated)) : com.createengines.engine.sim.Wear.REFERENCE_LOAD;
		boolean failed = step && wear.tick(level, EngineSimulation.omegaToRpm(Math.abs(sim.omega())), sim.thermal()
			.temperature(), sim.lubrication()
			.film(), pull) || step && wear.tickTurbos(level, turbos, sim.thermal()
				.temperature(), sim.lubrication()
				.film(), sim.isStopped());
		if (!failed && wearTotal.wanted(step))
			wearTotal.read(WearKeeper.total(level, layout));
		return failed;
	}

	/** A cylinder's faults as the scanner found them, plus the wear mode's (a blown gasket, a broken head, a thrown rod), which are not the scanner's to know. */
	int faultsOf(Cylinder cylinder) {
		return wearFaults.isEmpty() ? cylinder.faults() : cylinder.faults() | wearFaults.getOrDefault(cylinder.pos(), 0);
	}

	double ownRpm() {
		return sim.ownRpm();
	}

	/** How much of the engine's cold a starter is choking out of it now, 0 to 1. */
	public double crankAssist() {
		return sim.thermal()
			.crankAssist();
	}

	private void measureCamshafts(Level level, float crankSpeed) {
		for (int i = 0; i < camshafts.size(); i++) {
			CamLine line = layout.camLines()
				.get(i);
			Camshaft camshaft = camshafts.get(i);
			if (!(LoadedBlocks.blockEntity(level, line.segments()
				.getFirst()) instanceof CamshaftBlockEntity cam)) {
				camshaft.ratio = 0;
				continue;
			}
			if (crankSpeed != 0)
				camshaft.ratio = Math.abs(cam.getTheoreticalSpeed() / crankSpeed);
		}
	}

	/** How many injectors {@link #tuneFuel} set, how many it left alone, and the speed it tuned them at. */
	public record Tuning(int tuned, int skipped, int rpm) {
	}

	/** See {@link EngineInputs#tuneFuel}. */
	public Tuning tuneFuel(Level level) {
		return inputs.tuneFuel(level);
	}

	/** See {@link EngineInputs#setAllMetering}. */
	public int setAllMetering(Level level, BlockPos source, int value) {
		return inputs.setAllMetering(level, source, value);
	}

	/**
	 * Whether anything but engines is driving this crank's network. Two engines on one network each see the other's output
	 * as a drive, and if each held the crank at that speed they would keep one another turning with no fuel at all, and
	 * never stop. Only a source that is not a crankshaft (a motor, a wheel, a hand crank) is a drive, and only while it is
	 * actually turning: Create leaves a hand crank whose turn has run out in the network's sources with no capacity, and
	 * counting it held a coasting engine at its own speed, "cranking" for ever, until the crank was broken.
	 */
	private boolean drivenFromOutside() {
		if (!primary.hasNetwork())
			return false;
		for (KineticBlockEntity source : primary.getOrCreateNetwork().sources.keySet())
			if (!(source instanceof CrankshaftBlockEntity) && source.getGeneratedSpeed() != 0)
				return true;
		return false;
	}

	/** How many times as fast as the engine its shaft turns ({@code balance.rpmScale}, see {@link OutputGearing}). */
	static double speedScale() {
		return OutputGearing.scale(CEConfig.RPM_SCALE.get(), AllConfigs.server().kinetics.maxRotationSpeed.get());
	}

	static float engineLimit() {
		return (float) OutputGearing.engineLimit(speedScale(), AllConfigs.server().kinetics.maxRotationSpeed.get());
	}

	private void publishSpeed() {
		float maxSpeed = AllConfigs.server().kinetics.maxRotationSpeed.get();
		float target = 0;
		if (!sim.isStopped())
			target = sim.direction() * Math.min(maxSpeed, Math.max(1, Math.round(sim.meanRpm() * speedScale())));
		float current = primary.getGeneratedSpeed();
		ticksSincePublish++;
		// Create zeroes a crank when a rotation conflict breaks a source beside it (a hand crank turned against the engine)
		// or when it moves it into a moving structure, and never asks it again: the engine would run on, firing, with a crank
		// that does not turn. Whatever it turns at now: a speed a little off the last one sent is not sent again by itself.
		if (target != 0 && current != 0 && primary.getSpeed() == 0 && !primary.hasSource() && ticks % 20 == 0)
			primary.updateGeneratedRotation();
		if (target == current)
			return;
		boolean startOrStop = target == 0 || current == 0 || Math.signum(target) != Math.signum(current);
		boolean significant = Math.abs(target - current) >= Math.max(1, Math.abs(current) * 0.04f);
		if (startOrStop || significant && ticksSincePublish >= PUBLISH_INTERVAL) {
			// Create destroys a source whose speed it finds against, or faster than, the network it already belongs to: a
			// hand crank or another engine turning this crank at a speed this engine would then overrun. Held until that stops.
			float driven = primary.getTheoreticalSpeed();
			if (primary.hasSource() && driven != 0 && target != 0
				&& (Math.signum(target) != Math.signum(driven) || Math.abs(target) > Math.abs(driven)))
				return;
			primary.publishSpeed(target);
			ticksSincePublish = 0;
		}
	}

	/**
	 * The engine's torque curve as the network can use it.
	 *
	 * @param rpm      the speeds it was measured at
	 * @param torque   stress capacity per RPM the engine can be rated for at each, after roughness and the rating margin
	 * @param current  the engine's speed now
	 * @param rated    stress capacity per RPM it is rated at for the speed it is turning now: the best of {@code torque}
	 *                 at or below that speed, which is what the network is offered
	 * @param complete the whole curve has been measured
	 */
	public record Curve(float[] rpm, float[] torque, float current, float rated, boolean complete) {
	}

	/** What a dynamometer block shows; asking for it counts as a dynamometer reading this engine. */
	public Curve curve() {
		return ratings.curve();
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

	/** Called when this engine is being replaced or dismantled: stop driving the network. */
	public void shutDown() {
		primary.publishCapacity(0);
		primary.publishSpeed(0);
	}
}
