package com.createengines.content.crankshaft;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** A value box on any side of an axis block that is not one of its shaft ends. */
public class SideValueBox extends ValueBoxTransform.Sided {

	private final float depth;

	public SideValueBox(float depth) {
		this.depth = depth;
	}

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 8, depth);
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
	}
}
