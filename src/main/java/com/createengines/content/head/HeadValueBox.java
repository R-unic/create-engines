package com.createengines.content.head;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A value box on any face of a cylinder head except the one against its cylinder. */
public class HeadValueBox extends ValueBoxTransform.Sided {

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 8, 16);
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction != state.getValue(CylinderHeadBlock.FACING)
			.getOpposite();
	}
}
