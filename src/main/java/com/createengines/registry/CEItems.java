package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.world.item.Item;

/** Parts that are installed into blocks rather than placed: they are part of the engine but smaller than a block. */
public final class CEItems {

	public static final ItemEntry<Item> PISTON = REGISTRATE.item("piston", Item::new)
		.lang("Engine Piston")
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/piston")))
		.register();

	public static final ItemEntry<Item> CONNECTING_ROD = REGISTRATE.item("connecting_rod", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/rod_item")))
		.register();

	public static final ItemEntry<Item> FUEL_INJECTOR = REGISTRATE.item("fuel_injector", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/injector_item")))
		.register();

	public static final ItemEntry<Item> SPARK_PLUG = REGISTRATE.item("spark_plug", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/plug_item")))
		.register();

	private CEItems() {
	}

	public static void register() {
	}
}
