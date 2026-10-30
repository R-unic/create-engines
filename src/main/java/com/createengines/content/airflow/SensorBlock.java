package com.createengines.content.airflow;

import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A length of pipe with a sensor in it. It joins pipes exactly as a manifold does, and reads what flows through the
 * network it sits in: the engine hands each reading to its block entity, which puts it out as a redstone signal on
 * every side (and to a comparator) as well as on goggles.
 */
public abstract class SensorBlock<T extends SensorBlockEntity> extends ManifoldBlock implements IBE<T> {

	protected SensorBlock(Properties properties, PortKind kind) {
		super(properties, kind);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getBlockEntityOptional(level, pos).map(SensorBlockEntity::signal)
			.orElse(0);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos).map(SensorBlockEntity::signal)
			.orElse(0);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		// Whatever it was powering has to notice that the signal is gone
		if (!level.isClientSide && !state.is(newState.getBlock()))
			level.updateNeighborsAt(pos, this);
	}
}
