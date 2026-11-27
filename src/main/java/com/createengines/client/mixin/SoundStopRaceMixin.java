package com.createengines.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.createengines.client.SoundStopRace;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Closes a race in Minecraft's {@code SoundEngine.stopAll}, which runs on every sound reload, "stop all sounds" and
 * shutdown. It asks the sound thread to stop each playing sound and then, without waiting, deletes the same sources
 * itself ({@code channelAccess.clear()}). When the deleting wins halfway through a stop, OpenAL is given a source that
 * is gone and the game logs {@code Stop: Invalid name parameter}. Mods whose sounds keep playing, as engines do, make it
 * likelier.
 *
 * <p>The requests do nothing that is needed: every one of these sounds got its handle from {@code ChannelAccess}, and
 * clearing that stops each source before deleting it. So the list they are made from is emptied first, and the rest of
 * the method is left as it is. {@code defaultRequire} is 0, so it quietly does not apply if the method changes; the
 * client audit fails then.
 */
@Mixin(SoundEngine.class)
public abstract class SoundStopRaceMixin {

	@Shadow
	private boolean loaded;

	@Shadow
	@Final
	public Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

	@Inject(method = "stopAll", at = @At("HEAD"))
	private void createEngines$leaveStoppingToTheCleanup(CallbackInfo callback) {
		if (loaded) {
			instanceToChannel.clear();
			SoundStopRace.closed();
		}
	}
}
