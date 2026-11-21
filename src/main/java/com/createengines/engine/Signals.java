package com.createengines.engine;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Redstone reads at a block's six neighbours that make no positions to do it. Vanilla's {@code getBestNeighborSignal} and
 * {@code hasNeighborSignal} build a new position for each side, 144 bytes a call, and an engine's starters, turbos,
 * blowers, throttles, governors and cam lines ask every tick, so a running engine made kilobytes of garbage a tick out of
 * them. These read the same sides in the same order through one position that is moved, so they give what vanilla gives.
 */
public final class Signals {

	/** In the order vanilla walks them: down, up, north, south, west, east. */
	private static final Direction[] SIDES = Direction.values();
	/** One to a thread, moved for each read. Nothing in a read keeps the position it is handed. */
	private static final ThreadLocal<BlockPos.MutableBlockPos> PROBE = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private Signals() {
	}

	/** The strongest signal the block at {@code pos} receives from any side: {@code level.getBestNeighborSignal(pos)}. */
	public static int bestNeighbour(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos probe = PROBE.get();
		int best = 0;
		for (Direction side : SIDES) {
			int signal = level.getSignal(probe.setWithOffset(pos, side), side);
			if (signal >= 15)
				return 15;
			if (signal > best)
				best = signal;
		}
		return best;
	}

	/**
	 * {@link #bestNeighbour}, leaving out any neighbour whose block is {@code ignored}: what the strongest of the others
	 * gives. With nothing ignored it reads exactly what {@link #bestNeighbour} does.
	 */
	public static int bestNeighbourIgnoring(Level level, BlockPos pos, Predicate<BlockState> ignored) {
		BlockPos.MutableBlockPos probe = PROBE.get();
		int best = 0;
		for (Direction side : SIDES) {
			probe.setWithOffset(pos, side);
			if (ignored.test(level.getBlockState(probe)))
				continue;
			int signal = level.getSignal(probe, side);
			if (signal >= 15)
				return 15;
			if (signal > best)
				best = signal;
		}
		return best;
	}

	/** Whether the block at {@code pos} receives any signal at all: {@code level.hasNeighborSignal(pos)}. */
	public static boolean hasNeighbour(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos probe = PROBE.get();
		for (Direction side : SIDES)
			if (level.getSignal(probe.setWithOffset(pos, side), side) > 0)
				return true;
		return false;
	}

	/** Whether the chunk beside each of the six sides of {@code pos} is loaded, so reading a signal there loads nothing. */
	public static boolean neighboursLoaded(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos probe = PROBE.get();
		for (Direction side : SIDES)
			if (!level.isLoaded(probe.setWithOffset(pos, side)))
				return false;
		return true;
	}
}
