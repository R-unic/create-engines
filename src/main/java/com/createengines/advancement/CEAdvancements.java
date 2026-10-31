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
 * Every feature added to the mod should add rows here. A part is rewarded for what it does, not for being crafted: it
 * gets a {@link Milestone} that is fired where it happens, and a row that waits for it ({@link #on}). The "obtained"
 * row ({@link #has}) is kept for the few recipes every engine needs before anything can run.
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

		// Building an engine: the first three are the recipes everyone needs before anything can run
		has("crankshaft", "engineer", () -> CEBlocks.CRANKSHAFT.get(), "Bottom End",
			"Craft a Crankshaft, the spine every engine is built on.");
		has("cylinder", "crankshaft", () -> CEBlocks.CYLINDER.get(), "Bore Ambition",
			"Craft a Cylinder, then add a piston and connecting rod.");
		has("cylinder_head", "cylinder", () -> CEBlocks.CYLINDER_HEAD.get(), "Head Gasket",
			"Craft a Cylinder Head to seal the bore and hold the injector.");

		// Every part below is rewarded for what it does in a running engine, not for being crafted
		on("camshaft", "cylinder_head", () -> CEBlocks.CAMSHAFT.get(), AdvancementType.TASK, Milestone.CAMSHAFT_TIMED,
			"Timing Is Everything", "Run an engine whose valves are opened by a Camshaft, geared to half the crank's speed.");
		on("pipework", "cylinder_head", () -> CEBlocks.INTAKE_MANIFOLD.get(), AdvancementType.TASK,
			Milestone.PIPED_ENGINE, "Breathing Room",
			"Run an engine that takes air in and lets exhaust out through piped manifolds.");
		on("throttle", "pipework", () -> CEBlocks.THROTTLE.get(), AdvancementType.TASK, Milestone.THROTTLED,
			"Pedal to the Metal", "Run an engine whose intake a Throttle Body is holding partly closed.");
		on("starter", "crankshaft", () -> CEBlocks.STARTER.get(), AdvancementType.TASK, Milestone.CRANKING,
			"Turn the Key", "Crank an engine over with a Starter.");

		// Fuels and oil
		on("diesel", "cylinder_head", () -> CEFluids.DIESEL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.DIESEL_BURNED, "Rudolf's Recipe", "Run an engine on Diesel, which lights readily under compression.");
		on("gasoline", "diesel", () -> CEFluids.GASOLINE.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.GASOLINE_BURNED, "Refined Taste", "Run an engine on Gasoline. It will not light by compression, so it needs a spark.");
		on("engine_oil", "diesel", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.OILED, "Oil Change", "Run an engine whose moving parts are lubricated with Engine Oil.");
		on("sump", "engine_oil", () -> CEBlocks.SUMP.get(), AdvancementType.TASK, Milestone.SUMP_OILING,
			"Oil Pan", "Run an engine kept oiled from an Oil Sump set against its crankshaft or head.");

		// Ignition and electrics
		on("spark_plug", "cylinder_head", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK, Milestone.SPARK_ENGINE,
			"Spark of Genius", "Run an engine whose fuel is lit by a Spark Plug, with a Battery to supply the sparks.");
		on("alternator", "spark_plug", () -> CEBlocks.ALTERNATOR.get(), AdvancementType.TASK,
			Milestone.ALTERNATOR_CHARGING, "Keeping the Lights On",
			"Turn an Alternator's shaft to put charge back into a Battery.");
		on("charged_battery", "alternator", () -> CEBlocks.BATTERY.get(), AdvancementType.TASK, Milestone.CHARGED_BATTERY,
			"Fully Charged", "Fill a battery with an alternator.");

		// Running
		on("alive", "starter", () -> CEBlocks.CYLINDER.get(), AdvancementType.GOAL, Milestone.ENGINE_RUNNING,
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
		on("well_oiled", "alive", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.WELL_OILED, "Well Oiled Machine", "Run an engine with every part fully lubricated.");
		on("dry_run", "alive", () -> CEBlocks.CRANKSHAFT.get(), AdvancementType.TASK, Milestone.DRY_RUN,
			"Running on Hope", "Run an engine with no oil in it. It drags terribly, but it runs.");
		on("smoke_signals", "alive", () -> CEBlocks.EXHAUST_OUTLET.get(), AdvancementType.TASK, Milestone.SMOKE_SIGNALS,
			"Smoke Signals", "Run so rich that fuel pours out of the exhaust as black smoke.");
		on("dialed_in", "alive", () -> CEBlocks.CYLINDER_HEAD.get(), AdvancementType.GOAL, Milestone.DIALED_IN,
			"Dialed In", "Run an engine above 100 RPM with every firing cylinder on a balanced air-fuel mixture.");
		on("fouled_plug", "spark_plug", () -> CEItems.SPARK_PLUG.get(), AdvancementType.TASK, Milestone.FOULED_PLUG,
			"Fouled Up", "Run a spark engine so rich that soot builds up on its plug.");
		on("thinned_oil", "smoke_signals", () -> CEFluids.ENGINE_OIL.getBucket().orElseThrow(), AdvancementType.TASK,
			Milestone.THINNED_OIL, "Gas in the Oil", "Run rich for so long that unburned fuel thins the engine oil.");
		on("stalled", "alive", () -> CEBlocks.STARTER.get(), AdvancementType.TASK, Milestone.STALLED,
			"Stall Out", "Let a turning engine die.");
		on("overloaded", "alive", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.OVERLOADED,
			"Overworked", "Ask an engine for more torque than it is rated for.");

		// Forced induction
		on("turbocharger", "pipework", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK, Milestone.BOOST,
			"Forced Induction",
			"Spool a Turbocharger up to 0.3 bar of boost. Put it in both the exhaust run and the intake run.");
		on("big_boost", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL, Milestone.BIG_BOOST,
			"Big Boost", "Spool a turbocharger up to a full bar of boost.");
		on("blow_off", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.TASK, Milestone.BLOW_OFF,
			"Psssht", "Dump a turbocharger's boost with a redstone signal.");
		on("supercharger", "turbocharger", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.TASK,
			Milestone.SUPERCHARGED, "Blown Away",
			"Run a Supercharger at 0.3 bar of boost on a running engine: drive its shaft and put it in the intake run for boost with no lag.");
		on("compound_boost", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL,
			Milestone.COMPOUND_BOOST, "Compound Interest",
			"Run two turbochargers on the same cylinders, each making at least 0.2 bar of boost, with all the air going through both so their pressures multiply.");
		on("twin_turbo", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL, Milestone.TWIN_TURBO,
			"Two Lungs", "Run two turbochargers side by side on the same cylinders, each making at least 0.2 bar, so they share the air.");
		on("twincharged", "supercharger", () -> CEBlocks.SUPERCHARGER.get(), AdvancementType.CHALLENGE,
			Milestone.TWINCHARGED, "Twincharged",
			"Run a turbocharger and a supercharger on the same cylinders, each making at least 0.2 bar.");
		on("intercooler", "turbocharger", () -> CEBlocks.INTERCOOLER.get(), AdvancementType.TASK,
			Milestone.COOLED_CHARGE, "Chill Out",
			"Run a firing engine at 0.3 bar of boost with an Intercooler in its intake, taking the heat out of the air a compressor has squeezed.");

		// Engine management
		on("maf_sensor", "throttle", () -> CEBlocks.MAF_SENSOR.get(), AdvancementType.TASK, Milestone.MAF_READING,
			"Weighing the Air", "Run an engine with a MAF Sensor in its intake, which caps every shot at the air the cylinder drew in.");
		on("maf_trim", "maf_sensor", () -> CEBlocks.MAF_SENSOR.get(), AdvancementType.TASK, Milestone.MAF_TRIM,
			"Penny Pincher", "Run an engine whose MAF sensor is holding every shot back by a fifth or more to match the air.");
		on("lambda_sensor", "pipework", () -> CEBlocks.LAMBDA_SENSOR.get(), AdvancementType.TASK,
			Milestone.LAMBDA_READING, "Breath Test",
			"Run an engine with a Lambda Sensor in its exhaust, reading the mixture the exhaust gas says was burned.");
		on("lambda_lock", "lambda_sensor", () -> CEBlocks.LAMBDA_SENSOR.get(), AdvancementType.GOAL,
			Milestone.LAMBDA_LOCK, "Right on Lambda",
			"Run an engine above 100 RPM on a balanced mixture with a Lambda Sensor in its exhaust.");
		on("rev_limiter", "crankshaft", () -> CEBlocks.REV_LIMITER.get(), AdvancementType.TASK,
			Milestone.REV_LIMITED, "Know Your Limits",
			"Rev an engine up to a Rev Limiter's setting and let it cut the fuel.");

		on("governor", "rev_limiter", () -> CEBlocks.GOVERNOR.get(), AdvancementType.TASK, Milestone.GOVERNOR_ACTIVE,
			"Steady as She Goes", "Run an engine with a Governor pulling the fuel rack back to hold it at a set speed.");
		on("governed", "governor", () -> CEBlocks.GOVERNOR.get(), AdvancementType.GOAL, Milestone.GOVERNED,
			"Cruise Control", "Let a Governor hold a loaded engine on its setting for ten seconds.");

		// Firing order
		on("timing_tool", "camshaft", () -> CEItems.TIMING_TOOL.get(), AdvancementType.TASK, Milestone.TOOL_USED,
			"Timing Light", "Use a Timing Tool on an engine to set its throws and cam lobes to the best firing order it can have.");
		on("flywheel", "alive", () -> CEBlocks.CAST_FLYWHEEL.get(), AdvancementType.TASK, Milestone.FLYWHEEL_SMOOTH,
			"Carried Through", "Run an engine with a flywheel on its crankshaft's line that turns smoothly enough to keep its full rating.");
		on("variable_vanes", "turbocharger", () -> CEBlocks.VARIABLE_TURBOCHARGER.get(), AdvancementType.TASK,
			Milestone.VARIABLE_VANES, "Shape Shifter",
			"Run a Variable-Geometry Turbocharger making boost with its vanes partly closed, catching a thin exhaust.");
		on("anti_lag", "turbocharger", () -> CEBlocks.TURBOCHARGER.get(), AdvancementType.GOAL, Milestone.ANTI_LAG,
			"Bang Bang", "Set a turbocharger's anti-lag and keep it spooled off the throttle by burning fuel in the exhaust.");
		on("bank_builder", "crankshaft", () -> CEItems.BANK_BUILDER.get(), AdvancementType.TASK, Milestone.BANK_BUILT,
			"Assembly Line", "Use a Bank Builder on a crankshaft to lay out its cylinders and heads in one click.");
		on("vee_engine", "four_cylinders", () -> CEItems.BANK_BUILDER.get(), AdvancementType.GOAL, Milestone.VEE_ENGINE,
			"Vee for Victory", "Run an engine of four or more cylinders with two banks on the same crank throws, a quarter turn apart.");
		on("flat_engine", "four_cylinders", () -> CEItems.BANK_BUILDER.get(), AdvancementType.GOAL, Milestone.FLAT_ENGINE,
			"Boxer Rebellion", "Run an engine of four or more cylinders with opposed banks on the same crank throws.");
		on("dyno_run", "heavy_duty", () -> CEBlocks.DYNO.get(), AdvancementType.TASK, Milestone.DYNO_RUN,
			"On the Rollers", "Read a running engine's torque and power curves with a Dynamometer set against it.");
		on("tuned_up", "timing_tool", () -> CEItems.TIMING_TOOL.get(), AdvancementType.TASK, Milestone.FUEL_TUNED,
			"Tune-Up", "Sneak-use a Timing Tool until it is in tune mode, then use it on a running engine to set every injector for a balanced mixture.");
		on("tool_timed", "timing_tool",() -> CEItems.TIMING_TOOL.get(), AdvancementType.GOAL, Milestone.TOOL_TIMED,
			"Right on Time", "Time an engine of four or more cylinders with a Timing Tool so every firing is evenly spaced.");

		// Precision parts
		on("precision_cylinder", "cylinder", () -> CEBlocks.PRECISION_CYLINDER.get(), AdvancementType.TASK,
			Milestone.PRECISION_BORE, "Tighter Tolerances",
			"Run an engine with a Precision Cylinder: a machined liner gives higher compression and less friction.");
		on("precision_head", "cylinder_head", () -> CEBlocks.PRECISION_CYLINDER_HEAD.get(), AdvancementType.TASK,
			Milestone.PRECISION_HEAD, "Four Valves a Cylinder",
			"Run an engine with a Precision Cylinder Head, which breathes better and lights the charge sooner.");
		on("forged_piston", "precision_cylinder", () -> CEItems.FORGED_PISTON.get(), AdvancementType.TASK,
			Milestone.PRECISION_PISTON, "Forged in Fire",
			"Run an engine with a Forged Piston: lighter and slipperier than a cast one.");
		on("precision_injector", "precision_head", () -> CEItems.PRECISION_INJECTOR.get(), AdvancementType.TASK,
			Milestone.PRECISION_INJECTOR, "Common Rail",
			"Run an engine with a Precision Injector, which atomises fuel finely so it ignites sooner and burns faster.");
		on("precision_engine", "precision_injector", () -> CEBlocks.PRECISION_CYLINDER.get(), AdvancementType.GOAL,
			Milestone.PRECISION_ENGINE, "Blueprinted",
			"Run an engine of four or more cylinders built entirely from precision parts.");

		// Wankel rotors
		on("rotor_housing", "crankshaft", () -> CEBlocks.ROTOR_HOUSING.get(), AdvancementType.TASK,
			Milestone.ROTOR_HOUSING_TURNED, "Going in Circles",
			"Turn a crankshaft with a Rotor Housing in its line: a link that is its own cylinder and head, with ports instead of valves.");
		on("rotary_engine", "rotor_housing", () -> CEItems.ROTOR.get(), AdvancementType.GOAL, Milestone.ROTARY_ENGINE,
			"Valve? What Valve?",
			"Run an engine on an Engine Rotor, which turns on its eccentric at a third of the shaft's speed and has neither valves nor a camshaft. It needs a spark plug.");
		on("rotary_revs", "rotary_engine", () -> CEItems.ROTOR.get(), AdvancementType.TASK, Milestone.ROTARY_REVS,
			"Apex Predator", "Turn a running rotary engine faster than 200 RPM, where a rotor keeps its breath.");
		on("twin_rotors", "rotary_engine", () -> CEBlocks.ROTOR_HOUSING.get(), AdvancementType.GOAL,
			Milestone.TWIN_ROTORS, "Two Triangles", "Run an engine with two rotors firing on the same shaft.");

		// Gearing
		on("transmission", "crankshaft", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.SHIFTED,
			"Gear Up", "Change gear on a Transmission that is carrying drive.");
		on("reverse", "transmission", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.TASK, Milestone.REVERSE,
			"Reverse Engineering", "Run a transmission's output backwards in reverse gear.");
		on("every_gear", "transmission", () -> CEBlocks.TRANSMISSION.get(), AdvancementType.GOAL, Milestone.EVERY_GEAR,
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
