package com.createengines.advancement;

import static com.createengines.CreateEngines.REGISTRATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateAdvancementProvider;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * The advancement tree, written once as a table: datagen turns each row into an advancement and its lang entries.
 * <p>
 * Every feature added to the mod should add rows here. A part gets an "obtained" row ({@link #has}); something the
 * engine or a block does gets a {@link Milestone} that is fired where it happens, and a row that waits for it
 * ({@link #on}).
 */
public final class CEAdvancements {

	private static final ResourceLocation BACKGROUND =
		ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");

	private record Row(String id, @Nullable String parent, Supplier<ItemLike> icon, AdvancementType type,
					   Supplier<Criterion<?>> criterion, String title, String description) {
	}

	private static final List<Row> ROWS = new ArrayList<>();

	static {
		// The root: everyone gets it, and it names the tab
		row("engineer", null, () -> CEBlocks.CRANK_THROW.get(), AdvancementType.TASK,
			() -> CriteriaTriggers.TICK.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty())),
			"Create: Engine Engineer", "Burn something on purpose and put the torque to work.");

		// Building an engine
		has("crankshaft", "engineer", () -> CEBlocks.CRANKSHAFT.get(), "Bottom End",
			"Craft a Crankshaft, the spine every engine is built on.");
		has("cylinder", "crankshaft", () -> CEBlocks.CYLINDER.get(), "Bore Ambition",
			"Craft a Cylinder, then add a piston and connecting rod.");
		has("cylinder_head", "cylinder", () -> CEBlocks.CYLINDER_HEAD.get(), "Head Gasket",
			"Craft a Cylinder Head to seal the bore and hold the injector.");
		has("camshaft", "cylinder_head", () -> CEBlocks.CAMSHAFT.get(), "Timing Is Everything",
			"Craft a Camshaft. Geared to half the crank's speed, its lobes open the valves on time.");
		has("pipework", "cylinder_head", () -> CEBlocks.INTAKE_MANIFOLD.get(), "Breathing Room",
			"Craft an Intake Manifold: an engine needs air in and exhaust out.");
		has("throttle", "pipework", () -> CEBlocks.THROTTLE.get(), "Pedal to the Metal",
			"Craft a Throttle Body and let redstone decide how much air the engine gets.");
		has("starter", "crankshaft", () -> CEBlocks.STARTER.get(), "Turn the Key",
			"Craft a Starter to crank an engine over.");

		// Fuels and oil
		has("diesel", "cylinder_head", () -> CEFluids.DIESEL.getBucket().orElseThrow(), "Rudolf's Recipe",
			"Get a bucket of Diesel, which lights readily under compression.");
		has("gasoline", "diesel", () -> CEFluids.GASOLINE.getBucket().orElseThrow(), "Refined Taste",
			"Get a bucket of Gasoline. It will not light by compression, so it needs a spark.");
		has("engine_oil", "diesel", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), "Oil Change",
			"Get a bucket of Engine Oil to lubricate the moving parts.");

		has("sump", "engine_oil", () -> CEBlocks.SUMP.get(), "Oil Pan",
			"Craft an Oil Sump. Set against a crankshaft or head, it oils the whole engine from one place.");

		// Ignition and electrics
		has("spark_plug", "cylinder_head", () -> CEItems.SPARK_PLUG.get(), "Spark of Genius",
			"Craft a Spark Plug to light fuel by spark instead of by squeezing.");
		has("battery", "spark_plug", () -> CEBlocks.BATTERY.get(), "Charged Up",
			"Craft a Battery, which supplies the sparks.");
		has("alternator", "battery", () -> CEBlocks.ALTERNATOR.get(), "Keeping the Lights On",
			"Craft an Alternator to put charge back into a battery from a turning shaft.");
		on("charged_battery", "alternator", () -> CEBlocks.BATTERY.get(), AdvancementType.TASK, Milestone.CHARGED_BATTERY,
			"Fully Charged", "Fill a battery with an alternator.");

		// Running
		on("cranking", "starter", () -> CEBlocks.STARTER.get(), AdvancementType.TASK, Milestone.CRANKING,
			"Turning Over", "Crank an engine with a starter.");
		on("alive", "cranking", () -> CEBlocks.CYLINDER.get(), AdvancementType.GOAL, Milestone.ENGINE_RUNNING,
			"It's Alive!", "Get an engine running on its own power.");
		on("four_cylinders", "alive", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.TASK, Milestone.FOUR_CYLINDERS,
			"Four on the Floor", "Run an engine with four firing cylinders.");
		on("eight_cylinders", "four_cylinders", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.GOAL,
			Milestone.EIGHT_CYLINDERS, "Eight Is Enough", "Run an engine with eight firing cylinders.");
		on("twelve_cylinders", "eight_cylinders", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.CHALLENGE,
			Milestone.TWELVE_CYLINDERS, "Twelve Angry Pistons", "Run an engine with twelve firing cylinders.");
		on("sixteen_cylinders", "twelve_cylinders", () -> CEBlocks.CRANK_THROW.get(), AdvancementType.CHALLENGE,
			Milestone.SIXTEEN_CYLINDERS, "Hypercar Money", "Run an engine with sixteen firing cylinders.");
		on("coupled_cranks", "four_cylinders", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.GOAL,
			Milestone.COUPLED_CRANKS, "In Step",
			"Run an engine of four or more cylinders whose crankshafts are geared together one to one, so they turn as one.");
		on("even_firing", "four_cylinders", () -> CEBlocks.CAM_LOBE.get(), AdvancementType.GOAL, Milestone.EVEN_FIRING,
			"Smooth Operator", "Run an engine of four or more cylinders whose firings are spaced evenly round the cycle.");
		on("pushrod_cam", "camshaft", () -> CEBlocks.CAM_LOBE.get(), AdvancementType.TASK, Milestone.PUSHROD_CAM,
			"Push It", "Run an engine whose cam sits beside the cylinder block and works the valves through pushrods.");
		on("high_revs", "alive", () -> CEBlocks.CAMSHAFT.get(), AdvancementType.TASK, Milestone.HIGH_REVS,
			"Revving High", "Turn a running engine faster than 200 RPM.");
		on("redline", "high_revs", () -> CEBlocks.CAMSHAFT.get(), AdvancementType.GOAL, Milestone.REDLINE,
			"Redline", "Turn a running engine faster than 240 RPM, close to the limit Create allows.");
		on("heavy_duty", "alive", () -> CEBlocks.ALTERNATOR.get(), AdvancementType.GOAL, Milestone.HEAVY_DUTY,
			"Heavy Duty", "Run an engine rated for 16384 stress units or more: as much as a Create steam engine.");
		on("steam_beater", "heavy_duty", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.CHALLENGE,
			Milestone.STEAM_BEATER, "Steam Is for Kettles",
			"Run an engine rated for 65536 stress units or more: four Create steam engines' worth from one crankshaft.");
		on("spark_engine", "alive", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK, Milestone.SPARK_ENGINE,
			"Spark Ignition", "Run an engine whose fuel is lit by a spark plug.");
		on("well_oiled", "alive", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.WELL_OILED, "Well Oiled Machine", "Run an engine with every part fully lubricated.");
		on("dry_run", "alive", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.TASK, Milestone.DRY_RUN,
			"Running on Hope", "Run an engine with no oil in it. It drags terribly, but it runs.");
		on("smoke_signals", "alive", () -> CEBlocks.EXHAUST_OUTLET.get(), AdvancementType.TASK, Milestone.SMOKE_SIGNALS,
			"Smoke Signals", "Run so rich that fuel pours out of the exhaust as black smoke.");
		on("dialed_in", "alive", () -> CEBlocks.CYLINDER_HEAD.get(), AdvancementType.GOAL, Milestone.DIALED_IN,
			"Dialed In", "Run an engine above 100 RPM with every firing cylinder on a balanced air-fuel mixture.");
		on("fouled_plug", "spark_engine", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK, Milestone.FOULED_PLUG,
			"Fouled Up", "Run a spark engine so rich that soot builds up on its plug.");
		on("thinned_oil", "smoke_signals", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.THINNED_OIL, "Gas in the Oil", "Run rich for so long that unburned fuel thins the engine oil.");
		on("stalled", "alive", () -> CEBlocks.STARTER.get(), AdvancementType.TASK, Milestone.STALLED,
			"Stall Out", "Let a turning engine die.");
		on("overloaded", "alive", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.OVERLOADED,
			"Overworked", "Ask an engine for more torque than it is rated for.");

		// Forced induction
		has("turbocharger", "pipework", () -> CEBlocks.TURBOCHARGER.get(), "Forced Induction",
			"Craft a Turbocharger. Put it in both the exhaust run and the intake run.");
		on("boost", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK, Milestone.BOOST,
			"Boost!", "Spool a turbocharger up to 0.3 bar of boost.");
		on("big_boost", "boost", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL, Milestone.BIG_BOOST,
			"Big Boost", "Spool a turbocharger up to a full bar of boost.");
		on("blow_off", "boost", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK, Milestone.BLOW_OFF,
			"Psssht", "Dump a turbocharger's boost with a redstone signal.");
		has("supercharger", "turbocharger", () -> CEBlocks.SUPERCHARGER.get(), "Blown Away",
			"Craft a Supercharger. Drive its shaft and put it in the intake run for boost with no lag.");
		on("supercharged", "supercharger", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.TASK,
			Milestone.SUPERCHARGED, "Whine On", "Run a supercharger at 0.3 bar of boost on a running engine.");
		on("compound_boost", "boost", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL,
			Milestone.COMPOUND_BOOST, "Compound Interest",
			"Run two turbochargers on the same cylinders, each making at least 0.2 bar of boost, with all the air going through both so their pressures multiply.");
		on("twin_turbo", "boost", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL, Milestone.TWIN_TURBO,
			"Two Lungs", "Run two turbochargers side by side on the same cylinders, each making at least 0.2 bar, so they share the air.");
		on("twincharged", "supercharged", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.CHALLENGE,
			Milestone.TWINCHARGED, "Twincharged",
			"Run a turbocharger and a supercharger on the same cylinders, each making at least 0.2 bar.");
		has("intercooler", "turbocharger", () -> CEBlocks.INTERCOOLER.get(), "Chill Out",
			"Craft an Intercooler to take the heat out of air a compressor has squeezed.");
		on("cooled_charge", "intercooler", () -> CEBlocks.INTERCOOLER.get(), AdvancementType.TASK,
			Milestone.COOLED_CHARGE, "Cool Customer",
			"Run a firing engine at 0.3 bar of boost with an intercooler in its intake.");

		// Engine management
		has("maf_sensor", "throttle", () -> CEBlocks.MAF_SENSOR.get(), "Weighing the Air",
			"Craft a MAF Sensor. In an intake run it caps every shot at the air the cylinder drew in.");
		on("maf_trim", "maf_sensor", () -> CEBlocks.MAF_SENSOR.get(), AdvancementType.TASK, Milestone.MAF_TRIM,
			"Penny Pincher", "Run an engine whose MAF sensor is holding every shot back by a fifth or more to match the air.");
		has("lambda_sensor", "pipework", () -> CEBlocks.LAMBDA_SENSOR.get(), "Breath Test",
			"Craft a Lambda Sensor, which reads the mixture the exhaust gas says was burned.");
		on("lambda_lock", "lambda_sensor", () -> CEBlocks.LAMBDA_SENSOR.get(), AdvancementType.GOAL,
			Milestone.LAMBDA_LOCK, "Right on Lambda",
			"Run an engine above 100 RPM on a balanced mixture with a Lambda Sensor in its exhaust.");
		has("rev_limiter", "crankshaft", () -> CEBlocks.REV_LIMITER.get(), "Know Your Limits",
			"Craft a Rev Limiter and set a speed an engine will not pass.");
		on("rev_limited", "rev_limiter", () -> CEBlocks.REV_LIMITER.get(), AdvancementType.TASK,
			Milestone.REV_LIMITED, "Bouncing Off the Limiter",
			"Rev an engine up to a Rev Limiter's setting and let it cut the fuel.");

		has("governor", "rev_limiter", () -> CEBlocks.GOVERNOR.get(), "Steady as She Goes",
			"Craft a Governor, which moves the fuel rack to hold an engine at a set speed.");
		on("governed", "governor", () -> CEBlocks.GOVERNOR.get(), AdvancementType.GOAL, Milestone.GOVERNED,
			"Cruise Control", "Let a Governor hold a loaded engine on its setting for ten seconds.");

		// Firing order
		has("timing_tool", "camshaft", () -> CEItems.TIMING_TOOL.get(), "Timing Light",
			"Craft a Timing Tool, which sets an engine's throws and cam lobes to the best firing order it can have.");
		on("tool_timed", "timing_tool", () -> CEItems.TIMING_TOOL.get(), AdvancementType.GOAL, Milestone.TOOL_TIMED,
			"Right on Time", "Time an engine of four or more cylinders with a Timing Tool so every firing is evenly spaced.");

		// Precision parts
		has("precision_cylinder", "cylinder", () -> CEBlocks.PRECISION_CYLINDER.get(), "Tighter Tolerances",
			"Craft a Precision Cylinder: a machined liner gives higher compression and less friction.");
		has("precision_head", "cylinder_head", () -> CEBlocks.PRECISION_CYLINDER_HEAD.get(), "Four Valves a Cylinder",
			"Craft a Precision Cylinder Head, which breathes better and lights the charge sooner.");
		has("forged_piston", "precision_cylinder", () -> CEItems.FORGED_PISTON.get(), "Forged in Fire",
			"Craft a Forged Piston: lighter and slipperier than a cast one.");
		has("precision_injector", "precision_head", () -> CEItems.PRECISION_INJECTOR.get(), "Common Rail",
			"Craft a Precision Injector, which atomises fuel finely so it ignites sooner and burns faster.");
		on("precision_engine", "precision_injector", () -> CEBlocks.PRECISION_CYLINDER.get(), AdvancementType.GOAL,
			Milestone.PRECISION_ENGINE, "Blueprinted",
			"Run an engine of four or more cylinders built entirely from precision parts.");

		// Gearing
		has("transmission", "crankshaft", () -> CEBlocks.TRANSMISSION.get(), "Gear Up",
			"Craft a Transmission, a gearbox with selectable gears.");
		on("shifted", "transmission", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.SHIFTED,
			"Shifting Gears", "Change gear on a transmission that is carrying drive.");
		on("reverse", "shifted", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.REVERSE,
			"Reverse Engineering", "Run a transmission's output backwards in reverse gear.");
		on("every_gear", "shifted", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.GOAL, Milestone.EVERY_GEAR,
			"Gearhead", "Shift a loaded transmission through all four gears and reverse.");
	}

	private CEAdvancements() {
	}

	private static void row(String id, @Nullable String parent, Supplier<ItemLike> icon, AdvancementType type,
							Supplier<Criterion<?>> criterion, String title, String description) {
		ROWS.add(new Row(id, parent, icon, type, criterion, title, description));
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

	private static String titleKey(String id) {
		return "advancement." + CreateEngines.MOD_ID + "." + id;
	}

	/** Called while the mod registers: the text goes into the lang file. */
	public static void register() {
		for (Row row : ROWS) {
			REGISTRATE.addRawLang(titleKey(row.id()), row.title());
			REGISTRATE.addRawLang(titleKey(row.id()) + ".desc", row.description());
		}
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
				root ? BACKGROUND : null, row.type(), !root, !root, false);
			builder.addCriterion(row.id(), row.criterion()
				.get());
			built.put(row.id(), builder.save(provider, CreateEngines.MOD_ID + ":" + row.id()));
		}
	}

	/** How many advancements the mod defines, for tests. */
	public static int count() {
		return ROWS.size();
	}
}
