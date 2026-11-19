package com.createengines.advancement;

import static com.createengines.CreateEngines.REGISTRATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateAdvancementProvider;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * The advancement tree, written once as a table: datagen turns each row into an advancement and its lang entries.
 * <p>
 * From 2.0 every row is in the tab and the ID is the track and the row, {@code builder/vee_engine}. The 1.x IDs, shown and
 * hidden, are not defined any more; {@link AdvancementMigration} carries a player's old progress onto these rows.
 * <p>
 * A part is rewarded for what it does, not for being crafted: it gets a {@link Milestone} that is fired where it happens,
 * and a row that waits for it ({@link #on}). The "obtained" row ({@link #has}) is kept for the two recipes every engine
 * needs before anything can run. An advancement only goes in where it marks something worth being proud of.
 */
public final class CEAdvancements {

	private static final ResourceLocation BACKGROUND =
		CreateEngines.asResource("textures/gui/advancements/tread_plate.png");

	/** The root of the tab. */
	public static final String ROOT = "root";

	/**
	 * @param criterion the one thing a row waits for; null for a capstone, which waits for every milestone in
	 *                  {@code requires} instead
	 */
	private record Row(String id, @Nullable String parent, Supplier<ItemLike> icon, AdvancementType type,
					   @Nullable Supplier<Criterion<?>> criterion, List<Milestone> requires, String title,
					   String description) {
	}

	private static final List<Row> ROWS = new ArrayList<>();

	/**
	 * Rows that are toast only: small stepping stones and minor discoveries, which would fill chat in a new player's first
	 * ten minutes. Every other row also announces in chat.
	 */
	private static final Set<String> QUIET = Set.of("first_light/crankshaft", "first_light/cylinder", "first_light/starter",
		"first_light/warmed_up", "first_light/sump", "builder/camshaft", "builder/pipework", "builder/bank_builder",
		"builder/engine_casing", "power/blow_off", "fuel/thinned_oil", "fuel/fouled_plug", "tuning/engine_display",
		"driveline/alternator", "driveline/flywheel", "service/preheated", "fuel/spark_plug", "driveline/transmission");

	/** What the Chief Engineer capstone hands over: an engraved Maker's Plate. */
	public static final ResourceKey<LootTable> CHIEF_ENGINEER_REWARD =
		ResourceKey.create(Registries.LOOT_TABLE, CreateEngines.asResource("advancement/chief_engineer"));

	static {
		// The root: everyone gets it, and it names the tab
		row(ROOT, null, () -> CEBlocks.CRANK_THROW.get(), AdvancementType.TASK,
			() -> CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty())),
			"Create: Engine Engineer", "Burn something on purpose and put the torque to work.");

		// First Light: the first ten minutes, a rung every few
		has("first_light/crankshaft", ROOT, () -> CEBlocks.CRANKSHAFT.get(), "Bottom End",
			"Craft a Crankshaft, the spine every engine is built on.");
		has("first_light/cylinder", "first_light/crankshaft", () -> CEBlocks.CYLINDER.get(), "Bore Ambition",
			"Craft a Cylinder, then add a piston and connecting rod.");
		on("first_light/starter", "first_light/cylinder", () -> CEBlocks.STARTER.get(), AdvancementType.TASK,
			Milestone.CRANKING, "Turn the Key", "Crank an engine over with a Starter.");
		on("first_light/alive", "first_light/starter", () -> CEBlocks.CYLINDER.get(), AdvancementType.GOAL,
			Milestone.ENGINE_RUNNING, "It's Alive!", "Get an engine running on its own power.");
		on("first_light/warmed_up", "first_light/alive", () -> CEBlocks.CYLINDER.get(), AdvancementType.TASK,
			Milestone.WARMED_UP, "Operating Temperature",
			"Run an engine until its block has warmed through. A cold engine drags until it has.");
		on("first_light/sump", "first_light/alive", () -> CEBlocks.SUMP.get(), AdvancementType.TASK,
			Milestone.SUMP_OILING, "Oil Pan",
			"Run an engine kept oiled from an Oil Sump set against its crankshaft or head.");
		on("first_light/stalled", "first_light/alive", () -> CEBlocks.STARTER.get(), AdvancementType.TASK,
			Milestone.STALLED, "Stall Out", "Let a turning engine die.");
		on("first_light/flooded", "first_light/starter", () -> CEBlocks.AIR_INTAKE.get(), AdvancementType.TASK,
			Milestone.FLOODED, "That's Not Air",
			"Crank an engine that sits underwater, or opens its air intake or exhaust outlet there. Nothing burns down there.");

		// Builder: layouts
		on("builder/camshaft", "first_light/cylinder", () -> CEBlocks.CAMSHAFT.get(), AdvancementType.TASK,
			Milestone.CAMSHAFT_TIMED, "Timing Is Everything",
			"Run an engine whose valves are opened by a Camshaft, geared to half the crank's speed.");
		on("builder/pipework", "builder/camshaft", () -> CEBlocks.INTAKE_MANIFOLD.get(), AdvancementType.TASK,
			Milestone.PIPED_ENGINE, "Breathing Room",
			"Run an engine that takes air in and lets exhaust out through piped manifolds.");
		on("builder/four_cylinders", "first_light/alive", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.TASK,
			Milestone.FOUR_CYLINDERS, "Four on the Floor", "Run an engine with four firing cylinders.");
		on("builder/eight_cylinders", "builder/four_cylinders", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.GOAL,
			Milestone.EIGHT_CYLINDERS, "Eight Is Enough", "Run an engine with eight firing cylinders.");
		on("builder/twelve_cylinders", "builder/eight_cylinders", () -> CEBlocks.CRANK_THROW.get(),
			AdvancementType.CHALLENGE, Milestone.TWELVE_CYLINDERS, "Twelve Angry Pistons",
			"Run an engine with twelve firing cylinders.");
		on("builder/sixteen_cylinders", "builder/twelve_cylinders", () -> CEBlocks.CRANK_THROW.get(),
			AdvancementType.CHALLENGE, Milestone.SIXTEEN_CYLINDERS, "Hypercar Money",
			"Run an engine with sixteen firing cylinders.");
		on("builder/coupled_cranks", "builder/four_cylinders", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.GOAL,
			Milestone.COUPLED_CRANKS, "In Step",
			"Run an engine of four or more cylinders whose crankshafts are geared together one to one, so they turn as one.");
		on("builder/vee_engine", "builder/four_cylinders", () -> CEItems.BANK_BUILDER.get(), AdvancementType.GOAL,
			Milestone.VEE_ENGINE, "Vee for Victory",
			"Run an engine of four or more cylinders with two banks on the same crank throws, a quarter turn apart.");
		on("builder/flat_engine", "builder/four_cylinders", () -> CEItems.BANK_BUILDER.get(), AdvancementType.GOAL,
			Milestone.FLAT_ENGINE, "Boxer Rebellion",
			"Run an engine of four or more cylinders with opposed banks on the same crank throws.");
		on("builder/valley_cam", "builder/camshaft", () -> CEBlocks.CAM_LOBE.get(), AdvancementType.GOAL,
			Milestone.VALLEY_CAM, "Cam in the Valley",
			"Run an engine where one cam lobe times cylinders on different banks of the same crank throw.");
		on("builder/rotary_engine", "first_light/alive", () -> CEItems.ROTOR.get(), AdvancementType.GOAL,
			Milestone.ROTARY_ENGINE, "Valve? What Valve?",
			"Run an engine on an Engine Rotor, which turns on its eccentric at a third of the shaft's speed and has neither valves nor a camshaft. It needs a spark plug.");
		on("builder/bank_builder", "first_light/crankshaft", () -> CEItems.BANK_BUILDER.get(), AdvancementType.TASK,
			Milestone.BANK_BUILT, "Assembly Line",
			"Use a Bank Builder on a crankshaft to lay out its cylinders and heads in one click.");
		on("builder/engine_casing", "first_light/alive", () -> CEBlocks.ENGINE_CASING.get(), AdvancementType.TASK,
			Milestone.CLAD, "Under the Hood",
			"Run an engine with four or more blocks of Engine Casing built round its cylinders.");

		// Fuel: what burns, and what goes wrong when it burns badly
		on("fuel/diesel", "first_light/cylinder", () -> CEFluids.DIESEL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.DIESEL_BURNED, "Rudolf's Recipe", "Run an engine on Diesel, which lights readily under compression.");
		on("fuel/gasoline", "fuel/diesel", () -> CEFluids.GASOLINE.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.GASOLINE_BURNED, "Refined Taste", "Run an engine on Gasoline. It will not light by compression, so it needs a spark.");
		on("fuel/spark_plug", "fuel/diesel", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK,
			Milestone.SPARK_ENGINE, "Spark of Genius",
			"Run an engine whose fuel is lit by a Spark Plug, with a Battery to supply the sparks.");
		on("fuel/ethanol", "fuel/spark_plug", () -> CEFluids.ETHANOL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.ETHANOL_BURNED, "Home Brew",
			"Run a spark engine on Ethanol, fermented from crops and distilled in a heated basin. Its autoignition point is so high that boost will not light it early.");
		on("fuel/ethanol_boost", "fuel/ethanol", () -> CEFluids.ETHANOL.getBucket().orElseThrow(), AdvancementType.GOAL,
			Milestone.ETHANOL_BOOSTED, "Cool Under Pressure",
			"Run a boosted engine on Ethanol with its charge squeezed past the heat where gasoline would light itself early and lose power. Ethanol waits for the plug, and its boiling cools the charge.");
		on("fuel/dual_fuel", "fuel/gasoline", () -> CEFluids.GASOLINE.getBucket().orElseThrow(), AdvancementType.GOAL,
			Milestone.DUAL_FUEL, "Pilot Light",
			"Pipe diesel into an oil pan's pilot tank and light gasoline or ethanol in an engine with no spark plug.");
		on("fuel/heat_exchanger", "builder/pipework", () -> CEBlocks.HEAT_EXCHANGER.get(), AdvancementType.TASK,
			Milestone.HEAT_RECOVERED, "Waste Not",
			"Run an engine whose exhaust runs through an Exhaust Heat Exchanger hot enough to cook heated basin recipes, with the basin on top of it.");
		on("fuel/combined_cycle", "fuel/heat_exchanger", () -> AllBlocks.STEAM_ENGINE.get(), AdvancementType.GOAL,
			Milestone.COMBINED_CYCLE, "Combined Cycle",
			"Heat a boiler from an engine's exhaust: set a fluid tank over a hot Exhaust Heat Exchanger and run a Create steam engine off it.");
		on("fuel/smoke_signals", "first_light/alive", () -> CEBlocks.EXHAUST_OUTLET.get(), AdvancementType.TASK,
			Milestone.SMOKE_SIGNALS, "Smoke Signals", "Run so rich that fuel pours out of the exhaust as black smoke.");
		on("fuel/fouled_plug", "fuel/spark_plug", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK,
			Milestone.FOULED_PLUG, "Fouled Up", "Run a spark engine so rich that soot builds up on its plug.");
		on("fuel/thinned_oil", "fuel/smoke_signals", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(),
			AdvancementType.TASK, Milestone.THINNED_OIL, "Gas in the Oil",
			"Run rich for so long that unburned fuel thins the engine oil.");

		// Power: rating, revs and boost
		on("power/heavy_duty", "first_light/alive", () -> CEBlocks.ALTERNATOR.get(), AdvancementType.GOAL,
			Milestone.HEAVY_DUTY, "Heavy Duty",
			"Run an engine rated for 16384 stress units or more: as much as a Create steam engine.");
		on("power/redline", "first_light/alive", () -> CEBlocks.CAMSHAFT.get(), AdvancementType.GOAL, Milestone.REDLINE,
			"Redline", "Turn a running engine faster than 240 RPM, close to the limit Create allows.");
		on("power/steam_beater", "power/heavy_duty", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.CHALLENGE,
			Milestone.STEAM_BEATER, "Steam Is for Kettles",
			"Run an engine rated for 65536 stress units or more: four Create steam engines' worth from one crankshaft.");
		on("power/steam_titan", "power/steam_beater", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.CHALLENGE,
			Milestone.STEAM_TITAN, "Titan of Industry", "Rate an engine for as much stress as sixteen Create steam engines.");
		on("power/overloaded", "power/heavy_duty", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK,
			Milestone.OVERLOADED, "Overworked", "Ask an engine for more torque than it is rated for.");
		on("power/turbocharger", "builder/pipework", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK,
			Milestone.BOOST, "Forced Induction",
			"Spool a Turbocharger up to 0.3 bar of boost. Put it in both the exhaust run and the intake run.");
		on("power/big_boost", "power/turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL,
			Milestone.BIG_BOOST, "Big Boost", "Spool a turbocharger up to a full bar of boost.");
		on("power/blow_off", "power/turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK,
			Milestone.BLOW_OFF, "Psssht", "Dump a turbocharger's boost with a redstone signal.");
		on("power/twin_turbo", "power/turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL,
			Milestone.TWIN_TURBO, "Two Lungs",
			"Run two turbochargers side by side on the same cylinders, each making at least 0.2 bar, so they share the air.");
		on("power/anti_lag", "power/turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL,
			Milestone.ANTI_LAG, "Bang Bang",
			"Set a turbocharger's anti-lag and keep it spooled off the throttle by burning fuel in the exhaust.");
		on("power/supercharger", "power/turbocharger", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.TASK,
			Milestone.SUPERCHARGED, "Blown Away",
			"Run a Supercharger at 0.3 bar of boost on a running engine: drive its shaft and put it in the intake run for boost with no lag.");
		on("power/twincharged", "power/supercharger", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.CHALLENGE,
			Milestone.TWINCHARGED, "Twincharged",
			"Run a turbocharger and a supercharger on the same cylinders, each making at least 0.2 bar.");
		on("power/precision_engine", "power/heavy_duty", () -> CEBlocks.PRECISION_CYLINDER.get(), AdvancementType.GOAL,
			Milestone.PRECISION_ENGINE, "Blueprinted",
			"Run an engine of four or more cylinders built entirely from precision parts.");

		// Tuning: reading an engine and trimming it
		on("tuning/dialed_in", "first_light/alive", () -> CEBlocks.CYLINDER_HEAD.get(), AdvancementType.GOAL,
			Milestone.DIALED_IN, "Dialed In",
			"Run an engine above 100 RPM with every firing cylinder on a balanced air-fuel mixture.");
		on("tuning/governed", "tuning/dialed_in", () -> CEBlocks.GOVERNOR.get(), AdvancementType.GOAL,
			Milestone.GOVERNED, "Cruise Control", "Let a Governor hold a loaded engine on its setting for ten seconds.");
		on("tuning/engine_display", "first_light/alive", () -> AllBlocks.DISPLAY_LINK.get(), AdvancementType.TASK,
			Milestone.ENGINE_DISPLAYED, "Instrument Panel",
			"Point a Display Link at a running engine's crankshaft and show its figures on a Display Board or nixie tubes.");
		on("tuning/stampede", "tuning/engine_display", () -> AllBlocks.DISPLAY_LINK.get(), AdvancementType.GOAL,
			Milestone.STAMPEDE, "Stampede", "Show an engine delivering 500 horsepower or more on a Display Link.");
		on("tuning/dyno_run", "tuning/engine_display", () -> CEBlocks.DYNO.get(), AdvancementType.TASK,
			Milestone.DYNO_RUN, "On the Rollers",
			"Read a running engine's torque and power curves with a Dynamometer set against it.");

		// Driveline: getting the power out
		on("driveline/transmission", "first_light/crankshaft", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK,
			Milestone.SHIFTED, "Gear Up", "Change gear on a Transmission that is carrying drive.");
		on("driveline/every_gear", "driveline/transmission", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.GOAL,
			Milestone.EVERY_GEAR, "Gearhead", "Shift a loaded transmission through all thirteen gears and reverse.");
		on("driveline/alternator", "fuel/spark_plug", () -> CEBlocks.ALTERNATOR.get(), AdvancementType.TASK,
			Milestone.ALTERNATOR_CHARGING, "Keeping the Lights On",
			"Turn an Alternator's shaft to put charge back into a Battery.");
		on("driveline/flywheel", "first_light/alive", () -> CEBlocks.CAST_FLYWHEEL.get(), AdvancementType.TASK,
			Milestone.FLYWHEEL_SMOOTH, "Carried Through",
			"Run an engine with a flywheel on its crankshaft's line that turns smoothly enough to keep its full rating.");

		// Service: keeping an engine in good order
		on("service/oil_kept_fresh", "first_light/sump", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(),
			AdvancementType.GOAL, Milestone.OIL_KEPT_FRESH, "Always Fresh",
			"Run an engine at speed while an oil exchange keeps the oil nearly new.");
		on("service/cool_running", "first_light/warmed_up", () -> CEFluids.COOLANT.getBucket().orElseThrow(),
			AdvancementType.GOAL, Milestone.COOL_RUNNING, "Cool Running",
			"Run an engine at speed cool enough to earn the whole cooling bonus.");
		on("service/preheated", "first_light/warmed_up", () -> Items.BLAZE_POWDER, AdvancementType.TASK,
			Milestone.PREHEATED, "Block Heater",
			"Start an engine whose block a heat source has already warmed: put a Blaze Burner against its heat exchanger and leave it a while.");

		// The top of each track, and the top of them all
		List<Milestone> builder = List.of(Milestone.TWELVE_CYLINDERS, Milestone.STIFF_CRANK, Milestone.COUPLED_CRANKS,
			Milestone.VEE_ENGINE, Milestone.FLAT_ENGINE, Milestone.EVEN_FIRING, Milestone.VALLEY_CAM,
			Milestone.ROTARY_ENGINE);
		List<Milestone> power = List.of(Milestone.STEAM_HERD, Milestone.REDLINE, Milestone.BIG_BOOST,
			Milestone.TWINCHARGED, Milestone.PRECISION_ENGINE, Milestone.ANTI_LAG, Milestone.VARIABLE_DRIVE);
		List<Milestone> tuning = List.of(Milestone.DIALED_IN, Milestone.MAF_TRIM, Milestone.MAF_ENRICH,
			Milestone.GOVERNED, Milestone.FUEL_TUNED, Milestone.TOOL_TIMED);
		List<Milestone> driveline = List.of(Milestone.EVERY_GEAR, Milestone.CHARGED_BATTERY,
			Milestone.FLYWHEEL_SMOOTH, Milestone.GOVERNOR_COMMANDED);
		List<Milestone> fuel = List.of(Milestone.GASOLINE_BURNED, Milestone.ETHANOL_BURNED,
			Milestone.SUPERHEAT_RECOVERED, Milestone.DUAL_FUEL, Milestone.WASTE_OIL_BURNED);
		List<Milestone> service = List.of(Milestone.OIL_KEPT_FRESH, Milestone.OIL_FILTERED, Milestone.SUMP_OILING,
			Milestone.WARMED_UP, Milestone.COOL_RUNNING, Milestone.PREHEATED, Milestone.HARD_START);
		capstone("builder/master_builder", "builder/sixteen_cylinders", () -> CEBlocks.CRANK_THROW.get(), builder,
			"Master Builder",
			"Finish the builder's track: a twelve with a stiff crank, coupled cranks, a vee, a flat, even firing, a valley cam and a Wankel.");
		capstone("power/power_plant", "power/steam_titan", () -> CEBlocks.TURBOCHARGER.get(), power, "Power Plant",
			"Finish the power track: eight steam engines' worth, redline, big boost, twincharging, precision parts, anti-lag and a variable-drive supercharger on its overdrive.");
		capstone("tuning/fine_tuner", "tuning/dyno_run", () -> CEBlocks.MAF_SENSOR.get(), tuning, "Fine Tuner",
			"Finish the tuning track: a balanced mixture, MAF trimming and enrichment, a governor holding speed, a fuel tune and a timed engine.");
		capstone("driveline/full_driveline", "driveline/every_gear", () -> CEBlocks.TRANSMISSION.get(), driveline,
			"Full Driveline",
			"Finish the driveline track: every gear, a charged battery, a flywheel smoothing the engine and a governor under redstone.");
		capstone("fuel/closed_loop", "fuel/dual_fuel", () -> CEFluids.ETHANOL.getBucket().orElseThrow(), fuel,
			"Closed Loop",
			"Finish the fuel track: burn gasoline, ethanol and waste oil, superheat from the exhaust and light a dual-fuel engine.");
		capstone("service/service_record", "service/oil_kept_fresh", () -> CEItems.OIL_FILTER.get(), service,
			"Service Record",
			"Finish the service track: an oil pan keeping the oil fresh, a filter, a warm block, an engine running cool, a block heater and a stone-cold start on a slow fuel.");
		List<Milestone> everything = new ArrayList<>();
		for (List<Milestone> track : List.of(builder, power, tuning, driveline, fuel, service))
			everything.addAll(track);
		capstone("chief_engineer", ROOT, () -> CEBlocks.DYNO.get(), everything, "Chief Engineer",
			"Finish every track: builder, power, tuning, driveline, fuel and service.");
	}

	private CEAdvancements() {
	}

	private static void row(String id, @Nullable String parent, Supplier<ItemLike> icon, AdvancementType type,
							Supplier<Criterion<?>> criterion, String title, String description) {
		ROWS.add(new Row(id, parent, icon, type, criterion, List.of(), title, description));
	}

	/**
	 * The top of a track: done once every one of {@code needs} has been reached, in any order, by the same player. Each
	 * milestone is its own criterion, so the advancement screen shows how far along it is.
	 */
	private static void capstone(String id, String parent, Supplier<ItemLike> icon, List<Milestone> needs,
								 String title, String description) {
		ROWS.add(new Row(id, parent, icon, AdvancementType.CHALLENGE, null, needs, title, description));
	}

	/** An advancement for getting hold of {@code item}. */
	private static void has(String id, String parent, Supplier<ItemLike> item, String title, String description) {
		row(id, parent, item, AdvancementType.TASK,
			() -> InventoryChangeTrigger.TriggerInstance.hasItems(item.get()), title, description);
	}

	/** An advancement that waits for the mod to report {@code milestone} to a player. */
	private static void on(String id, String parent, Supplier<ItemLike> icon, AdvancementType type, Milestone milestone,
						   String title, String description) {
		row(id, parent, icon, type, () -> CETriggers.EVENT.get()
			.waitingFor(milestone), title, description);
	}

	/** The lang key of an advancement's title; the description adds {@code .desc}. A path's slashes become dots. */
	public static String titleKey(String id) {
		return "advancement." + CreateEngines.MOD_ID + "." + id.replace('/', '.');
	}

	/** Called while the mod registers: the text goes into the lang file. */
	public static void register() {
		for (Row row : ROWS) {
			REGISTRATE.addRawLang(titleKey(row.id()), row.title());
			REGISTRATE.addRawLang(titleKey(row.id()) + ".desc", row.description());
		}
		REGISTRATE.addDataGenerator(ProviderType.LOOT, provider -> provider.addLootAction(LootContextParamSets.ADVANCEMENT_REWARD,
			tables -> tables.accept(CHIEF_ENGINEER_REWARD, LootTable.lootTable()
				.withPool(LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(CEBlocks.MAKER_PLATE.get())
						.apply(SetNameFunction.setName(Component.literal("Chief Engineer"), SetNameFunction.Target.CUSTOM_NAME)))))));
		REGISTRATE.addDataGenerator(ProviderType.ADVANCEMENT, CEAdvancements::generate);
	}

	private static void generate(RegistrateAdvancementProvider provider) {
		Map<String, AdvancementHolder> built = new HashMap<>();
		for (Row row : ROWS) {
			boolean root = row.parent() == null;
			Advancement.Builder builder = Advancement.Builder.advancement();
			if (!root)
				builder.parent(built.get(row.parent()));
			builder.display(row.icon()
				.get(), Component.translatable(titleKey(row.id())), Component.translatable(titleKey(row.id()) + ".desc"),
				root ? BACKGROUND : null, row.type(), !root, !root && !isQuiet(row.id()), false);
			if (row.id().equals("chief_engineer"))
				builder.rewards(AdvancementRewards.Builder.loot(CHIEF_ENGINEER_REWARD));
			if (row.requires()
				.isEmpty())
				builder.addCriterion(row.id(), row.criterion()
					.get());
			else
				for (Milestone needed : row.requires())
					builder.addCriterion(needed.id(), CETriggers.EVENT.get()
						.waitingFor(needed));
			built.put(row.id(), builder.save(provider, CreateEngines.MOD_ID + ":" + row.id()));
		}
	}

	/** Whether the advancement {@code id} shows its toast without a line in chat. */
	public static boolean isQuiet(String id) {
		return QUIET.contains(id);
	}

	/** The IDs of every advancement the mod defines, for tests. */
	public static List<String> ids() {
		return ROWS.stream()
			.map(Row::id)
			.toList();
	}

	/** How many advancements the mod defines, for tests. */
	public static int count() {
		return ROWS.size();
	}
}
