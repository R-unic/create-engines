package com.createengines.content.display;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.battery.BatteryBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A Display Link set against a battery: its charge as a percentage or a progress bar, chosen in the link's settings like
 * the engine's other fill figures. A flat battery reads 0 and a full one 1.
 */
public class BatteryDisplaySource extends EngineFillDisplaySource {

	@Override
	protected Float getProgress(DisplayLinkContext context) {
		return fill(context.getSourceBlockEntity());
	}

	/** How full the block entity is if it is a battery, from 0 flat to 1 full; 0 for anything else. */
	public static float fill(@Nullable BlockEntity source) {
		return source instanceof BatteryBlockEntity battery ? Math.clamp(battery.chargeFraction(), 0f, 1f) : 0f;
	}
}
