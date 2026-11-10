package com.createengines.content.turbo;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.createengines.engine.sim.Thermal;
import com.createengines.registry.CEPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The turbine and compressor wheels, turning at the speed the engine last reported. The housings are the block model;
 * the wheels are modelled along +Y like the engine's other spinning parts and turned onto the shaft's axis.
 */
public class TurbochargerRenderer extends SafeBlockEntityRenderer<TurbochargerBlockEntity> {

	public TurbochargerRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(TurbochargerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
							  int light, int overlay) {
		BlockState state = be.getBlockState();
		var facing = state.getValue(TurbochargerBlock.FACING);
		// Modelled with the compressor up: spin about that axis, then turn it to face the way the block does
		Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(facing.step())))
			.rotateY((float) Math.toRadians(be.wheelAngle(partialTicks)))
			.translate(-0.5f, -0.5f, -0.5f);
		ms.pushPose();
		ms.last()
			.pose()
			.mul(pose);
		ms.last()
			.normal()
			.mul(new Matrix3f(pose).normal());
		// A hot turbine wheel glows a dull red, and keeps glowing for a while after the engine stops: it is the part
		// sitting in the exhaust. The compressor side stays dark metal.
		float glow = be.status() == null || !be.status()
			.turbine() ? 0 : (float) Thermal.glow(be.status()
				.heat());
		int lit = light;
		int tint = 0xFFFFFF;
		if (glow > 0.02f) {
			int block = Math.max(LightTexture.block(light), Math.round(glow * 11));
			lit = LightTexture.pack(block, LightTexture.sky(light));
			int green = Math.round(255 - 125 * glow);
			int blue = Math.round(255 - 175 * glow);
			tint = 0xFF << 16 | green << 8 | blue;
		}
		CachedBuffers.partial(CEPartialModels.TURBO_WHEEL, state)
			.light(lit)
			.color(tint)
			.renderInto(ms, buffer.getBuffer(RenderType.solid()));
		ms.popPose();
	}
}
