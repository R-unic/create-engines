package com.createengines.content.crankshaft;

import com.createengines.content.EnginePoses;
import com.createengines.content.RotatingPartVisual;
import com.createengines.registry.CEPartialModels;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;

public class CrankshaftVisual extends RotatingPartVisual<CrankshaftBlockEntity> {

	public CrankshaftVisual(VisualizationContext context, CrankshaftBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick,
			blockEntity.getBlockState()
				.getBlock() instanceof CrankThrowBlock ? CEPartialModels.CRANK_THROW : CEPartialModels.CRANKSHAFT);
	}

	@Override
	protected float angle(float renderTime) {
		return EnginePoses.crankAngle(blockEntity, renderTime);
	}
}
