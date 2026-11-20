package com.createengines.engine;

import java.util.ArrayList;
import java.util.List;

import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.IgnitionSource;
import com.createengines.engine.sim.Tuning;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The electrical supply behind an engine's spark plugs: every battery touching the engine, and the rest of each bank, drawn
 * on together so the bank runs down evenly. With no battery at all a plug still counts as fitted, it just never fires.
 * <p>
 * A cold engine makes each spark cost more charge ({@link BatteryChemistry#sparkCost}); the controller says how cold it is each
 * tick. The same supply feeds a starter turning a spark engine over ({@link #drawCranking}).
 */
public final class BatteryIgnition implements IgnitionSource {

	private final Level level;
	private final List<BlockPos> batteries;
	/** The batteries behind {@link #batteries}, looked up again when the game tick changes: a step is many sparks, a tick is many steps. */
	private final List<BatteryBlockEntity> found = new ArrayList<>();
	private long foundAt = Long.MIN_VALUE;
	/** How cold the engine feels, 0 to 1, as the controller last said. */
	private volatile double engineCold;

	public BatteryIgnition(Level level, List<BlockPos> batteries) {
		this.level = level;
		this.batteries = batteries;
	}

	/** Tells the supply how cold the engine is (0 warm to 1 as cold as it gets), which sets what a spark costs. */
	public void setEngineCold(double cold) {
		engineCold = cold;
	}

	private List<BatteryBlockEntity> supply() {
		long now = level.getGameTime();
		if (now != foundAt) {
			foundAt = now;
			found.clear();
			for (BlockPos pos : batteries)
				if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)
					found.add(battery);
		}
		return found;
	}

	private float sparkCost() {
		return (float) BatteryChemistry.sparkCost(engineCold, Tuning.batteryCold);
	}

	@Override
	public boolean installed() {
		return true;
	}

	@Override
	public boolean powered() {
		return BatteryBank.deliverable(supply()) >= sparkCost() - 1e-4f;
	}

	@Override
	public boolean spark() {
		return BatteryBank.drawAll(supply(), sparkCost());
	}

	/** Whether the batteries are there at all: an engine with none has no supply to draw a starter's current from. */
	public boolean hasBatteries() {
		return !supply().isEmpty();
	}

	/** How much of the bank's capacity it can give now, 0 to 1. */
	public double deliverableShare() {
		List<BatteryBlockEntity> supply = supply();
		float capacity = BatteryBank.capacity(supply);
		return capacity <= 0 ? 0 : BatteryBank.deliverable(supply) / capacity;
	}

	/** Takes what a starter turning the engine over uses this tick ({@link BatteryChemistry#crankDraw}), as much of it as there is. */
	public void drawCranking() {
		BatteryBank.drawUpTo(supply(), (float) BatteryChemistry.crankDraw(engineCold, deliverableShare()));
	}
}
