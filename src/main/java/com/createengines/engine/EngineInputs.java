package com.createengines.engine;

import java.util.LinkedHashMap;
import java.util.Map;

import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.head.GalleryMember;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CycleStats;

import com.createengines.engine.sim.Breather;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.Governor;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Sensors;
import com.createengines.engine.sim.Throttle;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** What the engine reads from the blocks on it: throttles, turbos, superchargers, fuel racks, governors, sensors and oil. Part of {@link EngineController}. */
final class EngineInputs {

	private final EngineController engine;

	EngineInputs(EngineController engine) {
		this.engine = engine;
	}

	/** Crank speed below which a blower's gearing to it is not worked out. */
	static final double MIN_RATIO_RPM = 8;
	static final double MAX_DRIVE_RATIO = 16;
	/** How close to its setting, as a share of it, a governed engine has to be held to count as on speed. */
	static final double GOVERNED_BAND = 0.03;
	/**
	 * Mixture {@link #tuneFuel} aims for. A little lean of balanced, because an engine that burns cleaner speeds up, and
	 * breathes less as it does, so it drifts richer after the tune.
	 */
	static final double TUNE_MIXTURE = 0.9;
	/** Whether an oil pan swapped oil during the last tick, and whether any pan has a filter fitted: for achievements and goggles. */
	boolean exchanging;
	boolean filtered;
	final Governor governor = new Governor();
	/** Whether the governor has been handed the rack position its block saved, once since it was last taken off. */
	boolean governorSeeded;
	/** Ticks the governor has held the engine on its setting while carrying a load. */
	int governedTicks;
	/** What the engine-management blocks last saw, for milestones. */
	boolean mafTrimming;
	boolean mafEnriching;
	boolean mafReading;
	boolean governing;
	boolean commanded;
	/** Heat level of the hottest exhaust heat exchanger on the engine, {@code HeatRecovery.NONE} to {@code SEETHE}. */
	int exchangerLevel;

