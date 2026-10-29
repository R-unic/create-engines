package com.createengines.content.head;

import java.util.function.Consumer;

import com.createengines.content.EnginePoses;
import com.createengines.registry.CEPartialModels;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;

/**
 * Intake and exhaust valve stems on the face touching the cam lobe, pushed in as the lobes come round. They are
 * driven by the same cam profile the simulation uses, so you can watch the valve timing.
 */
public class CylinderHeadVisual extends AbstractBlockEntityVisual<CylinderHeadBlockEntity> implements SimpleDynamicVisual {

	private final TransformedInstance intake;
	private final TransformedInstance exhaust;

	public CylinderHeadVisual(VisualizationContext context, CylinderHeadBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		intake = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.VALVE_STEM))
			.createInstance();
		exhaust = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.VALVE_STEM))
			.createInstance();
		animate();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		animate();
	}

	private void animate() {
		EnginePoses.ValvePose pose = EnginePoses.valves(blockEntity, AnimationTickHolder.getRenderTime(level));
		intake.setVisible(pose != null);
		exhaust.setVisible(pose != null);
		if (pose == null)
			return;
		BlockPos origin = getVisualPosition();
		intake.setIdentityTransform()
			.translate(origin)
			.mul(pose.intake())
			.setChanged();
		exhaust.setIdentityTransform()
			.translate(origin)
			.mul(pose.exhaust())
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(intake, exhaust);
	}

	@Override
	protected void _delete() {
		intake.delete();
		exhaust.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(intake);
		consumer.accept(exhaust);
	}
}
