package com.createengines.compat.curios;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * Curios (mod id {@value #MOD_ID}): the headphones fit its head slot through the {@code curios:head} item tag (a data file,
 * which needs no code) and are counted as worn there. Optional and one way. Curios' types are named only in
 * {@link CuriosBridge} (and the renderer in {@code client.compat}), which are never loaded without the mod.
 */
public final class CuriosCompat {

	public static final String MOD_ID = "curios";

	private static final Logger LOGGER = LogUtils.getLogger();

	private static boolean broken;

	private CuriosCompat() {
	}

	public static boolean installed() {
		return !broken && ModList.get() != null && ModList.get()
			.isLoaded(MOD_ID);
	}

	/** Whether {@code wearer} has {@code item} in any Curios slot. A failure turns this off for the session. */
	public static boolean wears(LivingEntity wearer, Item item) {
		if (!installed())
			return false;
		try {
			return CuriosBridge.wears(wearer, item);
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Curios did not answer what is worn; the headphones will count only in the head slot this session", e);
			return false;
		}
	}
}
