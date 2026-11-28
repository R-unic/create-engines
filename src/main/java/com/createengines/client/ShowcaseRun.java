package com.createengines.client;

import static com.createengines.client.ShowcaseRunWorks.*;
import static com.createengines.client.ShowcaseRunRover.*;
import static com.createengines.client.ShowcaseRunBuild.*;
import static com.createengines.client.ShowcaseRunCamera.*;
import static com.createengines.client.ShowcaseRunHooks.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid for the mod page's screenshots, off unless the {@code showcase} run is used (gradle
 * {@code runShowcase}): builds a handful of engines of different kinds on stone pads in a flat world, runs them, and
 * photographs each from several angles at 1920x1080, then once more at dusk. Pictures land in
 * {@code run/showcase/screenshots}; each engine's state is written to the log so a picture of a stalled engine can be
 * told from one of a running engine.
 */
public final class ShowcaseRun {

	static final String PROPERTY = "create_engines.showcase";

	/** The crankshaft's row and line in a banked engine, relative to the pad's corner. */
	static final int CY = 3;
	static final int CZ = 5;
	/** Distance between pads. Short enough that every engine is inside the simulation distance of the camera. */
	static final int BAY = 36;
	/** Which way the Bank Builder puts the cam line and a vee's second bank, off the crank's line. */
	static final int BESIDE_Z = Direction.UP.getClockWise(Axis.X)
		.getStepZ();

	enum Fuel {
		DIESEL, GASOLINE
	}

	record Step(int tick, Runnable action) {
	}

	record Tank(BlockPos pos, Fuel fuel) {
	}

	/** A viewpoint, as a target relative to the pad's corner, a heading, and how far back to stand. */
	record View(String label, double x, double y, double z, float yaw, float pitch, double distance) {
	}

	/** A pad and the views of it. {@code crank} is the engine's crankshaft, or null for a pad with no engine (the refinery). */
	record Scene(String name, int bay, BlockPos crank, boolean banked, List<View> views) {
	}

	/**
	 * What an engine is fitted with besides its banks: {@code turbos} and {@code blowers} are 0 for none, 1 for the first
	 * bank only and 2 for every bank; the variable flags swap in the variable-geometry turbo and the variable-drive
	 * blower; {@code intercooler} cools the charge from either; {@code instruments} adds a second flywheel, a dynamometer,
	 * a governor, a black box and an oil sump.
	 */
	record Kit(Fuel fuel, int turbos, boolean variableTurbo, int blowers, boolean variableBlower,
		boolean intercooler, boolean instruments) {

		static Kit plain(Fuel fuel) {
			return new Kit(fuel, 0, false, 0, false, false, false);
		}
	}

	/** The set of engines this run builds: the plain engines, or the ones with accessories ({@code -PshowcaseSet}). */
	static final boolean ACCESSORIES = "accessories".equals(System.getProperty(PROPERTY + ".set"));
	/** The trailer: the plain engines, then a scripted camera that moves smoothly past them ({@code -PshowcaseSet=trailer}). */
	static final boolean TRAILER = "trailer".equals(System.getProperty(PROPERTY + ".set"));

	/** The wheeled car's pad, apart from the courtyards: open ground, no walls, as a car needs room to drive. */
	static final int AERO_BAY = 11;
	/** The pads after the wheeled car's (which drives east across bays 12 to 14): the casing styles, the big block, the service bay. */
	static final int CASING_BAY = 15;
	static final int BIGBLOCK_BAY = 16;
	static final int SERVICE_BAY = 17;
	static final int FUEL_BAY = 18;
	/** The pad of the twin-rotor rotary. */
	static final int ROTARY_BAY = 4;
	/**
	 * Where the wheeled car's structure is in the world right now, and where it was when it was lifted off its pad, or null before
	 * then (written by {@link ShowcaseRunAero}, which exists only for runs with Create Aeronautics and Sable).
	 */
	static volatile double[] aeroAt;
	static volatile double[] aeroStart;

	static final List<Step> STEPS = new ArrayList<>();
	static final List<Tank> TANKS = new ArrayList<>();
	static final List<Scene> SCENES = new ArrayList<>();
	static boolean worldRequested;
	static int ticksInWorld = -1;
	static BlockPos origin = BlockPos.ZERO;

	private ShowcaseRun() {
	}

