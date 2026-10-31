package com.createengines.content.rotor;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A value box on the sides of a rotor housing, a little above or below the middle so that two can share a face: one for
 * the eccentric's angle, one for the injector's fuel metering. Not on the shaft's ends.
 */
public class RotorValueBox extends ValueBoxTransform.Sided {

	private final double height;

	public RotorValueBox(double height) {
		this.height = height;
	}

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, height, 16);
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
	}
}
