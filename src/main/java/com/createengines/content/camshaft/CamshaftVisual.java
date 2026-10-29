package com.createengines.content.camshaft;

import com.createengines.content.RotatingPartVisual;
import com.createengines.registry.CEPartialModels;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;

public class CamshaftVisual extends RotatingPartVisual<CamshaftBlockEntity> {

	public CamshaftVisual(VisualizationContext context, CamshaftBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick,
			blockEntity.getBlockState()
				.getBlock() instanceof CamLobeBlock ? CEPartialModels.CAM_LOBE : CEPartialModels.CAMSHAFT);
	}

	@Override
	protected float partAngle() {
		return (float) blockEntity.lobeAngle();
	}
}
