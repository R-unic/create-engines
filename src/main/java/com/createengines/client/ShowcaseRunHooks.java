package com.createengines.client;

import static com.createengines.client.ShowcaseRun.*;
import static com.createengines.client.ShowcaseRunBuild.*;
import static com.createengines.client.ShowcaseRunCamera.*;

import java.util.Locale;

import static com.createengines.client.ShowcaseRunShots.NO_TIME;

import com.createengines.CreateEngines;
import com.createengines.client.ShowcaseRunShots.Shot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** The event handlers that keep a trailer run's picture and sound to the shot being filmed. Part of {@link ShowcaseRun}, and like it kept out of the jar. */
final class ShowcaseRunHooks {

	private ShowcaseRunHooks() {
	}

	static boolean onPad(int bay, double x, double z) {
		BlockPos pad = pad(bay);
		return x >= pad.getX() + PAD_X0 && x <= pad.getX() + PAD_X1 + 1
			&& z >= pad.getZ() + PAD_Z0 && z <= pad.getZ() + PAD_Z1 + 1;
	}

	/**
	 * The gameplay clips keep the player's hand and the item in it, but not the hotbar, the crosshair or the name of the item
	 * picked: they read as a game's interface over the footage and the tall cut of the trailer shows them.
	 */
	static void onRenderGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
		var name = event.getName();
		if (name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR)
			|| name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)
			|| name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME))
			event.setCanceled(true);
	}

	/**
	 * The trailer is filmed with one lens, whatever the player's setting or the flying camera's widening would make it, so the
	 * framing's sums hold. The hand in the first-person clips keeps its own.
	 */
	static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
		if (event.usedConfiguredFov() && trailerStart >= 0)
			event.setFOV(ShowcaseRunFraming.FOV);
	}

	/** A sound from another pad never starts: the engines of the other courtyards are not heard in this one. */
	static void onPlaySound(PlaySoundEvent event) {
		SoundInstance sound = event.getSound();
		if (sound == null)
			return;
		String path = sound.getLocation()
			.getPath();
		// The gameplay clips earn real advancements: neither this mod's sting nor the game's toast sounds are heard over the footage
		if (sound instanceof com.createengines.content.StingSound || path.startsWith("ui.toast") || path.contains("sting")) {
			CreateEngines.LOGGER.info("Showcase: dropped the sound {} (a toast or sting over the footage)", path);
			event.setSound(null);
		} else if (!inCurrentPad(sound))
			event.setSound(null);
		else
			HEARD.add(sound);
	}

	/** Stops the sounds already playing from a pad the camera has left. */
	static void sweepSounds() {
		var manager = Minecraft.getInstance()
			.getSoundManager();
		HEARD.removeIf(sound -> {
			if (inCurrentPad(sound))
				return !manager.isActive(sound);
			manager.stop(sound);
			return true;
		});
	}

	/**
	 * Every frame, not every tick, so the movement is smooth: the camera is the player, put where the shot says with no
	 * old position to blend from. The server is told every few ticks so the world around the camera stays loaded.
	 */
	static void onRenderFrame(RenderFrameEvent.Pre event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (syncPending && minecraft.player != null && minecraft.screen == null) {
			syncPending = false;
			minecraft.setScreen(new SyncFlash());
		}
		if (minecraft.player != null) {
			// Recipe and advancement toasts, and the chat's lines about them, never show over the footage
			minecraft.getToasts()
				.clear();
			minecraft.gui.getChat()
				.clearMessages(false);
		}
		if (trailerStart < 0 || trailerOver || minecraft.player == null)
			return;
		double elapsed = (System.nanoTime() - trailerStart) / 1e9;
		double begins = 0;
		for (int i = 0; i < SHOTS.size(); i++) {
			Shot shot = SHOTS.get(i);
			if (elapsed >= begins + shot.seconds()) {
				begins += shot.seconds();
				continue;
			}
			if (i != trailerShot) {
				trailerShot = i;
				trailerStill = false;
				if (shot.time() != NO_TIME)
					server(server -> command(server, "time set " + shot.time()));
				CreateEngines.LOGGER.info("Showcase trailer shot {} {}", i + 1, shot.name());
				FIRED.clear();
				startShot(shot, minecraft);
				sweepSounds();
			}
			for (Cue cue : CUES)
				if (cue.shot().equals(shot.name()) && elapsed - begins >= cue.at() && FIRED.add(cue))
					cue.action().run();
			double t = (elapsed - begins) / shot.seconds();
			double[] pose = pose(shot, t);
			trailerPose = pose;
			LocalPlayer player = minecraft.player;
			double feetY = pose[1] - 1.62;
			player.setPos(pose[0], feetY, pose[2]);
			player.xo = player.xOld = pose[0];
			player.yo = player.yOld = feetY;
			player.zo = player.zOld = pose[2];
			player.setDeltaMovement(0, 0, 0);
			player.setYRot((float) pose[3]);
			player.setXRot((float) pose[4]);
			player.yRotO = (float) pose[3];
			player.xRotO = (float) pose[4];
			// One still from the middle of every shot, to check the framing without watching the whole thing
			if (!trailerStill && t >= 0.5) {
				trailerStill = true;
				shot(String.format(Locale.ROOT, "trailer_%02d_%s", i + 1, shot.name()));
			}
			return;
		}
		// Hold the last frame for a moment, then end the run
		if (elapsed > begins + 1.5) {
			trailerOver = true;
			logEngines("after the trailer");
			minecraft.stop();
		}
	}
}
