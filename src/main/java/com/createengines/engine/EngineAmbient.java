package com.createengines.engine;

import com.createengines.engine.sim.Altitude;

import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.Cylinder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The air an engine breathes where it is, and the world water round one on a moving structure. Part of {@link EngineController}. */
final class EngineAmbient {

	private final EngineController engine;

	EngineAmbient(EngineController engine) {
		this.engine = engine;
	}

	/**
	 * How thick the air is where this engine really is. On a moving structure (Sable) that is its height in the world, not its
	 * plot's, and it changes as the structure climbs, so it is read again every {@link #AIR_INTERVAL} ticks.
	 */
	double airDensity() {
		readIntakeAir();
		return heightDensity() * (1 - intakeLoss);
	}

	/** The share of its air density the engine loses to dirty air at its intakes: 0 unless polluted intake air is on. */
	double intakeLoss;

	/**
	 * Reads what the air at each intake opening holds and works out what it costs after any filter, as the mean over the intake
	 * lines that open to the air. The reading of each mouth is cached, so this is cheap to ask for often.
	 */
	void readIntakeAir() {
		Level level = engine.primary.getLevel();
		if (level == null || !com.createengines.compat.pollution.PollutionCompat.pollutedIntake()) {
			intakeLoss = 0;
			return;
		}
		long full = com.createengines.compat.pollution.PollutionCompat.dirtiestAir();
		double lost = 0;
		int lines = 0;
		for (EngineLayout.PortNetwork network : engine.layout.ports()) {
			if (network.kind() != com.createengines.content.airflow.PortKind.INTAKE || network.outlets()
				.isEmpty())
				continue;
			double dirt = 0;
			for (BlockPos opening : network.outlets())
				dirt += com.createengines.engine.sim.IntakeAir.dirt(
					com.createengines.compat.pollution.PollutionCompat.gasAtMouth(level, opening), full);
			lost += com.createengines.engine.sim.IntakeAir.loss(dirt / network.outlets()
				.size(), network.filtering());
			lines++;
		}
		intakeLoss = lines == 0 ? 0 : lost / lines;
	}

	/** The air density where the engine is, from its height alone (or Sable's pressure). */
	double heightDensity() {
		if (!com.createengines.engine.sim.Tuning.altitudeThinning)
			return 1;
		Level level = engine.primary.getLevel();
		if (level != null && com.createengines.engine.sim.Tuning.sableAirPressure) {
			// With Sable the air is as thick as its own pressure says where the engine is, so a plane's ceiling is Sable's
			double pressure = com.createengines.compat.sable.SableCompat.airPressure(level, engine.primary.getBlockPos());
			if (!Double.isNaN(pressure))
				return pressure;
		}
		return Altitude.density(level == null ? engine.primary.getBlockPos()
			.getY() : com.createengines.compat.sable.SableCompat.worldY(level, engine.primary.getBlockPos()));
	}

	/** What {@link #worldWater} read last, and whether it has read at all; see {@link #watchWorldWater}. */
	int lastWorldWater;
	boolean worldWaterKnown;

	/**
	 * An engine on a moving structure (Sable) is under water when the world around the structure says so, which no block
	 * update tells it as the structure moves. Every {@link #AIR_INTERVAL} ticks it reads which of its cylinders, heads and
	 * outlets are in a real fluid, and when that changes it asks for a rescan, which makes the same check the scanner makes for
	 * any engine. Off a structure this costs one check.
	 */
	void watchWorldWater(Level level) {
		if (!com.createengines.compat.sable.SableCompat.onStructure(level, engine.primary.getBlockPos())) {
			worldWaterKnown = false;
			return;
		}
		int now = worldWater(level);
		if (worldWaterKnown && now != lastWorldWater)
			com.createengines.engine.topology.EngineTopology.markDirty(level, engine.primary.getBlockPos());
		lastWorldWater = now;
		worldWaterKnown = true;
	}

	int worldWater(Level level) {
		int hash = 1;
		for (Cylinder cylinder : engine.layout.cylinders()) {
			hash = 31 * hash + (com.createengines.compat.sable.SableCompat.wetInWorld(level, cylinder.pos())
				|| cylinder.head() != null && com.createengines.compat.sable.SableCompat.wetInWorld(level, cylinder.head()) ? 1 : 0);
		}
		for (var network : engine.layout.ports())
			for (BlockPos outlet : network.outlets()) {
				var state = level.getBlockState(outlet);
				hash = 31 * hash + (state.getBlock() instanceof com.createengines.content.airflow.PortOpeningBlock
					&& com.createengines.content.airflow.PortOpeningBlock.isFlooded(level, outlet, state) ? 1 : 0);
			}
		return hash;
	}
}