	/**
	 * Throttle bodies in an intake network are in series, so the most closed one governs what every cylinder on that
	 * network can breathe. Read every tick because it is a pedal: it follows the redstone signal as it moves.
	 */
	void readThrottles(Level level) {
		for (int port = 0; port < engine.portOpening.length; port++) {
			// Starting from 1 would cap a bore opened past the intake right back at it, so start from none found
			double narrowest = Double.POSITIVE_INFINITY;
			for (BlockPos pos : engine.layout.ports()
				.get(port)
				.throttles())
				if (LoadedBlocks.blockEntity(level, pos) instanceof ThrottleBlockEntity throttle)
					narrowest = Math.min(narrowest, throttle.opening());
			engine.portOpening[port] = narrowest == Double.POSITIVE_INFINITY ? 1 : narrowest;
		}
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			Cylinder placed = engine.layout.cylinders()
				.get(i);
			int port = placed.intake();
			if (model != null && port >= 0) {
				model.intakeFactor = CylinderFault.SUBMERGED.in(placed.faults()) ? 0 : engine.flow(port) * engine.portOpening[port];
				model.fuelTrim = Throttle.fuelTrim(engine.portOpening[port]);
			}
		}
	}

	/** The wastegate settings, read every tick: a redstone signal on a turbo dumps its boost at once. */
	void readTurbos(Level level) {
		for (int i = 0; i < engine.turbos.size(); i++) {
			Turbocharger turbo = engine.turbos.get(i);
			if (LoadedBlocks.blockEntity(level, engine.layout.turbos()
				.get(i)
				.pos()) instanceof TurbochargerBlockEntity be) {
				turbo.limit = be.boostLimit();
				turbo.output = be.boostShare();
				turbo.dump = be.dumping();
				turbo.antiLag = be.antiLagLevel();
				turbo.offThrottle = !engine.sim.isStopped() && offThrottle(turbo);
			}
		}
	}

	/**
	 * Whether every cylinder feeding a turbo's turbine is off the throttle: its fuel cut by a limiter, its rack shut by
	 * redstone, its throttle closed or its governor holding it back. Then there is hardly any heat in the exhaust, and
	 * anti-lag has something to do.
	 */
	static boolean offThrottle(Turbocharger turbo) {
		if (turbo.turbine()
			.isEmpty())
			return false;
		for (Breather source : turbo.turbine())
			// Only a cylinder has a rack to lift off; anything else on the exhaust counts as still working
			if (!(source instanceof CylinderModel cylinder)
				|| !(cylinder.rack < 0.3 || cylinder.fuelTrim < 0.3 || cylinder.governor < 0.3))
				return false;
		return true;
	}

	/**
	 * Each blower turns at the speed of its shaft, whatever is turning it. The ratio of that to the crank's speed is
	 * kept for the dynamometer, which has no shaft to read and runs it with the crank at the same ratio.
	 */
	void readSuperchargers(Level level) {
		double crankRpm = engine.sim.rpm();
		for (int i = 0; i < engine.blowers.size(); i++) {
			if (!(LoadedBlocks.blockEntity(level, engine.layout.superchargers()
				.get(i)
				.pos()) instanceof SuperchargerBlockEntity be))
				continue;
			Supercharger blower = engine.blowers.get(i);
			blower.limit = be.boostLimit();
			blower.dump = be.dumping();
			// A blower geared off this engine's own shaft turns as much faster as rpmScale turns that: seen as at 1, and its
			// stress per RPM shrinks to match, so what it takes from the engine is unchanged
			double gearing = onEngineNetwork(be) ? EngineController.speedScale() : 1;
			be.setGearing(gearing);
			blower.rpm = be.shaftRpm() / gearing;
			if (crankRpm >= MIN_RATIO_RPM)
				blower.driveRatio = Math.min(MAX_DRIVE_RATIO, blower.rpm / crankRpm);
		}
	}

	/**
	 * A redstone signal anywhere on a camshaft closes its fuel rack, as on an inline injection pump. The metering
	 * each injector is set to is read here too.
	 */
	static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> SENSOR_OUTPUT =
		state -> state.getBlock() instanceof com.createengines.content.airflow.SensorBlock<?>;

	/** Whether redstone has shut the fuel rack of this cylinder's camshaft line all the way. A rotor housing has no cam. */
	boolean fuelCut(Cylinder cylinder) {
		int line = cylinder.camLine();
		return line >= 0 && line < engine.racks.length && engine.racks[line] <= 0;
	}

	void readFuelRacks(Level level) {
		for (int i = 0; i < engine.racks.length; i++) {
			int signal = 0;
			for (BlockPos pos : engine.layout.camLines()
				.get(i)
				.segments())
				// A sensor's own reading is not a command: a MAF sensor beside the cam would otherwise close the rack it is trimming
				signal = Math.max(signal, Signals.bestNeighbourIgnoring(level, pos, SENSOR_OUTPUT));
			engine.racks[i] = 1 - signal / 15d;
		}
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			int line = engine.layout.cylinders()
				.get(i)
				.camLine();
			if (model != null && line >= 0)
				model.rack = engine.racks[line];
			BlockPos head = engine.layout.cylinders()
				.get(i)
				.head();
			if (model != null && head != null && LoadedBlocks.blockEntity(level, head) instanceof GalleryMember be)
				model.metering = be.meteringFraction();
			if (model != null)
				model.airMatch = airMatch(level, i);
		}
	}

	/**
	 * Sets every injector's metering to what gave a balanced mixture over the last cycle at the speed the engine is
	 * turning now, so that a beginner does not have to dial each head in by hand. Heads whose shot is already held by a
	 * MAF sensor or a closed rack, and cylinders that did not inject, are left alone. A stopped engine tunes nothing.
	 * Breathing falls with speed, so the result is right at this speed and drifts richer above it.
	 */
	public EngineController.Tuning tuneFuel(Level level) {
		if (engine.sim.isStopped())
			return new EngineController.Tuning(0, 0, 0);
		Map<BlockPos, double[]> readings = new LinkedHashMap<>();
		int skipped = 0;
		for (int i = 0; i < engine.models.size(); i++) {
			CylinderModel model = engine.models.get(i);
			BlockPos head = engine.layout.cylinders()
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
		int[] tunedCount = {0};
		// Each head gets its own figure here, so the all-heads setting must not copy one head's to the rest
		com.createengines.content.head.MeteringFollow.withoutFollow(() -> {
		for (Map.Entry<BlockPos, double[]> entry : readings.entrySet()) {
			ScrollValueBehaviour metering =
				switch (LoadedBlocks.blockEntity(level, entry.getKey())) {
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
			tunedCount[0]++;
		}
		});
		int tuned = tunedCount[0];
		return new EngineController.Tuning(tuned, skipped + readings.size() - tuned, (int) Math.round(engine.sim.rpm() * EngineController.speedScale()));
	}

	/**
	 * Sets the metering of every injector of this engine except the one at {@code source} to {@code value} (the
	 * {@code features.meterAllHeads} setting, see {@code MeteringFollow}). A head whose shot is held by a MAF sensor or a closed
	 * rack is left alone, as in {@link #tuneFuel}. Returns how many heads were set.
	 */
	public int setAllMetering(Level level, BlockPos source, int value) {
		int clamped = Math.clamp(value, CylinderHeadBlockEntity.MIN_METERING, CylinderHeadBlockEntity.MAX_METERING);
		java.util.Set<BlockPos> held = new java.util.HashSet<>();
		java.util.Set<BlockPos> heads = new java.util.LinkedHashSet<>();
		for (int i = 0; i < engine.models.size(); i++) {
			BlockPos head = engine.layout.cylinders()
				.get(i)
				.head();
			if (head == null)
				continue;
			heads.add(head);
			CylinderModel model = engine.models.get(i);
			if (model != null && (model.airMatch > 0 || model.rack < 0.99))
				held.add(head);
		}
		int set = 0;
		for (BlockPos head : heads) {
			if (head.equals(source) || held.contains(head))
				continue;
			ScrollValueBehaviour metering = com.createengines.content.head.MeteringFollow.dial(level, head);
			if (metering == null || metering.getValue() == clamped)
				continue;
			metering.setValue(clamped);
			set++;
		}
		return set;
	}

	/**
	 * The mixture the leanest MAF sensor in a cylinder's intake holds its shot to, as an equivalence ratio, or 0 where
	 * there is none.
	 */
	double airMatch(Level level, int index) {
		int port = engine.layout.cylinders()
			.get(index)
			.intake();
		double target = 0;
		if (port >= 0)
			for (BlockPos pos : engine.layout.ports()
				.get(port)
				.sensors())
				if (LoadedBlocks.blockEntity(level, pos) instanceof MafSensorBlockEntity maf)
					target = target == 0 ? maf.targetMixture() : Math.min(target, maf.targetMixture());
		return target;
	}

	/**
	 * Governors on the engine: the lowest speed set governs, and the rack (the throttle, in a spark engine) is moved to
	 * hold the crank there. Read every tick because a governor is a feedback loop: it has to keep up with the load.
	 */
	void readGovernors(Level level) {
		double target = Double.MAX_VALUE;
		double setting = Double.MAX_VALUE;
		double saved = -1;
		for (BlockPos pos : engine.layout.governors())
			if (LoadedBlocks.blockEntity(level, pos) instanceof GovernorBlockEntity be) {
				target = Math.min(target, be.governedSpeed());
				setting = Math.min(setting, be.target());
				saved = Math.max(saved, be.savedPosition());
			}
		boolean fitted = target < Double.MAX_VALUE;
		double position = 1;
		// A governor is set and read in shaft speed
		double shaftRpm = engine.sim.rpm() * EngineController.speedScale();
		if (fitted) {
			if (!governorSeeded) {
				// Just loaded or rebuilt: carry on from the rack the block last reported, not from wide open
				governorSeeded = true;
				if (saved >= 0)
					governor.seed(saved, shaftRpm, target);
			}
			position = governor.update(shaftRpm, target, EngineSimulation.TICK);
		} else if (!engine.layout.governors()
			.isEmpty()) {
			// Its block is not loaded for a moment: keep the rack where it was, an unloaded governor must not race the engine
			position = governor.position();
			fitted = false;
		} else {
			governor.reset();
			governorSeeded = false;
		}
		for (CylinderModel model : engine.sim.cylinders())
			model.governor = position;
		boolean holding = fitted && !engine.sim.isStopped() && Math.abs(shaftRpm - target) <= GOVERNED_BAND * target
			&& position > 0.05 && position < 0.95;
		governedTicks = holding ? governedTicks + 1 : 0;
		governing = fitted && !engine.sim.isStopped() && position < 0.95;
		// A redstone signal has turned the governed speed down
		commanded = governing && target < setting - 0.5;
		float rpm = (float) shaftRpm;
		for (BlockPos pos : engine.layout.governors())
			if (LoadedBlocks.blockEntity(level, pos) instanceof GovernorBlockEntity be)
				be.reading(rpm, (float) position, (float) be.governedSpeed());
	}

	/**
	 * Tells each sensor in the engine's pipes what its network is doing. A MAF sensor gets the breath of the cylinders
	 * drawing on its intake and how much of their shot it held back or added; an exhaust heat exchanger gets the heat of
	 * the cylinders that fired into its exhaust. A stopped engine reads nothing, rather than its last breath for ever.
	 */
	void readSensors(Level level) {
		mafTrimming = false;
		mafEnriching = false;
		mafReading = false;
		exchangerLevel = 0;
		int preheat = 0;
		boolean running = !engine.sim.isStopped();
		for (int port = 0; port < engine.layout.ports()
			.size(); port++) {
			EngineLayout.PortNetwork network = engine.layout.ports()
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
			for (int i = 0; running && i < engine.models.size(); i++) {
				CylinderModel model = engine.models.get(i);
				if (model == null)
					continue;
				CycleStats last = model.lastCycle();
				EngineLayout.Cylinder placed = engine.layout.cylinders()
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
				if (!intake && LoadedBlocks.blockEntity(level, pos) instanceof HeatExchangerBlockEntity)
					exchangers++;
			float heatShare = exchangers == 0 ? 0 : (float) (heat / EngineSimulation.TICK / exchangers);
			if (intake) {
				mafReading |= breathing > 0;
				mafTrimming |= breathing > 0 && meanCut >= EngineMilestones.MAF_TRIMMING;
				mafEnriching |= breathing > 0 && meanAdded >= EngineMilestones.MAF_ENRICHING;
			}
			for (BlockPos pos : network.sensors()) {
				var be = LoadedBlocks.blockEntity(level, pos);
				if (be instanceof MafSensorBlockEntity maf && intake)
					maf.reading(meanCharge, meanCut, meanAdded);
				else if (be instanceof HeatExchangerBlockEntity exchanger && !intake) {
					preheat = Math.max(preheat, exchanger.externalHeat());
					exchanger.reading(heatShare);
					exchangerLevel = Math.max(exchangerLevel, exchanger.heatLevel());
				}
			}
		}
		engine.sim.thermal()
			.setPreheat(preheat);
	}

	/**
	 * Swaps oil in the pans that have an exchange running, so fresh oil takes the place of spent oil, and spends some of
	 * what condition is left for the turning done. An engine that is not turning neither ages its oil nor flushes it.
	 */
	void serviceOil(Level level) {
		exchanging = false;
		if (engine.sim.revolutionsLastTick() <= 0)
			return;
		double swapped = 0;
		boolean fitted = false;
		for (BlockPos pos : engine.layout.sumps()) {
			if (!level.isLoaded(pos) || !(LoadedBlocks.blockEntity(level, pos) instanceof SumpBlockEntity sump))
				continue;
			fitted |= sump.filtered();
			swapped += sump.exchange(EngineSimulation.TICK);
		}
		filtered = fitted;
		engine.sim.ageOil(fitted);
		if (swapped > 0) {
			engine.sim.lubrication()
				.refresh(swapped);
			exchanging = true;
		}
	}

	boolean onEngineNetwork(KineticBlockEntity be) {
		return be.hasNetwork() && engine.primary.hasNetwork() && be.network.equals(engine.primary.network);
	}
}
