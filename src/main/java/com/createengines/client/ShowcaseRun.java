package com.createengines.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Development aid for the mod page's screenshots, off unless the {@code showcase} run is used (gradle
 * {@code runShowcase}): builds a handful of engines of different kinds on stone pads in a flat world, runs them, and
 * photographs each from several angles at 1920x1080, then once more at dusk. Pictures land in
 * {@code run/showcase/screenshots}; each engine's state is written to the log so a picture of a stalled engine can be
 * told from one of a running engine.
 */
public final class ShowcaseRun {

	private static final String PROPERTY = "create_engines.showcase";

	/** The crankshaft's row and line in a banked engine, relative to the pad's corner. */
	private static final int CY = 3;
	private static final int CZ = 5;
	/** Distance between pads. Short enough that every engine is inside the simulation distance of the camera. */
	private static final int BAY = 36;
	/** Which way the Bank Builder puts the cam line and a vee's second bank, off the crank's line. */
	private static final int BESIDE_Z = Direction.UP.getClockWise(Axis.X)
		.getStepZ();

	private enum Fuel {
		DIESEL, GASOLINE
	}

	private record Step(int tick, Runnable action) {
	}

	private record Tank(BlockPos pos, Fuel fuel) {
	}

	/** A viewpoint, as a target relative to the pad's corner, a heading, and how far back to stand. */
	private record View(String label, double x, double y, double z, float yaw, float pitch, double distance) {
	}

	private record Scene(String name, int bay, BlockPos crank, boolean banked, List<View> views) {
	}

	/**
	 * What an engine is fitted with besides its banks: {@code turbos} and {@code blowers} are 0 for none, 1 for the first
	 * bank only and 2 for every bank; the variable flags swap in the variable-geometry turbo and the variable-drive
	 * blower; {@code intercooler} cools the charge from either; {@code instruments} adds a second flywheel, a dynamometer,
	 * a governor, a black box and an oil sump.
	 */
	private record Kit(Fuel fuel, int turbos, boolean variableTurbo, int blowers, boolean variableBlower,
		boolean intercooler, boolean instruments) {

		static Kit plain(Fuel fuel) {
			return new Kit(fuel, 0, false, 0, false, false, false);
		}
	}

	/** The set of engines this run builds: the plain engines, or the ones with accessories ({@code -PshowcaseSet}). */
	private static final boolean ACCESSORIES = "accessories".equals(System.getProperty(PROPERTY + ".set"));

	private static final List<Step> STEPS = new ArrayList<>();
	private static final List<Tank> TANKS = new ArrayList<>();
	private static final List<Scene> SCENES = new ArrayList<>();
	private static boolean worldRequested;
	private static int ticksInWorld = -1;
	private static BlockPos origin = BlockPos.ZERO;

	private ShowcaseRun() {
	}

