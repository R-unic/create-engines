package com.createengines.engine;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.engine.sim.CycleStats;

import com.createengines.engine.sim.Breather;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Thermal;
import com.createengines.engine.sim.Torsion;
import com.createengines.engine.sim.Intercooler;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.PrecisionParts;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/** Credits the engine's operator with the milestones it reaches. Part of {@link EngineController}. */
final class EngineMilestoneReporter {

	private final EngineController engine;

	EngineMilestoneReporter(EngineController engine) {
		this.engine = engine;
	}

	/** Ticks before the same milestone is reported again, for an operator who was offline when it was first reached. */
	static final int MILESTONE_COOLDOWN = 200;
	/** Ticks a governor has to hold an engine on its setting under load for it to count, ten seconds. */
	static final int GOVERNED_TICKS = 200;
	EngineState previousState = EngineState.STALLED;
	final Map<Milestone, Integer> milestoneTicks = new EnumMap<>(Milestone.class);
	/** The engine was running the last time its milestones were looked at, and how it caught: warm, or cold on a slow fuel. */
	boolean wasRunning;
	boolean warmStart;
	boolean hardStart;

	/** Credits the engine's operator with anything notable it is doing, no more than once every ten seconds. */
	void reportMilestones(Level level, EngineState state) {
		boolean sparkFiring = false;
		boolean rich = false;
		boolean balanced = true;
		float fouling = 0;
		boolean pushrod = false;
		boolean precision = true;
		int firing = 0;
		int cylinders = 0;
		float[] firingAngles = engine.readouts.firingScratch();
		int firingAngleCount = 0;
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			cylinders++;
			if (model == null) {
				precision = false;
				continue;
			}
			precision &= (engine.layout.cylinders()
				.get(i)
				.precision() & PrecisionParts.ALL) == PrecisionParts.ALL;
			fouling = Math.max(fouling, (float) model.fouling());
			if (!model.lastCycle().result.fired())
				continue;
			firing++;
			if (model.camshaft() != null && model.camshaft()
				.isPhaseLocked())
				firingAngles[firingAngleCount++] = (float) model.firingRotorAngle();
			Cylinder placed = engine.layout.cylinders()
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
		for (Turbocharger turbo : engine.turbos) {
			boost = Math.max(boost, (float) turbo.boost());
			dumping |= turbo.dump;
		}
		float rpm = (float) engine.ownRpm();
		reportInduction(level, state);
		reportRotary(level, state);
		reportRefining(level, state);
		reportService(level, state);
		reportWater(level, state);
		award(level, EngineMilestones.reachedFlex(state == EngineState.RUNNING, firing, (float) Torsion.scaled(engine.flexLoss)));
		reportMechanics(level, state);
		reportParts(level, state, firing);
		reportLooks(level, state);
		EngineMilestones.Snapshot snapshot = new EngineMilestones.Snapshot(state, previousState, rpm,
			engine.activeStarter != null, firing, (float) (engine.primary.publishedCapacity() * rpm * EngineController.speedScale()), sparkFiring, (float) engine.sim.lubrication()
				.effective(),
			rich, boost, dumping, fouling, firing > 0 && balanced, (float) engine.sim.lubrication()
				.dilution(),
			pushrod, firingAngleCount == cylinders && cylinders >= EngineMilestones.EVEN_FIRING_CYLINDERS
				&& EngineMilestones.evenFiring(Arrays.copyOf(firingAngles, firingAngleCount)),
			engine.inputs.mafTrimming, engine.inputs.mafEnriching,
			engine.inputs.governedTicks >= GOVERNED_TICKS, precision && cylinders >= EngineMilestones.EVEN_FIRING_CYLINDERS,
			engine.layout.lines() > 1);
		if (state != previousState)
			com.createengines.api.EngineEvents.stateChanged(level, engine.layout.primary(), previousState, state);
		previousState = state;
		award(level, EngineMilestones.reached(snapshot));
	}

