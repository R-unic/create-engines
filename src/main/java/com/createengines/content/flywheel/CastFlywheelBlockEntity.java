package com.createengines.content.flywheel;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The disc's shaft: it only turns, and the engine it sits on counts its mass. */
public class CastFlywheelBlockEntity extends KineticBlockEntity {

	public CastFlywheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
}
