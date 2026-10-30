package com.createengines.content.airflow;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A value box on any face of a pipe block that no pipe leaves through. */
class PipeValueBox extends ValueBoxTransform.Sided {

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 8, 16);
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return !state.getValue(ManifoldBlock.CONNECTIONS.get(direction));
	}
}