	public static void register() {
		if (!Boolean.getBoolean(PROPERTY))
			return;
		CreateEngines.LOGGER.info("Showcase run enabled");
		NeoForge.EVENT_BUS.addListener(ShowcaseRun::onClientTick);
		if (TRAILER && aero())
			ShowcaseRunAero.listen();
		if (TRAILER)
		{
			NeoForge.EVENT_BUS.addListener(ShowcaseRunHooks::onRenderFrame);
			NeoForge.EVENT_BUS.addListener(ShowcaseRunHooks::onPlaySound);
			com.createengines.content.EngineNoteSound.hearOnly(ShowcaseRunCamera::engineInShot);
			NeoForge.EVENT_BUS.addListener(ShowcaseRunHooks::onRenderGuiLayer);
			NeoForge.EVENT_BUS.addListener(ShowcaseRunHooks::onFov);
		}
		schedule();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The run
	// -----------------------------------------------------------------------------------------------------------------

	/** Whether this run has Create Aeronautics and Sable, so the wheeled car can be built (the trailer's other shots do not need them). */
	static boolean aero() {
		return com.createengines.compat.sable.SableCompat.installed();
	}

	static BlockPos pad(int bay) {
		// Five pads to a row, so every engine stays inside the simulation distance of the camera however many pads there are
		// The sixth row goes north of the first: one further south would be past the view distance from where the run starts
		int row = bay / 5 == 5 ? -1 : bay / 5;
		return origin.offset(bay % 5 * BAY, 0, row * BAY);
	}

	static void schedule() {
		defineScenes();

		STEPS.add(new Step(40, () -> {
			Minecraft.getInstance().options.hideGui = true;
			// A window of 1920x1080 is taller than a 1080p screen once it has a title bar, so the trailer goes fullscreen
			if (TRAILER && !Minecraft.getInstance()
				.getWindow()
				.isFullscreen())
				Minecraft.getInstance()
					.getWindow()
					.toggleFullScreen();
			server(server -> {
				ServerLevel level = server.overworld();
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 3))
					.getY();
				origin = new BlockPos(0, y, 3);
				command(server, "gamerule sendCommandFeedback false");
				command(server, "gamerule doDaylightCycle false");
				command(server, "gamerule doWeatherCycle false");
				command(server, "gamerule doMobSpawning false");
				command(server, "gamerule announceAdvancements false");
				command(server, "weather clear");
				command(server, "time set 6000");
				for (Scene scene : SCENES)
					if (scene.bay() != AERO_BAY)
						floor(level, pad(scene.bay()), scene.bay() != ShowcaseRunSounds.V8_BAY);
			});
		}));

