package com.createengines.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.createengines.advancement.LegacyProgress;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;

/**
 * Carries a player's progress on the 1.x advancement IDs over to the 2.0 ones. Vanilla throws away saved advancements it
 * does not know, so the only chance to read them is while the player's file is being loaded: just after the known ones are
 * applied and before the listeners for what is left are registered, so what is credited here is part of the first sync and
 * is neither announced nor rewarded.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

	@Shadow
	@Final
	private Path playerSavePath;

	@Inject(method = "load", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/PlayerAdvancements;checkForAutomaticTriggers(Lnet/minecraft/server/ServerAdvancementManager;)V"),
		require = 1)
	private void create_engines$carryOverLegacyProgress(ServerAdvancementManager manager, CallbackInfo ci) {
		LegacyProgress.carryOver(playerSavePath, manager, (PlayerAdvancements) (Object) this);
	}
}
