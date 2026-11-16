package com.createengines.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.createengines.content.refinery.BasinHints;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.network.chat.Component;

/**
 * Adds one line to Create's Basin goggles when a refining column would make better fuel from what the basin holds.
 * It is a single injection at the end of a Create method and it must never break the basin: {@code require = 0}
 * lets it quietly not apply if a later Create changes the method, and the helper swallows its own errors.
 */
@Mixin(value = BasinBlockEntity.class, remap = false)
public abstract class BasinGoggleMixin {

	@Inject(method = "addToGoggleTooltip(Ljava/util/List;Z)Z", at = @At("RETURN"), require = 0)
	private void createEngines$columnHint(List<Component> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
		BasinHints.addTo((BasinBlockEntity) (Object) this, tooltip);
	}
}
