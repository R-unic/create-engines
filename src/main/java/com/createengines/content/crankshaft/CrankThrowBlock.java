package com.createengines.content.crankshaft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crank pin offset from the axis. Cylinders mount on its four sides; its throw angle (set with a value box) and
 * the side a cylinder sits on decide when that cylinder's piston reaches top dead centre.
 */
public class CrankThrowBlock extends CrankshaftBlock {

	private static final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 14, 14);

	public CrankThrowBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isThrow() {
		return true;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
