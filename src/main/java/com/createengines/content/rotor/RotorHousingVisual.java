package com.createengines.content.rotor;

import java.util.function.Consumer;

import com.createengines.content.EnginePoses;
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
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The inside of a rotor housing: the shaft with its eccentric turning, and the rotor riding on the eccentric. Both are
 * posed on the CPU each frame from the same drawn crank angle, so they stay in step with the rest of the crankshaft.
 */
public class RotorHousingVisual extends AbstractBlockEntityVisual<RotorHousingBlockEntity>
	implements SimpleDynamicVisual {

	private final TransformedInstance eccentric;
	private final TransformedInstance rotor;
	/** The wear mode's: the seals sprung out of a housing whose set has failed. Hidden unless it has. */
	private final TransformedInstance wreck;
	private final Axis axis;
	private boolean wrecked;

	public RotorHousingVisual(VisualizationContext context, RotorHousingBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		axis = blockState.getValue(BlockStateProperties.AXIS);
		eccentric = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.ROTOR_ECCENTRIC))
			.createInstance();
		rotor = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.ROTOR))
			.createInstance();
		wreck = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.ROTOR_WRECK))
			.createInstance();
		wrecked = blockEntity.apexFailed();
		layWreck();
		animate();
	}

	/** Lays the wreck when the seals fail, and takes it away when a new set goes in; nothing in between. */
	private void layWreck() {
		wreck.setVisible(wrecked);
		wreck.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.axisFrame(axis))
			.setChanged();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		// Posed from the render time, so it is exact again the frame it comes back into view
		if (!StructureCulling.visible(level, pos, context.frustum(), isVisible(context.frustum())))
			return;
		animate();
	}

	private void animate() {
		if (wrecked != blockEntity.apexFailed()) {
			wrecked = !wrecked;
			layWreck();
			relight(wreck);
		}
		float renderTime = AnimationTickHolder.getRenderTime(level);
		eccentric.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.rotatingPart(axis, EnginePoses.crankAngle(blockEntity, renderTime)))
			.setChanged();
		// A rotor is only there if one has been fitted
		rotor.setVisible(blockEntity.getBlockState()
			.getValue(RotorHousingBlock.HAS_ROTOR));
		rotor.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.rotor(blockEntity, renderTime))
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(eccentric, rotor, wreck);
	}

	@Override
	protected void _delete() {
		eccentric.delete();
		rotor.delete();
		wreck.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(eccentric);
		consumer.accept(rotor);
		consumer.accept(wreck);
	}
}
