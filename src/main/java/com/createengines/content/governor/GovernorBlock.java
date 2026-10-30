package com.createengines.content.governor;

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
 * A mechanical governor: set against a crankshaft block or a cylinder head, it moves the engine's fuel rack (a spark
 * engine's throttle) to hold the crank at the speed it is set to, whatever the load does. Where a rev limiter only
 * stops an engine passing a speed, this one holds it there, so everything the engine drives runs at a steady pace.
 * Its redstone output is how far open it holds the rack, 15 wide open and 0 shut, so it reads as a load meter.
 */
public class GovernorBlock extends Block implements IBE<GovernorBlockEntity> {

	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

	public GovernorBlock(Properties properties) {
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
		return getBlockEntityOptional(level, pos).map(GovernorBlockEntity::signal)
			.orElse(0);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos).map(GovernorBlockEntity::signal)
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
	public Class<GovernorBlockEntity> getBlockEntityClass() {
		return GovernorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends GovernorBlockEntity> getBlockEntityType() {
		return CEBlockEntities.GOVERNOR.get();
	}
}
