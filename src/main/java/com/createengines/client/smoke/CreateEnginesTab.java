package com.createengines.client.smoke;

import java.util.HashSet;
import java.util.Set;

import com.createengines.registry.CECreativeTabs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The items this mod's creative tab currently lists. */
final class CreateEnginesTab {

	private CreateEnginesTab() {
	}

	static Set<Item> items() {
		Set<Item> items = new HashSet<>();
		for (ItemStack stack : CECreativeTabs.MAIN.get()
			.getDisplayItems())
			items.add(stack.getItem());
		return items;
	}
}
