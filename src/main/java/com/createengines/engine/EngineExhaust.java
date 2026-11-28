package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.Muffling;
import com.createengines.engine.sim.SootCollector;
import com.createengines.content.muffler.SootTrapBlockEntity;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.PrecisionParts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** What leaves the engine: its sound's layout, muffling, casing round it, soot and pollution. Part of {@link EngineController}. */
final class EngineExhaust {

	private final EngineController engine;

	EngineExhaust(EngineController engine) {
		this.engine = engine;
	}

	/** Syncs between looks at the casing round the cylinders: two seconds. */
	static final int ENCLOSURE_SYNCS = 40 / EngineController.SYNC_INTERVAL;
	/** Layout-derived values, worked out on first use: the layout is fixed for the life of a controller. */
	float muffling = Float.NaN;
	@Nullable List<EngineLayout.PortNetwork> trapRuns;
	int exhaustHeads;
	/** Parallel to {@link #trapRuns}: whether the exhaust cannot go round every trap of that run. */
	private boolean @Nullable [] trapSeries;

	/**
	 * The part of the engine's sound that only the layout decides, worked out once per controller (the layout never changes for
	 * one): each cylinder's exhaust outlet, each outlet's run length, how the charge is lit and the share of precision
	 * cylinders.
	 */
	record SoundBase(int[] outletOf, float[] pipes, float[] muffles, boolean compression,
							 float precision, boolean rotary) {
	}

	@Nullable SoundBase soundBase;
	int @Nullable [] outletScratch;
	/** How much of the cylinders' sides are clad in Engine Casing, 0 to 1; looked at now and then, as casing is not part of the layout. */
	float enclosure;
	int enclosureCountdown;

	int[] outletScratch() {
		if (outletScratch == null)
			outletScratch = new int[Math.max(1, 2 * engine.models.size())];
		return outletScratch;
	}

	SoundBase soundBase() {
		if (soundBase == null)
			soundBase = computeSoundBase();
		return soundBase;
	}

	SoundBase computeSoundBase() {
		List<Cylinder> cylinders = engine.layout.cylinders();
		EngineSound.Outlets outlets = EngineSound.assignOutlets(cylinders.stream()
			.mapToInt(Cylinder::exhaust)
			.toArray());
		float[] runLength = new float[cylinders.size()];
		float[] runMuffle = new float[cylinders.size()];
		for (int i = 0; i < cylinders.size(); i++) {
			int port = cylinders.get(i)
				.exhaust();
			EngineLayout.PortNetwork network = port < 0 ? null : engine.layout.ports()
				.get(port);
			runLength[i] = network == null ? Float.NaN : EngineSound.pipeLength(network.run());
			runMuffle[i] = network == null ? Float.NaN : muffled(network);
		}
		float[] pipes = EngineSound.perOutlet(outlets.outletOf(), runLength, outlets.runs().length);
		float[] muffles = EngineSound.perOutlet(outlets.outletOf(), runMuffle, outlets.runs().length);
		int spark = 0;
		int compression = 0;
		int precise = 0;
		for (int i = 0; i < cylinders.size(); i++) {
			Cylinder placed = cylinders.get(i);
			if (placed.sparkPlug() || placed.rotary())
				spark++;
			else
				compression++;
			if ((placed.precision() & PrecisionParts.ALL) != 0)
				precise++;
		}
		return new SoundBase(outlets.outletOf(), pipes, muffles, compression > spark,
			cylinders.isEmpty() ? 1 : precise / (float) cylinders.size(),
			!cylinders.isEmpty() && cylinders.stream()
				.allMatch(Cylinder::rotary));
	}

	/**
	 * Share of the cylinders' sides that are clad in Engine Casing, 0 to 1, looked at every two seconds: casing is not part of
	 * the layout, so placing it does not rebuild the engine.
	 */
	void refreshEnclosure(Level level) {
		if (enclosureCountdown-- > 0)
			return;
		enclosureCountdown = ENCLOSURE_SYNCS - 1;
		int sides = 0;
		int clad = 0;
		for (Cylinder placed : engine.layout.cylinders())
			for (Direction side : EngineController.SIDES) {
				if (side.getAxis() == placed.bore()
					.getAxis())
					continue;
				BlockPos beside = placed.pos()
					.relative(side);
				if (!level.isLoaded(beside))
					continue;
				sides++;
				if (level.getBlockState(beside)
					.getBlock() instanceof com.createengines.content.EngineCasingBlock)
					clad++;
			}
		enclosure = sides == 0 ? 0 : clad / (float) sides;
	}

	/**
	 * How much of the engine's exhaust goes through a muffler, 0 to 1: each exhaust run counts by the heads it serves, as
	 * fully muffled if every path through it goes through a muffler and half if a muffler can be gone round.
	 */
	float muffling() {
		// Depends only on the layout, which never changes for a controller
		if (Float.isNaN(muffling))
			muffling = computeMuffling();
		return muffling;
	}

	float computeMuffling() {
		java.util.List<EngineLayout.PortNetwork> runs = engine.layout.ports()
			.stream()
			.filter(network -> network.kind() == com.createengines.content.airflow.PortKind.EXHAUST)
			.toList();
		int[] heads = new int[runs.size()];
		float[] muffled = new float[runs.size()];
		for (int i = 0; i < runs.size(); i++) {
			EngineLayout.PortNetwork run = runs.get(i);
			heads[i] = run.heads();
			muffled[i] = muffled(run);
		}
		return Muffling.engine(heads, muffled);
	}

