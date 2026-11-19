package com.createengines.compat.sable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable (mod id {@value #MOD_ID}), the physics engine under Create Aeronautics: a moving structure is kept as real blocks in a
 * "plot" far from where it is drawn and where it is, so a block's own coordinates say nothing about where it is in the world.
 * Anything that asks "where is this engine" for the air, the sky or the sea has to project the position out of the plot first.
 * Optional and one way. Sable's types are named only in {@link SableBridge}, which is never loaded without the mod.
 */
public final class SableCompat {

	public static final String MOD_ID = "sable";

	private static final Logger LOGGER = LogUtils.getLogger();

	private static boolean broken;

	private SableCompat() {
	}

	public static boolean installed() {
		return !broken && ModList.get() != null && ModList.get()
			.isLoaded(MOD_ID);
	}

	/** Where {@code pos} really is in the world: itself, unless a moving structure holds it. A failure turns this off. */
	public static Vec3 worldPosition(Level level, BlockPos pos) {
		Vec3 centre = Vec3.atCenterOf(pos);
		if (!installed())
			return centre;
		try {
			return SableBridge.project(level, centre);
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Sable did not answer where a block is; engines will use the block's own height this session", e);
			return centre;
		}
	}

	/** The height in the world of the block at {@code pos}, which is {@code pos.getY()} off a moving structure. */
	public static double worldY(Level level, BlockPos pos) {
		return worldPosition(level, pos).y;
	}
}
