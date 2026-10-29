package com.createengines.content.camshaft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A set of cam lobes (intake, exhaust and unit-injector) on the camshaft. It works the valves and injector of every
 * cylinder head touching its sides. Its lobe angle decides when, relative to the crank, that cylinder breathes and
 * fires.
 */
public class CamLobeBlock extends CamshaftBlock {

	private static final VoxelShape SHAPE = Block.box(3, 3, 3, 13, 13, 13);

	public CamLobeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isLobe() {
		return true;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
