package com.createengines.content.raincap;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.createengines.client.RainCaps;
import com.createengines.registry.CEPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * The flap of a rain cap. The flap is modelled for a cap that faces up, hinged along its south edge, and turned onto the
 * way the cap faces exactly as the blockstate turns the hood, so the two stay together; then it is lifted about the hinge
 * by how far the last exhaust pulses have kicked it open.
 */
public class RainCapRenderer extends SafeBlockEntityRenderer<RainCapBlockEntity> {

	/** The hinge in the modelled frame, in blocks: along the south edge of the flap, on the pipe's rim. */
	private static final float HINGE_Y = 0.5f / 16;
	private static final float HINGE_Z = 12.5f / 16;

	public RainCapRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(RainCapBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
							  int overlay) {
		Direction facing = be.getBlockState()
			.getValue(RainCapBlock.FACING);
		// What the blockstate does to a model that faces up: x rotation first, then y (clockwise, so negative here)
		int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
		int y = facing.getAxis()
			.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
		double now = be.getLevel()
			.getGameTime() + partialTicks;
		float lift = (float) Math.toRadians(RainCaps.degrees(be.getBlockPos(), now));
		Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotateY((float) -Math.toRadians(y))
			.rotateX((float) -Math.toRadians(x))
			.translate(-0.5f, -0.5f, -0.5f)
			.translate(0, HINGE_Y, HINGE_Z)
			.rotateX(lift)
			.translate(0, -HINGE_Y, -HINGE_Z);
		ms.pushPose();
		ms.last()
			.pose()
			.mul(pose);
		ms.last()
			.normal()
			.mul(new Matrix3f(pose).normal());
		CachedBuffers.partial(CEPartialModels.RAIN_CAP_FLAP, be.getBlockState())
			.light(light)
			.renderInto(ms, buffer.getBuffer(RenderType.solid()));
		ms.popPose();
	}
}
