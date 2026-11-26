package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.radiator.RadiatorBlockEntity;
import com.createengines.engine.sim.Radiator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The radiators touching an engine. One in an unloaded chunk does nothing. */
public final class RadiatorBank implements Radiator.Supply {

	private final Level level;
	private final List<BlockPos> radiators;

	public RadiatorBank(Level level, List<BlockPos> radiators) {
		this.level = level;
		this.radiators = radiators;
	}

	private @Nullable RadiatorBlockEntity radiator(int i) {
		BlockPos pos = radiators.get(i);
		return level.isLoaded(pos) && LoadedBlocks.blockEntity(level, pos) instanceof RadiatorBlockEntity radiator
			? radiator : null;
	}

	@Override
	public int count() {
		return radiators.size();
	}

	@Override
	public double quality(int i) {
		RadiatorBlockEntity radiator = radiator(i);
		return radiator == null ? 0 : radiator.quality();
	}

	@Override
	public double airflow(int i) {
		RadiatorBlockEntity radiator = radiator(i);
		return radiator == null ? 1 : radiator.airflow();
	}

	@Override
	public void ran(int i, double shed, double boiled) {
		RadiatorBlockEntity radiator = radiator(i);
		if (radiator != null)
			radiator.ran(shed, boiled);
	}
}
