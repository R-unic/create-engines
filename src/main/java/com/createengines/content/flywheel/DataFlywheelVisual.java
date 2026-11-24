package com.createengines.content.flywheel;

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The block's own model, turning with the shaft. The model is drawn as the upright one (axis Y), which the instance then
 * turns onto the block's real axis, the way Create's own shafts and cogwheels are drawn; a model that is already turned by
 * the block state would be turned twice.
 */
public final class DataFlywheelVisual {

	private DataFlywheelVisual() {
	}

	public static BlockEntityVisual<? super CastFlywheelBlockEntity> create(VisualizationContext context,
																		   CastFlywheelBlockEntity blockEntity,
																		   float partialTick) {
		return new SingleAxisRotatingVisual<>(context, blockEntity, partialTick,
			Models.block(blockEntity.getBlockState()
				.setValue(BlockStateProperties.AXIS, Axis.Y)));
	}
}
