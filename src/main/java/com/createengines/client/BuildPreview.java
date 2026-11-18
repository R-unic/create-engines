package com.createengines.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.blueprint.BlueprintTurner;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Shows where a blueprint would go before the Bank Builder lays it: with a Bank Builder in one hand and a recorded
 * blueprint in the other, the engine's blocks are drawn as outlines at the face the player is looking at, green where the
 * space is free and red where something is in the way (the same cells the server would refuse for). A drawing only: it
 * places nothing, sends nothing and changes no block, and it checks nothing the server does not check again.
 */
public final class BuildPreview {

	/** Most outlines drawn, so a huge blueprint cannot slow the frame. The nearest to the origin are kept. */
	public static final int MAX_DRAWN = 1500;

	private static String cachedText = "";
	private static Rotation cachedRotation = Rotation.NONE;
	private static @Nullable Blueprint cached;

	private BuildPreview() {
	}

	public static void onRender(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return;
		if (!(minecraft.player.getMainHandItem()
			.getItem() instanceof BankBuilderItem))
			return;
		RecordedBlueprint held = BlueprintItem.recorded(minecraft.player.getOffhandItem());
		Blueprint blueprint = held == null ? null : decode(held.text(), BlueprintTurner.forFacing(minecraft.player.getDirection()));
		if (blueprint == null)
			return;

		Level level = minecraft.level;
		BlockPos origin = BlueprintBuilder.originOn(blueprint, hit.getBlockPos(), hit.getDirection());
		Vec3 camera = event.getCamera()
			.getPosition();
		PoseStack pose = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers()
			.bufferSource();
		List<AABB> clear = new ArrayList<>();
		List<AABB> blocked = new ArrayList<>();
		for (BlockPos pos : cells(blueprint, origin)) {
			BlockState there = level.getBlockState(pos);
			boolean free = there.isAir() || there.canBeReplaced();
			(free ? clear : blocked).add(new AABB(pos).inflate(-0.02)
				.move(-camera.x, -camera.y, -camera.z));
		}
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		for (AABB box : clear)
			LevelRenderer.renderLineBox(pose, lines, box, 0.35f, 0.9f, 0.45f, 0.9f);
		buffers.endBatch(RenderType.lines());
		if (!blocked.isEmpty()) {
			// Drawn through the terrain: a cell inside the ground is inside a solid block and would be hidden otherwise
			RenderSystem.disableDepthTest();
			lines = buffers.getBuffer(RenderType.lines());
			for (AABB box : blocked)
				LevelRenderer.renderLineBox(pose, lines, box, 1f, 0.2f, 0.2f, 0.9f);
			buffers.endBatch(RenderType.lines());
			RenderSystem.enableDepthTest();
		}
	}

	/** The cells the blueprint would fill with its origin at {@code origin}, at most {@link #MAX_DRAWN} of them. */
	public static List<BlockPos> cells(Blueprint blueprint, BlockPos origin) {
		List<BlockPos> cells = new ArrayList<>(Math.min(blueprint.parts()
			.size(), MAX_DRAWN));
		for (Blueprint.Part part : blueprint.parts()) {
			if (cells.size() >= MAX_DRAWN)
				break;
			cells.add(origin.offset(part.x(), part.y(), part.z()));
		}
		return cells;
	}

	/**
	 * The decoded blueprint turned the way the build would turn it, kept until the text or the turn changes. Null if it does
	 * not decode or cannot be turned (the server would refuse it too).
	 */
	private static @Nullable Blueprint decode(String text, Rotation rotation) {
		if (text.equals(cachedText) && rotation == cachedRotation)
			return cached;
		cachedText = text;
		cachedRotation = rotation;
		try {
			Blueprint plain = BlueprintCodec.decode(text);
			cached = BlueprintTurner.turn(plain, rotation);
		} catch (BlueprintException e) {
			cached = null;
		}
		return cached;
	}
}
