package com.createengines.content.blackbox;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A flight recorder. Set it against any block of a built engine (a crankshaft, a cylinder or a head) and it keeps the
 * last ten minutes of the engine's speed, load and warmth, and every time the engine changed state, with the cause the
 * figures point to. Goggles on it read the tape back.
 */
public class BlackBoxBlock extends Block implements IBE<BlackBoxBlockEntity> {

	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

	public BlackBoxBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public Class<BlackBoxBlockEntity> getBlockEntityClass() {
		return BlackBoxBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BlackBoxBlockEntity> getBlockEntityType() {
		return CEBlockEntities.BLACK_BOX.get();
	}
}
