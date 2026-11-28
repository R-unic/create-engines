package com.createengines.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.EngineGlow;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.sim.Camshaft;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CycleStats;

import com.createengines.engine.sim.Breather;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.DualMass;
import com.createengines.engine.sim.StatusLine;
import com.createengines.engine.sim.TorqueSource;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.Roughness;
import com.createengines.engine.sim.Torsion;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.EngineLayout.Turbo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** What the engine tells goggles and the client: the summary, status lines, diagnosis and the sync to its blocks. Part of {@link EngineController}. */
final class EngineReadouts {

	private final EngineController engine;

	EngineReadouts(EngineController engine) {
		this.engine = engine;
	}

	float @Nullable [] firingScratch;

	EngineState state() {
		if (engine.sim.isStopped())
			return EngineState.STALLED;
		if (engine.cranking)
			return EngineState.CRANKING;
		if (engine.primary.isOverStressed())
			return EngineState.OVERLOADED;
		for (CylinderModel model : engine.models)
			if (model != null && model.lastCycle().result.fired())
				return EngineState.RUNNING;
		// Still turning, but nothing is burning fuel: it will run down
		return EngineState.COASTING;
	}

	EngineSummary summary(EngineState state, double torqueScale) {
		int firing = 0;
		int working = 0;
		float[] listed = firingScratch();
		EngineExhaust.SoundBase base = engine.exhaust.soundBase();
		// Which exhaust outlet each listed firing goes out of, kept beside it (only the sound)
		int[] listedOutlets = engine.exhaust.outletScratch();
		int listedCount = 0;
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			if (model == null)
				continue;
			working++;
			// A stopped engine's cylinders keep the result of the last cycle they ran, which is not firing now
			if (state != EngineState.STALLED && model.lastCycle().result.fired())
				firing++;
			Camshaft camshaft = model.camshaft();
			if (camshaft != null && camshaft.isPhaseLocked()) {
				listedOutlets[listedCount] = base.outletOf()[i];
				listed[listedCount++] = (float) model.firingRotorAngle();
			}
		}
		// A rotor fires once a turn of the shaft, which in the engine's 720 degrees is twice; its three chambers are the
		// same event, so only the first is listed
		for (int i = 0; i < engine.models.size(); i++) {
			Cylinder placed = engine.layout.cylinders()
				.get(i);
			if (engine.models.get(i) == null || !placed.rotary() || placed.chamber() != 0)
				continue;
			float angle = (float) (((engine.sim.direction() * placed.tdcAngle() + 180) % 360 + 360) % 360);
			listedOutlets[listedCount] = base.outletOf()[i];
			listed[listedCount++] = angle;
			listedOutlets[listedCount] = base.outletOf()[i];
			listed[listedCount++] = angle + 360;
		}
		EngineSound.sortFirings(listed, listedOutlets, listedCount);
		// The summary keeps these arrays, so they are the things here that have to be made new
		float[] angles = Arrays.copyOf(listed, listedCount);
		EngineSound sound = new EngineSound(Arrays.copyOf(listedOutlets, listedCount), base.pipes(), base.compression(),
			base.precision(), engine.exhaust.enclosure, base.muffles(), base.rotary());
		float roughness = (float) (engine.sim.isStopped() ? 0 : Dynamometer.roughnessLoss(engine.ratings.torqueCurve, engine.ratings.rippleCurve, engine.sim.meanRpm()));
		Diagnosis.Report report = diagnose(state, firing, roughness);
		return new EngineSummary(state, (float) (engine.sim.meanRpm() * EngineController.speedScale()), engine.primary.publishedCapacity(),
			(float) (engine.smoothedLoad * torqueScale), (float) (engine.sim.fuelRate() * 60), (float) engine.sim.ripple(), firing,
			working, (float) engine.sim.inertia(), (float) engine.sim.lubrication()
				.effective(),
			engine.activeStarter != null, angles, engine.layout.lines(), (float) engine.sim.lubrication()
				.dilution(),
			roughness,
			(float) engine.sim.lubrication()
				.condition(),
			(float) engine.sim.thermal()
				.temperature(),
			(float) engine.sim.thermal()
				.coolantRate(),
			pilotLit(), engine.inputs.filtered, (float) Torsion.scaled(engine.flexLoss), engine.longestLine, report.health(), report.cause(), engine.exhaust.muffling(),
				inductionBoost(), sourceLines(), engine.sim.isStopped() ? 0 : (float) engine.oilRate, engine.wearTotal.total(), engine.wearFailures, sound,
				(float) engine.ambient.intakeLoss);
	}

	/**
	 * What the sources packs and addons added say about themselves, capped so a careless one cannot flood the sync. A
	 * source that throws is left out of the readout, and reported like a provider that fails.
	 */
	List<StatusLine> sourceLines() {
		List<TorqueSource> all = engine.sim.sources();
		int first = engine.sim.cylinders()
			.size();
		StatusLine flywheel = flywheelLine();
		StatusLine dualMass = dualMassLine();
		if (all.size() == first) {
			if (flywheel == null)
				return dualMass == null ? List.of() : List.of(dualMass);
			return dualMass == null ? List.of(flywheel) : List.of(flywheel, dualMass);
		}
		List<StatusLine> lines = new ArrayList<>();
		if (flywheel != null)
			lines.add(flywheel);
		if (dualMass != null)
			lines.add(dualMass);
		for (int i = first; i < all.size() && lines.size() < StatusLine.LIMIT; i++) {
			try {
				List<StatusLine> given = all.get(i)
					.status();
				if (given == null)
					continue;
				for (StatusLine line : given)
					if (line != null && lines.size() < StatusLine.LIMIT)
						lines.add(line);
			} catch (RuntimeException e) {
				EngineProviders.reportSourceFailure(engine.layout.primary(), e);
			}
		}
		return List.copyOf(lines);
	}

	/**
	 * What the flywheels on the line are worth to this engine now: the speed ripple it has and what it would have without
	 * their mass, and the share of the rating that difference keeps. Information only, so choosing between a Cast and a
	 * Dual-Mass Flywheel is a reading: nothing it says feeds back. A ripple scales as one over the inertia, so the bare
	 * crank's ripple is the live one times the whole inertia over the crank's own. Null with no flywheel or at a stand.
	 * Whole percentages, so a drifting speed does not make the readout sync every tick.
	 */
	@Nullable StatusLine flywheelLine() {
		double mass = engine.layout.flywheelInertia();
		double inertia = engine.sim.inertia();
		double bare = inertia - mass;
		double ripple = engine.sim.ripple();
		if (!(mass > 0) || !(bare > 0) || !(ripple > 0) || engine.sim.isStopped())
			return null;
		double without = ripple * inertia / bare;
		long with = Math.round(ripple * 100);
		long bareRipple = Math.round(without * 100);
		long kept = Math.round((Roughness.loss(without) - Roughness.loss(ripple)) * 100);
		if (kept >= 1)
			return StatusLine.of("create_engines.goggles.flywheel.keeps", StatusLine.Tone.GOOD, String.valueOf(with),
				String.valueOf(bareRipple), String.valueOf(kept));
		return StatusLine.of("create_engines.goggles.flywheel", StatusLine.Tone.NOTE, String.valueOf(with),
			String.valueOf(bareRipple));
	}

	/**
	 * What a dual-mass flywheel on the line is doing now: how much of its secondary mass is isolated at this speed, and the
	 * ripple the network sees with it. Null with none. The live crank's own ripple is what it has with the primary mass
	 * alone, so the secondary mass is that ripple's share of the inertia it adds to what the ripple sees. Rounded, so a
	 * drifting speed does not make the readout sync every tick.
	 */
	@Nullable StatusLine dualMassLine() {
		double secondary = engine.sim.isolatedInertia();
		if (!(secondary > 0))
			return null;
		double rpm = engine.sim.meanRpm();
		double share = DualMass.isolation(rpm, engine.sim.isolationResonanceRpm());
		double seen = DualMass.effectiveInertia(engine.sim.inertia(), secondary, rpm, engine.sim.isolationResonanceRpm());
		long isolating = Math.round(share * 10) * 10;
		long ripple = Math.round(engine.sim.ripple() * engine.sim.inertia() / seen * 100);
		return StatusLine.of("create_engines.goggles.dual_mass", isolating >= 50 ? StatusLine.Tone.GOOD : StatusLine.Tone.NOTE,
			String.valueOf(isolating), String.valueOf(ripple));
	}

	/** The hardest any turbo or blower is pushing, for the roar at the intake. */
	float inductionBoost() {
		double boost = 0;
		for (Turbocharger turbo : engine.turbos)
			boost = Math.max(boost, turbo.boost());
		for (Supercharger blower : engine.blowers)
			boost = Math.max(boost, blower.boost());
		return (float) boost;
	}

	/**
	 * Rates the engine for goggles: one health and the one thing most responsible. A cylinder left out of the simulation
	 * for a missing part counts as one that is not firing, so the engine still shows it is short of a cylinder.
	 */
	Diagnosis.Report diagnose(EngineState state, int firing, float roughness) {
		Map<Diagnosis.Cause, Integer> causes = new java.util.EnumMap<>(Diagnosis.Cause.class);
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			int faults = engine.layout.cylinders()
				.get(i)
				.faults();
			Diagnosis.Cause cause = model == null ? (faults == 0 ? Diagnosis.Cause.NONE : Diagnosis.Cause.BUILD)
				: Diagnosis.ofCylinder(faults, model.lastCycle().result);
			if (cause != Diagnosis.Cause.NONE)
				causes.merge(cause, 1, Integer::sum);
		}
		boolean running = state == EngineState.RUNNING || state == EngineState.OVERLOADED || state == EngineState.CRANKING
			|| state == EngineState.COASTING;
		return Diagnosis.assess(running, engine.models.size(), firing, causes, roughness, engine.sim.lubrication()
			.effective(), engine.sim.thermal()
				.cold());
	}

	/** Whether a pilot shot, and not compression or a spark, lit the charge in a cylinder on its last cycle. */
	boolean pilotLit() {
		for (CylinderModel model : engine.models)
			if (model != null && model.lastCycle().pilotShot > 0 && model.lastCycle().result.fired())
				return true;
		return false;
	}

	void syncDiagnostics(Level level, double torqueScale) {
		EngineState state = state();
		engine.exhaust.refreshEnclosure(level);
		engine.primary.updateSummary(summary(state, torqueScale));
		glow(level, state == EngineState.RUNNING || state == EngineState.OVERLOADED);
		engine.reporter.reportMilestones(level, state);
		syncTurbos(level);
		syncSuperchargers(level);
		engine.exhaust.catchSoot(level);
		engine.exhaust.emitPollution(level, state);

		for (EngineLayout.Segment segment : engine.layout.segments())
			if (LoadedBlocks.blockEntity(level, segment.pos()) instanceof CrankshaftBlockEntity crank)
				crank.setPrimary(engine.layout.primary(), segment.sense());

		for (int i = 0; i < engine.models.size(); i++) {
			Cylinder cylinder = engine.layout.cylinders()
				.get(i);
			if (cylinder.rotary()) {
				if (LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof RotorHousingBlockEntity housing)
					housing.updateChamber(cylinder.chamber(), status(cylinder, engine.models.get(i)));
				continue;
			}
			if (!(LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof CylinderBlockEntity be))
				continue;
			CylinderModel model = engine.models.get(i);
			CycleStats last = model == null ? null : model.lastCycle();
			float timing = Float.NaN;
			if (model != null && model.camshaft() != null && model.camshaft()
				.isPhaseLocked())
				timing = (float) model.timingError(engine.sim.direction());
			FuelSpec fuel = model == null ? null : model.fuelSupply.fuel();
			float charge = last == null || last.fuelInjected <= 0 ? Float.NaN : (float) last.compressionTemp;
			List<BlockPos> outlets = engine.exhaust.openings(cylinder.exhaust());
			be.updateStatus(new CylinderStatus(engine.layout.primary(), engine.faultsOf(cylinder),
				last == null ? com.createengines.engine.sim.CycleResult.NONE : last.result, last != null && last.rich(),
				(float) cylinder.tdcAngle(), timing, charge, fuel == null ? Float.NaN : (float) fuel.autoignitionTemp(),
				cylinder.lobe(), outlets, last != null && last.lean(),
				last == null || last.equivalence <= 0 ? Float.NaN : (float) last.equivalence, cylinder.sparkPlug(),
				model == null ? 0 : (float) model.fouling(), model == null ? 0 : (float) model.lobeShift(),
				engine.exhaust.openings(cylinder.intake()), last == null ? 0 : (float) last.preignition, engine.inputs.fuelCut(cylinder),
				engine.exhaust.muffled(cylinder.exhaust())));
		}

		for (BlockPos pos : engine.layout.batteries())
			if (LoadedBlocks.blockEntity(level, pos) instanceof BatteryBlockEntity battery)
				battery.attachToEngine(engine.layout.primary());
		for (BlockPos pos : engine.layout.sumps())
			if (LoadedBlocks.blockEntity(level, pos) instanceof SumpBlockEntity sump)
				sump.attachToEngine(engine.layout.primary());
		for (BlockPos pos : engine.layout.alternators())
			if (LoadedBlocks.blockEntity(level, pos) instanceof AlternatorBlockEntity alternator)
				alternator.attachToEngine(engine.layout.primary(), engine.layout.batteries(), engine.layout.alternators());

		for (int line = 0; line < engine.camshafts.size(); line++) {
			Camshaft camshaft = engine.camshafts.get(line);
			for (BlockPos pos : engine.layout.camLines()
				.get(line)
				.segments()) {
				if (!(LoadedBlocks.blockEntity(level, pos) instanceof CamshaftBlockEntity cam))
					continue;
				int actuated = 0;
				float timing = Float.NaN;
				for (int i = 0; i < engine.models.size(); i++) {
					Cylinder cylinder = engine.layout.cylinders()
						.get(i);
					if (!pos.equals(cylinder.lobe()))
						continue;
					actuated++;
					CylinderModel model = engine.models.get(i);
					if (model != null && camshaft.isPhaseLocked() && Float.isNaN(timing))
						timing = (float) model.timingError(engine.sim.direction());
				}
				cam.updateStatus(engine.layout.primary(), (float) camshaft.ratio, camshaft.isPhaseLocked(),
					(float) engine.racks[line], actuated, timing);
			}
		}
	}

	/** Gives the heads and rotor housings of a burning engine their faint glow, and takes it away when the burning stops. */
	void glow(Level level, boolean burning) {
		for (int i = 0; i < engine.models.size(); i++) {
			Cylinder cylinder = engine.layout.cylinders()
				.get(i);
			boolean lit = burning && engine.models.get(i) != null;
			EngineGlow.set(level, cylinder.rotary() || cylinder.head() == null ? cylinder.pos() : cylinder.head(), lit);
		}
	}

	/** What a rotor chamber shows through goggles and effects. */
	CylinderStatus status(Cylinder cylinder, @Nullable CylinderModel model) {
		CycleStats last = model == null ? null : model.lastCycle();
		FuelSpec fuel = model == null ? null : model.fuelSupply.fuel();
		List<BlockPos> outlets = engine.exhaust.openings(cylinder.exhaust());
		return new CylinderStatus(engine.layout.primary(), engine.faultsOf(cylinder),
			last == null ? CycleResult.NONE : last.result, last != null && last.rich(), (float) cylinder.tdcAngle(),
			Float.NaN, last == null || last.fuelInjected <= 0 ? Float.NaN : (float) last.compressionTemp,
			fuel == null ? Float.NaN : (float) fuel.autoignitionTemp(), null, outlets, last != null && last.lean(),
			last == null || last.equivalence <= 0 ? Float.NaN : (float) last.equivalence, cylinder.sparkPlug(),
			model == null ? 0 : (float) model.fouling(), 0, engine.exhaust.openings(cylinder.intake()),
			last == null ? 0 : (float) last.preignition, false, engine.exhaust.muffled(cylinder.exhaust()));
	}

	void syncSuperchargers(Level level) {
		for (int i = 0; i < engine.blowers.size(); i++) {
			Supercharger blower = engine.blowers.get(i);
			if (LoadedBlocks.blockEntity(level, engine.layout.superchargers()
				.get(i)
				.pos()) instanceof SuperchargerBlockEntity be)
				be.updateBlower(engine.layout.primary(), blower.compressor()
					.size(), (float) blower.boost(), (float) blower.bypass());
		}
	}

	void syncTurbos(Level level) {
		for (int i = 0; i < engine.turbos.size(); i++) {
			Turbo placed = engine.layout.turbos()
				.get(i);
			Turbocharger turbo = engine.turbos.get(i);
			if (LoadedBlocks.blockEntity(level, placed.pos()) instanceof TurbochargerBlockEntity be)
				be.updateStatus(engine.layout.primary(), new TurboStatus((float) turbo.spool(), (float) turbo.boost(), (float) turbo.wastegate(),
					(float) turbo.backPressure(), (float) charge(turbo), !turbo.compressor()
						.isEmpty(),
					!turbo.turbine()
						.isEmpty(),
					(float) turbo.vanes(), turbo.antiLagging(), (float) engine.sim.thermal()
						.temperature(), turbo.offThrottle));
		}
	}

	/** The boost on the air a turbo's compressor feeds, all stages and coolers counted. */
	static double charge(Turbocharger turbo) {
		double charge = 0;
		for (Breather cylinder : turbo.compressor())
			charge = Math.max(charge, cylinder.intakePressure() - 1);
		return charge;
	}

	/**
	 * Room for every firing angle an engine can list: one for each cylinder on a cam, and two for each rotor's first chamber.
	 * Kept and filled again each time, since the sync that fills it runs for every engine every half second.
	 */
	float[] firingScratch() {
		if (firingScratch == null)
			firingScratch = new float[Math.max(1, 2 * engine.models.size())];
		return firingScratch;
	}
}