	/** How muffled one exhaust run is, 0 to 1. */
	static float muffled(EngineLayout.PortNetwork run) {
		boolean series = run.mufflers()
			.stream()
			.anyMatch(muffler -> !run.parallel()
				.contains(muffler));
		return Muffling.run(series, !series && !run.mufflers()
			.isEmpty());
	}

	/**
	 * Hands the soot the cylinders made since the last round to the soot traps in the exhaust runs. A run catches all of
	 * it behind a trap the exhaust cannot go round, half behind one it can; what no trap catches goes out of the pipe.
	 * The cylinders are drained whether or not there is a trap, so their counts never pile up.
	 */
	void catchSoot(Level level) {
		double soot = 0;
		for (CylinderModel model : engine.models)
			if (model != null)
				soot += model.takeSoot();
		sootSinceEmission += soot;
		if (!(soot > 0))
			return;
		if (trapRuns == null) {
			// Layout-only, so worked out once
			trapRuns = engine.layout.ports()
				.stream()
				.filter(network -> network.kind() == com.createengines.content.airflow.PortKind.EXHAUST
					&& !network.traps()
						.isEmpty())
				.toList();
			exhaustHeads = engine.layout.ports()
				.stream()
				.filter(network -> network.kind() == com.createengines.content.airflow.PortKind.EXHAUST)
				.mapToInt(EngineLayout.PortNetwork::heads)
				.sum();
			trapSeries = new boolean[trapRuns.size()];
			for (int i = 0; i < trapSeries.length; i++) {
				EngineLayout.PortNetwork run = trapRuns.get(i);
				trapSeries[i] = run.traps()
					.stream()
					.anyMatch(trap -> !run.parallel()
						.contains(trap));
			}
		}
		java.util.List<EngineLayout.PortNetwork> runs = trapRuns;
		if (runs.isEmpty())
			return;
		int totalHeads = exhaustHeads;
		double caught = 0;
		for (int i = 0; i < runs.size(); i++) {
			EngineLayout.PortNetwork run = runs.get(i);
			boolean series = trapSeries[i];
			double each = SootCollector.share(soot, run.heads(), totalHeads, SootCollector.catchShare(series, !series),
				run.traps()
					.size());
			if (each <= 0)
				continue;
			for (BlockPos pos : run.traps())
				if (LoadedBlocks.blockEntity(level, pos) instanceof SootTrapBlockEntity trap)
					caught += trap.catchSoot(each);
		}
		// What a trap kept out of the exhaust is not released as the extra carbon of a rich run
		sootSinceEmission -= soot - SootCollector.escaped(soot, caught);
	}

	/** Fuel that left the exhaust unburned and past every trap since the last round of emissions, in mB. */
	double sootSinceEmission;
	long lastEmissionTick = -1;
	final com.createengines.engine.sim.Emissions emissions = new com.createengines.engine.sim.Emissions();

	/**
	 * With Pollution of the Realms installed, puts the gas of the stretch since the last round through the engine's open
	 * exhaust outlets. The fuel burned is what each cylinder's last cycle burned, for the cycles it has turned in the time;
	 * nothing is read back, so the engine runs the same with or without the other mod.
	 */
	void emitPollution(Level level, EngineState state) {
		long now = level.getGameTime();
		double soot = sootSinceEmission;
		sootSinceEmission = 0;
		long last = lastEmissionTick;
		lastEmissionTick = now;
		if (!com.createengines.compat.pollution.PollutionCompat.active())
			return;
		if (last < 0 || state != EngineState.RUNNING && state != EngineState.OVERLOADED) {
			emissions.clear();
			return;
		}
		double seconds = Math.min(5, (now - last) / 20.0);
		double cycles = Math.abs(engine.sim.rpm()) / 120 * seconds;
		double burned = 0;
		double dirt = 0;
		int fuels = 0;
		for (CylinderModel model : engine.models) {
			if (model == null)
				continue;
			burned += Math.max(0, model.lastCycle().fuelBurned) * cycles;
			FuelSpec fuel = model.fuelSupply.fuel();
			if (fuel != null) {
				dirt += fuel.sulfur();
				fuels++;
			}
		}
		emissions.add(burned, soot, fuels == 0 ? 1 : dirt / fuels, com.createengines.config.CEConfig.POLLUTION_RATE.get(),
			com.createengines.config.CEConfig.CARBON_RATE.get(), com.createengines.config.CEConfig.SULFUR_RATE.get(),
			com.createengines.config.CEConfig.POLLUTION_ENGINE_CEILING.get(), seconds);
		int carbon = emissions.takeCarbon();
		int sulfur = emissions.takeSulfur();
		if (carbon <= 0 && sulfur <= 0)
			return;
		java.util.List<BlockPos> outlets = new java.util.ArrayList<>();
		for (EngineLayout.PortNetwork network : engine.layout.ports())
			if (network.kind() == com.createengines.content.airflow.PortKind.EXHAUST)
				outlets.addAll(network.outlets());
		if (outlets.isEmpty())
			return;
		for (BlockPos outlet : outlets)
			com.createengines.compat.pollution.PollutionCompat.release(level, outlet, carbon / outlets.size()
				+ (outlet.equals(outlets.get(0)) ? carbon % outlets.size() : 0), sulfur / outlets.size()
					+ (outlet.equals(outlets.get(0)) ? sulfur % outlets.size() : 0));
	}

	/** How muffled the exhaust run on a port is, 0 to 1. */
	float muffled(int port) {
		return port < 0 ? 0 : muffled(engine.layout.ports()
			.get(port));
	}

	/** Every opening of a port network, clear or not, for the effects at its mouths. */
	List<BlockPos> openings(int port) {
		return port < 0 ? List.of() : engine.layout.ports()
			.get(port)
			.outlets();
	}
}
