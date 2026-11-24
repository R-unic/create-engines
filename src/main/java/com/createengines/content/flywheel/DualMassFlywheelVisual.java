package com.createengines.content.flywheel;

import java.util.function.Consumer;

import com.createengines.content.EnginePoses;
import com.createengines.content.StructureCulling;
import com.createengines.registry.CEPartialModels;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
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
 * The two masses and the spring ring between them. The primary mass, its springs and the shaft turn with the shaft; the
 * secondary disc turns with it too but is drawn a little behind it as the engine passes through the spring pack's resonance
 * (see {@link SpringPackLag}), and falls back into step as the speed rises. Posed on the CPU each frame, like the crankshaft.
 */
public class DualMassFlywheelVisual extends AbstractBlockEntityVisual<CastFlywheelBlockEntity>
	implements SimpleDynamicVisual {

	private final TransformedInstance primary;
	private final TransformedInstance secondary;
	private final Axis axis;
	private boolean posed;
	private float lastPrimary, lastSecondary;

	public static BlockEntityVisual<? super CastFlywheelBlockEntity> create(VisualizationContext context,
																		   CastFlywheelBlockEntity blockEntity,
																		   float partialTick) {
		return new DualMassFlywheelVisual(context, blockEntity, partialTick);
	}

	private DualMassFlywheelVisual(VisualizationContext context, CastFlywheelBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		axis = blockState.getValue(BlockStateProperties.AXIS);
		primary = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.DUAL_MASS_PRIMARY))
			.createInstance();
		secondary = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(CEPartialModels.DUAL_MASS_SECONDARY))
			.createInstance();
		animate();
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		if (!StructureCulling.visible(level, pos, context.frustum(), isVisible(context.frustum())))
			return;
		animate();
	}

	private void animate() {
		float turn = DualMassPose.primary(blockEntity, axis);
		float behind = DualMassPose.secondary(blockEntity, turn, AnimationTickHolder.getRenderTime(level));
		// A wheel that has not moved since the last frame (a stopped engine) keeps its pose and is not re-uploaded
		if (posed && Float.floatToRawIntBits(turn) == Float.floatToRawIntBits(lastPrimary)
			&& Float.floatToRawIntBits(behind) == Float.floatToRawIntBits(lastSecondary))
			return;
		posed = true;
		lastPrimary = turn;
		lastSecondary = behind;
		primary.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.rotatingPart(axis, turn))
			.setChanged();
		secondary.setIdentityTransform()
			.translate(getVisualPosition())
			.mul(EnginePoses.rotatingPart(axis, behind))
			.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		relight(primary, secondary);
	}

	@Override
	protected void _delete() {
		primary.delete();
		secondary.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		consumer.accept(primary);
		consumer.accept(secondary);
	}
}