	public static void register() {
		if (!Boolean.getBoolean(PROPERTY))
			return;
		CreateEngines.LOGGER.info("Showcase run enabled");
		NeoForge.EVENT_BUS.addListener(ShowcaseRun::onClientTick);
		schedule();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The run
	// -----------------------------------------------------------------------------------------------------------------

	private static BlockPos pad(int bay) {
		return origin.offset(bay * BAY, 0, 0);
	}

	private static void schedule() {
		defineScenes();

		STEPS.add(new Step(40, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				ServerLevel level = server.overworld();
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 3))
					.getY();
				origin = new BlockPos(0, y, 3);
				command(server, "gamerule sendCommandFeedback false");
				command(server, "gamerule doDaylightCycle false");
				command(server, "gamerule doWeatherCycle false");
				command(server, "gamerule doMobSpawning false");
				command(server, "weather clear");
				command(server, "time set 6000");
				for (Scene scene : SCENES)
					floor(level, pad(scene.bay()));
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
		})));

		// Every engine has been found by now: set the firing order of each
		STEPS.add(new Step(260, () -> server(server -> {
			for (Scene scene : SCENES)
				TimingToolItem.apply(server.overworld(), pad(scene.bay()).offset(scene.crank()), FiringPlanner.Style.SMOOTH);
		})));

		// Keep every tank topped up for the whole run
		for (int tick = 100; tick < 4200; tick += 40)
			STEPS.add(new Step(tick, ShowcaseRun::fuelUp));

		// Release the starters; the engines carry on by themselves
		STEPS.add(new Step(520, () -> server(server -> {
			for (Scene scene : SCENES)
				server.overworld()
					.removeBlock(pad(scene.bay()).offset(starterPower(scene)), false);
		})));
		STEPS.add(new Step(900, () -> logEngines("running")));

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

	private static BlockPos starterPower(Scene scene) {
		return scene.banked() ? new BlockPos(0, CY - 1, CZ) : TestEngine.STARTER_POWER;
	}

	private static void defineScenes() {
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
	}

	/** The engines with accessories: turbos, blowers, intercoolers, flywheels and instruments. */
	private static void defineAccessoryScenes() {
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

	// -----------------------------------------------------------------------------------------------------------------
	// Building
	// -----------------------------------------------------------------------------------------------------------------

	private static Block block(String id, Block fallback) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
			.orElse(fallback);
	}

	private static final int PAD_X0 = -10;
	private static final int PAD_X1 = 24;
	private static final int PAD_Z0 = -10;
	private static final int PAD_Z1 = 18;
	private static final int WALL = 8;

	/**
	 * A courtyard over the grass: a dark tiled floor with a copper line round it, walls of deepslate brick with a wooden
	 * pillar every six blocks and a lantern on each, so every view has a backdrop instead of the horizon.
	 */
	private static void floor(ServerLevel level, BlockPos corner) {
		BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
		BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState line = Blocks.WAXED_CUT_COPPER.defaultBlockState();
		BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		for (int x = PAD_X0; x <= PAD_X1; x++)
			for (int z = PAD_Z0; z <= PAD_Z1; z++) {
				boolean border = x == PAD_X0 || x == PAD_X1 || z == PAD_Z0 || z == PAD_Z1;
				boolean inner = !border && (x == PAD_X0 + 1 || x == PAD_X1 - 1 || z == PAD_Z0 + 1 || z == PAD_Z1 - 1);
				BlockState floor = inner ? line : (x + z) % 2 == 0 ? tiles : polished;
				level.setBlock(corner.offset(x, -1, z), border ? wall : floor, Block.UPDATE_CLIENTS);
				if (!border)
					continue;
				boolean post = ((x - PAD_X0) % 6 == 0 && (z == PAD_Z0 || z == PAD_Z1))
					|| ((z - PAD_Z0) % 6 == 0 && (x == PAD_X0 || x == PAD_X1));
				for (int y = 0; y < WALL; y++)
					level.setBlock(corner.offset(x, y, z), post ? pillar : wall, Block.UPDATE_CLIENTS);
				if (post)
					level.setBlock(corner.offset(x, WALL, z), lantern, Block.UPDATE_CLIENTS);
			}
	}

	/** A one-block platform under a banked engine, so the engine and its starter stand on something. */
	private static void dais(ServerLevel level, BlockPos at, int length) {
		BlockState stone = Blocks.POLISHED_ANDESITE.defaultBlockState();
		for (int x = -1; x <= length; x++)
			for (int z = CZ - 2; z <= CZ + 2; z++)
				level.setBlock(at.offset(x, 0, z), stone, Block.UPDATE_CLIENTS);
	}

	private static BlockState axisX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	private static void set(ServerLevel level, BlockPos at, int x, int y, int z, BlockState state) {
		level.setBlock(at.offset(x, y, z), state, Block.UPDATE_ALL);
	}

	private static void register(BlockPos head, Fuel fuel) {
		TANKS.add(new Tank(head, fuel));
	}

	/** The crude single-cylinder diesel with a flywheel on its free end. */
	private static void firstEngine(ServerLevel level, BlockPos at) {
		TestEngine.build(level, at, false);
		set(level, at, 0, 2, 2, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
		register(at.offset(TestEngine.HEAD), Fuel.DIESEL);
	}

	/** A twin-rotor engine on gasoline. */
	private static void rotary(ServerLevel level, BlockPos at) {
		TestEngine.buildRotary(level, at, true);
		TestEngine.addSecondRotor(level, at);
		set(level, at, 6, 2, 2, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
		register(at.offset(TestEngine.HOUSING), Fuel.GASOLINE);
		register(at.offset(TestEngine.HOUSING_2), Fuel.GASOLINE);
	}

	/**
	 * A banked engine laid out by the Bank Builder: a crank along X with {@code count} throws side by side, a cam line
	 * driven from the crank's far end at half its speed, an intake pipe on one side of each bank and an exhaust pipe on
	 * the other, a flywheel on the end, and a starter underneath. The {@link Kit} says what else it wears: a turbo on the
	 * exhaust's east end (its compressor breathing from the east, its air coming back along the near side), a
	 * motor-driven blower with its intercooler and air intake at the intake's west end, or a plain throttle, muffler and
	 * outlet; and the instruments that go on a test-bed engine.
	 */
	private static void bank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit) {
		Fuel fuel = kit.fuel();
		boolean spark = fuel == Fuel.GASOLINE;
		int lastThrow = count + 1;
		dais(level, at, lastThrow + 5);
		// Engine casing legs under the far journal, to match the starter and motor under the near one
		for (int y = 1; y < CY; y++)
			set(level, at, lastThrow + 1, y, CZ, CEBlocks.ENGINE_CASING.getDefaultState());

		// The crank line: a journal at each end of the throws, then a shaft, the small cog that drives the cam, a flywheel
		set(level, at, 1, CY, CZ, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		for (int x = 2; x <= lastThrow; x++)
			set(level, at, x, CY, CZ, axisX(CEBlocks.CRANK_THROW.getDefaultState()));
		set(level, at, lastThrow + 1, CY, CZ, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set(level, at, lastThrow + 2, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, lastThrow + 3, CY, CZ, axisX(AllBlocks.COGWHEEL.getDefaultState()));
		set(level, at, lastThrow + 4, CY, CZ, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));

		// A starter under the first journal, a motor under that, a redstone block to engage it
		set(level, at, 1, CY - 1, CZ, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set(level, at, 1, CY - 2, CZ, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		if (level.getBlockEntity(at.offset(1, CY - 2, CZ)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(TestEngine.MOTOR_RPM);
		set(level, at, 0, CY - 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (spark) {
			set(level, at, 0, CY, CZ, axisX(CEBlocks.ALTERNATOR.getDefaultState()));
			set(level, at, 1, CY + 1, CZ, CEBlocks.BATTERY.getDefaultState());
		}

		// The cylinders, heads, plugs and cam lobes, as the Bank Builder lays them
		var result = BankBuilderItem.build(level, at.offset(2, CY, CZ), Direction.UP, layout, player, true);
		CreateEngines.LOGGER.info("Showcase bank {} at {}: {}", layout, at, result);

		// The cam line is carried on from its last lobe to a large cog meshing with the small one on the crank
		for (BlockPos beside : BankBuilderItem.camOffsets(layout, Axis.X, Direction.UP)) {
			int y = CY + beside.getY();
			int z = CZ + beside.getZ();
			for (int x = lastThrow + 1; x <= lastThrow + 2; x++)
				set(level, at, x, y, z, axisX(CEBlocks.CAMSHAFT.getDefaultState()));
			set(level, at, lastThrow + 3, y, z, axisX(AllBlocks.LARGE_COGWHEEL.getDefaultState()));
		}

		// Pipes and fittings on each bank
		int bankIndex = 0;
		for (Direction bore : BankBuilderItem.faces(layout, Axis.X, Direction.UP)) {
			boolean turbo = kit.turbos() == 2 || (kit.turbos() == 1 && bankIndex == 0);
			boolean blower = kit.blowers() == 2 || (kit.blowers() == 1 && bankIndex == 0);
			bankIndex++;
			Direction intake = intakeSide(bore);
			Direction exhaust = intake.getOpposite();
			List<BlockPos> intakeRow = new ArrayList<>();
			List<BlockPos> exhaustRow = new ArrayList<>();
			for (int x = 2; x <= lastThrow; x++) {
				BlockPos head = at.offset(x, CY, CZ)
					.relative(bore, 2);
				register(head, fuel);
				// Two blowers over-lean the default shot until nothing lights, so every head runs a rich one
				if (kit.blowers() == 2 && level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be)
					be.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
				intakeRow.add(head.relative(intake));
				exhaustRow.add(head.relative(exhaust));
			}
			BlockPos intakeFirst = intakeRow.get(0);
			BlockPos exhaustLast = exhaustRow.get(exhaustRow.size() - 1);

			if (turbo) {
				// Exhaust runs into the turbine on the row's east end and out of a stack; the compressor breathes from the
				// east, and its air goes back along the near side, through an intercooler or a throttle, into the intake
				BlockPos turboPos = exhaustLast.east();
				Block turboBlock = kit.variableTurbo() ? CEBlocks.VARIABLE_TURBOCHARGER.get() : CEBlocks.TURBOCHARGER.get();
				level.setBlock(turboPos, turboBlock.defaultBlockState()
					.setValue(com.createengines.content.turbo.TurbochargerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				level.setBlock(turboPos.east(), CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				Direction stack = bore == Direction.DOWN ? exhaust : bore;
				level.setBlock(turboPos.relative(stack), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, stack), Block.UPDATE_ALL);
				BlockPos feed = turboPos.relative(intake);
				if (kit.intercooler())
					level.setBlock(feed, CEBlocks.INTERCOOLER.getDefaultState()
						.setValue(com.createengines.content.intercooler.IntercoolerBlock.AXIS, intake.getAxis()), Block.UPDATE_ALL);
				else
					level.setBlock(feed, throttleState(exhaust, intake), Block.UPDATE_ALL);
				pipe(level, feed.relative(intake), CEBlocks.INTAKE_MANIFOLD.get());
			} else {
				// Gas out through a muffler and an outlet at the east end
				BlockPos muffler = exhaustLast.east();
				level.setBlock(muffler, axisX(CEBlocks.MUFFLER.getDefaultState()), Block.UPDATE_ALL);
				level.setBlock(muffler.east(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
			}

			if (blower) {
				// A blower on a shaft beside the intake's west end, a motor on its shaft, then the cooler and the air intake
				BlockPos west = intakeFirst.west();
				intakeRow.add(0, west);
				BlockPos blow = west.relative(intake);
				Block blowBlock = kit.variableBlower() ? CEBlocks.VARIABLE_SUPERCHARGER.get() : CEBlocks.SUPERCHARGER.get();
				level.setBlock(blow, axisX(blowBlock.defaultBlockState()), Block.UPDATE_ALL);
				level.setBlock(blow.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
					.setValue(CreativeMotorBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				if (level.getBlockEntity(blow.west()) instanceof CreativeMotorBlockEntity drive)
					drive.generatedSpeed.setValue(kit.blowers() == 2 ? 64 : 96);
				BlockPos next = blow.relative(intake);
				if (kit.intercooler()) {
					level.setBlock(next, CEBlocks.INTERCOOLER.getDefaultState()
						.setValue(com.createengines.content.intercooler.IntercoolerBlock.AXIS, intake.getAxis()), Block.UPDATE_ALL);
					next = next.relative(intake);
				}
				level.setBlock(next, CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, intake), Block.UPDATE_ALL);
			} else if (!turbo) {
				// Air in through a throttle at the west end
				BlockPos throttle = intakeFirst.west();
				level.setBlock(throttle, throttleState(Direction.EAST, Direction.WEST), Block.UPDATE_ALL);
				level.setBlock(throttle.west(), CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
			}
			for (BlockPos pos : intakeRow)
				pipe(level, pos, CEBlocks.INTAKE_MANIFOLD.get());
			for (BlockPos pos : exhaustRow)
				pipe(level, pos, CEBlocks.EXHAUST_MANIFOLD.get());
		}

		if (kit.instruments()) {
			// A second flywheel on the near end, a dynamometer on the first journal, a governor on the last, a black box
			// beside it, and an oil sump in place of the last leg
			set(level, at, 0, CY, CZ, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
			set(level, at, 1, CY + 1, CZ, CEBlocks.DYNO.getDefaultState());
			BlockPos governor = at.offset(lastThrow + 1, CY + 1, CZ);
			level.setBlock(governor, CEBlocks.GOVERNOR.getDefaultState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(governor) instanceof com.createengines.content.governor.GovernorBlockEntity be)
				be.target.setValue(80);
			set(level, at, lastThrow + 1, CY, CZ + 1, CEBlocks.BLACK_BOX.getDefaultState());
			BlockPos pan = at.offset(lastThrow + 1, CY - 1, CZ);
			level.setBlock(pan, CEBlocks.SUMP.getDefaultState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(pan) instanceof com.createengines.content.sump.SumpBlockEntity sump)
				sump.tank()
					.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
						.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
		}
	}

	/** The side of a bank's heads that takes the intake pipe: the other is exhaust. */
	private static Direction intakeSide(Direction bore) {
		for (Direction side : new Direction[] {Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN})
			if (side.getAxis() != bore.getAxis())
				return side;
		throw new IllegalStateException();
	}

	private static BlockState throttleState(Direction one, Direction other) {
		BlockState state = CEBlocks.THROTTLE.getDefaultState();
		return state.setValue(ManifoldBlock.CONNECTIONS.get(one), true)
			.setValue(ManifoldBlock.CONNECTIONS.get(other), true);
	}

	/** Places a pipe joined to everything round it that takes one. */
	private static void pipe(ServerLevel level, BlockPos pos, ManifoldBlock block) {
		BlockState state = block.defaultBlockState();
		for (Direction side : Direction.values())
			if (Pipes.accepts(level.getBlockState(pos.relative(side)), side.getOpposite(), block.kind()))
				state = state.setValue(ManifoldBlock.CONNECTIONS.get(side), true);
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void fuelUp() {
		server(server -> {
			ServerLevel level = server.overworld();
			for (Tank tank : TANKS) {
				FluidStack fuel = new FluidStack(tank.fuel() == Fuel.DIESEL ? CEFluids.DIESEL.get()
					.getSource()
					: CEFluids.GASOLINE.get()
						.getSource(),
					CylinderHeadBlockEntity.CAPACITY);
				FluidStack oil = new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), CylinderHeadBlockEntity.CAPACITY);
				if (level.getBlockEntity(tank.pos()) instanceof CylinderHeadBlockEntity head) {
					head.tank()
						.fill(fuel, IFluidHandler.FluidAction.EXECUTE);
					head.oilTank()
						.fill(oil, IFluidHandler.FluidAction.EXECUTE);
				} else if (level.getBlockEntity(tank.pos()) instanceof RotorHousingBlockEntity housing) {
					housing.tank()
						.fill(fuel, IFluidHandler.FluidAction.EXECUTE);
					housing.oilTank()
						.fill(oil, IFluidHandler.FluidAction.EXECUTE);
				}
			}
		});
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Pictures
	// -----------------------------------------------------------------------------------------------------------------

	/** Stands the camera {@code view.distance} blocks back from the view's target, looking at it, and takes two pictures. */
	private static void orbit(Scene scene, View view, String phase, int tick) {
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
	private static void aim(double x, double feetY, double z, float yaw, float pitch) {
		server(server -> {
			command(server, String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier", (int) Math.floor(x),
				(int) Math.floor(feetY) - 1, (int) Math.floor(z)));
			command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, feetY, z, yaw, pitch));
		});
	}

	private static void logEngines(String when) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		for (Scene scene : SCENES) {
			BlockPos crank = pad(scene.bay()).offset(scene.crank());
			if (minecraft.level.getBlockEntity(crank) instanceof CrankshaftBlockEntity be)
				CreateEngines.LOGGER.info("Showcase {} {}: {}", scene.name(), when, be.summary());
			else
				CreateEngines.LOGGER.info("Showcase {} {}: no crankshaft at {}", scene.name(), when, crank);
		}
	}

	private static void shot(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
			message -> CreateEngines.LOGGER.info("Showcase screenshot: {}", message.getString()));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Plumbing for the run itself
	// -----------------------------------------------------------------------------------------------------------------

	private static void onClientTick(ClientTickEvent.Post event) {
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
		for (Step step : STEPS)
			if (step.tick() == ticksInWorld)
				step.action()
					.run();
	}

	private static void createWorld(Minecraft minecraft) {
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

	private static void server(java.util.function.Consumer<MinecraftServer> action) {
		MinecraftServer server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server != null)
			server.execute(() -> action.accept(server));
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommands()
			.performPrefixedCommand(server.createCommandSourceStack(), command);
	}
}
