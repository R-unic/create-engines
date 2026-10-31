package com.createengines.content.flywheel;

import com.createengines.registry.CEPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

/** The disc and its shaft, turning with the shaft. */
public final class CastFlywheelVisual {

	private CastFlywheelVisual() {
	}

	public static BlockEntityVisual<? super CastFlywheelBlockEntity> create(VisualizationContext context,
																		   CastFlywheelBlockEntity blockEntity,
																		   float partialTick) {
		return new SingleAxisRotatingVisual<>(context, blockEntity, partialTick,
			Models.partial(CEPartialModels.FLYWHEEL_DISC));
	}
}
