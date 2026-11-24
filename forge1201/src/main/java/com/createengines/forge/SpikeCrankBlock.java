package com.createengines.forge;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The spike's one block: a crankshaft that carries a running engine simulation onto a Create network. */
public class SpikeCrankBlock extends RotatedPillarKineticBlock implements IBE<SpikeCrankBlockEntity> {

	public SpikeCrankBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, net.minecraft.core.BlockPos pos,
								   BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	public Class<SpikeCrankBlockEntity> getBlockEntityClass() {
		return SpikeCrankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SpikeCrankBlockEntity> getBlockEntityType() {
		return CreateEnginesForge.CRANK_BE.get();
	}
}
