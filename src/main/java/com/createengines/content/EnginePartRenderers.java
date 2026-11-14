package com.createengines.content;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.createengines.client.render.ChamberVolume;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankThrowBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.registry.CEPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Block entity renderers for the moving engine parts, used where Flywheel visuals are not: inside Ponder scenes and
 * with Flywheel's backends switched off. They pose parts with {@link EnginePoses}, exactly like the visuals.
 */
public final class EnginePartRenderers {

	private EnginePartRenderers() {
	}

	private abstract static class Base<T extends BlockEntity> extends SafeBlockEntityRenderer<T> {

		protected Base(BlockEntityRendererProvider.Context context) {
		}

		@Override
		protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
								  int overlay) {
			if (VisualizationManager.supportsVisualization(be.getLevel()))
				return;
			render(be, AnimationTickHolder.getRenderTime(be.getLevel()), ms, buffer, light);
		}

		protected abstract void render(T be, float renderTime, PoseStack ms, MultiBufferSource buffer, int light);

		protected static void draw(PartialModel model, BlockState state, Matrix4f pose, PoseStack ms,
								   MultiBufferSource buffer, int light) {
			ms.pushPose();
			ms.last()
				.pose()
				.mul(pose);
			ms.last()
				.normal()
				.mul(new Matrix3f(pose).normal());
			CachedBuffers.partial(model, state)
				.light(light)
				.renderInto(ms, buffer.getBuffer(RenderType.solid()));
			ms.popPose();
		}
	}

	public static class Crankshaft extends Base<CrankshaftBlockEntity> {
		public Crankshaft(BlockEntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected void render(CrankshaftBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer,
							  int light) {
			BlockState state = be.getBlockState();
			PartialModel model = state.getBlock() instanceof CrankThrowBlock ? CEPartialModels.CRANK_THROW
				: CEPartialModels.CRANKSHAFT;
			draw(model, state, EnginePoses.rotatingPart(state.getValue(BlockStateProperties.AXIS),
				EnginePoses.crankAngle(be, renderTime)), ms, buffer, light);
		}
	}

	public static class RotorHousing extends Base<RotorHousingBlockEntity> {
		public RotorHousing(BlockEntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected void render(RotorHousingBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer,
							  int light) {
			BlockState state = be.getBlockState();
			draw(CEPartialModels.ROTOR_ECCENTRIC, state, EnginePoses.rotatingPart(state.getValue(BlockStateProperties.AXIS),
				EnginePoses.crankAngle(be, renderTime)), ms, buffer, light);
			if (state.getValue(RotorHousingBlock.HAS_ROTOR))
				draw(CEPartialModels.ROTOR, state, EnginePoses.rotor(be, renderTime), ms, buffer, light);
		}
	}

	public static class Camshaft extends Base<CamshaftBlockEntity> {
		public Camshaft(BlockEntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected void render(CamshaftBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer,
							  int light) {
			BlockState state = be.getBlockState();
			boolean lobes = state.getBlock() instanceof CamLobeBlock;
			Axis axis = state.getValue(BlockStateProperties.AXIS);
			draw(lobes ? CEPartialModels.CAM_LOBE_SHAFT : CEPartialModels.CAMSHAFT, state,
				EnginePoses.rotatingPart(axis, EnginePoses.camAngle(be, renderTime)), ms, buffer, light);
			if (!lobes)
				return;
			EnginePoses.LobeAngles angles = EnginePoses.lobeAngles(be, renderTime);
			draw(CEPartialModels.CAM_LOBE_INTAKE, state, EnginePoses.rotatingPart(axis, angles.intake()), ms, buffer, light);
			draw(CEPartialModels.CAM_LOBE_EXHAUST, state, EnginePoses.rotatingPart(axis, angles.exhaust()), ms, buffer,
				light);
			draw(CEPartialModels.CAM_LOBE_INJECTOR, state, EnginePoses.rotatingPart(axis, angles.injector()), ms, buffer,
				light);
		}
	}

	public static class Cylinder extends Base<CylinderBlockEntity> {
		public Cylinder(BlockEntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected void renderSafe(CylinderBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
								  int light, int overlay) {
			super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
			// The gas is drawn here whether or not Flywheel draws the moving parts
			ChamberVolume.render(be, AnimationTickHolder.getRenderTime(be.getLevel()), ms, buffer);
		}

		@Override
		protected void render(CylinderBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer,
							  int light) {
			EnginePoses.CylinderPose pose = EnginePoses.cylinder(be, renderTime);
			if (pose.showPiston())
				draw(be.getBlockState().getValue(CylinderBlock.FORGED_PISTON) ? CEPartialModels.FORGED_PISTON
					: CEPartialModels.PISTON, be.getBlockState(), pose.piston(), ms, buffer, light);
			if (pose.showRod())
				draw(CEPartialModels.CONNECTING_ROD, be.getBlockState(), pose.rod(), ms, buffer, light);
		}
	}

	public static class Head extends Base<CylinderHeadBlockEntity> {
		public Head(BlockEntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected void render(CylinderHeadBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer,
							  int light) {
			EnginePoses.ValvePose pose = EnginePoses.valves(be, renderTime);
			if (pose == null)
				return;
			draw(CEPartialModels.VALVE_STEM, be.getBlockState(), pose.intake(), ms, buffer, light);
			draw(CEPartialModels.VALVE_STEM, be.getBlockState(), pose.exhaust(), ms, buffer, light);
		}
	}
}
