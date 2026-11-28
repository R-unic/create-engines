package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.topology.EngineLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The starters on one engine's crank. Part of {@link EngineController}: they are looked up once a tick by
 * {@link #resolve} and every later step of that tick uses what it found, in arrays owned here, so nothing allocates.
 */
final class EngineStarters {

	private final List<BlockPos> positions;
	/** Which way the crank line each starter turns is geared, parallel to {@link #positions}; the layout never changes. */
	private final int[] sense;
	/** This tick's block entities, parallel to {@link #positions}; null where there is none or it is not loaded. */
	private final @Nullable StarterBlockEntity[] found;
	/** Which of them are pushing this tick. */
	private final boolean[] pushing;

	/** The crank speed the engine is being turned at, rad/s, and which way, as {@link #choose} left them. */
	double floor;
	int floorDirection;

	EngineStarters(EngineLayout layout) {
		positions = layout.starters();
		sense = new int[positions.size()];
		for (int i = 0; i < sense.length; i++)
			sense[i] = senseAt(layout, positions.get(i));
		found = new StarterBlockEntity[sense.length];
		pushing = new boolean[sense.length];
	}

	/**
	 * Which way the crank line a starter turns is geared, relative to the engine's own direction: -1 if it turns the
	 * other way, so that cranking it forwards turns the engine backwards.
	 */
	private static int senseAt(EngineLayout layout, BlockPos starter) {
		for (EngineLayout.Segment segment : layout.segments())
			if (segment.pos()
				.distManhattan(starter) == 1)
				return segment.sense();
		return 1;
	}

	void resolve(Level level) {
		for (int i = 0; i < found.length; i++)
			found[i] = LoadedBlocks.blockEntity(level, positions.get(i)) instanceof StarterBlockEntity starter ? starter : null;
	}

	void setElectricReady(boolean ready) {
		for (StarterBlockEntity starter : found)
			if (starter != null)
				starter.setElectricReady(ready);
	}

	/**
	 * Finds the starter that sets the pace, if any turns the crank harder than {@code outsideFloor}, and those that push
	 * along with it. Starters turning the crank the same way as the one that sets the pace all push on it, so a big
	 * engine can be cranked by several drives, each feeling its share of the effort. One much slower than the leader
	 * would just be overrun by its clutch, so it sits out.
	 *
	 * @return where the leading starter is, or null if none is
	 */
	@Nullable BlockPos choose(double outsideFloor, int outsideDirection) {
		floor = outsideFloor;
		floorDirection = outsideDirection;
		BlockPos leader = null;
		double top = 0;
		for (int i = 0; i < found.length; i++) {
			StarterBlockEntity starter = found[i];
			if (starter == null || !starter.wantsToCrank())
				continue;
			double omega = EngineSimulation.rpmToOmega(starter.crankRpm());
			if (omega > top)
				top = omega;
			if (omega > floor) {
				floor = omega;
				floorDirection = starter.crankDirection() * sense[i];
				leader = positions.get(i);
			}
		}
		for (int i = 0; i < found.length; i++) {
			StarterBlockEntity starter = found[i];
			pushing[i] = leader != null && starter != null && starter.wantsToCrank()
				&& starter.crankDirection() * sense[i] == floorDirection
				&& EngineSimulation.rpmToOmega(starter.crankRpm()) >= top * 0.5;
		}
		return leader;
	}

	/** Whether one of those pushing makes a cold engine catch. */
	boolean warming() {
		for (int i = 0; i < found.length; i++)
			if (pushing[i] && found[i].warmsColdEngines())
				return true;
		return false;
	}

	/** The strength of all those pushing, in plain starters. */
	double strength() {
		double strength = 0;
		for (int i = 0; i < found.length; i++)
			if (pushing[i])
				strength += found[i].strength();
		return strength;
	}

	/** Tells every starter what it did this tick: its share of the effort, and what the batteries behind it are doing. */
	void report(BlockPos primary, double share, boolean drawsBattery, boolean cranking, @Nullable BatteryIgnition supply) {
		for (int i = 0; i < found.length; i++) {
			StarterBlockEntity starter = found[i];
			if (starter == null)
				continue;
			boolean active = pushing[i];
			starter.updateCranking(primary, active ? share : 0, active);
			starter.setDrawingCharge(active && drawsBattery && cranking && supply.deliverableShare() > 0);
			starter.setBatteryWeak(drawsBattery && BatteryChemistry.isLow(supply.deliverableShare()) && starter.wantsToCrank());
		}
	}
}
