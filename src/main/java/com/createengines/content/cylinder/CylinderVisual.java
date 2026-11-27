package com.createengines.content.cylinder;

import java.util.function.Consumer;

import com.createengines.content.AnimationDiagnostic;
import com.createengines.content.EnginePoses;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.StructureCulling;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.Wear;
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

	/** One instance for each kind of piston, by {@link CEPartialModels#pistonIndex}; only the fitted one shows. */
	private final TransformedInstance[] pistons;
	/** One instance for each number of rings a piston can carry; only the fitted number shows, and none with no rings. */
	private final TransformedInstance[] rings;
	private int lastRings = -1;
	/** One instance for each kind of rod, by {@link Wear.Rod} ordinal; only the fitted one shows. */
	private final TransformedInstance[] rods;
	private Wear.Rod lastRod;
	/** The wear mode's wreck lying in the bore of a cylinder whose rod was thrown, and the scorch on a seized piston. Hidden unless they are wanted. */
	private final TransformedInstance wreck;
	private final TransformedInstance scorch;
	private Boolean lastThrown;
	private boolean lastSeized;
	/** Intake and exhaust pushrods, then their rockers on the head, for a cylinder whose cam is beside its block. */
	private final TransformedInstance[] pushrods;
	/** Which piston was last posed, -1 until the first time, and whether the pushrods are showing. */
	private int lastPiston = -1;
	private boolean pushrodsShown = true;
	private EnginePoses.CylinderPose lastPose;
	private EnginePoses.PushrodPose lastPushrodPose;

	public CylinderVisual(VisualizationContext context, CylinderBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		var models = CEPartialModels.pistons();
		pistons = new TransformedInstance[models.length];
		for (int i = 0; i < models.length; i++)
			pistons[i] = instance(models[i]);
		rings = new TransformedInstance[CylinderSpec.MAX_RINGS];
		for (int i = 0; i < rings.length; i++)
			rings[i] = instance(CEPartialModels.rings(i + 1));
		rods = new TransformedInstance[Wear.Rod.values().length];
		for (Wear.Rod kind : Wear.Rod.values())
			rods[kind.ordinal()] = instance(CEPartialModels.rod(kind));
		wreck = instance(CEPartialModels.CYLINDER_WRECK);
		scorch = instance(CEPartialModels.SEIZED_RING);
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
		boolean visible = StructureCulling.visible(level, pos, context.frustum(), plotVisible);
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
		int fittedPiston = CEPartialModels.pistonIndex(blockEntity.getBlockState());
		int fittedRings = blockEntity.getBlockState().getValue(CylinderBlock.RINGS);
		boolean swapped = fittedPiston != lastPiston || fittedRings != lastRings;
		// A change of piston or rings hides them all, and the pose below shows the ones now fitted
		if (swapped) {
			hide(pistons);
			hide(rings);
		}
		// Piston and rod that have not moved since the last frame (a stopped engine) keep their pose and are not re-uploaded
		boolean seized = blockEntity.showsSeized();
		Wear.Rod kind = blockEntity.rodKind();
		// A change of rod hides all three, and the pose below shows the one now fitted
		if (kind != lastRod)
			for (TransformedInstance unused : rods) {
				unused.setVisible(false);
				unused.setChanged();
			}
		if (swapped || !pose.equals(lastPose) || seized != lastSeized || kind != lastRod) {
			pose(pistons[fittedPiston], pose.showPiston(), origin, pose.piston());
			if (fittedRings > 0)
				pose(rings[fittedRings - 1], pose.showPiston(), origin, pose.piston());
			lastPiston = fittedPiston;
			lastRings = fittedRings;
			pose(rods[kind.ordinal()], pose.showRod(), origin, pose.rod());
			lastRod = kind;
			// The scorch rides on the piston it is the scorch of
			pose(scorch, seized && pose.showPiston(), origin, pose.piston());
			lastSeized = seized;
			lastPose = pose;
		}
		// A thrown rod's wreck is laid once, when it appears or goes
		boolean thrown = blockEntity.showsThrown();
		if (lastThrown == null || lastThrown != thrown) {
			pose(wreck, thrown, origin, EnginePoses.facingFrame(blockEntity.getBlockState()
				.getValue(CylinderBlock.FACING)));
			lastThrown = thrown;
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

	private static void hide(TransformedInstance[] instances) {
		for (TransformedInstance instance : instances) {
			instance.setVisible(false);
			instance.setChanged();
		}
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
		relight(pistons);
		relight(rings);
		relight(wreck, scorch);
		relight(rods);
		relight(pushrods);
	}

	@Override
	protected void _delete() {
		for (TransformedInstance instance : pistons)
			instance.delete();
		for (TransformedInstance instance : rings)
			instance.delete();
		for (TransformedInstance instance : rods)
			instance.delete();
		wreck.delete();
		scorch.delete();
		for (TransformedInstance instance : pushrods)
			instance.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		for (TransformedInstance instance : pistons)
			consumer.accept(instance);
		for (TransformedInstance instance : rings)
			consumer.accept(instance);
		for (TransformedInstance instance : rods)
			consumer.accept(instance);
		consumer.accept(wreck);
		consumer.accept(scorch);
		for (TransformedInstance instance : pushrods)
			consumer.accept(instance);
	}
}