	/** Credits the operator with each milestone, skipping any reported within the last {@link #MILESTONE_COOLDOWN} ticks. */
	void award(Level level, Set<Milestone> reached) {
		for (Milestone milestone : reached) {
			Integer last = milestoneTicks.get(milestone);
			if (last != null && engine.ticks - last < MILESTONE_COOLDOWN)
				continue;
			milestoneTicks.put(milestone, engine.ticks);
			if (last == null)
				com.createengines.api.EngineEvents.milestone(level, engine.layout.primary(), milestone.name()
					.toLowerCase(java.util.Locale.ROOT));
			Achievements.award(level, engine.layout.members(), milestone);
		}
	}

	/**
	 * Credits the operator with the parts the engine is putting to work: the camshaft that times it, the pipes it
	 * breathes through, the throttle, the fuel and oil it burns, its sensors and governor, and its precision parts.
	 */
	void reportParts(Level level, EngineState state, int firing) {
		boolean camTimed = false;
		boolean piped = false;
		boolean throttled = false;
		int precision = 0;
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			if (model == null || !model.lastCycle().result.fired())
				continue;
			Cylinder placed = engine.layout.cylinders()
				.get(i);
			camTimed |= model.camshaft() != null && model.camshaft()
				.isPhaseLocked();
			piped |= placed.intake() >= 0 && placed.exhaust() >= 0;
			throttled |= placed.intake() >= 0 && engine.portOpening[placed.intake()] < EngineMilestones.THROTTLING;
			precision |= placed.precision();
		}
		Fluid fuel = engine.fuels.fluid();
		boolean rotorHousing = false;
		for (Cylinder placed : engine.layout.cylinders())
			rotorHousing |= placed.rotary();
		award(level, EngineMilestones.reachedParts(new EngineMilestones.Parts(state == EngineState.RUNNING && firing > 0,
			camTimed, piped, throttled, fuel != null && fuel.isSame(CEFluids.DIESEL.get()),
			fuel != null && fuel.isSame(CEFluids.GASOLINE.get()), (float) engine.sim.lubrication()
				.effective(),
			!engine.layout.sumps()
				.isEmpty(),
			engine.inputs.mafReading, engine.inputs.governing, engine.inputs.commanded, precision, rotorHousing && !engine.sim.isStopped())));
	}

	/** Credits the operator with a muffler quieting the running engine and with casing built round its cylinders. */
	void reportLooks(Level level, EngineState state) {
		boolean running = state == EngineState.RUNNING;
		int casings = running ? countCasings(level) : 0;
		award(level, EngineMilestones.reachedLooks(running, engine.exhaust.muffling(), casings));
	}

	/**
	 * Casing blocks touching the engine's cylinders, each counted once, up to {@link EngineMilestones#CLAD_BLOCKS}:
	 * that is all the milestone asks, so the scan stops there instead of counting every block of a big engine's casing
	 * (and keeps no set to do it with).
	 */
	int countCasings(Level level) {
		CasingTally tally = new CasingTally(EngineMilestones.CLAD_BLOCKS);
		for (Cylinder placed : engine.layout.cylinders())
			for (Direction side : EngineController.SIDES) {
				BlockPos beside = placed.pos()
					.relative(side);
				if (level.getBlockState(beside)
					.getBlock() instanceof com.createengines.content.EngineCasingBlock)
					tally.add(beside.asLong());
				if (tally.full())
					return tally.count();
			}
		return tally.count();
	}

	/** Credits the operator with a flywheel that smooths a running engine, variable vanes at work, and anti-lag. */
	void reportMechanics(Level level, EngineState state) {
		boolean vaneBoost = false;
		boolean antiLagBoost = false;
		for (Turbocharger turbo : engine.turbos) {
			vaneBoost |= turbo.variableGeometry && turbo.boost() >= EngineMilestones.STAGE_BOOST
				&& turbo.vanes() >= EngineMilestones.VANES_CLOSING;
			antiLagBoost |= turbo.antiLagging() && turbo.boost() >= EngineMilestones.STAGE_BOOST;
		}
		float roughness = (float) (engine.sim.isStopped() ? 0 : Dynamometer.roughnessLoss(engine.ratings.torqueCurve, engine.ratings.rippleCurve, engine.sim.meanRpm()));
		// Banks: firing pistons that share a throw, on sides a quarter turn apart (a vee) or opposite (flat)
		int firing = 0;
		boolean sharedLobe = false;
		boolean vee = false;
		boolean flat = false;
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			Cylinder placed = engine.layout.cylinders()
				.get(i);
			if (model == null || placed.rotary() || !model.lastCycle().result.fired())
				continue;
			firing++;
			// A cylinder on a shared lobe is timed at an offset from the cylinder the lobe's angle sets
			sharedLobe |= !Double.isNaN(placed.lobeLeaderTdc()) && model.lobeShift() > 0.5;
			// Paired with every earlier firing piston on the same throw: the same pairs a list of bores per throw would give
			for (int j = 0; j < i; j++) {
				Cylinder other = engine.layout.cylinders()
					.get(j);
				if (other.rotary() || !firedLast(j) || !other.throwPos()
					.equals(placed.throwPos()))
					continue;
				flat |= other.bore() == placed.bore()
					.getOpposite();
				vee |= other.bore()
					.getAxis() != placed.bore()
						.getAxis();
			}
		}
		award(level, EngineMilestones.reachedMechanics(new EngineMilestones.Mechanics(state == EngineState.RUNNING,
			(float) engine.ownRpm(), engine.layout.flywheelInertia(), roughness, vaneBoost, antiLagBoost, firing, vee, flat,
			engine.ticks - engine.ratings.dynoReadTick < 2 * EngineController.SYNC_INTERVAL && engine.ratings.rated && engine.ratings.rating == null, sharedLobe)));
	}

	/** Credits the operator with an engine burning ethanol, and one whose exhaust heat is cooking a basin's recipe. */
	void reportRefining(Level level, EngineState state) {
		Fluid fuel = engine.fuels.fluid();
		boolean ethanol = fuel != null && fuel.isSame(CEFluids.ETHANOL.get());
		// Ethanol under boost that has the charge past where gasoline lights itself early, and still lighting only at the plug
		boolean coolCharge = false;
		if (ethanol && state == EngineState.RUNNING)
			for (CylinderModel model : engine.models) {
				if (model == null)
					continue;
				CycleStats last = model.lastCycle();
				if (last.result.fired() && model.intakePressure > 1.2
					&& last.compressionTemp >= FuelSpec.GASOLINE.autoignitionTemp()) {
					coolCharge = true;
					break;
				}
			}
		award(level, EngineMilestones.reachedRefining(state == EngineState.RUNNING, ethanol, engine.inputs.exchangerLevel, coolCharge));
	}

	/** Credits the operator with oil that is kept fresh, an engine that has warmed up or is cooled, and a pilot shot at work. */
	void reportService(Level level, EngineState state) {
		Fluid fuel = engine.fuels.fluid();
		boolean running = state == EngineState.RUNNING;
		float temperature = (float) engine.sim.thermal()
			.temperature();
		if (running && !wasRunning) {
			// How the engine caught decides the warm-up steps, whatever happens to its temperature afterwards
			warmStart = engine.sim.thermal()
				.preheat() > 0 && temperature >= Thermal.COLD_BELOW;
			boolean slowFuel = fuel != null && (fuel.isSame(CEFluids.ETHANOL.get()) || fuel.isSame(CEFluids.PLANT_OIL.get()));
			hardStart = slowFuel && temperature < Thermal.COLD_BELOW;
			award(level, EngineMilestones.reachedColdStart(engine.coldAssistTicks > 0 && temperature < Thermal.COLD_BELOW));
		} else if (!running) {
			warmStart = false;
			hardStart = false;
		}
		wasRunning = running;
		award(level, EngineMilestones.reachedWarmup(running, warmStart, hardStart, temperature));
		award(level, EngineMilestones.reachedService(new EngineMilestones.Service(state == EngineState.RUNNING,
			(float) engine.ownRpm(), engine.inputs.exchanging, engine.inputs.filtered, (float) engine.sim.lubrication()
				.condition(),
			(float) engine.sim.thermal()
				.temperature(),
			(float) engine.sim.thermal()
				.cool(),
			engine.sim.thermal()
				.coolantRate() > 0,
			engine.readouts.pilotLit(), fuel != null && fuel.isSame(CEFluids.USED_OIL.get()))));
	}

	/** Credits the operator with trying to start an engine that sits underwater or opens there. */
	void reportWater(Level level, EngineState state) {
		boolean underwater = false;
		for (Cylinder placed : engine.layout.cylinders())
			underwater |= CylinderFault.underwater(placed.faults());
		award(level, EngineMilestones.reachedWater(state == EngineState.CRANKING && engine.activeStarter != null, underwater));
	}

	/** Credits the operator with a running rotary engine, a high-revving one, and one with several rotors. */
	void reportRotary(Level level, EngineState state) {
		// Housings with a chamber that fired, each counted once however many of its three chambers did: a chamber counts if
		// no earlier one of the same housing did
		int firing = 0;
		for (int i = 0; i < engine.models.size(); i++) {
			Cylinder placed = engine.layout.cylinders()
				.get(i);
			if (!placed.rotary() || !firedLast(i))
				continue;
			boolean counted = false;
			for (int j = 0; j < i && !counted; j++)
				counted = engine.layout.cylinders()
					.get(j)
					.rotary() && firedLast(j) && engine.layout.cylinders()
						.get(j)
						.pos()
						.equals(placed.pos());
			if (!counted)
				firing++;
		}
		award(level, EngineMilestones.reachedRotary(
			new EngineMilestones.Rotary(state == EngineState.RUNNING, (float) engine.ownRpm(), firing)));
	}

	/** Whether the working cylinder at {@code index} fired on its last cycle. */
	boolean firedLast(int index) {
		CylinderModel model = engine.models.get(index);
		return model != null && model.lastCycle().result.fired();
	}

	/** Whether a source on a network just lit its charge: a cylinder says from its last cycle, anything else from {@link Breather#fired}. */
	static boolean fired(Breather source) {
		return source instanceof CylinderModel cylinder ? cylinder.lastCycle().result.fired() : source.fired();
	}

	/** Credits the operator with boost from superchargers, stacked stages and intercoolers. */
	void reportInduction(Level level, EngineState state) {
		// Made only once a stage is working: most engines have no forced induction, and this runs for every engine every half second
		Map<Breather, int[]> stages = null;
		// Per cylinder: turbos in series, blowers, turbos side by side
		for (Turbocharger turbo : engine.turbos)
			if (turbo.boost() >= EngineMilestones.STAGE_BOOST)
				for (Breather cylinder : turbo.compressor()) {
					if (stages == null)
						stages = new IdentityHashMap<>();
					stages.computeIfAbsent(cylinder, $ -> new int[3])[turbo.parallelCompressor ? 2 : 0]++;
				}
		float blowerBoost = 0;
		float overdriven = 0;
		for (Supercharger blower : engine.blowers) {
			if (blower.boost() >= EngineMilestones.STAGE_BOOST)
				for (Breather cylinder : blower.compressor()) {
					if (stages == null)
						stages = new IdentityHashMap<>();
					stages.computeIfAbsent(cylinder, $ -> new int[3])[1]++;
				}
			boolean anyFired = false;
			List<Breather> breathing = blower.compressor();
			for (int i = 0, n = breathing.size(); i < n && !anyFired; i++)
				anyFired = fired(breathing.get(i));
			if (anyFired) {
				blowerBoost = Math.max(blowerBoost, (float) blower.boost());
				if (blower.variableDrive && blower.overdrive() >= EngineMilestones.OVERDRIVE_ACTIVE)
					overdriven = Math.max(overdriven, (float) blower.boost());
			}
		}
		int turboStages = 0;
		int blowerStages = 0;
		int twinStages = 0;
		if (stages != null)
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
		for (Intercooler cooler : engine.sim.intercoolers())
			for (Breather cylinder : cooler.charge())
				if (fired(cylinder))
					cooled = Math.max(cooled, (float) (cylinder.intakePressure() - 1));
		award(level, EngineMilestones.reachedInduction(new EngineMilestones.Induction(
			state == EngineState.RUNNING, blowerBoost, turboStages, blowerStages, twinStages, cooled, overdriven)));
	}
}
