package com.createengines.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** The one place that names Curios' API: loaded only once {@link CuriosCompat#installed()} has said the mod is there. */
final class CuriosBridge {

	private CuriosBridge() {
	}

	static boolean wears(LivingEntity wearer, Item item) {
		return CuriosApi.getCuriosInventory(wearer)
			.map(inventory -> inventory.isEquipped(item))
			.orElse(false);
	}
}
