package com.createengines.client;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.blueprint.FramedBlueprintBlockEntity;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.FrameSheet;
import com.createengines.engine.blueprint.PaperTurn;
import com.createengines.engine.sim.PlateFigures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * The engine's plan and the plate's figures on the paper of a framed blueprint. Laid out on the paper in text pixels, the
 * way {@link com.createengines.content.plate.MakerPlateRenderer} lays its text, then turned onto whichever face the frame
 * is on exactly as the blockstate turns the model. Client only.
 */
public class FramedBlueprintRenderer extends SafeBlockEntityRenderer<FramedBlueprintBlockEntity> {

	/**
	 * The paper is 1.2 pixels up in the modelled frame (and runs unbroken across joined frames, whose models have no rim
	 * between them). The drawing sits a clear step above it, and the text a step above that, so no two layers share a depth.
	 */
	private static final float FACE = 1.2f / 16 + 0.004f;
	/** Text pixels the lettering floats above the plan. */
	private static final float TEXT_LIFT = 0.7f;
	private static final float SCALE = 0.0085f;
	/** The paper is about 80 text pixels across. */
	private static final float HALF = 40f;
	/** Kept inside the paper (39 text pixels from its centre) so the title and figures are not cut off by the rim. */
	private static final float HALF_HEIGHT = 40f;

	private final Font font;

	public FramedBlueprintRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	@Override
	protected void renderSafe(FramedBlueprintBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
							  int overlay) {
		Blueprint blueprint = be.drawing();
		if (blueprint == null)
			return;
		// Frames joined into one sheet show the picture of its source frame across all of them; the others draw nothing.
		FrameSheet.Sheet sheet = be.sheet();
		if (sheet.source() == null || sheet.source().u() != 0 || sheet.source().v() != 0)
			return;
		Direction facing = be.getBlockState()
			.getValue(FramedBlueprintBlock.FACING);
		int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
		int y = facing.getAxis()
			.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
		Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotateY((float) -Math.toRadians(y))
			.rotateX((float) -Math.toRadians(x))
			.translate(-0.5f, -0.5f, -0.5f)
			.translate(0.5f, FACE, 0.5f)
			.rotateX((float) Math.toRadians(-90))
			.rotateZ((float) Math.PI)
			.scale(SCALE, -SCALE, SCALE);
		// One block is 1 / SCALE text pixels, and the sheet's centre is not the source frame's centre unless it is alone
		float pitch = 1f / SCALE;
		float cx = (sheet.minU() + (sheet.width() - 1) / 2f) * pitch;
		float cy = (sheet.minV() + (sheet.height() - 1) / 2f) * pitch;
		// A drawing turned on its side is laid out across the sheet as if the sheet were turned, then turned about its centre
		int turn = be.turn();
		float spanX = (PaperTurn.layoutWidth(sheet.width(), sheet.height(), turn) - 1) * pitch / 2f;
		float spanY = (PaperTurn.layoutHeight(sheet.width(), sheet.height(), turn) - 1) * pitch / 2f;
		float textScale = Math.max(1, Math.min(sheet.width(), sheet.height()));
		ms.pushPose();
		ms.last()
			.pose()
			.mul(pose);
		ms.last()
			.normal()
			.mul(new Matrix3f(pose).normal());
		if (turn != 0) {
			ms.translate(cx, cy, 0);
			ms.mulPose(new org.joml.Quaternionf().rotateZ(PaperTurn.radians(turn)));
			ms.translate(-cx, -cy, 0);
		}
		drawPlan(ms, buffer, blueprint, be.partsByHeight(), cx, cy, spanX + HALF, spanY + HALF_HEIGHT, textScale, 8f * Math.max(sheet.width(), sheet.height()));
		drawText(ms, buffer, blueprint, light, cx, cy, spanX + HALF, spanY + HALF_HEIGHT, textScale);
		ms.popPose();
	}

	/** The plan, as the drafting sheet draws it, between the name and the figures. */
	private void drawPlan(PoseStack ms, MultiBufferSource buffer, Blueprint blueprint, List<Blueprint.Part> byHeight, float cx, float cy, float halfW, float halfH,
						  float s, float maxCell) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (Blueprint.Part part : blueprint.parts()) {
			minX = Math.min(minX, part.x());
			maxX = Math.max(maxX, part.x());
			minZ = Math.min(minZ, part.z());
			maxZ = Math.max(maxZ, part.z());
			minY = Math.min(minY, part.y());
			maxY = Math.max(maxY, part.y());
		}
		float spanX = maxX - minX + 1;
		float spanZ = maxZ - minZ + 1;
		float areaW = halfW * 2 - 4;
		float areaH = halfH * 2 - 36f * s;
		float cell = Math.min(maxCell, Math.min(areaW / spanX, areaH / spanZ));
		float left = cx - spanX * cell / 2f;
		float top = cy - halfH + 14f * s + areaH / 2f - spanZ * cell / 2f;
		double range = Math.max(1, maxY - minY);
		VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
		Matrix4f pose = ms.last()
			.pose();
		for (Blueprint.Part part : byHeight) {
			float px = left + (part.x() - minX) * cell;
			float py = top + (part.z() - minZ) * cell;
			int colour = BlueprintScreen.shade(BlueprintScreen.colourOf(part.block()), 1 - (part.y() - minY) / range);
			quad(vc, pose, px, py, px + cell, py + cell, colour);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, int argb) {
		int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
		vc.addVertex(pose, x0, y0, 0).setColor(r, g, b, 255);
		vc.addVertex(pose, x1, y0, 0).setColor(r, g, b, 255);
		vc.addVertex(pose, x1, y1, 0).setColor(r, g, b, 255);
		vc.addVertex(pose, x0, y1, 0).setColor(r, g, b, 255);
	}

	private void drawText(PoseStack ms, MultiBufferSource buffer, Blueprint blueprint, int light, float cx, float cy, float halfW,
						  float halfH, float s) {
		ms.pushPose();
		ms.translate(0, 0, TEXT_LIFT);
		drawCentred(ms, buffer, blueprint.name(), cx, cy - halfH + 3 * s, halfW, s, light, 0xD7E6FA);
		PlateFigures figures = blueprint.figures();
		if (figures == null) {
			ms.popPose();
			return;
		}
		drawCentred(ms, buffer, MakerPlateBlockEntity.compact(figures.peakPower()) + " SU @ " + figures.powerRpm(), cx,
			cy + halfH - 18 * s, halfW, s, light, 0x9AFFA8);
		drawCentred(ms, buffer, MakerPlateBlockEntity.compact(figures.peakTorque()) + " per RPM @ " + figures.torqueRpm(), cx,
			cy + halfH - 9 * s, halfW, s, light, 0x9AE0FF);
		ms.popPose();
	}

	private void drawCentred(PoseStack ms, MultiBufferSource buffer, String text, float cx, float top, float halfW, float s, int light,
							 int colour) {
		if (text.isEmpty())
			return;
		float width = font.width(text) * s;
		float fit = width > halfW * 2 - 4 ? (halfW * 2 - 4) / width : 1f;
		float size = s * fit;
		ms.pushPose();
		ms.scale(size, size, 1f);
		font.drawInBatch(text, (cx - width * fit / 2f) / size, top / size, colour, false, ms.last()
			.pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
		ms.popPose();
	}
}
