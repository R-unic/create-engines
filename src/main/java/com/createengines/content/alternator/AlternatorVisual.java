package com.createengines.content.alternator;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

/** The shaft through the alternator, turning. */
public final class AlternatorVisual {

	private AlternatorVisual() {
	}

	public static BlockEntityVisual<? super AlternatorBlockEntity> create(VisualizationContext context,
																		   AlternatorBlockEntity blockEntity,
																		   float partialTick) {
		return new SingleAxisRotatingVisual<>(context, blockEntity, partialTick,
			Models.partial(AllPartialModels.SHAFT));
	}
}
