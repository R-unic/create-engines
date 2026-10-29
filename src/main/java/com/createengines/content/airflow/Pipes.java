package com.createengines.content.airflow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** How pipes find the blocks they join. */
public final class Pipes {

	private Pipes() {
	}

	/** Whether {@code neighbour} would take a pipe of {@code kind} on its {@code side} face. */
	public static boolean accepts(BlockState neighbour, Direction side, PortKind kind) {
		return neighbour.getBlock() instanceof AirflowPort port && port.acceptsPipe(neighbour, side, kind);
	}

	/**
	 * Opens every pipe beside a newly placed port towards it, if the port takes pipes on that side. Pipes pick the
	 * ports around them when they are placed; this is the other half, for a port placed against pipes that are
	 * already there.
	 */
	public static void attachNeighbours(Level level, BlockPos pos, BlockState port) {
		if (level.isClientSide)
			return;
		for (Direction direction : Direction.values()) {
			BlockPos at = pos.relative(direction);
			BlockState pipe = level.getBlockState(at);
			if (!(pipe.getBlock() instanceof ManifoldBlock manifold))
				continue;
			var property = ManifoldBlock.CONNECTIONS.get(direction.getOpposite());
			if (!pipe.getValue(property) && accepts(port, direction, manifold.kind()))
				level.setBlock(at, pipe.setValue(property, true), Block.UPDATE_ALL);
		}
	}
}
