package com.createengines.content.cylinder;

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
import org.joml.Matrix4f;

/**
 * Piston and connecting rod, posed every frame from the crank throw's visible angle with slider-crank geometry.
 */
public class CylinderVisual extends AbstractBlockEntityVisual<CylinderBlockEntity> implements SimpleDynamicVisual {

	private final TransformedInstance piston;
	private final TransformedInstance forgedPiston;
	private final TransformedInstance rod;

	public CylinderVisual(VisualizationContext context, CylinderBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		piston = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.PISTON))
			.createInstance();
		forgedPiston = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
			Models.partial(CEPartialModels.FORGED_PISTON)).createInstance();
		rod = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.CONNECTING_ROD))
			.createInstance();
		animate();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		animate();
	}

	private void animate() {
		EnginePoses.CylinderPose pose = EnginePoses.cylinder(blockEntity, AnimationTickHolder.getRenderTime(level));
		BlockPos origin = getVisualPosition();
		boolean forged = blockEntity.getBlockState().getValue(CylinderBlock.FORGED_PISTON);
		pose(forged ? forgedPiston : piston, pose.showPiston(), origin, pose.piston());
		TransformedInstance unused = forged ? piston : forgedPiston;
		unused.setVisible(false);
		unused.setChanged();
		pose(rod, pose.showRod(), origin, pose.rod());
	}

	private static void pose(TransformedInstance instance, boolean visible, BlockPos origin, Matrix4f pose) {
		instance.setVisible(visible);
		instance.setIdentityTransform()
			.translate(origin)
			.mul(pose)
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(piston, forgedPiston, rod);
	}

	@Override
	protected void _delete() {
		piston.delete();
		forgedPiston.delete();
		rod.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(piston);
		consumer.accept(forgedPiston);
		consumer.accept(rod);
	}
}
