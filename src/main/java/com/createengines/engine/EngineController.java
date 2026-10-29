package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.Camshaft;
import com.createengines.engine.sim.CycleStats;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.FuelSupply;
import com.createengines.engine.sim.OilSupply;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.CamLine;
import com.createengines.engine.topology.EngineLayout.Cylinder;
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
	public record Motion(double angle, double omega, int direction, double film, @Nullable EngineController source) {
	}

	private static final int PUBLISH_INTERVAL = 10;
	private static final int CAPACITY_INTERVAL = 5;
	private static final int SYNC_INTERVAL = 10;
	private static final int RACK_INTERVAL = 10;
	private static final int RATING_INTERVAL = 20;
	/** Rated torque is held back a little from the dyno peak, so a load Create accepts never quite stalls the engine. */
	private static final double RATING_MARGIN = 0.9;

	private final CrankshaftBlockEntity primary;
	private final EngineLayout layout;
	private final EngineSimulation sim;
	/** Parallel to {@link EngineLayout#cylinders()}; null where the cylinder can't work at all. */
	private final List<@Nullable CylinderModel> models;
	/** Parallel to {@link EngineLayout#camLines()}. */
	private final List<Camshaft> camshafts;
	private final double[] racks;
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

		List<FuelSupply> galleries = new ArrayList<>();
		List<OilSupply> oilGalleries = new ArrayList<>();
		for (List<BlockPos> gallery : layout.galleries()) {
			galleries.add(new GalleryFuelSupply(level, gallery));
			oilGalleries.add(new GalleryOilSupply(level, gallery));
		}
		oilSupply = mb -> {
			double delivered = 0;
			for (OilSupply gallery : oilGalleries)
				if (delivered < mb)
					delivered += gallery.draw(mb - delivered);
			return delivered;
		};

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
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, cylinder.tdcAngle(), camshaft,
				cylinder.lobeAngle());
			boolean injector = !CylinderFault.NO_INJECTOR.in(cylinder.faults()) && cylinder.gallery() >= 0;
			model.fuelSupply = injector ? galleries.get(cylinder.gallery()) : FuelSupply.NONE;
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
		// A new engine starts dry, and has to be oiled
		sim.lubrication()
			.setFilm(motion == null ? 0 : motion.film());
		if (motion != null)
			sim.restore(motion.angle(), motion.omega(), motion.direction());
		for (CylinderModel model : working)
			model.resetGas(model.pistonAngle(sim.angle(), sim.direction()));
		if (motion != null && motion.source() != null)
			carryCylinders(motion.source());
	}

	private void carryCylinders(EngineController old) {
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
			.film(), this);
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
				floorDirection = starter.crankDirection();
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

	/** A redstone signal anywhere on a camshaft closes its fuel rack, as on an inline injection pump. */
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
	 * kind, which cams are geared 1:2, and how well oiled the engine is.
	 */
	private void updateRating() {
		List<Object> key = new ArrayList<>();
		key.add((int) Math.round(sim.lubrication()
			.film() * 5));
		for (CylinderModel model : sim.cylinders()) {
			key.add(model.fuelSupply.fuel());
			Camshaft camshaft = model.camshaft();
			key.add(camshaft != null && camshaft.isPhaseLocked());
		}
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
				.film(),
			activeStarter != null, angles);
	}

	private void syncDiagnostics(Level level, double torqueScale) {
		primary.updateSummary(summary(state(), torqueScale));

		for (EngineLayout.Segment segment : layout.segments())
			if (level.getBlockEntity(segment.pos()) instanceof CrankshaftBlockEntity crank)
				crank.setPrimary(layout.primary());

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
				cylinder.lobe(), outlets));
		}

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

	/** Called when this engine is being replaced or dismantled: stop driving the network. */
	public void shutDown() {
		primary.publishCapacity(0);
		primary.publishSpeed(0);
	}
}
