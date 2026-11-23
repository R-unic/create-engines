package com.createengines.client;

import com.createengines.content.RenderRotation;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Tells {@link RenderRotation} how far Flywheel's clock (which turns Create's shafts in a shader) is from Create's animation
 * clock (which the engine parts are posed from), once a frame. It lives here because reading the level renderer's tick count is
 * client code and the visuals are common-package classes that a dedicated server must be able to load.
 */
public final class ShaftClock {

	private ShaftClock() {
	}

	public static void onRenderFrame(RenderFrameEvent.Pre event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		if (com.createengines.content.AnimationDiagnostic.ENABLED) {
			net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
			com.createengines.content.AnimationDiagnostic.camera(camera.getPosition(), new org.joml.Vector3f(camera.getLookVector()));
		}
		float partial = event.getPartialTick()
			.getGameTimeDeltaPartialTick(false);
		RenderRotation.setShaftTimeShift(minecraft.levelRenderer.ticks + partial - AnimationTickHolder.getRenderTime(minecraft.level));
	}
}
