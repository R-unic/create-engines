package com.createengines.content.cylinder;

import java.util.function.Consumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CrankGeometry;
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
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Piston and connecting rod, posed every frame from the crank throw's visible angle with slider-crank geometry.
 */
public class CylinderVisual extends AbstractBlockEntityVisual<CylinderBlockEntity> implements SimpleDynamicVisual {

	private static final Vector3f UP = new Vector3f(0, 1, 0);

	private final TransformedInstance piston;
	private final TransformedInstance rod;

	public CylinderVisual(VisualizationContext context, CylinderBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		piston = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.PISTON))
			.createInstance();
		rod = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.CONNECTING_ROD))
			.createInstance();
		animate();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		animate();
	}

	private void animate() {
		BlockState state = blockEntity.getBlockState();
		Direction bore = state.getValue(CylinderBlock.FACING);
		Vector3f boreVector = new Vector3f(bore.step());
		CrankshaftBlockEntity crank = blockEntity.crankThrow();

		// Crank pin position relative to the throw's centre
		Vector3f pin;
		if (crank != null) {
			Axis axis = crank.getBlockState()
				.getValue(BlockStateProperties.AXIS);
			float angle = CrankGeometry.renderAngle(AnimationTickHolder.getRenderTime(level),
				crank.getTheoreticalSpeed()) + (float) crank.throwAngle();
			pin = CrankGeometry.featureDirection(axis, angle)
				.mul(CrankGeometry.THROW_RADIUS);
		} else
			pin = new Vector3f(boreVector).mul(CrankGeometry.THROW_RADIUS);

		float along = pin.dot(boreVector);
		Vector3f across = new Vector3f(pin).sub(new Vector3f(boreVector).mul(along));
		float rodLength = CrankGeometry.ROD_LENGTH;
		float wristDistance = along + (float) Math.sqrt(rodLength * rodLength - across.lengthSquared());

		Vector3f throwCentre = new Vector3f(0.5f).sub(boreVector);
		Vector3f pinPos = new Vector3f(throwCentre).add(pin);
		Vector3f wrist = new Vector3f(throwCentre).add(new Vector3f(boreVector).mul(wristDistance));
		Vector3f rodDirection = new Vector3f(wrist).sub(pinPos)
			.normalize();

		BlockPos origin = getVisualPosition();
		piston.setVisible(state.getValue(CylinderBlock.HAS_PISTON));
		piston.setIdentityTransform()
			.translate(origin)
			.translate(wrist.x, wrist.y, wrist.z)
			.rotate(new Quaternionf().rotationTo(UP, boreVector))
			.translate(-0.5f, 0, -0.5f)
			.setChanged();

		rod.setVisible(state.getValue(CylinderBlock.HAS_ROD) && crank != null);
		rod.setIdentityTransform()
			.translate(origin)
			.translate(pinPos.x, pinPos.y, pinPos.z)
			.rotate(new Quaternionf().rotationTo(UP, rodDirection))
			.translate(-0.5f, 0, -0.5f)
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(piston, rod);
	}

	@Override
	protected void _delete() {
		piston.delete();
		rod.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(piston);
		consumer.accept(rod);
	}
}
