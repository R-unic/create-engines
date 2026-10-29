package com.createengines.content.starter;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a crankshaft through a one-way clutch. {@link #FACING} points at the crankshaft block it engages; rotation
 * comes in through a shaft on the opposite face, from any Create source. It engages while it has a redstone signal,
 * and the engine pulls away from it as soon as it runs faster than the starter turns.
 * <p>
 * The starter is deliberately not kinetically connected to the crank: it never joins the engine's network, it only
 * pushes on the crank, and the effort that takes is the stress it puts on its own input.
 */
public class StarterBlock extends DirectionalKineticBlock implements IBE<StarterBlockEntity> {

	public StarterBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace()
			.getOpposite());
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING)
			.getOpposite();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		super.onRemove(state, level, pos, newState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<StarterBlockEntity> getBlockEntityClass() {
		return StarterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StarterBlockEntity> getBlockEntityType() {
		return CEBlockEntities.STARTER.get();
	}
}
