package com.createengines.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.createengines.client.GoggleOverlayLayout;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;

/**
 * Keeps Create's goggle readout clear of the hunger bar and air bubbles. Create clamps the readout's top with the second
 * {@code Math.min} of {@code renderOverlay} (the first clamps x); that call is swapped for one with a higher bottom
 * margin. {@code require = 0} lets it quietly not apply if a later Create changes the method.
 */
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleOverlayMixin {

	@Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1),
		require = 0, remap = false)
	private static int createEngines$clearStatusBars(int y, int createLimit) {
		return GoggleOverlayLayout.clampTop(y, createLimit);
	}
}
