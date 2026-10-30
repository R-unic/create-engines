package com.createengines.content.supercharger;

import com.createengines.registry.CEPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

/** The blower's rotor and its shaft, turning with the shaft. */
public final class SuperchargerVisual {

	private SuperchargerVisual() {
	}

	public static BlockEntityVisual<? super SuperchargerBlockEntity> create(VisualizationContext context,
																			 SuperchargerBlockEntity blockEntity,
																			 float partialTick) {
		return new SingleAxisRotatingVisual<>(context, blockEntity, partialTick,
			Models.partial(CEPartialModels.SUPERCHARGER_ROTOR));
	}
}
