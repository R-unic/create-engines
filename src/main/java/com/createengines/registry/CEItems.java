package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.headphones.HeadphonesItem;
import com.createengines.content.jerrycan.JerryCanItem;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.world.item.Item;

/** Parts that are installed into blocks rather than placed: they are part of the engine but smaller than a block. */
public final class CEItems {

	public static final ItemEntry<Item> PISTON = REGISTRATE.item("piston", p -> (Item) new com.createengines.content.WornPartItem(p))
		.lang("Engine Piston")
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/piston")))
		.register();

	public static final ItemEntry<Item> CONNECTING_ROD = REGISTRATE.item("connecting_rod", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/rod_item")))
		.register();

	public static final ItemEntry<Item> FUEL_INJECTOR = REGISTRATE.item("fuel_injector", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/injector_item")))
		.register();

	public static final ItemEntry<Item> SPARK_PLUG = REGISTRATE.item("spark_plug", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/plug_item")))
		.register();

	/** A lighter, harder piston that drags less: a precision part for a cylinder. */
	public static final ItemEntry<Item> FORGED_PISTON = REGISTRATE.item("forged_piston", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder/forged_piston")))
		.register();

	/** A common-rail injector that atomises the fuel finely: a precision part for a head. */
	public static final ItemEntry<Item> PRECISION_INJECTOR = REGISTRATE.item("precision_injector", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/precision_injector_item")))
		.register();

	/**
	 * An electric pump and driver that sits on a head's injector and spends battery charge to atomise each shot more finely and
	 * pass a little more fuel at the limit. A bonus on top of an injector: it does nothing without one, and without charge the
	 * injector runs exactly as it would have.
	 */
	public static final ItemEntry<Item> INJECTOR_ASSIST = REGISTRATE.item("injector_assist", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/assist_item")))
		.register();

	/**
	 * A high-energy coil that sits on a head's spark plug and spends extra battery charge on a hotter spark, which lights a leaner
	 * mixture and burns through more soot. A bonus on top of a plug: it does nothing without one, and without charge the plug sparks
	 * exactly as it would have.
	 */
	public static final ItemEntry<Item> IGNITION_COIL = REGISTRATE.item("ignition_coil", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/cylinder_head/coil_item")))
		.register();

	/**
	 * Wear mode: a head gasket in three tiers (see {@code Wear.Gasket}), fitted by right-clicking a head. A better gasket lasts longer and takes
	 * more boost. With the mode off a fitted one does nothing, and every head has the stock gasket, which is not an item.
	 */
	/** The plainest gasket, the one a head is read as holding when none is fitted; fitting one explicitly puts a new one in. */
	public static final ItemEntry<Item> STOCK_GASKET = REGISTRATE.item("stock_gasket", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.generated(c, p.modLoc("item/stock_gasket")))
		.register();

	public static final ItemEntry<Item> CARDBOARD_GASKET = REGISTRATE.item("cardboard_gasket", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.generated(c, p.modLoc("item/cardboard_gasket")))
		.register();

	public static final ItemEntry<Item> RUBBER_GASKET = REGISTRATE.item("rubber_gasket", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.generated(c, p.modLoc("item/rubber_gasket")))
		.register();

	public static final ItemEntry<Item> COPPER_GASKET = REGISTRATE.item("copper_gasket", p -> (Item) new com.createengines.content.WornPartItem(p))
		.model((c, p) -> p.generated(c, p.modLoc("item/copper_gasket")))
		.register();

	/** A Rubber Gasket part way through its Create sequenced assembly into a Copper Gasket (sheet deployed, pressed, sheet deployed, pressed). */
	public static final ItemEntry<Item> INCOMPLETE_COPPER_GASKET = REGISTRATE.item("incomplete_copper_gasket",
			p -> (Item) new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(p))
		.model((c, p) -> p.generated(c, p.modLoc("item/copper_gasket")))
		.register();

	/** Wear mode: right-click a cylinder or a head to set its piston, rod or gasket right when the wear is light. Heavy wear needs the part replaced. */
	public static final ItemEntry<Item> SERVICE_KIT = REGISTRATE.item("service_kit", p -> (Item) new com.createengines.content.ServiceKitItem(p))
		.properties(p -> p.durability(com.createengines.content.WearService.KIT_USES))
		.model((c, p) -> p.generated(c, p.modLoc("item/service_kit")))
		.register();

	/**
	 * Wear mode: a fresh set of apex seals for a rotor housing, fitted by right-clicking it. It replaces the set there, worn or failed. With the
	 * mode off a fitted set does nothing and a housing needs none: the stock set is not an item.
	 */
	public static final ItemEntry<Item> APEX_SEALS = REGISTRATE.item("apex_seals", Item::new)
		.model((c, p) -> p.generated(c, p.modLoc("item/apex_seals")))
		.register();

	/** The triangular rotor of a Wankel engine, which goes in a rotor housing. */
	public static final ItemEntry<Item> ROTOR = REGISTRATE.item("rotor", Item::new)
		.lang("Engine Rotor")
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/rotor_housing/rotor_item")))
		.register();

	/** Fitted in an oil pan: the oil ages more slowly and fuel is cleaned out of it faster. Nothing wears out. */
	public static final ItemEntry<Item> OIL_FILTER = REGISTRATE.item("oil_filter", Item::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/sump/filter_item")))
		.register();

	/** Sets an engine's throws and cam lobes to the best firing order its layout allows. */
	public static final ItemEntry<TimingToolItem> TIMING_TOOL = REGISTRATE.item("timing_tool", TimingToolItem::new)
		.properties(p -> p.stacksTo(1))
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/timing_tool/item")))
		.register();

	/** Lays out a bank of cylinders, inline, V or flat, in one click. */
	public static final ItemEntry<BankBuilderItem> BANK_BUILDER =
		REGISTRATE.item("bank_builder", BankBuilderItem::new)
			.properties(p -> p.stacksTo(1))
			.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/bank_builder/item")))
			.register();

	/** Soot caught from a rich engine's exhaust: it is ground into black dye. */
	public static final ItemEntry<Item> SOOT = REGISTRATE.item("soot", Item::new)
		.model((c, p) -> p.generated(c, p.modLoc("item/soot")))
		.register();

	/** Four buckets of one fluid in one item, for carrying fuel where there is no pipe. */
	public static final ItemEntry<JerryCanItem> JERRY_CAN = REGISTRATE.item("jerry_can", JerryCanItem::new)
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/jerry_can/item")))
		.register();

	/** Records an engine's layout and tune when used on it. */
	public static final ItemEntry<BlueprintItem> BLUEPRINT = REGISTRATE.item("blueprint", BlueprintItem::new)
		.properties(p -> p.stacksTo(1))
		.model((c, p) -> p.generated(c, p.modLoc("item/blueprint")))
		.register();

	/**
	 * Worn on the head, or in a Curios head slot, they muffle every sound from the world for the wearer (not music, menus or
	 * voices), never silence it. The muffling is client side: see client.HeadphoneMuffle.
	 */
	public static final ItemEntry<HeadphonesItem> HEADPHONES = REGISTRATE.item("headphones", HeadphonesItem::new)
		.properties(p -> p.stacksTo(1))
		.lang("Noise-Cancelling Headphones")
		.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/headphones/item")))
		.register();

	private CEItems() {
	}

	public static void register() {
	}
}
