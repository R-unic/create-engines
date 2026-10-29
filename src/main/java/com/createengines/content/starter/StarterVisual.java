package com.createengines.content.starter;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

/** The starter's input shaft, turning on its back face. */
public final class StarterVisual {

	private StarterVisual() {
	}

	public static BlockEntityVisual<? super StarterBlockEntity> create(VisualizationContext context,
																		StarterBlockEntity blockEntity,
																		float partialTick) {
		Direction input = blockEntity.getBlockState()
			.getValue(StarterBlock.FACING)
			.getOpposite();
		return new OrientedRotatingVisual<>(context, blockEntity, partialTick, Direction.SOUTH, input,
			Models.partial(AllPartialModels.SHAFT_HALF));
	}
}
