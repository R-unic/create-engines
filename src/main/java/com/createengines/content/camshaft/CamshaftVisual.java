package com.createengines.content.camshaft;

import java.util.function.Consumer;

import com.createengines.content.EnginePoses;
import com.createengines.content.RotatingPartVisual;
import com.createengines.registry.CEPartialModels;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;

public class CamshaftVisual extends RotatingPartVisual<CamshaftBlockEntity> {

	/** A lobe set's intake, exhaust and injector lobes, each turned on its own; empty for a plain camshaft. */
	private final TransformedInstance[] lobes;

	public CamshaftVisual(VisualizationContext context, CamshaftBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick, blockEntity.getBlockState()
			.getBlock() instanceof CamLobeBlock ? CEPartialModels.CAM_LOBE_SHAFT : CEPartialModels.CAMSHAFT);
		if (blockEntity.getBlockState()
			.getBlock() instanceof CamLobeBlock)
			lobes = new TransformedInstance[] {lobe(CEPartialModels.CAM_LOBE_INTAKE),
				lobe(CEPartialModels.CAM_LOBE_EXHAUST), lobe(CEPartialModels.CAM_LOBE_INJECTOR)};
		else
			lobes = new TransformedInstance[0];
		poseLobes();
	}

	private TransformedInstance lobe(PartialModel model) {
		return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
			.createInstance();
	}

	@Override
	protected float angle(float renderTime) {
		return EnginePoses.camAngle(blockEntity, renderTime);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		super.beginFrame(context);
		poseLobes();
	}

	private void poseLobes() {
		if (lobes.length == 0)
			return;
		EnginePoses.LobeAngles angles = EnginePoses.lobeAngles(blockEntity, AnimationTickHolder.getRenderTime(level));
		float[] each = {angles.intake(), angles.exhaust(), angles.injector()};
		for (int i = 0; i < lobes.length; i++)
			lobes[i].setIdentityTransform()
				.translate(getVisualPosition())
				.mul(EnginePoses.rotatingPart(axis, each[i]))
				.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		super.updateLight(partialTick);
		for (TransformedInstance lobe : lobes)
			relight(lobe);
	}

	@Override
	protected void _delete() {
		super._delete();
		for (TransformedInstance lobe : lobes)
			lobe.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		super.collectCrumblingInstances(consumer);
		for (TransformedInstance lobe : lobes)
			consumer.accept(lobe);
	}
}
