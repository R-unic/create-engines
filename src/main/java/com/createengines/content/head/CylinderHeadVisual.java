package com.createengines.content.head;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CylinderModel;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Intake and exhaust valve stems on the face touching the cam lobe, pushed in as the lobes come round. They are
 * driven by the same cam profile the simulation uses, so you can watch the valve timing.
 */
public class CylinderHeadVisual extends AbstractBlockEntityVisual<CylinderHeadBlockEntity> implements SimpleDynamicVisual {

	private static final Vector3f UP = new Vector3f(0, 1, 0);
	private static final float STEM_SPACING = 3 / 16f;
	private static final float MAX_LIFT = 1.5f / 16;

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

	private @Nullable Direction lobeFace(BlockState head) {
		Direction cylinderSide = head.getValue(CylinderHeadBlock.FACING)
			.getOpposite();
		for (Direction face : Direction.values()) {
			if (face == cylinderSide)
				continue;
			BlockState neighbour = level.getBlockState(pos.relative(face));
			if (neighbour.getBlock() instanceof CamLobeBlock
				&& neighbour.getValue(BlockStateProperties.AXIS) != face.getAxis())
				return face;
		}
		return null;
	}

	private void animate() {
		Direction face = lobeFace(blockEntity.getBlockState());
		if (face == null || !(level.getBlockEntity(pos.relative(face)) instanceof CamshaftBlockEntity lobe)) {
			intake.setVisible(false);
			exhaust.setVisible(false);
			return;
		}
		intake.setVisible(true);
		exhaust.setVisible(true);

		float camAngle = Math.abs(
			CrankGeometry.renderAngle(AnimationTickHolder.getRenderTime(level), lobe.getTheoreticalSpeed()));
		double cycle = CylinderModel.camCycleAngle(camAngle, lobe.lobeAngle());
		Vector3f camAxis = CrankGeometry.axisVector(lobe.getBlockState()
			.getValue(BlockStateProperties.AXIS));
		Vector3f normal = new Vector3f(face.step());

		pose(intake, normal, camAxis, STEM_SPACING, (float) CamProfile.BASIC.intakeLift(cycle));
		pose(exhaust, normal, camAxis, -STEM_SPACING, (float) CamProfile.BASIC.exhaustLift(cycle));
	}

	private void pose(TransformedInstance stem, Vector3f normal, Vector3f camAxis, float offset, float lift) {
		Vector3f at = new Vector3f(0.5f).add(new Vector3f(normal).mul(0.5f - lift * MAX_LIFT))
			.add(new Vector3f(camAxis).mul(offset));
		BlockPos origin = getVisualPosition();
		stem.setIdentityTransform()
			.translate(origin)
			.translate(at.x, at.y, at.z)
			.rotate(new Quaternionf().rotationTo(UP, normal))
			.translate(-0.5f, 0, -0.5f)
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
