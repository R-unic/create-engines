package com.createengines.content.cylinder;

import java.util.function.Consumer;

import com.createengines.content.AnimationDiagnostic;
import com.createengines.content.EnginePoses;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.StructureCulling;
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
	/** Intake and exhaust pushrods, then their rockers on the head, for a cylinder whose cam is beside its block. */
	private final TransformedInstance[] pushrods;
	/** Which piston was last left hidden, and whether the pushrods are showing; null/true until first posed. */
	private Boolean lastForged;
	private boolean pushrodsShown = true;
	private EnginePoses.CylinderPose lastPose;
	private EnginePoses.PushrodPose lastPushrodPose;

	public CylinderVisual(VisualizationContext context, CylinderBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		piston = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.PISTON))
			.createInstance();
		forgedPiston = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
			Models.partial(CEPartialModels.FORGED_PISTON)).createInstance();
		rod = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.CONNECTING_ROD))
			.createInstance();
		pushrods = new TransformedInstance[] {instance(CEPartialModels.PUSHROD), instance(CEPartialModels.PUSHROD),
			instance(CEPartialModels.ROCKER), instance(CEPartialModels.ROCKER)};
		animate();
		if (AnimationDiagnostic.ENABLED)
			org.slf4j.LoggerFactory.getLogger("CreateEngines")
				.info("[animation diagnostic] cylinder visual created at {} (visualPos {}, onStructure {})", pos, visualPos,
					com.createengines.compat.sable.SableCompat.onStructure(level, pos));
	}

	private TransformedInstance instance(dev.engine_room.flywheel.lib.model.baked.PartialModel model) {
		return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
			.createInstance();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		// Posed from the render time, so it is exact again the frame it comes back into view
		boolean plotVisible = isVisible(context.frustum());
		boolean visible = StructureCulling.visible(level, pos, visualPos, context.frustum(), plotVisible);
		if (AnimationDiagnostic.ENABLED) {
			float time = AnimationTickHolder.getRenderTime(level);
			CrankshaftBlockEntity crank = blockEntity.crankThrow();
			AnimationDiagnostic.frame(level, pos, visualPos, plotVisible, visible, time, crank == null ? Double.NaN : crank.getTheoreticalSpeed(),
				crank == null ? Double.NaN : EnginePoses.drawnShaft(crank, time), EnginePoses.cylinder(blockEntity, time), lastPose);
		}
		if (!visible)
			return;
		animate();
	}

	private void animate() {
		EnginePoses.CylinderPose pose = EnginePoses.cylinder(blockEntity, AnimationTickHolder.getRenderTime(level));
		BlockPos origin = getVisualPosition();
		boolean forged = blockEntity.getBlockState().getValue(CylinderBlock.FORGED_PISTON);
		// Piston and rod that have not moved since the last frame (a stopped engine) keep their pose and are not re-uploaded
		if (lastForged == null || lastForged != forged || !pose.equals(lastPose)) {
			pose(forged ? forgedPiston : piston, pose.showPiston(), origin, pose.piston());
			pose(rod, pose.showRod(), origin, pose.rod());
			lastPose = pose;
		}
		// The piston not in use only needs hiding once, when the cylinder is first posed or its piston type changes
		if (lastForged == null || lastForged != forged) {
			TransformedInstance unused = forged ? piston : forgedPiston;
			unused.setVisible(false);
			unused.setChanged();
			lastForged = forged;
		}
		EnginePoses.PushrodPose pushrodPose = EnginePoses.pushrods(blockEntity, AnimationTickHolder.getRenderTime(level));
		if (pushrodPose == null) {
			// Most cylinders have no pushrods: hide them once instead of re-posing and re-uploading four every frame
			if (pushrodsShown) {
				for (TransformedInstance pushrod : pushrods) {
					pushrod.setVisible(false);
					pushrod.setChanged();
				}
				pushrodsShown = false;
			}
			return;
		}
		if (pushrodsShown && pushrodPose.equals(lastPushrodPose))
			return;
		pushrodsShown = true;
		lastPushrodPose = pushrodPose;
		pose(pushrods[0], true, origin, pushrodPose.intakeRod());
		pose(pushrods[1], true, origin, pushrodPose.exhaustRod());
		pose(pushrods[2], true, origin, pushrodPose.intakeRocker());
		pose(pushrods[3], true, origin, pushrodPose.exhaustRocker());
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
		relight(pushrods);
	}

	@Override
	protected void _delete() {
		piston.delete();
		forgedPiston.delete();
		rod.delete();
		for (TransformedInstance instance : pushrods)
			instance.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(piston);
		consumer.accept(forgedPiston);
		consumer.accept(rod);
		for (TransformedInstance instance : pushrods)
			consumer.accept(instance);
	}
}