		STEPS.add(new Step(70, () -> server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = server.getPlayerList()
				.getPlayers()
				.get(0);
			player.getInventory()
				.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 64));
			player.getInventory()
				.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 64));
			player.getInventory()
				.add(new ItemStack(CEItems.SPARK_PLUG.get(), 64));
			TANKS.clear();
			if (ACCESSORIES) {
				BankBuilderItem.Layout vee = BankBuilderItem.Layout.VEE;
				bank(level, player, pad(0), vee, 4, new Kit(Fuel.GASOLINE, 2, false, 0, false, false, false));
				bank(level, player, pad(1), BankBuilderItem.Layout.FLAT, 3, new Kit(Fuel.GASOLINE, 0, false, 2, false, true, false));
				bank(level, player, pad(2), BankBuilderItem.Layout.INLINE, 6, new Kit(Fuel.GASOLINE, 0, false, 1, true, true, false));
				bank(level, player, pad(3), BankBuilderItem.Layout.INLINE, 4, new Kit(Fuel.DIESEL, 1, true, 0, false, true, false));
				bank(level, player, pad(4), BankBuilderItem.Layout.INLINE, 4, new Kit(Fuel.DIESEL, 0, false, 0, false, false, true));
				return;
			}
			firstEngine(level, pad(0));
			bank(level, player, pad(1), BankBuilderItem.Layout.INLINE, 6, new Kit(Fuel.DIESEL, 1, false, 0, false, false, false));
			bank(level, player, pad(2), BankBuilderItem.Layout.VEE, 4, Kit.plain(Fuel.GASOLINE));
			bank(level, player, pad(3), BankBuilderItem.Layout.FLAT, 3, Kit.plain(Fuel.GASOLINE));
			rotary(level, pad(4));
			if (TRAILER) {
				electric(level, player, pad(5));
				bank(level, player, pad(6), BankBuilderItem.Layout.INLINE, 6,
					new Kit(Fuel.GASOLINE, 0, false, 1, true, true, false));
				bank(level, player, pad(7), BankBuilderItem.Layout.INLINE, 4,
					new Kit(Fuel.DIESEL, 0, false, 0, false, false, true));
				// The later pads (a pad that cannot be built is logged, and the others still stand)
					try {
						casing(level, pad(CASING_BAY));
					} catch (RuntimeException e) {
						CreateEngines.LOGGER.error("Showcase: the casing pad failed to build", e);
					}
					try {
						bigBlock(level, player, pad(BIGBLOCK_BAY));
					} catch (RuntimeException e) {
						CreateEngines.LOGGER.error("Showcase: the big block failed to build", e);
					}
					try {
						service(level, player, pad(SERVICE_BAY));
					} catch (RuntimeException e) {
						CreateEngines.LOGGER.error("Showcase: the service bay failed to build", e);
					}
					try {
						// The turbo six's stack wears a rain cap
						rainCaps(level, pad(1));
					} catch (RuntimeException e) {
						CreateEngines.LOGGER.error("Showcase: the rain cap failed to build", e);
					}
					// Pad 10 stays bare: the growing-engine clip builds on it during the shot
				// A pad that cannot be built is logged and the others still stand, so one mistake costs one shot, not the run
				try {
					factory(level, player, pad(8));
				} catch (RuntimeException e) {
					CreateEngines.LOGGER.error("Showcase: the factory pad failed to build", e);
				}
				try {
					ShowcaseRunFuels.build(level, pad(FUEL_BAY), CZ);
				} catch (RuntimeException e) {
					CreateEngines.LOGGER.error("Showcase: the fuel tanks failed to build", e);
				}
				try {
					refinery(level, pad(9));
				} catch (RuntimeException e) {
					CreateEngines.LOGGER.error("Showcase: the refinery pad failed to build", e);
				}
				ShowcaseRunFeatures.build(level, player);
				if (TRAILER) {
					ShowcaseRunSounds.build(level, player);
					ShowcaseRunWear.build(level, player);
				}
				if (aero())
					try {
						rover(level, player, pad(AERO_BAY));
					} catch (RuntimeException e) {
						CreateEngines.LOGGER.error("Showcase: the wheeled car failed to build", e);
					}
			}
		})));

		// The service bay's used-oil pump is switched on once its basin has charcoal and clay
		if (TRAILER)
			STEPS.add(new Step(600, () -> server(server -> {
				if (serviceUsedOilDrive != null)
					serviceUsedOilDrive.run();
			})));

		// Every engine has been found by now: set the firing order of each
		STEPS.add(new Step(260, () -> server(server -> {
			for (Scene scene : SCENES)
				if (scene.crank() != null)
					TimingToolItem.apply(server.overworld(), pad(scene.bay()).offset(scene.crank()), FiringPlanner.Style.SMOOTH);
		})));

		// Keep every tank topped up for the whole run
		for (int tick = 100; tick < 9000; tick += 40)
			STEPS.add(new Step(tick, ShowcaseRunBuild::fuelUp));

		// Release the starters; the engines carry on by themselves
		STEPS.add(new Step(520, () -> server(server -> {
			for (Scene scene : SCENES)
				if (scene.crank() != null)
					server.overworld()
						.removeBlock(pad(scene.bay()).offset(starterPower(scene)), false);
		})));
		STEPS.add(new Step(900, () -> logEngines("running")));

		if (TRAILER) {
			scheduleTrailer();
			return;
		}

		// The day tour: every scene from every angle
		int tick = 1000;
		STEPS.add(new Step(tick - 5, () -> server(server -> command(server, "time set 2500"))));
		for (Scene scene : SCENES)
			for (View view : scene.views()) {
				orbit(scene, view, "day", tick);
				tick += 40;
			}
		STEPS.add(new Step(tick, () -> logEngines("after the day tour")));

		if (ACCESSORIES) {
			STEPS.add(new Step(tick + 20, () -> Minecraft.getInstance()
				.stop()));
			return;
		}

		// Dusk, when the glow and the flames show
		tick += 20;
		int dusk = tick;
		STEPS.add(new Step(dusk, () -> server(server -> command(server, "time set 11800"))));
		tick += 40;
		for (Scene scene : SCENES)
			for (int i = 0; i < Math.min(2, scene.views()
				.size()); i++) {
				orbit(scene, scene.views()
					.get(i), "dusk", tick);
				tick += 40;
			}
		STEPS.add(new Step(tick + 20, () -> {
			logEngines("at dusk");
			Minecraft.getInstance()
				.stop();
		}));
	}

	static void defineScenes() {
		if (ACCESSORIES) {
			defineAccessoryScenes();
			return;
		}
		// The first engine: one cylinder, the way everyone starts
		SCENES.add(new Scene("first", 0, TestEngine.PRIMARY, false, List.of(
			new View("hero", 3, 3.2, 2.5, -40, 14, 6),
			new View("exhaust", 3, 3.2, 2.5, 155, 12, 6),
			new View("close", 2.5, 3.5, 2.5, -15, 20, 3.8),
			new View("top", 3, 3, 2.5, -30, 52, 6.5))));
		// An inline six with a turbocharger on its exhaust
		SCENES.add(new Scene("inline6", 1, new BlockPos(1, CY, CZ), true, List.of(
			new View("hero", 6, 4, CZ, -40, 14, 9),
			new View("intake", 6, 4, CZ, 0, 8, 8.5),
			new View("exhaust", 6, 4, CZ, 160, 10, 8.5),
			new View("turbo", 8.5, 5, CZ + 1, 105, 12, 4.5),
			new View("top", 6, 4, CZ, -20, 52, 9.5))));
		// A V8, from the front it is the V that shows
		double vz = CZ + BESIDE_Z;
		SCENES.add(new Scene("v8", 2, new BlockPos(1, CY, CZ), true, List.of(
			new View("hero", 4.5, 4, vz, -40, 15, 8),
			new View("front", 4.5, 4, vz, -90, 10, 6.5),
			new View("side", 4.5, 4, vz, 0, 10, 8),
			new View("exhaust", 4.5, 4, vz, 145, 12, 8),
			new View("top", 4.5, 4, vz, -25, 55, 8.5))));
		// A flat six
		SCENES.add(new Scene("flat6", 3, new BlockPos(1, CY, CZ), true, List.of(
			new View("hero", 4, 3, CZ, -40, 14, 7.5),
			new View("front", 4, 3, CZ, -80, 8, 6.5),
			new View("side", 4, 3, CZ, 0, 6, 8),
			new View("exhaust", 4, 3, CZ, 150, 12, 7.5),
			new View("top", 4, 3, CZ, -25, 55, 8.5))));
		// A twin-rotor Wankel
		SCENES.add(new Scene("rotary", 4, TestEngine.PRIMARY, false, List.of(
			new View("hero", 3, 3, 2.5, -40, 15, 6),
			new View("side", 3, 3, 2.5, 180, 5, 6.5),
			new View("close", 3, 3, 2.5, -20, 38, 4.5),
			new View("exhaust", 3, 3, 2.5, 150, 13, 5.5))));
		if (TRAILER)
			defineTrailerScenes();
	}

	/**
	 * The trailer's extra pads, after the plain engines: the electrical parts (a joined bank of all three battery sizes, and
	 * an ignition coil and an injector assist on every head), a blown inline six, and the test-bed diesel. When a significant
	 * part is added to the mod it goes on one of these (or a new pad), and in {@code src/smoke/trailer-parts.txt}.
	 */
	static void defineTrailerScenes() {
		BlockPos crank = new BlockPos(1, CY, CZ);
		SCENES.add(new Scene("electric", 5, crank, true, List.of(
			new View("hero", 4.5, 4.5, CZ, -40, 14, 9),
			new View("battery", 1, 5.5, CZ, -50, 12, 5),
			new View("heads", 4.5, 5.5, CZ, -35, 28, 5.5),
			new View("alternator", 0.5, 3.6, CZ, -60, 12, 5))));
		SCENES.add(new Scene("blown_i6", 6, crank, true, List.of(
			new View("hero", 5.5, 4, CZ - 1, -50, 14, 10.5),
			new View("blower", 1, 5, CZ - 2.5, -65, 14, 6))));
		SCENES.add(new Scene("instruments_i4", 7, crank, true, List.of(
			new View("hero", 3.5, 4, CZ, -40, 14, 9),
			new View("dyno", 1, 4, CZ, -60, 18, 5),
			new View("governor", 6, 4, CZ, 125, 16, 5))));
		// A diesel four driving a row of Create machines from the near end of its crank
		SCENES.add(new Scene("factory", 8, crank, true, List.of(
			new View("line", -2.5, 3, CZ, -55, 14, 6.5),
			new View("wide", 0.5, 3.5, CZ, -35, 16, 11),
			new View("flywheel", 9.5, 3.2, CZ, 35, 10, 6),
			new View("gearbox", 0.5, 3, CZ, 25, 14, 6))));
		// A refining column, three by three and three layers of sections tall, with its flare stack
		SCENES.add(new Scene("refinery", 9, null, false, List.of(
			new View("column", REFINERY_X + 1, 3.5, REFINERY_Z + 1, -35, 12, 12),
			new View("flare", REFINERY_X + 1, 6, REFINERY_Z + 1, 40, 8, 9),
				new View("base", REFINERY_X + 0.5, 1.6, REFINERY_Z - 0.5, -40, 14, 8),
				new View("tops", REFINERY_X + 0.5, 6.4, REFINERY_Z, -40, 12, 7.5))));
		// The growing engine: a bare pad, and the player's own eyes standing still in front of it
		SCENES.add(new Scene("grow", 10, null, false, List.of(
			new View("stand", 7, 4, CZ + BESIDE_Z * 0.5, -20, 14, 12))));
		// The four casing styles on one shaft line, between pillars of the same casing (and a Maker's Plate), turned by a motor
		SCENES.add(new Scene("casing", CASING_BAY, null, false, List.of(
			new View("row", 9.5, CY + 0.5, CZ, -10, 9, 13),
			new View("plate", 2.5, CY + 1.5, CZ - 1, -35, 10, 5))));
		// A precision turbocharged V16 with the heavy starter under its first journal: it is cold until the starter's cue
		double bz = CZ + BESIDE_Z;
		SCENES.add(new Scene("bigblock", BIGBLOCK_BAY, crank, true, List.of(
			new View("starter", 1, CY - 0.6, CZ, -55, 10, 5.5),
			new View("hero", 6.5, 4, bz, -45, 13, 14),
			new View("stand", 8, 3.6, bz, -80, 6, 9))));
		// A diesel four with an oil pan, beside a small service line: a heated basin and mixer that clean its used oil, and a spout
		// filling jerry cans with diesel
		SCENES.add(new Scene("service", SERVICE_BAY, crank, true, List.of(
			new View("hero", 3, 3.5, CZ, -40, 14, 12),
			new View("sump", 1, CY, CZ - 1, -40, 14, 6),
			new View("station", -1, 1.6, CZ + 4, 180, 14, 9),
			new View("mixer", -2, 3.2, CZ - 0.5, -35, 14, 7))));
		// The later pads of ShowcaseRunFeatures; each shot gives its own framing, so their one view is only for the record
		View over = new View("hero", 6, 4, CZ, -40, 14, 10);
		SCENES.add(new Scene("heat", ShowcaseRunFeatures.HEAT_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("tuning", ShowcaseRunFeatures.TUNING_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("sound", ShowcaseRunFeatures.SOUND_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("gasket", ShowcaseRunFeatures.GASKET_BAY, null, false, List.of(over)));
		SCENES.add(new Scene("night", ShowcaseRunFeatures.NIGHT_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("coldstart", ShowcaseRunFeatures.HOOK_BAY, crank, false, List.of(over)));
		// The pads of ShowcaseRunSounds and ShowcaseRunWear
		SCENES.add(new Scene("open_v8", ShowcaseRunSounds.V8_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("diesel", ShowcaseRunSounds.DIESEL_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("wear", ShowcaseRunWear.WEAR_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("rods", ShowcaseRunWear.ROD_BAY, crank, true, List.of(over)));
		SCENES.add(new Scene("wear_parts", ShowcaseRunWear.PARTS_BAY, null, false, List.of(over)));
		// The five fuels, a tank of each
		SCENES.add(new Scene("fuels", FUEL_BAY, null, false, List.of(
			new View("row", 7.5, 2.5, CZ, 0, 10, 10))));
		// An inline six on a moving structure, driving itself on wheels: only with Create Aeronautics (its Offroad wheels) and Sable in
		// the run. Its views are offsets from the craft, which the camera follows (see pose)
		if (aero())
			SCENES.add(new Scene("rover", AERO_BAY, crank, true, List.of(
				new View("side", 5.5, 2.5, CZ, -90, 8, 20),
				new View("hero", 5.5, 2.5, CZ, -50, 14, 19),
				new View("wheel", 16, 1.5, CZ, -60, 8, 8))));
	}

	/** The engines with accessories: turbos, blowers, intercoolers, flywheels and instruments. */
	static void defineAccessoryScenes() {
		BlockPos crank = new BlockPos(1, CY, CZ);
		double vz = CZ + BESIDE_Z;
		// A V8 with a turbo on each bank and a stack on each turbo
		SCENES.add(new Scene("twinturbo_v8", 0, crank, true, List.of(
			new View("hero", 4.5, 4, vz, -40, 15, 9),
			new View("turbos", 6.3, 4.5, vz, 165, 22, 6),
			new View("exhaust", 4.5, 4, vz, 150, 12, 9),
			new View("front", 4.5, 4, vz, -90, 10, 7),
			new View("top", 4.5, 4, vz, -25, 55, 9.5))));
		// A flat six with a blower and an intercooler on each bank
		SCENES.add(new Scene("blown_flat6", 1, crank, true, List.of(
			new View("hero", 3.5, 3, CZ - 1, -55, 14, 9),
			new View("blowers", 1, 3, CZ - 2.5, -65, 12, 7),
			new View("side", 3.5, 3, CZ - 1, 0, 6, 9),
			new View("exhaust", 4, 3, CZ, 150, 12, 8),
			new View("top", 3.5, 3, CZ - 1, -25, 55, 9.5))));
		// An inline six with a variable-drive blower and an intercooler
		SCENES.add(new Scene("blown_i6", 2, crank, true, List.of(
			new View("hero", 5.5, 4, CZ - 1, -50, 14, 10.5),
			new View("blower", 1, 5, CZ - 2.5, -65, 14, 6),
			new View("intake", 5.5, 4, CZ - 1, 0, 8, 10),
			new View("exhaust", 6, 4, CZ, 160, 10, 9),
			new View("top", 5.5, 4, CZ - 1, -20, 52, 11))));
		// A diesel four with a variable-geometry turbo and an intercooler
		SCENES.add(new Scene("vgt_i4", 3, crank, true, List.of(
			new View("hero", 4.5, 4, CZ, -40, 14, 9),
			new View("turbo", 6, 5, CZ + 1, 165, 22, 5.5),
			new View("intake", 4.5, 4, CZ, 0, 8, 8.5),
			new View("exhaust", 4.5, 4, CZ, 155, 10, 8.5),
			new View("top", 4.5, 4, CZ, -20, 52, 9))));
		// A diesel four on the test bed: two flywheels, dynamometer, governor, black box and sump
		SCENES.add(new Scene("instruments_i4", 4, crank, true, List.of(
			new View("hero", 3.5, 4, CZ, -40, 14, 9),
			new View("dyno", 1, 4, CZ, -60, 18, 5),
			new View("governor", 6, 4, CZ, 125, 16, 5),
			new View("side", 4, 4, CZ, 0, 8, 9),
			new View("top", 4, 4, CZ, -25, 52, 9))));
	}

	/** A pad that cannot be built is logged and the others still stand, so one mistake costs one shot, not the run. */
	static void attempt(String what, Runnable build) {
		try {
			build.run();
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.error("Showcase: {} failed to build", what, e);
		}
	}

	static BlockState axisX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	static void set(ServerLevel level, BlockPos at, int x, int y, int z, BlockState state) {
		level.setBlock(at.offset(x, y, z), state, Block.UPDATE_ALL);
	}

	static void register(BlockPos head, Fuel fuel) {
		TANKS.add(new Tank(head, fuel));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Pictures
	// -----------------------------------------------------------------------------------------------------------------

	/** Stands the camera {@code view.distance} blocks back from the view's target, looking at it, and takes two pictures. */
	static void orbit(Scene scene, View view, String phase, int tick) {
		String name = "showcase_" + scene.name() + "_" + view.label() + "_" + phase;
		STEPS.add(new Step(tick, () -> {
			double yaw = Math.toRadians(view.yaw());
			double pitch = Math.toRadians(view.pitch());
			BlockPos pad = pad(scene.bay());
			double tx = pad.getX() + view.x();
			double ty = pad.getY() + view.y();
			double tz = pad.getZ() + view.z();
			double x = tx + Math.sin(yaw) * Math.cos(pitch) * view.distance();
			double y = ty + Math.sin(pitch) * view.distance();
			double z = tz - Math.cos(yaw) * Math.cos(pitch) * view.distance();
			y = Math.max(y, pad.getY() + 1.8);
			aim(x, y - 1.62, z, view.yaw(), view.pitch());
		}));
		STEPS.add(new Step(tick + 18, () -> shot(name + "_a")));
		STEPS.add(new Step(tick + 30, () -> shot(name + "_b")));
	}

	/** Puts the player, feet at the position, on an invisible barrier. */
	static void aim(double x, double feetY, double z, float yaw, float pitch) {
		server(server -> {
			command(server, String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier", (int) Math.floor(x),
				(int) Math.floor(feetY) - 1, (int) Math.floor(z)));
			command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, feetY, z, yaw, pitch));
		});
	}

	static void logPlumbing(String when) {
		MinecraftServer server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server == null)
			return;
		server.execute(() -> {
			ServerLevel level = server.overworld();
			var fluid = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK;
			StringBuilder out = new StringBuilder();
			for (var entry : java.util.Map.of("refinery stock", refineryStockTank == null ? BlockPos.ZERO : refineryStockTank,
				"refinery product", refineryProductTank == null ? BlockPos.ZERO : refineryProductTank, "service diesel stock",
				serviceStockTank == null ? BlockPos.ZERO : serviceStockTank, "service engine tank",
				serviceEngineTank == null ? BlockPos.ZERO : serviceEngineTank)
				.entrySet()) {
				var handler = level.getCapability(fluid, entry.getValue(), Direction.UP);
				out.append(entry.getKey())
					.append(' ')
					.append(handler == null ? "no tank" : handler.getFluidInTank(0)
						.getAmount() + " mB")
					.append("; ");
			}
			if (servicePan != null && level.getBlockEntity(servicePan) instanceof com.createengines.content.sump.SumpBlockEntity sump)
				out.append("pan oil ")
					.append(sump.tank()
						.getFluidAmount())
					.append(", used ")
					.append(sump.usedTank()
						.getFluidAmount())
					.append("; ");
			if (serviceBasin != null) {
				var basin = level.getCapability(fluid, serviceBasin, Direction.UP);
				out.append("basin ")
					.append(basin == null ? "none" : basinTanks(basin))
					.append("; ");
				if (level.getBlockEntity(serviceBasin.above(2)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity mixer)
					out.append("mixer ")
						.append(mixer.getSpeed())
						.append(" rpm; ");
				for (BlockPos link : new BlockPos[] { serviceBasin.offset(2, 1, 0), serviceBasin.offset(1, 1, 0),
					serviceBasin.offset(1, 2, 0) })
					if (level.getBlockEntity(link) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic)
						out.append("link ")
							.append(link.subtract(serviceBasin)
								.toShortString())
							.append(' ')
							.append(kinetic.getSpeed())
							.append(kinetic.isOverStressed() ? " overstressed" : "")
							.append("; ");
			}
			for (BlockPos head : SERVICE_HEADS)
				if (level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be)
					out.append("head fuel ")
						.append(be.tank()
							.getFluidAmount())
						.append(' ');
			if (refineryFeed != null && level.getBlockEntity(refineryFeed) instanceof com.createengines.content.refinery.ColumnPortBlockEntity port) {
				out.append("; column feed ")
					.append(port.feedTank()
						.getFluidAmount())
					.append(", product ")
					.append(port.productTank()
						.getFluidAmount())
					.append(", catalyst ")
					.append(port.heldCatalyst());
				out.append(", limit ")
					.append(port.limit())
					.append(", heat ")
					.append(port.heat())
					.append(", formed ")
					.append(port.shape()
						.formed());
			}
			for (BlockPos pos : PUMPS)
				if (level.getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic)
					out.append(String.format(Locale.ROOT, "pump %d,%d,%d %.0f rpm; ", pos.getX(), pos.getY(), pos.getZ(), kinetic.getSpeed()));
			for (BlockPos pos : new BlockPos[] { refineryBarrel, serviceBarrel })
				if (pos != null && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)
					out.append("barrel ")
						.append(pos.toShortString())
						.append(" slot0 ")
						.append(barrel.getItem(0))
						.append(" slot1 ")
						.append(barrel.getItem(1))
						.append("; ");
			if (serviceBasin != null) {
				var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, serviceBasin, Direction.UP);
				if (items != null)
					for (int slot = 0; slot < items.getSlots(); slot++)
						if (!items.getStackInSlot(slot)
							.isEmpty())
							out.append("basin item ")
								.append(items.getStackInSlot(slot))
								.append("; ");
			}
			for (BlockPos pos : new BlockPos[] { refineryBarrel, serviceBarrel })
				if (pos != null)
					for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new net.minecraft.world.phys.AABB(pos).inflate(4)))
						out.append("item entity ")
							.append(item.getItem())
							.append(" at ")
							.append(item.blockPosition()
								.subtract(pos)
								.toShortString())
							.append("; ");
			if (refineryFeed != null)
				out.append("reboiler funnel state ")
					.append(level.getBlockState(refineryFeed.west()))
					.append("; ");
			if (!out.isEmpty())
				CreateEngines.LOGGER.info("Showcase plumbing {}: {}", when, out);
		});
	}

	static void logEngines(String when) {
		logPlumbing(when);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		for (Scene scene : SCENES) {
			if (scene.crank() == null)
				continue;
			BlockPos crank = pad(scene.bay()).offset(scene.crank());
			if (minecraft.level.getBlockEntity(crank) instanceof CrankshaftBlockEntity be)
				CreateEngines.LOGGER.info("Showcase {} {}: {}", scene.name(), when, be.summary());
			else
				CreateEngines.LOGGER.info("Showcase {} {}: no crankshaft at {}", scene.name(), when, crank);
			// The factory's transmission: its setting (15 is auto) and how fast its shaft turns, to tell a stalled row from a slow one
			if (scene.name().equals("factory") && minecraft.level.getBlockEntity(pad(scene.bay()).offset(0, CY, CZ))
				instanceof com.createengines.content.transmission.TransmissionBlockEntity box)
				CreateEngines.LOGGER.info("Showcase factory transmission {}: setting {}, shaft {} rpm", when, box.panel.getValue(),
					box.getSpeed());
		}
	}

	static void shot(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
			message -> CreateEngines.LOGGER.info("Showcase screenshot: {}", message.getString()));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Plumbing for the run itself
	// -----------------------------------------------------------------------------------------------------------------

	static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!worldRequested) {
			if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
				worldRequested = true;
				minecraft.options.pauseOnLostFocus = false;
				createWorld(minecraft);
			}
			return;
		}
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null)
			return;
		ticksInWorld++;
		double[] pose = trailerPose;
		if (pose != null && !trailerOver && ticksInWorld % 5 == 0)
			server(server -> teleport(server, pose));
		if (TRAILER && ticksInWorld % 10 == 0)
			sweepSounds();
		for (Step step : STEPS)
			if (step.tick() == ticksInWorld)
				step.action()
					.run();
	}

	static void createWorld(Minecraft minecraft) {
		LevelSettings settings = new LevelSettings("engine_showcase_" + System.currentTimeMillis(), GameType.CREATIVE, false,
			Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(0, false, false);
		minecraft.createWorldOpenFlows()
			.createFreshLevel(settings.levelName(), settings, options, registries -> registries
				.registryOrThrow(Registries.WORLD_PRESET)
				.getHolderOrThrow(WorldPresets.FLAT)
				.value()
				.createWorldDimensions(), minecraft.screen);
	}

	static void server(java.util.function.Consumer<MinecraftServer> action) {
		MinecraftServer server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server != null)
			server.execute(() -> action.accept(server));
	}

	static void command(MinecraftServer server, String command) {
		server.getCommands()
			.performPrefixedCommand(server.createCommandSourceStack(), command);
	}
}
