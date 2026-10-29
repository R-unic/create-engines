package com.createengines.content;

import java.util.function.Consumer;

import com.createengines.engine.CrankGeometry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A crankshaft or camshaft part spinning about its axis, with an extra fixed angle (throw or lobe angle). Posed on
 * the CPU each frame so that pistons and valves, which are posed from the same angle, stay in step with it.
 */
public abstract class RotatingPartVisual<T extends KineticBlockEntity> extends AbstractBlockEntityVisual<T>
	implements SimpleDynamicVisual {

	protected final TransformedInstance part;
	protected final Axis axis;

	protected RotatingPartVisual(VisualizationContext context, T blockEntity, float partialTick, PartialModel model) {
		super(context, blockEntity, partialTick);
		axis = blockState.getValue(BlockStateProperties.AXIS);
		part = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
			.createInstance();
		animate();
	}

	/** Fixed angle added to the shaft's own rotation, in degrees. */
	protected abstract float partAngle();

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		animate();
	}

	private void animate() {
		float angle = CrankGeometry.renderAngle(AnimationTickHolder.getRenderTime(level),
			blockEntity.getTheoreticalSpeed()) + partAngle();
		part.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.rotatingPart(axis, angle))
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(part);
	}

	@Override
	protected void _delete() {
		part.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(part);
	}
}
