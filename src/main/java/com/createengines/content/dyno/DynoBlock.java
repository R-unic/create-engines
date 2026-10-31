package com.createengines.content.dyno;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dynamometer. Set it against any block of a built engine (a crankshaft, a cylinder or a head) and goggles on it show
 * the engine's torque and power curves, its peak figures and its powerband. It also gives a redstone signal that
 * rises as the engine's speed climbs towards the speed it makes its peak power at.
 */
public class DynoBlock extends Block implements IBE<DynoBlockEntity> {

	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

	public DynoBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos).map(DynoBlockEntity::signal)
			.orElse(0);
	}

	@Override
	public Class<DynoBlockEntity> getBlockEntityClass() {
		return DynoBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends DynoBlockEntity> getBlockEntityType() {
		return CEBlockEntities.DYNO.get();
	}
}
