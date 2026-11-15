package com.createengines.content.plate;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * The name and the figures on the face of a plate. The text is laid out for a plate facing up, then turned onto the way
 * the plate faces exactly as the blockstate turns the model (the same turning as the rain cap's flap).
 */
public class MakerPlateRenderer extends SafeBlockEntityRenderer<MakerPlateBlockEntity> {

	/** Where the face of the plate is, in the modelled frame, in blocks. */
	private static final float FACE = 1.5f / 16 + 0.002f;
	private static final float SCALE = 0.0085f;

	private final Font font;

	public MakerPlateRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	protected void renderSafe(MakerPlateBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
							  int light, int overlay) {
		String name = be.name();
		boolean stamped = be.figures()
			.stamped();
		if (name.isEmpty() && !stamped)
			return;
		Direction facing = be.getBlockState()
			.getValue(MakerPlateBlock.FACING);
		int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
		int y = facing.getAxis()
			.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
		Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotateY((float) -Math.toRadians(y))
			.rotateX((float) -Math.toRadians(x))
			.translate(-0.5f, -0.5f, -0.5f)
			// Lay the text flat on the face, the right way up for someone reading it from the plate's south edge
			.translate(0.5f, FACE, 0.5f)
			.rotateX((float) Math.toRadians(-90))
			.rotateZ((float) Math.PI)
			.scale(SCALE, -SCALE, SCALE);
		ms.pushPose();
		ms.last()
			.pose()
			.mul(pose);
		ms.last()
			.normal()
			.mul(new Matrix3f(pose).normal());
		String line2 = stamped ? MakerPlateBlockEntity.compact(be.figures()
			.peakPower()) + " SU @ " + be.figures()
			.powerRpm() : be.builder();
		drawCentred(ms, buffer, name.isEmpty() ? be.builder() : name, -9, light);
		if (!line2.isEmpty() && !name.isEmpty() || stamped)
			drawCentred(ms, buffer, line2, 2, light);
		ms.popPose();
	}

	/** Widest a line may be on the plate, in text pixels: the width inside the rim; longer lines are shrunk to fit. */
	private static final float ROOM = 74f;

	private void drawCentred(PoseStack ms, MultiBufferSource buffer, String text, int y, int light) {
		float width = font.width(text);
		float fit = width > ROOM ? ROOM / width : 1f;
		ms.pushPose();
		ms.scale(fit, fit, 1f);
		font.drawInBatch(text, -width / 2f, y / fit, 0x2B1B05, false, ms.last()
			.pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
		ms.popPose();
	}
}
