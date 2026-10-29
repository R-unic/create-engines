package com.createengines.content.battery;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

/** The shaft through the battery, turning. */
public final class BatteryVisual {

	private BatteryVisual() {
	}

	public static BlockEntityVisual<? super BatteryBlockEntity> create(VisualizationContext context,
																		BatteryBlockEntity blockEntity,
																		float partialTick) {
		return new SingleAxisRotatingVisual<>(context, blockEntity, partialTick,
			Models.partial(AllPartialModels.SHAFT));
	}
}
