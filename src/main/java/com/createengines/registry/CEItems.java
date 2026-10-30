package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.tool.TimingToolItem;
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

	/** A lighter, harder piston that drags less: a precision part for a cylinder. */
	public static final ItemEntry<Item> FORGED_PISTON = REGISTRATE.item("forged_piston", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/forged_piston")))
		.register();

	/** A common-rail injector that atomises the fuel finely: a precision part for a head. */
	public static final ItemEntry<Item> PRECISION_INJECTOR = REGISTRATE.item("precision_injector", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/precision_injector_item")))
		.register();

	/** Sets an engine's throws and cam lobes to the best firing order its layout allows. */
	public static final ItemEntry<TimingToolItem> TIMING_TOOL = REGISTRATE.item("timing_tool", TimingToolItem::new)
		.properties(p -> p.stacksTo(1))
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/timing_tool/item")))
		.register();

	private CEItems() {
	}

	public static void register() {
	}
}
