package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.sim.IgnitionSource;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The electrical supply behind an engine's spark plugs: every battery touching the engine, drawn on in turn. With no
 * battery at all a plug still counts as fitted, it just never fires.
 */
public final class BatteryIgnition implements IgnitionSource {

	/** Charge one spark takes. */
	private static final float SPARK_COST = 1;

	private final Level level;
	private final List<BlockPos> batteries;

	public BatteryIgnition(Level level, List<BlockPos> batteries) {
		this.level = level;
		this.batteries = batteries;
	}

	private @Nullable BatteryBlockEntity battery(BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery ? battery : null;
	}

	@Override
	public boolean installed() {
		return true;
	}

	@Override
	public boolean powered() {
		for (BlockPos pos : batteries) {
			BatteryBlockEntity battery = battery(pos);
			if (battery != null && battery.charge() >= SPARK_COST)
				return true;
		}
		return false;
	}

	@Override
	public boolean spark() {
		for (BlockPos pos : batteries) {
			BatteryBlockEntity battery = battery(pos);
			if (battery != null && battery.charge() >= SPARK_COST) {
				battery.draw(SPARK_COST);
				return true;
			}
		}
		return false;
	}
}
