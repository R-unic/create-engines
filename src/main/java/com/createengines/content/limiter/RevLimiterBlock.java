package com.createengines.content.limiter;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rev limiter: set against a crankshaft block or a cylinder head, it holds every injector on that engine shut once
 * the crank reaches the speed it is set to, and lets fuel back in a few percent below it. The engine bounces off the
 * limit instead of revving until it breaks something. Its redstone output is how close the engine is to the limit,
 * 15 at the limit, so it doubles as a tachometer.
 */
public class RevLimiterBlock extends Block implements IBE<RevLimiterBlockEntity> {

	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

	public RevLimiterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getBlockEntityOptional(level, pos).map(RevLimiterBlockEntity::signal)
			.orElse(0);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos).map(RevLimiterBlockEntity::signal)
			.orElse(0);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
		EngineTopology.markDirty(level, pos);
		if (!level.isClientSide && !state.is(newState.getBlock()))
			level.updateNeighborsAt(pos, this);
	}

	@Override
	public Class<RevLimiterBlockEntity> getBlockEntityClass() {
		return RevLimiterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RevLimiterBlockEntity> getBlockEntityType() {
		return CEBlockEntities.REV_LIMITER.get();
	}
}
