package com.createengines.client;

import java.io.IOException;
import java.nio.file.Files;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
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
	static final int CY = 3;
	static final int CZ = 5;
	/** Distance between pads. Short enough that every engine is inside the simulation distance of the camera. */
	private static final int BAY = 36;
	/** Which way the Bank Builder puts the cam line and a vee's second bank, off the crank's line. */
	private static final int BESIDE_Z = Direction.UP.getClockWise(Axis.X)
		.getStepZ();

	enum Fuel {
		DIESEL, GASOLINE
	}

	private record Step(int tick, Runnable action) {
	}

	record Tank(BlockPos pos, Fuel fuel) {
	}

	/** A viewpoint, as a target relative to the pad's corner, a heading, and how far back to stand. */
	private record View(String label, double x, double y, double z, float yaw, float pitch, double distance) {
	}

	/** A pad and the views of it. {@code crank} is the engine's crankshaft, or null for a pad with no engine (the refinery). */
	private record Scene(String name, int bay, BlockPos crank, boolean banked, List<View> views) {
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
	private static final boolean ACCESSORIES = "accessories".equals(System.getProperty(PROPERTY + ".set"));
	/** The trailer: the plain engines, then a scripted camera that moves smoothly past them ({@code -PshowcaseSet=trailer}). */
	private static final boolean TRAILER = "trailer".equals(System.getProperty(PROPERTY + ".set"));

	/** The wheeled car's pad, apart from the courtyards: open ground, no walls, as a car needs room to drive. */
	private static final int AERO_BAY = 11;
	/** The pads after the wheeled car's (which drives east across bays 12 to 14): the casing styles, the big block, the service bay. */
	private static final int CASING_BAY = 15;
	private static final int BIGBLOCK_BAY = 16;
	private static final int SERVICE_BAY = 17;
	/**
	 * Where the wheeled car's structure is in the world right now, and where it was when it was lifted off its pad, or null before
	 * then (written by {@link ShowcaseRunAero}, which exists only for runs with Create Aeronautics and Sable).
	 */
	static volatile double[] aeroAt;
	static volatile double[] aeroStart;

	private static final List<Step> STEPS = new ArrayList<>();
	static final List<Tank> TANKS = new ArrayList<>();
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
		if (TRAILER && aero())
			ShowcaseRunAero.listen();
		if (TRAILER)
		{
			NeoForge.EVENT_BUS.addListener(ShowcaseRun::onRenderFrame);
			NeoForge.EVENT_BUS.addListener(ShowcaseRun::onPlaySound);
			NeoForge.EVENT_BUS.addListener(ShowcaseRun::onRenderGuiLayer);
			NeoForge.EVENT_BUS.addListener(ShowcaseRun::onFov);
		}
		schedule();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The run
	// -----------------------------------------------------------------------------------------------------------------

	/** Whether this run has Create Aeronautics and Sable, so the wheeled car can be built (the trailer's other shots do not need them). */
	private static boolean aero() {
		return com.createengines.compat.sable.SableCompat.installed();
	}

	static BlockPos pad(int bay) {
		// Five pads to a row, so every engine stays inside the simulation distance of the camera however many pads there are
		return origin.offset(bay % 5 * BAY, 0, bay / 5 * BAY);
	}

	private static void schedule() {
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
					refinery(level, pad(9));
				} catch (RuntimeException e) {
					CreateEngines.LOGGER.error("Showcase: the refinery pad failed to build", e);
				}
				ShowcaseRunFeatures.build(level, player);
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
			STEPS.add(new Step(tick, ShowcaseRun::fuelUp));

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

	// -----------------------------------------------------------------------------------------------------------------
	// The trailer's later pads: the casing styles, the big block with its heavy starter, the service bay
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * The four casing styles on one shaft line: a Create motor turns, in each style, a cased shaft, a cased cogwheel, a large
	 * one and another cased shaft, between two pillars of that style's casing (a dye can be used on those, which the clip does)
	 * with a Maker's Plate on the first.
	 */
	private static void casing(ServerLevel level, BlockPos at) {
		dais(level, at, 19);
		set(level, at, 0, CY, CZ, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		if (level.getBlockEntity(at.offset(0, CY, CZ)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(32);
		set(level, at, 1, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		Block[][] styles = {
			{ CEBlocks.ENGINE_CASING.get(), CEBlocks.ENGINE_CASED_SHAFT.get(), CEBlocks.ENGINE_CASED_COGWHEEL.get(),
				CEBlocks.ENGINE_CASED_LARGE_COGWHEEL.get() },
			{ CEBlocks.ENGINE_RIVETED_CASING.get(), CEBlocks.ENGINE_RIVETED_CASED_SHAFT.get(),
				CEBlocks.ENGINE_RIVETED_CASED_COGWHEEL.get(), CEBlocks.ENGINE_RIVETED_CASED_LARGE_COGWHEEL.get() },
			{ CEBlocks.ENGINE_LOUVRED_CASING.get(), CEBlocks.ENGINE_LOUVRED_CASED_SHAFT.get(),
				CEBlocks.ENGINE_LOUVRED_CASED_COGWHEEL.get(), CEBlocks.ENGINE_LOUVRED_CASED_LARGE_COGWHEEL.get() },
			{ CEBlocks.ENGINE_WINDOW_CASING.get(), CEBlocks.ENGINE_WINDOW_CASED_SHAFT.get(),
				CEBlocks.ENGINE_WINDOW_CASED_COGWHEEL.get(), CEBlocks.ENGINE_WINDOW_CASED_LARGE_COGWHEEL.get() } };
		for (int s = 0; s < styles.length; s++) {
			int x = 2 + 4 * s;
			Block[] style = styles[s];
			for (int pillar : new int[] { x, x + 3 }) {
				set(level, at, pillar, CY, CZ, axisX(style[1].defaultBlockState()));
				for (int y : new int[] { CY - 2, CY - 1, CY + 1, CY + 2 })
					set(level, at, pillar, y, CZ, style[0].defaultBlockState());
			}
			// An encased cogwheel only passes the drive along its axis through the shaft stubs it is given (Create sets them when a
			// cogwheel is placed beside a shaft), so both ends of both are on or the line stops at the first cogwheel
			set(level, at, x + 1, CY, CZ, throughShaft(axisX(style[2].defaultBlockState())));
			set(level, at, x + 2, CY, CZ, throughShaft(axisX(style[3].defaultBlockState())));
		}
		set(level, at, 2, CY + 1, CZ - 1, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(com.createengines.content.plate.MakerPlateBlock.FACING, Direction.NORTH));
	}

	/** An encased cogwheel with a shaft stub out of both ends. */
	private static BlockState throughShaft(BlockState cogwheel) {
		return cogwheel.setValue(com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock.TOP_SHAFT, true)
			.setValue(com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock.BOTTOM_SHAFT, true);
	}

	/** The pillar blocks of {@link #casing} that the dye clip paints: x along the line, one above the shaft. */
	private static BlockPos casingPillar(int x, int above) {
		return pad(CASING_BAY).offset(x, CY + above, CZ);
	}

	/**
	 * A precision turbocharged V16 with the heavy starter under its first journal. It is built without its redstone block, so
	 * it stands cold until a cue lays one (see {@code defineCues}), and the heavy starter turns it over until it catches.
	 */
	private static void bigBlock(ServerLevel level, ServerPlayer player, BlockPos at) {
		bank(level, player, at, BankBuilderItem.Layout.VEE, 8, new Kit(Fuel.GASOLINE, 2, false, 0, false, true, false),
			STAGE_STRUCTURE | STAGE_BANK | STAGE_FITTINGS, true);
		set(level, at, 1, CY - 1, CZ, CEBlocks.HEAVY_STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Plumbing: real Create pumps, pipes, funnels, belts and tanks. Only a heat exchanger's heat, and the stock a tank or a barrel
	// starts with and is topped up from, are put there by the harness.
	// -----------------------------------------------------------------------------------------------------------------

	private static BlockState axisState(Block block, Axis axis) {
		return block.defaultBlockState()
			.setValue(BlockStateProperties.AXIS, axis);
	}

	/** A Mechanical Pump at the cell, pushing out of its front ({@code flow}) and pulling on its back. */
	private static final List<BlockPos> PUMPS = new ArrayList<>();

	private static void pump(ServerLevel level, BlockPos at, int x, int y, int z, Direction flow) {
		PUMPS.add(at.offset(x, y, z));
		set(level, at, x, y, z, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, flow));
	}

	/**
	 * Turns something with a cogwheel beside it: a small cogwheel on {@code axis} at the cell (a pump on the same axis beside it
	 * meshes with it), and a Create motor behind it on that axis, on the {@code motorSide}, driving it at {@code rpm}.
	 */
	private static void drive(ServerLevel level, BlockPos at, int x, int y, int z, Axis axis, Direction motorSide, int rpm) {
		set(level, at, x, y, z, axisState(AllBlocks.COGWHEEL.get(), axis));
		BlockPos motorAt = at.offset(x, y, z)
			.relative(motorSide);
		level.setBlock(motorAt, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, motorSide.getOpposite()), Block.UPDATE_ALL);
		if (level.getBlockEntity(motorAt) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(rpm);
	}

	/** Fluid pipes at the cells, each joined to the pipes, pumps and tanks beside it. */
	private static void pipes(ServerLevel level, BlockPos at, int[]... cells) {
		var block = AllBlocks.FLUID_PIPE.get();
		for (int[] cell : cells)
			level.setBlock(at.offset(cell[0], cell[1], cell[2]), block.defaultBlockState(), Block.UPDATE_ALL);
		for (int[] cell : cells) {
			BlockPos pos = at.offset(cell[0], cell[1], cell[2]);
			BlockState state = block.updateBlockState(level.getBlockState(pos), Direction.UP, null, level, pos);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(level, pos, state);
		}
	}

	/** A Create fluid tank {@code height} blocks tall, the cell its lowest block (stacked tanks join into one). */
	private static void tank(ServerLevel level, BlockPos at, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++)
			set(level, at, x, y + i, z, AllBlocks.FLUID_TANK.getDefaultState());
	}

	private static void stockTank(ServerLevel level, BlockPos pos, FluidStack fluid) {
		var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
		if (handler != null)
			handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
	}

	/** A barrel with {@code stock} on offer, topped up to it again whenever it runs low. */
	private static void stockBarrel(ServerLevel level, BlockPos pos, ItemStack... stock) {
		if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel))
			return;
		for (int slot = 0; slot < stock.length && slot < barrel.getContainerSize(); slot++)
			if (barrel.getItem(slot)
				.isEmpty())
				barrel.setItem(slot, stock[slot].copy());
	}

	/**
	 * An Andesite Funnel on the side of whatever is behind it, its open face towards {@code towards}. An extracting one pulls
	 * items out of what is behind it and throws them out of its open face; an inserting one takes whatever lands in it into
	 * what is behind it.
	 */
	private static void funnel(ServerLevel level, BlockPos at, int x, int y, int z, Direction towards, boolean extracting) {
		set(level, at, x, y, z, AllBlocks.ANDESITE_FUNNEL.getDefaultState()
			.setValue(com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock.FACING, towards)
			.setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, extracting));
	}

	/**
	 * A barrel's items into a machine, with two Andesite Funnels: an inserting one on the machine's face (the cell
	 * {@code (x, y, z)}, open towards {@code outward}) and an extracting one directly above it that pulls items out of a barrel
	 * on its {@code barrelSide} and lets them drop into the funnel below (a wall funnel throws its items into its own cell, so
	 * they fall straight down). The barrel stands on a block of casing. Returns the barrel's position.
	 */
	private static BlockPos feedItems(ServerLevel level, BlockPos at, int x, int y, int z, Direction outward, Direction barrelSide) {
		funnel(level, at, x, y, z, outward, false);
		funnel(level, at, x, y + 1, z, barrelSide.getOpposite(), true);
		int bx = x + barrelSide.getStepX();
		int bz = z + barrelSide.getStepZ();
		set(level, at, bx, y, bz, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, bx, y + 1, bz, Blocks.BARREL.defaultBlockState());
		return at.offset(bx, y + 1, bz);
	}

	private static Runnable serviceUsedOilDrive;
	private static BlockPos servicePan;
	private static BlockPos serviceBasin;
	private static BlockPos serviceStockTank;
	private static BlockPos serviceBarrel;
	private static BlockPos serviceBeltStart;
	private static BlockPos serviceEnd;
	private static BlockPos serviceEngineTank;
	private static final List<BlockPos> SERVICE_HEADS = new ArrayList<>();

	/**
	 * A diesel inline four with an oil pan against its first journal, and, beside it, two small working lines. Everything is a
	 * real Create machine and the harness only keeps the stock topped up:
	 * <ul>
	 * <li>Oil exchange: the crank's near end runs through gearboxes into a Mechanical Mixer over a basin on a heat exchanger. A
	 * pump takes the pan's used oil to the basin, a funnel feeds charcoal and clay from a barrel, and a second pump returns
	 * the clean engine oil to the pan.</li>
	 * <li>Fuel: a spout filled by a pump from a tank fills jerry cans on a belt, the belt takes them to an item drain, a
	 * pump moves the fuel it drains into the engine's tank, and a last pump feeds it along a rail of pipes into every head.
	 * The empty cans are taken from the end of the belt and put back at its start, as a worker would.</li>
	 * </ul>
	 */
	private static void service(ServerLevel level, ServerPlayer player, BlockPos at) {
		int before = TANKS.size();
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.DIESEL));
		// The heads take their fuel from the engine's own tank, not from the harness
		SERVICE_HEADS.clear();
		for (Tank tank : TANKS.subList(before, TANKS.size()))
			SERVICE_HEADS.add(tank.pos());
		TANKS.subList(before, TANKS.size())
			.clear();
		int north = CZ - 1;
		// The oil pan, against the first journal's north face
		servicePan = at.offset(1, CY, north);
		level.setBlock(servicePan, CEBlocks.SUMP.getDefaultState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(servicePan) instanceof com.createengines.content.sump.SumpBlockEntity sump) {
			// Oil that has been changed for a while: some of it used and waiting in the pan's used tank
			sump.tank()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 600), IFluidHandler.FluidAction.EXECUTE);
			sump.usedTank()
				.fill(new FluidStack(CEFluids.USED_OIL.get()
					.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			sump.exchange.setValue(3);
		}

		// A radiator, full, against the third journal's north face, two blocks along from the pan
		BlockPos radiator = at.offset(3, CY, north);
		level.setBlock(radiator, CEBlocks.RADIATOR.getDefaultState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(radiator) instanceof com.createengines.content.radiator.RadiatorBlockEntity loop)
			loop.tank()
				.fill(new FluidStack(CEFluids.COOLANT.get()
					.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE);

		// The mixer: the crank's near end into a Create shaft (the crankshaft drives one, as on the wheeled car), a gearbox, and up
		// from that a cogwheel that the mixer meshes with: a mixer takes its rotation from a cog beside it, not from a shaft
		set(level, at, 0, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, -1, CY, CZ, axisState(AllBlocks.GEARBOX.get(), Axis.Z));
		set(level, at, -1, CY + 1, CZ, axisState(AllBlocks.COGWHEEL.get(), Axis.Y));
		set(level, at, -2, CY + 1, CZ, AllBlocks.MECHANICAL_MIXER.getDefaultState());
		set(level, at, -2, CY - 3, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -2, CY - 2, CZ, CEBlocks.HEAT_EXCHANGER.getDefaultState()
			.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
				com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
		set(level, at, -2, CY - 1, CZ, AllBlocks.BASIN.getDefaultState());
		serviceBasin = at.offset(-2, CY - 1, CZ);
		// Used oil: out of the pan's west face, along the north side and down, through a pump, into the basin's west face
		pump(level, at, -3, CY - 1, CZ, Direction.EAST);
		// Its motor is switched on a little later (see schedule), once the charcoal and the clay have reached the basin: with
		// 250 mB of used oil and no charcoal and clay yet, the basin would coke it, not clean it
		serviceUsedOilDrive = () -> drive(level, at, -3, CY - 2, CZ, Axis.X, Direction.WEST, 64);
		pipes(level, at, new int[] { 0, CY, north }, new int[] { -1, CY, north }, new int[] { -2, CY, north },
			new int[] { -3, CY, north }, new int[] { -4, CY, north }, new int[] { -4, CY, CZ }, new int[] { -4, CY - 1, CZ });
		// Clean oil: out of the basin's north face through a pump, along and up into the pan's north face
		pump(level, at, -2, CY - 1, north, Direction.NORTH);
		drive(level, at, -3, CY - 1, north, Axis.Z, Direction.NORTH, 64);
		pipes(level, at, new int[] { -2, CY - 1, north - 1 }, new int[] { -1, CY - 1, north - 1 },
			new int[] { 0, CY - 1, north - 1 }, new int[] { 1, CY - 1, north - 1 }, new int[] { 1, CY, north - 1 });
		// Charcoal and a clay ball for the recipe, from a barrel, through a pair of funnels into the basin's south face
		serviceBarrel = feedItems(level, at, -2, CY - 1, CZ + 1, Direction.SOUTH, Direction.WEST);
		// Thirteen crafts' worth of charcoal and clay, in the barrel once
		ItemStack[] stock = new ItemStack[26];
		for (int i = 0; i < stock.length; i++)
			stock[i] = new ItemStack(i % 2 == 0 ? net.minecraft.world.item.Items.CHARCOAL : net.minecraft.world.item.Items.CLAY_BALL);
		stockBarrel(level, serviceBarrel, stock);

		// The fuel line along the engine's south side, y = 0: a belt from a motor-driven pulley at the west end to another at
		// the east end, a spout over it, an item drain after it
		int south = CZ + 4;
		set(level, at, -6, 0, south, axisState(AllBlocks.SHAFT.get(), Axis.Z));
		set(level, at, 2, 0, south, axisState(AllBlocks.SHAFT.get(), Axis.Z));
		level.setBlock(at.offset(-6, 0, south + 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(at.offset(-6, 0, south + 1)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(64);
		com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, at.offset(-6, 0, south),
			at.offset(2, 0, south));
		serviceBeltStart = at.offset(-5, 0, south);
		// A tank of diesel over the spout, a pump between them
		tank(level, at, -3, CY + 1, south, 1);
		serviceStockTank = at.offset(-3, CY + 1, south);
		pump(level, at, -3, CY, south, Direction.DOWN);
		drive(level, at, -4, CY, south, Axis.Y, Direction.UP, 64);
		set(level, at, -3, CY - 1, south, AllBlocks.SPOUT.getDefaultState());
		for (int y = 0; y <= CY + 1; y++)
			set(level, at, -3, y, south - 1, AllBlocks.ANDESITE_CASING.getDefaultState());
		// The drain at the belt's end, empties caught on a depot beyond it, a pump into the engine's tank
		set(level, at, 3, 0, south, AllBlocks.ITEM_DRAIN.getDefaultState());
		set(level, at, 4, 0, south, AllBlocks.DEPOT.getDefaultState());
		serviceEnd = at.offset(4, 0, south);
		pump(level, at, 3, 0, south - 1, Direction.NORTH);
		drive(level, at, 3, 1, south - 1, Axis.Z, Direction.SOUTH, 64);
		tank(level, at, 3, 0, south - 2, 4);
		serviceEngineTank = at.offset(3, 0, south - 2);
		// ... and from that tank a pump up and a rail of pipes over the heads, which take the fuel from their top faces
		pump(level, at, 3, 4, south - 2, Direction.UP);
		drive(level, at, 4, 4, south - 2, Axis.Y, Direction.UP, 64);
		pipes(level, at, new int[] { 3, 5, south - 2 }, new int[] { 3, 6, south - 2 }, new int[] { 3, 6, south - 3 },
			new int[] { 3, 6, CZ },
			new int[] { 2, 6, CZ }, new int[] { 4, 6, CZ }, new int[] { 5, 6, CZ });
	}

	/**
	 * The stock the service bay works from, and the one thing a worker would do: the diesel tank over the spout and the
	 * engine's own tank start with fuel, the barrel is kept stocked with charcoal and clay, an empty can is put at the start
	 * of the belt now and then, and the empties that have gathered at its end are taken away.
	 */
	private static boolean serviceStarted;
	private static int serviceCans;

	private static void serviceStock(ServerLevel level) {
		if (serviceStockTank == null)
			return;
		boolean first = !serviceStarted;
		serviceStarted = true;
		stockTank(level, serviceStockTank, new FluidStack(CEFluids.DIESEL.get()
			.getSource(), 4000));
		if (first)
			stockTank(level, serviceEngineTank, new FluidStack(CEFluids.DIESEL.get()
				.getSource(), 3000));
		var depot = depotAt(level, serviceEnd);
		if (depot != null && !depot.getHeldItemStack()
			.isEmpty())
			depot.removeHeldItem();
		BlockPos start = serviceBeltStart.above();
		if (++serviceCans % 5 == 1 && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(start)
			.inflate(0.4))
			.isEmpty()) {
			net.minecraft.world.entity.item.ItemEntity can = new net.minecraft.world.entity.item.ItemEntity(level, start.getX() + 0.5,
				start.getY() + 0.1, start.getZ() + 0.5, new ItemStack(CEItems.JERRY_CAN.get()), 0, 0, 0);
			can.setPickUpDelay(32767);
			level.addFreshEntity(can);
		}
	}

	private static com.simibubi.create.content.logistics.depot.DepotBehaviour depotAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depot
			? depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE) : null;
	}

	/** A rain cap on the mouth of every exhaust outlet on a pad's engine, which flaps with each exhaust pulse. */
	private static void rainCaps(ServerLevel level, BlockPos at) {
		for (int x = -1; x <= 16; x++)
			for (int y = 0; y <= CY + 8; y++)
				for (int z = CZ - 4; z <= CZ + 4; z++) {
					BlockPos pos = at.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.is(CEBlocks.EXHAUST_OUTLET.get()))
						continue;
					Direction face = state.getValue(PortOpeningBlock.FACING);
					if (level.getBlockState(pos.relative(face))
						.isAir())
						level.setBlock(pos.relative(face), CEBlocks.RAIN_CAP.getDefaultState()
							.setValue(com.createengines.content.raincap.RainCapBlock.FACING, face), Block.UPDATE_ALL);
				}
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
		if (TRAILER)
			defineTrailerScenes();
	}

	/**
	 * The trailer's extra pads, after the plain engines: the electrical parts (a joined bank of all three battery sizes, and
	 * an ignition coil and an injector assist on every head), a blown inline six, and the test-bed diesel. When a significant
	 * part is added to the mod it goes on one of these (or a new pad), and in {@code src/smoke/trailer-parts.txt}.
	 */
	private static void defineTrailerScenes() {
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
		// An inline six on a moving structure, driving itself on wheels: only with Create Aeronautics (its Offroad wheels) and Sable in
		// the run. Its views are offsets from the craft, which the camera follows (see pose)
		if (aero())
			SCENES.add(new Scene("rover", AERO_BAY, crank, true, List.of(
				new View("side", 5.5, 2.5, CZ, -90, 8, 20),
				new View("hero", 5.5, 2.5, CZ, -50, 14, 19),
				new View("wheel", 16, 1.5, CZ, -60, 8, 8))));
	}

	/**
	 * The wheeled engine: a diesel inline six whose crank line runs out of both ends into a gearbox, a second gearbox below it, and
	 * an axle across the car with an Offroad wheel mount and tire at each end. Create Aeronautics' Offroad wheels take their drive from
	 * the shaft behind the mount, so the pad's dais is taken away and the car stands on its four wheels once {@link #liftOff} makes it
	 * a moving structure.
	 */
	private static void rover(ServerLevel level, ServerPlayer player, BlockPos at) {
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 8, new Kit(Fuel.DIESEL, 1, true, 0, false, false, false), STAGE_ALL, true);
		for (int x = -1; x <= 8 + 1 + 5; x++)
			for (int z = CZ - 2; z <= CZ + 2; z++)
				level.setBlock(at.offset(x, 0, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		// The east axle takes the flywheel's end; the west one the crank's near end
		axle(level, at, 8 + 1 + 6, 8 + 1 + 5, 1);
		// The west pair only rolls, on a frame of andesite casing from the starter's motor (blocks that touch nothing are split off
		// into structures of their own when the car is lifted)
		BlockState frame = AllBlocks.ANDESITE_CASING.getDefaultState();
		for (int x = -1; x <= 0; x++)
			set(level, at, x, 1, CZ, frame);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, -1, 1, z, frame);
		wheel(level, at.offset(-1, 1, CZ - 2), Direction.NORTH);
		wheel(level, at.offset(-1, 1, CZ + 2), Direction.SOUTH);
		body(level, at);
		clearDrops(level);
	}

	/**
	 * What makes the car a rover and not an engine on wheels: brass frame rails with crossmembers from the rear axle to the front one,
	 * running boards beside the engine, a closed cab over the rear axle (two red seats, a dash, glass all round, a roof with a lamp and an
	 * aerial), a cargo bed behind it with barrels, and a bumper with a headlamp at each corner. Every block touches the frame, since a
	 * block that touches nothing is split off into a structure of its own when the car is lifted, and none sits where the engine's
	 * own parts are (the crank line, its starter column and the pipes all keep to the engine's side of the line y = 2 / z = 4..6).
	 */
	private static void body(ServerLevel level, BlockPos at) {
		BlockState brass = AllBlocks.BRASS_CASING.getDefaultState();
		BlockState andesite = AllBlocks.ANDESITE_CASING.getDefaultState();
		BlockState bars = AllBlocks.ANDESITE_BARS.getDefaultState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		// The frame: a rail down each side, joined by crossmembers (the starter's column and the far journal's legs stand at z = 5)
		for (int x = 0; x <= 14; x++) {
			set(level, at, x, 1, CZ - 2, brass);
			set(level, at, x, 1, CZ + 2, brass);
		}
		for (int x : new int[] { 4, 7, 12 })
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 1, z, andesite);
		// Running boards along the engine, clear of the tires at either end
		BlockState board = Blocks.SPRUCE_SLAB.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
		for (int x = 3; x <= 11; x++) {
			set(level, at, x, 2, CZ - 2, board);
			set(level, at, x, 2, CZ + 2, board);
		}

		// The rear: a deck from the cab to the tailgate
		for (int x = -6; x <= -2; x++)
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 1, z, x >= -3 ? brass : andesite);
		// The cab: corner posts, a dash across the front, glass on all four sides, a roof
		for (int y = 2; y <= 4; y++)
			for (int x : new int[] { -3, -1 })
				for (int z : new int[] { CZ - 1, CZ + 1 })
					set(level, at, x, y, z, andesite);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, -1, 2, z, brass);
		for (int y = 3; y <= 4; y++) {
			set(level, at, -1, y, CZ, glass);
			set(level, at, -3, y, CZ, glass);
			set(level, at, -2, y, CZ - 1, glass);
			set(level, at, -2, y, CZ + 1, glass);
		}
		for (int x = -3; x <= -1; x++)
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 5, z, brass);
		set(level, at, -2, 2, CZ - 1, AllBlocks.SEATS.get(DyeColor.RED)
			.getDefaultState());
		set(level, at, -2, 2, CZ + 1, AllBlocks.SEATS.get(DyeColor.RED)
			.getDefaultState());
		// On the roof: a lamp at the front, a rack of bars round it, an aerial at the back
		set(level, at, -1, 6, CZ, Blocks.LANTERN.defaultBlockState());
		for (int x = -3; x <= -2; x++) {
			set(level, at, x, 6, CZ - 1, bars);
			set(level, at, x, 6, CZ + 1, bars);
		}
		set(level, at, -3, 6, CZ, bars);
		set(level, at, -2, 6, CZ, Blocks.BARREL.defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.UP));
		set(level, at, -3, 7, CZ + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
		set(level, at, -3, 8, CZ + 1, Blocks.END_ROD.defaultBlockState());

		// The bed: bars along both sides and across the tailgate, drums standing on the deck
		for (int x = -6; x <= -4; x++) {
			set(level, at, x, 2, CZ - 1, bars);
			set(level, at, x, 2, CZ + 1, bars);
		}
		set(level, at, -6, 2, CZ, bars);
		for (int x = -5; x <= -4; x++)
			set(level, at, x, 2, CZ, Blocks.BARREL.defaultBlockState()
				.setValue(BlockStateProperties.FACING, Direction.UP));
		set(level, at, -5, 3, CZ, Blocks.BARREL.defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.UP));

		// The nose: a bumper of bars between a brass post and headlamp at each corner
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, 16, 1, z, bars);
		for (int z : new int[] { CZ - 2, CZ + 2 }) {
			for (int y = 1; y <= 2; y++)
				set(level, at, 16, y, z, brass);
			set(level, at, 16, 3, z, Blocks.LANTERN.defaultBlockState());
		}
		// A grille of bars between the posts under a brass brow, with a lamp in the middle of it and a spotlamp on the brow
		for (int z = CZ - 1; z <= CZ + 1; z++) {
			set(level, at, 16, 2, z, bars);
			set(level, at, 16, 3, z, brass);
		}
		set(level, at, 16, 4, CZ, Blocks.LANTERN.defaultBlockState());
		set(level, at, 16, 4, CZ - 1, Blocks.END_ROD.defaultBlockState());
		set(level, at, 16, 4, CZ + 1, Blocks.END_ROD.defaultBlockState());
		// A plough ahead of the bumper: a wedge of polished andesite stepping down to the ground
		BlockState plough = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, 17, 1, z, plough);
		set(level, at, 18, 1, CZ, plough);
	}

	/**
	 * The driven wheel: from the crank line at {@code lineEnd} a shaft runs to a gearbox at {@code x}, down a shaft into a second
	 * gearbox at height 1 and out to one wheel mount on the north side, straight, with no reduction (one driven mount at full speed
	 * is within a turbocharged eight's stress; two are not). The south side has a mount with a tire and no drive, held on by a casing
	 * frame, so the car rolls on a pair.
	 */
	private static void axle(ServerLevel level, BlockPos at, int x, int lineEnd, int step) {
		for (int along = lineEnd; along != x; along += step)
			set(level, at, along, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, x, CY, CZ, AllBlocks.GEARBOX.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		set(level, at, x, CY - 1, CZ, AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Y));
		set(level, at, x, 1, CZ, AllBlocks.GEARBOX.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set(level, at, x, 1, CZ - 1, AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		BlockState frame = AllBlocks.ANDESITE_CASING.getDefaultState();
		set(level, at, x, 1, CZ + 1, frame);
		wheel(level, at.offset(x, 1, CZ - 2), Direction.NORTH);
		wheel(level, at.offset(x, 1, CZ + 2), Direction.SOUTH);
	}

	/** Picks up every dropped item round the wheeled car's pad and the road it drives (no drops lie in the shots). */
	private static void clearDrops(ServerLevel level) {
		BlockPos at = pad(AERO_BAY);
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(at.getX() - 20, at.getY() - 6, at.getZ() - 20, at.getX() + 110, at.getY() + 20, at.getZ() + 30)))
			item.discard();
	}

	/** An Offroad wheel mount facing {@code side}, with a tire on it. */
	private static void wheel(ServerLevel level, BlockPos mountAt, Direction side) {
		BlockState state = block("offroad:wheel_mount", Blocks.AIR).defaultBlockState();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, side);
		else
			CreateEngines.LOGGER.warn("Showcase: the wheel mount has no horizontal facing; blockstate {}", state);
		level.setBlock(mountAt, state, Block.UPDATE_ALL);
		putTire(level, mountAt, BuiltInRegistries.ITEM.get(ResourceLocation.parse("offroad:tire")));
	}

	/** Puts a tire in a wheel mount's one slot, by reflection: Offroad is not something this mod is built against. */
	private static void putTire(ServerLevel level, BlockPos mountAt, net.minecraft.world.item.Item tire) {
		try {
			var be = level.getBlockEntity(mountAt);
			Object inventory = be.getClass()
				.getMethod("getInventory")
				.invoke(be);
			inventory.getClass()
				.getMethod("setItem", int.class, ItemStack.class)
				.invoke(inventory, 0, new ItemStack(tire));
		} catch (ReflectiveOperationException | RuntimeException e) {
			CreateEngines.LOGGER.warn("Showcase: no tire went on the wheel mount at {}", mountAt, e);
		}
	}

	/** Lifts the wheeled car's pad into one moving structure, where it stands, and keeps its heads fed in their new places. */
	private static void liftOff() {
		server(server -> {
			ServerLevel level = server.overworld();
			BlockPos at = pad(AERO_BAY);
			clearDrops(level);
			var heads = ShowcaseRunAero.assemble(level, at.offset(-7, 0, CZ - 4), at.offset(18, CY + 6, CZ + 4));
			aeroStart = ShowcaseRunAero.position();
			TANKS.removeIf(tank -> tank.pos().getX() >= at.getX() - 7 && tank.pos().getX() <= at.getX() + 18
				&& tank.pos().getZ() >= at.getZ() + CZ - 4 && tank.pos().getZ() <= at.getZ() + CZ + 4 && tank.pos().getY() >= at.getY());
			for (BlockPos head : heads)
				TANKS.add(new Tank(head, Fuel.DIESEL));
		});
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
		BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState line = Blocks.WAXED_CUT_COPPER.defaultBlockState();
		BlockState base = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		BlockState band = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState cap = Blocks.STONE_BRICK_SLAB.defaultBlockState();
		BlockState pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState copper = Blocks.WAXED_CUT_COPPER.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		for (int x = PAD_X0; x <= PAD_X1; x++)
			for (int z = PAD_Z0; z <= PAD_Z1; z++) {
				boolean border = x == PAD_X0 || x == PAD_X1 || z == PAD_Z0 || z == PAD_Z1;
				boolean inner = !border && (x == PAD_X0 + 1 || x == PAD_X1 - 1 || z == PAD_Z0 + 1 || z == PAD_Z1 - 1);
				// A dark tiled floor: a chequer of tiles and polished slate, with the odd blackstone slab so it is not a grid
				int scatter = Math.floorMod(x * 31 + z * 17, 11);
				BlockState floor = inner ? line : scatter == 0 ? dark : (x + z) % 2 == 0 ? tiles : polished;
				level.setBlock(corner.offset(x, -1, z), border ? bricks : floor, Block.UPDATE_CLIENTS);
				if (!border)
					continue;
				boolean post = ((x - PAD_X0) % 6 == 0 && (z == PAD_Z0 || z == PAD_Z1))
					|| ((z - PAD_Z0) % 6 == 0 && (x == PAD_X0 || x == PAD_X1));
				for (int y = 0; y < WALL; y++) {
					int hash = Math.floorMod(x * 7 + z * 13 + y * 5, 17);
					// A plinth of slate, weathered brick above it, a darker band under the cap, a copper capital on each pillar
					BlockState wall = y < 2 ? base : y == WALL - 2 ? band : hash == 0 ? mossy : hash == 1 ? cracked : bricks;
					BlockState here = post ? (y == WALL - 1 ? copper : pillar) : wall;
					level.setBlock(corner.offset(x, y, z), here, Block.UPDATE_CLIENTS);
				}
				level.setBlock(corner.offset(x, WALL, z), post ? lantern : cap, Block.UPDATE_CLIENTS);
			}
	}

	/** A one-block platform under a banked engine, so the engine and its starter stand on something. */
	static void dais(ServerLevel level, BlockPos at, int length) {
		BlockState stone = Blocks.POLISHED_ANDESITE.defaultBlockState();
		for (int x = -1; x <= length; x++)
			for (int z = CZ - 2; z <= CZ + 2; z++)
				level.setBlock(at.offset(x, 0, z), stone, Block.UPDATE_CLIENTS);
	}

	static BlockState axisX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	static void set(ServerLevel level, BlockPos at, int x, int y, int z, BlockState state) {
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
	 * A diesel inline four with a dual-mass flywheel, whose crank runs west out of its near end through a transmission into a row
	 * of Create machines on one shaft line: two
	 * presses side by side, each over a depot with iron on it, and an encased fan blowing west at the end. The presses really
	 * stamp (the iron is put back on the depots from {@link #fuelUp()}), so the row reads as a working factory. A mixer would
	 * not do: it takes its power from above, not from a shaft along the line.
	 */
	private static void factory(ServerLevel level, ServerPlayer player, BlockPos at) {
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.DIESEL));
		BlockState shaft = axisX(AllBlocks.SHAFT.getDefaultState());
		BlockState press = AllBlocks.MECHANICAL_PRESS.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		FACTORY_DEPOTS.clear();
		// The crank's near end runs into a transmission on its auto setting, which shifts for itself, and the far end carries a
		// dual-mass flywheel where the plain engines have a cast one
		set(level, at, 0, CY, CZ, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		if (level.getBlockEntity(at.offset(0, CY, CZ)) instanceof com.createengines.content.transmission.TransmissionBlockEntity box)
			box.panel.setValue(com.createengines.content.transmission.Gears.AUTO);
		set(level, at, 4 + 1 + 4, CY, CZ, axisX(CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()));
		for (int x = -1; x >= -2; x--) {
			set(level, at, x, CY, CZ, press);
			set(level, at, x, CY - 3, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
			set(level, at, x, CY - 2, CZ, AllBlocks.DEPOT.getDefaultState());
			set(level, at, x, CY - 1, CZ, Blocks.AIR.defaultBlockState());
			FACTORY_DEPOTS.add(at.offset(x, CY - 2, CZ));
		}
		// A powered gearshift reverses the drive for the fan: the crank turns this line the way that makes a fan at its end draw
		// air in, and the fan has to blow
		set(level, at, -3, CY, CZ, AllBlocks.GEARSHIFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set(level, at, -3, CY + 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		// The fan blows west through a lit campfire onto a depot of raw beef, which it smokes: an empty fan blows at nothing
		set(level, at, -4, CY, CZ, AllBlocks.ENCASED_FAN.getDefaultState()
			.setValue(BlockStateProperties.FACING, Direction.WEST));
		set(level, at, -5, CY - 1, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -5, CY, CZ, Blocks.CAMPFIRE.defaultBlockState()
			.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
		for (int y = CY - 3; y <= CY - 1; y++)
			set(level, at, -6, y, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -6, CY, CZ, AllBlocks.DEPOT.getDefaultState());
		smokerDepot = at.offset(-6, CY, CZ);
		feedDepots(level);
	}

	/** The depot the factory's fan smokes beef on. */
	private static BlockPos smokerDepot;

	/** The depots under the factory's presses. */
	private static final List<BlockPos> FACTORY_DEPOTS = new ArrayList<>();

	/** Puts an iron ingot on each depot that is empty or holds what the press made, so the presses never run out of work. */
	private static void feedDepots(ServerLevel level) {
		// The smoker's beef goes back when it is eaten up, not when it is half cooked, so it is never reset mid-cook
		if (smokerDepot != null && level.getBlockEntity(smokerDepot) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity smoker) {
			var behaviour = smoker.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
			if (behaviour != null && behaviour.getHeldItemStack()
				.is(net.minecraft.world.item.Items.COOKED_BEEF) || behaviour != null && behaviour.getHeldItemStack()
				.isEmpty())
				behaviour.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
					new ItemStack(net.minecraft.world.item.Items.BEEF)));
		}
		for (BlockPos pos : FACTORY_DEPOTS)
			if (level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depot) {
				var behaviour = depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
				if (behaviour != null && !behaviour.getHeldItemStack()
					.is(net.minecraft.world.item.Items.IRON_INGOT))
					behaviour.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
						new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
			}
	}

	/** Where the refining column's south-west corner stands, relative to its pad. */
	private static final int REFINERY_X = 6;
	private static final int REFINERY_Z = 3;
	/** The Reboiler that feeds the column, which {@link #fuelUp()} keeps supplied (null until it is built). */
	private static BlockPos refineryFeed;
	private static BlockPos refineryStockTank;
	private static BlockPos refineryBarrel;
	private static BlockPos refineryProductTank;

	/** The refinery's stock: plant oil in the tank its pump draws from (the barrel of coal is stocked once, when it is built). */
	private static void refineryStock(ServerLevel level) {
		if (refineryStockTank == null)
			return;
		stockTank(level, refineryStockTank, new FluidStack(CEFluids.PLANT_OIL.get()
			.getSource(), 2000));
	}
	private static final List<BlockPos> REFINERY_CONDENSERS = new ArrayList<>();

	/**
	 * A refining column: a heated base of Heat Exchangers, Reboilers, three layers of Column Sections, Condensers on top and a
	 * Flare Stack above. It is fed plant oil and coal, and its product is drawn off, from {@link #fuelUp()}.
	 */
	private static void refinery(ServerLevel level, BlockPos at) {
		REFINERY_CONDENSERS.clear();
		for (int x = REFINERY_X; x < REFINERY_X + 3; x++)
			for (int z = REFINERY_Z; z < REFINERY_Z + 3; z++) {
				set(level, at, x, 0, z, CEBlocks.HEAT_EXCHANGER.getDefaultState()
					.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
						com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
				set(level, at, x, 1, z, CEBlocks.REBOILER.getDefaultState());
				for (int y = 2; y <= 4; y++)
					set(level, at, x, y, z, CEBlocks.COLUMN_SECTION.getDefaultState());
				set(level, at, x, 5, z, CEBlocks.CONDENSER.getDefaultState());
				REFINERY_CONDENSERS.add(at.offset(x, 5, z));
			}
		set(level, at, REFINERY_X + 1, 6, REFINERY_Z + 1, CEBlocks.FLARE_STACK.getDefaultState());
		refineryFeed = at.offset(REFINERY_X, 1, REFINERY_Z);
		// Feed: a tank of plant oil, and a pump into the controller Reboiler's north face. Catalyst: a barrel of coal and an
		// pair of funnels into its west face. Product: a pump over the corner Condenser into a tank that stands on it.
		tank(level, at, REFINERY_X, 1, REFINERY_Z - 2, 2);
		refineryStockTank = at.offset(REFINERY_X, 1, REFINERY_Z - 2);
		pump(level, at, REFINERY_X, 1, REFINERY_Z - 1, Direction.SOUTH);
		drive(level, at, REFINERY_X - 1, 1, REFINERY_Z - 1, Axis.Z, Direction.NORTH, 64);
		refineryBarrel = feedItems(level, at, REFINERY_X - 1, 1, REFINERY_Z, Direction.WEST, Direction.SOUTH);
		stockBarrel(level, refineryBarrel, new ItemStack(net.minecraft.world.item.Items.COAL, 64));
		pump(level, at, REFINERY_X, 6, REFINERY_Z, Direction.UP);
		drive(level, at, REFINERY_X - 1, 6, REFINERY_Z, Axis.Y, Direction.UP, 64);
		tank(level, at, REFINERY_X, 7, REFINERY_Z, 2);
		refineryProductTank = at.offset(REFINERY_X, 7, REFINERY_Z);
		pump(level, at, REFINERY_X + 2, 6, REFINERY_Z, Direction.UP);
		drive(level, at, REFINERY_X + 3, 6, REFINERY_Z, Axis.Y, Direction.UP, 64);
		tank(level, at, REFINERY_X + 2, 7, REFINERY_Z, 2);
	}

	/**
	 * A gasoline inline four with the electrical parts: an ignition coil on each head's plug and an injector assist on each
	 * head's injector, and above the starter's battery a Large Battery and a Dry Cell, which join it into one bank. The
	 * batteries start part full so their charge lamps show; the alternator keeps them going.
	 */
	private static void electric(ServerLevel level, ServerPlayer player, BlockPos at) {
		int before = TANKS.size();
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.GASOLINE));
		for (Tank tank : TANKS.subList(before, TANKS.size())) {
			BlockState head = level.getBlockState(tank.pos());
			if (head.hasProperty(com.createengines.content.head.CylinderHeadBlock.HAS_COIL))
				level.setBlock(tank.pos(), head.setValue(com.createengines.content.head.CylinderHeadBlock.HAS_ASSIST, true)
					.setValue(com.createengines.content.head.CylinderHeadBlock.HAS_COIL, true), Block.UPDATE_ALL);
		}
		set(level, at, 1, CY + 2, CZ, CEBlocks.LARGE_BATTERY.getDefaultState());
		set(level, at, 1, CY + 3, CZ, CEBlocks.DRY_CELL.getDefaultState());
		for (int y = 1; y <= 3; y++)
			if (level.getBlockEntity(at.offset(1, CY + y, CZ)) instanceof com.createengines.content.battery.BatteryBlockEntity battery)
				battery.charge(battery.capacity() * 0.35f);
	}

	/**
	 * A banked engine laid out by the Bank Builder: a crank along X with {@code count} throws side by side, a cam line
	 * driven from the crank's far end at half its speed, an intake pipe on one side of each bank and an exhaust pipe on
	 * the other, a flywheel on the end, and a starter underneath. The {@link Kit} says what else it wears: a turbo on the
	 * exhaust's east end (its compressor breathing from the east, its air coming back along the near side), a
	 * motor-driven blower with its intercooler and air intake at the intake's west end, or a plain throttle, muffler and
	 * outlet; and the instruments that go on a test-bed engine.
	 */
	static void bank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit) {
		bank(level, player, at, layout, count, kit, STAGE_ALL, false);
	}

	/** The parts of {@link #bank}, so a clip can lay an engine down in front of the camera one stage at a time. */
	private static final int STAGE_STRUCTURE = 1;
	private static final int STAGE_BANK = 2;
	private static final int STAGE_FITTINGS = 4;
	private static final int STAGE_POWER = 8;
	private static final int STAGE_ALL = 15;

	static void bank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit, int stages, boolean precision) {
		Fuel fuel = kit.fuel();
		boolean spark = fuel == Fuel.GASOLINE;
		int lastThrow = count + 1;
		if ((stages & STAGE_STRUCTURE) != 0) {
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
		if (stages == STAGE_ALL)
			set(level, at, 0, CY - 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (spark) {
			set(level, at, 0, CY, CZ, axisX(CEBlocks.ALTERNATOR.getDefaultState()));
			set(level, at, 1, CY + 1, CZ, CEBlocks.BATTERY.getDefaultState());
		}
		}

		if ((stages & STAGE_BANK) != 0) {
		// The cylinders, heads, plugs and cam lobes, as the Bank Builder lays them
		var result = BankBuilderItem.build(level, at.offset(2, CY, CZ), Direction.UP, layout, precision, player, true);
		CreateEngines.LOGGER.info("Showcase bank {} at {}: {}", layout, at, result);

		// The cam line is carried on from its last lobe to a large cog meshing with the small one on the crank
		for (BlockPos beside : BankBuilderItem.camOffsets(layout, Axis.X, Direction.UP)) {
			int y = CY + beside.getY();
			int z = CZ + beside.getZ();
			for (int x = lastThrow + 1; x <= lastThrow + 2; x++)
				set(level, at, x, y, z, axisX(CEBlocks.CAMSHAFT.getDefaultState()));
			set(level, at, lastThrow + 3, y, z, axisX(AllBlocks.LARGE_COGWHEEL.getDefaultState()));
		}
		}

		if ((stages & STAGE_FITTINGS) == 0)
			return;
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
	static void pipe(ServerLevel level, BlockPos pos, ManifoldBlock block) {
		BlockState state = block.defaultBlockState();
		for (Direction side : Direction.values())
			if (Pipes.accepts(level.getBlockState(pos.relative(side)), side.getOpposite(), block.kind()))
				state = state.setValue(ManifoldBlock.CONNECTIONS.get(side), true);
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void fuelUp() {
		server(server -> {
			ServerLevel level = server.overworld();
			if (aero())
				clearDrops(level);
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
			feedDepots(level);
			serviceStock(level);
			refineryStock(level);
			ShowcaseRunFeatures.stock(level);
			// (the refinery and the service bay are fed by their own pumps and funnels: see refinery() and serviceStock())
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

	private static String basinTanks(IFluidHandler basin) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < basin.getTanks(); i++)
			out.append(basin.getFluidInTank(i)
				.getAmount())
				.append(' ')
				.append(basin.getFluidInTank(i)
					.getFluid()
					.getFluidType()
					.getDescriptionId())
				.append(i + 1 < basin.getTanks() ? " | " : "");
		return out.toString();
	}

	private static void logPlumbing(String when) {
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

	private static void logEngines(String when) {
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

	private static void shot(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
			message -> CreateEngines.LOGGER.info("Showcase screenshot: {}", message.getString()));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The trailer: a camera that moves while the engines run, for a screen recording
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * One trailer shot. The camera arcs round a {@link ShowcaseRunFraming.Box} (in blocks from the pad's corner), its heading and
	 * height drifting from the first value to the second over {@code seconds}. How far it stands is worked out, not set: just far
	 * enough for the box to take up {@code fill0}, drifting to {@code fill1}, of the frame's safe area (more is a push in, less a
	 * pull back). The box itself travels {@code travelX} blocks along the crank's line. {@code time} is the time of day the shot
	 * cuts to, or {@link #NO_TIME}.
	 */
	private record Shot(String name, String scene, ShowcaseRunFraming.Box subject, float yaw0, float yaw1, float pitch0,
		float pitch1, double fill0, double fill1, double travelX, double seconds, int time) {
	}

	private static ShowcaseRunFraming.Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new ShowcaseRunFraming.Box(x0, y0, z0, x1, y1, z1);
	}

	private static final int NO_TIME = -1;
	private static final int DAY = 2500;
	/**
	 * The sun goes down across the last three shots. The exhaust-side shots look away from the low sun and the courtyard walls
	 * shade the engines, so they are shot early (golden hour, then dusk); the finale looks into the light and holds at 12300,
	 * where the glow and the flames show. Without shaders 11800 looks no different from day and 12700 is too dark.
	 */
	private static final int GOLDEN_HOUR = 11000;
	private static final int DUSK = 11700;
	private static final int NIGHTFALL = 12300;
	/** A little darker than the finale, for the shot that is about what glows, and light enough to still show the engine. */
	private static final int NIGHT = 12500;
	/** Seconds the camera waits at the first shot's start, so the chunks around it are in before the recording starts. */
	private static final double LEAD_IN = 8;
	private static final List<Shot> SHOTS = new ArrayList<>();
	private static long trailerStart = -1;
	private static long trailerStartMillis;
	private static int trailerShot = -1;
	private static boolean trailerStill;
	private static boolean trailerOver;
	private static volatile double[] trailerPose;
	private static boolean syncPending;

	/**
	 * The sync mark: the whole screen white for a quarter of a second and an anvil's clap through the speakers, both started from
	 * the first frame this screen draws, whose wall-clock moment is written to {@code trailer_sync.json} for tools/trailer/record.py.
	 */
	private static final class SyncFlash extends Screen {
		private long shownNanos;

		SyncFlash() {
			super(Component.empty());
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.fill(0, 0, width, height, 0xFFFFFFFF);
			if (shownNanos != 0) {
				if (System.nanoTime() - shownNanos > 250_000_000L)
					Minecraft.getInstance()
						.setScreen(null);
				return;
			}
			shownNanos = System.nanoTime();
			long epoch = System.currentTimeMillis();
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0F, 1.0F));
			try {
				Files.writeString(minecraft.gameDirectory.toPath()
					.resolve("trailer_sync.json"), String.format(Locale.ROOT, "{\"flashEpochMs\": %d}%n", epoch));
			} catch (IOException e) {
				CreateEngines.LOGGER.warn("Showcase: could not write the sync mark", e);
			}
			CreateEngines.LOGGER.info("Showcase trailer sync mark at {}", epoch);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public boolean shouldCloseOnEsc() {
			return false;
		}
	}

	private static void defineTrailer() {
		// In the order of the cut (tools/trailer/edit.txt), so the light only ever moves on: one cylinder, the title, the layouts,
		// forced induction, the engine grown to a V16 and the V16 cold-started, the instruments, the electrical parts, the service
		// bay, the casings, the drivetrain at work, the car, then fuels and the refinery as the sun goes down and the factory as
		// the finale. Every length is a whole number of bars at the backing track's tempo (tools/trailer/music.py: 80 beats a
		// minute, a bar of three seconds).
		// The subjects, in blocks from each pad's corner
		ShowcaseRunFraming.Box v8 = box(0.5, 1, 2.5, 10, 6.5, 7);
		ShowcaseRunFraming.Box bigV16 = box(0, 1, 2.5, 14, 7, 7);
		SHOTS.add(new Shot("first_rises", "first", box(1, 1, 1.4, 5, 5, 3.6), -62, -34, 8, 16, 0.94, 1.06, 0, 6, DAY));
		// The title lies over a high pull-in on the V8 at golden hour, and the next shot carries the same move on
		SHOTS.add(new Shot("title_sequence", "v8", v8, -130, -112, 13, 10, 0.82, 0.92, 0, 6, GOLDEN_HOUR));
		SHOTS.add(new Shot("hero_v8", "v8", v8, -54, -28, 12, 16, 1.02, 1.16, 0, 9, NO_TIME));
		// The layouts, quick cuts: the inline six, the flat six, the rotary circled
		SHOTS.add(new Shot("inline6_pass", "inline6", box(0.5, 1, 3.5, 12.5, 7, 7), -54, -34, 11, 13, 1.08, 1.16, 0, 3, DAY));
		SHOTS.add(new Shot("flat6_track", "flat6", box(0.5, 1, 3, 9.5, 5.2, 8), -18, 6, 14, 16, 0.94, 1.02, 0, 3, NO_TIME));
		SHOTS.add(new Shot("rotary_orbit", "rotary", box(1, 1.8, 1.2, 7, 4.6, 3.8), -62, -18, 16, 24, 1.04, 1.18, 0, 3, NO_TIME));
		// Forced induction: the turbocharger from its own side, clear of the cam's cogwheel, then the blower
		SHOTS.add(new Shot("inline6_turbo", "inline6", box(6.5, 3.5, 4.5, 10.5, 7.2, 7), -168, -142, 11, 14, 0.98, 1.14, 0, 6, NO_TIME));
		SHOTS.add(new Shot("blown_i6_blower", "blown_i6", box(0, 3.4, 1.5, 3.5, 6.4, 5.5), -68, -48, 14, 16, 1, 1.12, 0, 3, NO_TIME));
		// The variable-geometry turbo on its turbine side, then the MAF sensor in the intake
		SHOTS.add(new Shot("tuning_vgt", "tuning", box(5, 4, 4.8, 8.2, 7.2, 7), -164, -140, 13, 15, 0.92, 1.04, 0, 3, NO_TIME));
		SHOTS.add(new Shot("tuning_maf", "tuning", box(5.2, 4.2, 3.6, 7.4, 6.4, 5.4), -34, -14, 24, 28, 0.8, 0.92, 0, 3, NO_TIME));
		// Gameplay: first person, standing still, the Bank Builder in hand: one cylinder grown into a boosted precision V16
		SHOTS.add(new Shot("grow_engine", "grow", box(0, 1, 2.5, 14.5, 7, 7), -22, -22, 14, 14, 1.17, 1.17, 0, 15, NO_TIME));
		// The big block: the heavy starter turns a cold V16 over until it catches, and the player, standing by it, puts on headphones
		SHOTS.add(new Shot("heavy_starter", "bigblock", bigV16, -62, -44, 8, 11, 1.08, 1.18, 0, 6, NO_TIME));
		SHOTS.add(new Shot("headphones_on", "bigblock", bigV16, -32, -32, 10, 10, 1, 1, 0, 6, NO_TIME));
		// The test bed's instruments
		SHOTS.add(new Shot("instruments_dyno", "instruments_i4", box(-0.5, 2, 3.5, 4.5, 6.2, 6.5), -62, -42, 15, 18, 1, 1.12, 0, 3,
			NO_TIME));
		SHOTS.add(new Shot("instruments_governor", "instruments_i4", box(4.5, 2, 3.8, 9, 5.8, 7), 128, 148, 14, 17, 0.9, 1.02, 0, 3,
			NO_TIME));
		// Running hot: the engine steaming, then the radiators going on
		SHOTS.add(new Shot("running_hot", "heat", box(3, 4.8, 4.3, 7, 6.8, 6.2), -34, -24, 24, 26, 0.96, 1.08, 0, 6, NO_TIME));
		SHOTS.add(new Shot("running_cooled", "heat", box(1.5, 2.8, 3.8, 7.5, 6.8, 6.2), -26, -16, 22, 22, 1.06, 1, 0, 6, NO_TIME));
		// To be heard: one engine, one camera, its exhaust rebuilt between the shots
		ShowcaseRunFraming.Box heard = box(0.5, 2, 3.5, 8.5, 6.8, 7);
		SHOTS.add(new Shot("muffler_off", "sound", heard, -150, -146, 14, 14, 1.06, 1.1, 0, 6, NO_TIME));
		SHOTS.add(new Shot("muffler_on", "sound", heard, -146, -142, 14, 14, 1.1, 1.14, 0, 6, NO_TIME));
		ShowcaseRunFraming.Box run = box(1.5, 2, 3.5, 15.5, 6.8, 7);
		SHOTS.add(new Shot("exhaust_stub", "sound", run, -158, -155, 15, 15, 1.1, 1.13, 0, 6, NO_TIME));
		SHOTS.add(new Shot("exhaust_long", "sound", run, -155, -152, 15, 15, 1.13, 1.16, 0, 6, NO_TIME));
		// The electrical chain: the alternator, the joined batteries it fills, then a coil and an assist on every head
		SHOTS.add(new Shot("electric_alternator", "electric", box(-0.5, 2, 4, 3, 7, 6), -72, -52, 11, 13, 0.98, 1.08, 0, 3, NO_TIME));
		SHOTS.add(new Shot("electric_battery", "electric", box(0, 3.5, 4, 3, 7, 6), -62, -42, 10, 14, 0.84, 0.98, 0, 3, NO_TIME));
		SHOTS.add(new Shot("electric_heads", "electric", box(1.5, 4, 3.5, 6.5, 6.8, 7), -46, -26, 11, 10, 0.94, 1.06, 0, 3, NO_TIME));
		// The service bay: the player fits an oil filter and tops up the pan (gameplay), then the line that cleans the used oil and fills cans
		SHOTS.add(new Shot("service_sump", "service", box(-0.5, 1.5, 3, 4, 5, 6), -40, -40, 15, 15, 0.9, 0.9, 0, 3, NO_TIME));
		SHOTS.add(new Shot("service_radiator", "service", box(1.5, 1.5, 3, 5, 5, 6), -20, -20, 18, 18, 0.9, 0.95, 0, 3, NO_TIME));
		SHOTS.add(new Shot("service_oil", "service", box(-4.5, 0, 3, 1.5, 5, 6.5), -28, -10, 15, 17, 1, 1.08, 0, 3, NO_TIME));
		SHOTS.add(new Shot("service_cans", "service", box(-6, 0, 6, 5, 6, 10.5), 118, 138, 12, 13, 1.08, 1.14, 0, 6, NO_TIME));
		// The casing styles: the Maker's Plate and the four styles passed, then the player dyeing the pillars (gameplay)
		SHOTS.add(new Shot("casing_styles", "casing", box(5.5, 1, 4.5, 13.5, 6, 6), -22, -8, 10, 12, 1, 1, 8, 3, NO_TIME));
		SHOTS.add(new Shot("casing_dye", "casing", box(2, 1, 4.5, 18, 6, 6), -10, -10, 9, 9, 1, 1, 0, 6, NO_TIME));
		// The drivetrain: the dual-mass flywheel on the diesel crank, then the transmission the power leaves through
		SHOTS.add(new Shot("factory_flywheel", "factory", box(8, 1.5, 3.5, 11, 5, 6.8), 40, 64, 10, 14, 0.86, 0.98, 0, 3, NO_TIME));
		SHOTS.add(new Shot("factory_gearbox", "factory", box(-2, 1.5, 4, 2.5, 6.8, 6.5), 22, -4, 14, 17, 0.92, 1.04, 0, 6, NO_TIME));
		// What the power is for: the row of Create machines off a diesel crank
		SHOTS.add(new Shot("factory_line", "factory", box(-6, 0, 4, 3, 5.8, 6), -28, -8, 12, 15, 1.02, 1.1, 0, 6, NO_TIME));
		// The Copper Gasket's sequenced assembly, along its belt
		SHOTS.add(new Shot("gasket_line", "gasket", box(1, 1, 4.5, 10, 4.2, 6), -18, 6, 5, 7, 1.06, 1.16, 0, 9, NO_TIME));
		// The engine on wheels: only with Create Aeronautics and Sable in the run (-PshowcaseSet=trailer on runShowcaseAeronautics)
		if (aero()) {
			SHOTS.add(new Shot("rover_departs", "rover", box(-6, 0, 2, 19, 8, 8), -34, -6, 9, 13, 1.16, 1, 0, 9, GOLDEN_HOUR));
			SHOTS.add(new Shot("rover_wheels", "rover", box(12.8, 0, 2, 16.8, 3.6, 4.5), -22, 0, 6, 10, 0.9, 1.04, 0, 3, NO_TIME));
		}
		// Fuels and the refinery as the sun goes down (the exhaust-side shot is early, it faces away from the light)
		SHOTS.add(new Shot("v8_exhaust_dusk", "v8", v8, 136, 158, 10, 12, 1.08, 1.2, 0, 3, GOLDEN_HOUR));
		SHOTS.add(new Shot("refinery_column", "refinery", box(5, 0, 2, 10, 9, 6), -48, -24, 4, 7, 1.08, 1.18, 0, 6, DUSK));
		SHOTS.add(new Shot("refinery_base", "refinery", box(4.5, 0, 1, 8.5, 3.5, 5), -58, -40, 16, 14, 0.9, 1.02, 0, 3, NO_TIME));
		SHOTS.add(new Shot("refinery_tops", "refinery", box(5, 5, 3, 10, 9, 5), -58, -38, 0, 3, 0.9, 1.02, 0, 3, NO_TIME));
		SHOTS.add(new Shot("refinery_flare", "refinery", box(5.5, 5, 2.5, 9.5, 9.5, 5.5), 24, 52, -10, -4, 0.86, 1, 0, 3, NIGHTFALL));
		// After dark: from idle the throttles open and it revs, then off the fuel twice, with the blow-off and the anti-lag
		SHOTS.add(new Shot("night_pull", "night", box(4.5, 3.5, 3, 9, 8, 7), 122, 146, 11, 14, 0.96, 1.12, 0, 12, NIGHT));
		// The finale: from close on the factory, pulling back to the whole row
		SHOTS.add(new Shot("factory_wide", "factory", box(-6, 0, 3, 11, 6, 7), -12, -34, 12, 14, 1.16, 0.98, 0, 9, DUSK));
		defineCues();
		filterShots();
	}

	/**
	 * {@code -PtrailerShots=a,b} (a comma-separated list of shot names) keeps only those shots, so a new pad can be looked at in
	 * a short run instead of the whole tour. The shots stay in the tour's order.
	 */
	private static void filterShots() {
		String only = System.getProperty(PROPERTY + ".trailerShots", "");
		if (only.isBlank())
			return;
		List<String> keep = List.of(only.split(","));
		SHOTS.removeIf(shot -> !keep.contains(shot.name()));
		if (SHOTS.isEmpty())
			throw new IllegalStateException("no trailer shot is called any of " + keep);
	}

	private static void scheduleTrailer() {
		defineTrailer();
		// Spectator mode, so nothing is in the camera's way and it does not fall; the tour starts at the first shot
		STEPS.add(new Step(1000, () -> server(server -> {
			command(server, "gamemode spectator @a");
			command(server, "time set " + DAY);
			teleport(server, pose(SHOTS.get(0), 0));
		})));
		// tools/trailer/record.py starts its screen recording on this line, which is LEAD_IN seconds before the first shot
		STEPS.add(new Step(1001, () -> CreateEngines.LOGGER.info("Showcase trailer ready")));
		// A white flash and an anvil's clap at one moment, in the lead-in: record.py finds both in what it recorded and so measures
		// how far the sound trails the picture, instead of assuming it does not
		STEPS.add(new Step(1001 + 80, () -> syncPending = true));
		STEPS.add(new Step(1000 + (int) (LEAD_IN * 20), () -> {
			trailerStart = System.nanoTime();
			trailerStartMillis = System.currentTimeMillis();
			writeShotList();
		}));
	}

	/** Where the camera is, as {x, eyeY, z, yaw, pitch}, {@code t} of the way (0 to 1) through the shot. */
	private static double[] pose(Shot shot, double t) {
		Scene scene = SCENES.stream()
			.filter(s -> s.name().equals(shot.scene()))
			.findFirst()
			.orElseThrow();
		BlockPos pad = pad(scene.bay());
		// Half linear and half eased: the shot starts and ends gently without crawling in the middle
		double e = 0.5 * t + 0.5 * t * t * (3 - 2 * t);
		double yaw = shot.yaw0() + (shot.yaw1() - shot.yaw0()) * e;
		double pitch = shot.pitch0() + (shot.pitch1() - shot.pitch0()) * e;
		double fill = shot.fill0() + (shot.fill1() - shot.fill0()) * e;
		ShowcaseRunFraming.Placing placing = ShowcaseRunFraming.place(shot.subject(), yaw, pitch, fill);
		double tx = pad.getX() + placing.aimX() + shot.travelX() * (e - 0.5);
		double ty = pad.getY() + placing.aimY();
		double tz = pad.getZ() + placing.aimZ();
		double x = pad.getX() + placing.eyeX() + shot.travelX() * (e - 0.5);
		double y = Math.max(pad.getY() + placing.eyeY(), pad.getY() + 1.8);
		double z = pad.getZ() + placing.eyeZ();
		if (scene.bay() == AERO_BAY) {
			// Open ground, no walls to stay inside: the camera follows the craft by however far it has moved since it left its pad
			double[] moved = followedMove();
			tx += moved[0];
			ty += moved[1];
			tz += moved[2];
			x += moved[0];
			y = Math.max(y + moved[1], pad.getY() + 1.8);
			z += moved[2];
		} else {
			// Stay inside the courtyard: a wall is a block thick and the camera must not come out of it or look through it
			double inX = Math.max(pad.getX() + PAD_X0 + 1.6, Math.min(pad.getX() + PAD_X1 - 0.6, x));
			double inZ = Math.max(pad.getZ() + PAD_Z0 + 1.6, Math.min(pad.getZ() + PAD_Z1 - 0.6, z));
			double inY = Math.min(y, pad.getY() + WALL - 1.5);
			// A camera held in by a wall stands nearer than the framing asked, so say so: the shot's angle wants changing
			if (Math.abs(inX - x) + Math.abs(inY - y) + Math.abs(inZ - z) > 0.75 && CLAMPED.add(shot.name()))
				CreateEngines.LOGGER.warn("Showcase trailer shot {}: held inside the walls, {} blocks from its framing", shot.name(),
					String.format(Locale.ROOT, "%.1f", Math.abs(inX - x) + Math.abs(inY - y) + Math.abs(inZ - z)));
			x = inX;
			y = inY;
			z = inZ;
		}
		// Look at the aim from where the camera ended up, so the clamps do not turn it away
		double lookPitch = Math.toDegrees(Math.atan2(y - ty, Math.hypot(x - tx, z - tz)));
		double lookYaw = Math.hypot(x - tx, z - tz) < 0.01 ? yaw : Math.toDegrees(Math.atan2(x - tx, tz - z));
		return new double[] { x, y, z, lookYaw, lookPitch };
	}

	/** The shots already reported as held in by a wall, so each is said once. */
	private static final java.util.Set<String> CLAMPED = new java.util.HashSet<>();

	private static double[] seenAt;
	private static long seenNanos;
	private static double[] seenBefore;
	private static long seenBeforeNanos;
	private static final double[] FOLLOW = new double[3];
	private static long followNanos;

	/**
	 * How far the craft has moved since it left its pad, for the camera. The craft's place changes once a server tick (twenty times a
	 * second), which at a frame rate of sixty shows as a camera that steps, so the move is carried on from the last two places at the
	 * speed between them and then eased towards, a little behind the craft: smooth, and a fraction of a block late.
	 */
	private static double[] followedMove() {
		double[] now = aeroAt;
		double[] from = aeroStart;
		long t = System.nanoTime();
		if (now == null || from == null)
			return new double[3];
		if (now != seenAt) {
			seenBefore = seenAt;
			seenBeforeNanos = seenNanos;
			seenAt = now;
			seenNanos = t;
		}
		double[] target = new double[3];
		for (int i = 0; i < 3; i++) {
			target[i] = now[i] - from[i];
			if (seenBefore != null && seenNanos > seenBeforeNanos) {
				double speed = (seenAt[i] - seenBefore[i]) / ((seenNanos - seenBeforeNanos) / 1e9);
				// Carried on at that speed for no longer than a tick or two, so a stopped craft is not overshot
				target[i] += speed * Math.min((t - seenNanos) / 1e9, 0.1);
			}
		}
		double dt = followNanos == 0 ? 1 : Math.min((t - followNanos) / 1e9, 0.5);
		followNanos = t;
		// Ease towards the target: about a tenth of a second behind a craft at a steady speed, with the steps taken out
		double ease = followNanos == 0 || dt >= 0.5 ? 1 : 1 - Math.exp(-dt / 0.12);
		for (int i = 0; i < 3; i++)
			FOLLOW[i] += (target[i] - FOLLOW[i]) * ease;
		return FOLLOW.clone();
	}

	private static void teleport(MinecraftServer server, double[] pose) {
		command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", pose[0], pose[1] - 1.62, pose[2],
			pose[3], pose[4]));
	}

	private static void writeShotList() {
		StringBuilder text = new StringBuilder("Trailer shots, seconds from the first frame of the recording\n");
		double at = 0;
		int index = 0;
		for (Shot shot : SHOTS) {
			text.append(String.format(Locale.ROOT, "%5.1f - %5.1f  %02d %s%s%n", at, at + shot.seconds(), ++index,
				shot.name(), shot.time() != NO_TIME && shot.time() != DAY ? "  (time of day " + shot.time() + ")" : ""));
			at += shot.seconds();
		}
		text.append(String.format(Locale.ROOT, "total %.1f seconds%n", at));
		// The same list for tools/trailer/post.py, with the wall-clock moment the first shot began
		StringBuilder json = new StringBuilder(String.format(Locale.ROOT, "{\"startEpochMs\": %d, \"shots\": [%n", trailerStartMillis));
		double from = 0;
		for (int i = 0; i < SHOTS.size(); i++) {
			Shot shot = SHOTS.get(i);
			json.append(String.format(Locale.ROOT, "  {\"name\": \"%s\", \"start\": %.2f, \"end\": %.2f}%s%n", shot.name(), from,
				from + shot.seconds(), i + 1 < SHOTS.size() ? "," : ""));
			from += shot.seconds();
		}
		json.append(String.format(Locale.ROOT, "], \"total\": %.2f}%n", from));
		Minecraft minecraft = Minecraft.getInstance();
		try {
			Files.writeString(minecraft.gameDirectory.toPath()
				.resolve("trailer_shots.txt"), text.toString());
			Files.writeString(minecraft.gameDirectory.toPath()
				.resolve("trailer_shots.json"), json.toString());
		} catch (IOException e) {
			CreateEngines.LOGGER.warn("Showcase: could not write the trailer shot list", e);
		}
		CreateEngines.LOGGER.info("Showcase trailer starts:\n{}", text);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Gameplay clips: first person with the Bank Builder in hand, and things happening in front of the camera
	// -----------------------------------------------------------------------------------------------------------------

	/** Something that happens {@code at} seconds into a shot: a block laid, a swing of the arm. */
	private record Cue(String shot, double at, Runnable action) {
	}

	private static final List<Cue> CUES = new ArrayList<>();
	private static final java.util.Set<Cue> FIRED = new java.util.HashSet<>();
	/** Shots seen through the player's own eyes, with the shot's item (the Bank Builder, a filter, a dye) in hand, not a spectator's camera. */
	private static final java.util.Map<String, java.util.function.Supplier<ItemStack>> FIRST_PERSON = java.util.Map.of(
		"grow_engine", () -> new ItemStack(CEItems.BANK_BUILDER.get()),
		"headphones_on", () -> new ItemStack(CEItems.HEADPHONES.get()),
		"casing_dye", () -> new ItemStack(net.minecraft.world.item.Items.RED_DYE),
		"service_sump", () -> new ItemStack(CEItems.OIL_FILTER.get()));

	/**
	 * The player uses {@code held} on a block, for real: the server runs the item's and the block's own code (a dye on a
	 * casing, a filter in the pan) as if a click had landed, and the arm swings.
	 */
	private static void useOn(BlockPos pos, Direction face, ItemStack held) {
		swing();
		withPlayer((level, player) -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, held);
			player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), face, pos, false));
		});
	}

	private static void withPlayer(java.util.function.BiConsumer<ServerLevel, ServerPlayer> action) {
		server(server -> {
			var players = server.getPlayerList()
				.getPlayers();
			if (!players.isEmpty())
				action.accept(server.overworld(), players.get(0));
		});
	}

	private static void swing() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null)
			player.swing(InteractionHand.MAIN_HAND);
	}

	/** The stages of the growing engine: layout, throws, whether it is precision, and what it wears. */
	private record Stage(BankBuilderItem.Layout layout, int throwsCount, boolean precision, Kit kit) {
	}

	private static void defineCues() {
		Kit plain = Kit.plain(Fuel.GASOLINE);
		Kit boosted = new Kit(Fuel.GASOLINE, 2, false, 0, false, true, false);
		BankBuilderItem.Layout inline = BankBuilderItem.Layout.INLINE;
		BankBuilderItem.Layout vee = BankBuilderItem.Layout.VEE;
		Stage[] stages = { new Stage(inline, 1, false, plain), new Stage(inline, 4, false, plain),
			new Stage(vee, 4, false, plain), new Stage(vee, 8, false, plain), new Stage(vee, 8, true, plain),
			new Stage(vee, 8, true, boosted) };
		// One cylinder, an inline four, a V8, a V16, the V16 in precision parts, then boosted: each is the Bank Builder's work
		// on a fresh crank line, two seconds apart, with the starter turning each over. The last gets its timing set and is
		// let go to run on its own.
		for (int i = 0; i < stages.length; i++) {
			Stage stage = stages[i];
			boolean last = i == stages.length - 1;
			CUES.add(new Cue("grow_engine", 0.2 + 2.0 * i, () -> {
				if (stage.throwsCount() != 1)
					swing();
				withPlayer((level, player) -> {
					growTo(level, player, stage);
					if (last)
						TimingToolItem.apply(level, pad(10).offset(1, CY, CZ), FiringPlanner.Style.SMOOTH);
					level.playSound(null, pad(10).offset(4, CY, CZ), SoundEvents.NETHERITE_BLOCK_PLACE,
						net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f + 0.05f * stage.throwsCount());
				});
				fuelUp();
			}));
		}
		CUES.add(new Cue("grow_engine", 0.2 + 2.0 * (stages.length - 1) + 2.5, () -> withPlayer((level, player) ->
			level.removeBlock(pad(10).offset(0, CY - 1, CZ), false))));
		CUES.add(new Cue("grow_engine", 14.5, () -> shot("trailer_grow_end")));
		// The heavy starter turns the cold V16 over (a redstone block under its input, as the other pads' starters get while they
		// are built), the engine catches and runs on, and the next shot, the player's own, finds it running
		// (positions are worked out when a cue runs: the pads' origin is not known until the world is up)
		CUES.add(new Cue("heavy_starter", 0.5, () -> server(server -> server.overworld()
			.setBlock(pad(BIGBLOCK_BAY).offset(1, CY - 1, CZ - 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL))));
		CUES.add(new Cue("headphones_on", 0.0, () -> server(server -> server.overworld()
			.removeBlock(pad(BIGBLOCK_BAY).offset(1, CY - 1, CZ - 1), false))));
		// The player puts the headphones on: the item's own use, which wears them, and the engine's note is muffled from then on
		CUES.add(new Cue("headphones_on", 2.0, () -> {
			swing();
			withPlayer((level, player) -> player.gameMode.useItem(player, level, player.getItemInHand(InteractionHand.MAIN_HAND),
				InteractionHand.MAIN_HAND));
		}));
		// Dye on the casing pillars, one after another, from the first-person view
		net.minecraft.world.item.Item[] dyes = { net.minecraft.world.item.Items.RED_DYE, net.minecraft.world.item.Items.ORANGE_DYE,
			net.minecraft.world.item.Items.LIME_DYE, net.minecraft.world.item.Items.CYAN_DYE, net.minecraft.world.item.Items.BLUE_DYE,
			net.minecraft.world.item.Items.PURPLE_DYE, net.minecraft.world.item.Items.PINK_DYE,
			net.minecraft.world.item.Items.YELLOW_DYE };
		int[] pillars = { 2, 5, 6, 9, 10, 13, 14, 17 };
		for (int i = 0; i < dyes.length; i++) {
			net.minecraft.world.item.Item dye = dyes[i];
			int x = pillars[i];
			CUES.add(new Cue("casing_dye", 0.5 + 0.65 * i, () -> useOn(casingPillar(x, 2), Direction.NORTH, new ItemStack(dye))));
		}
		// An oil filter into the pan (the item's and the pan's own code)
		CUES.add(new Cue("service_sump", 0.8, () -> useOn(pad(SERVICE_BAY).offset(1, CY, CZ - 1), Direction.NORTH,
			new ItemStack(CEItems.OIL_FILTER.get()))));
		ShowcaseRunFeatures.defineCues((shot, cued) -> CUES.add(new Cue(shot, cued.at(),
			() -> server(server -> cued.action()
				.accept(server.overworld())))));
		// The wheeled engine is lifted into its structure, off the ground, as its first shot begins
		CUES.add(new Cue("rover_departs", 0.0, ShowcaseRun::liftOff));
	}

	/** Takes the pad's engine down (no drops: blocks are set to air, not broken) and builds the stage's in its place. */
	private static void growTo(ServerLevel level, ServerPlayer player, Stage stage) {
		BlockPos at = pad(10);
		for (int x = -1; x <= 19; x++)
			for (int y = 0; y <= CY + 7; y++)
				for (int z = CZ - 7; z <= CZ + 7; z++)
					level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		TANKS.removeIf(tank -> tank.pos().getX() >= at.getX() - 1 && tank.pos().getX() <= at.getX() + 19
			&& tank.pos().getZ() >= at.getZ() && tank.pos().getZ() <= at.getZ() + 12 && tank.pos().getY() >= at.getY());
		// Whatever was dropped is gone before the camera sees it
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(at.getX() - 12, at.getY() - 2, at.getZ() - 12, at.getX() + 36, at.getY() + 14, at.getZ() + 20)))
			item.discard();
		// The builder takes its plugs and cam lobes from the player, who is out of them by the fourth stage: restock
		var inventory = player.getInventory();
		inventory.setItem(9, new ItemStack(CEBlocks.CAM_LOBE.get(), 64));
		inventory.setItem(10, new ItemStack(CEBlocks.CAMSHAFT.get(), 64));
		inventory.setItem(11, new ItemStack(CEItems.SPARK_PLUG.get(), 64));
		bank(level, player, at, stage.layout(), stage.throwsCount(), stage.kit(), STAGE_ALL, stage.precision());
	}

	/**
	 * A shot starts: the camera is a spectator's, with no interface, except in the gameplay clips, where it is the player's own
	 * eyes with the Bank Builder in hand (the hotbar and crosshair are hidden by {@link #onRenderGuiLayer}).
	 */
	private static void startShot(Shot shot, Minecraft minecraft) {
		boolean firstPerson = FIRST_PERSON.containsKey(shot.name());
		minecraft.options.hideGui = !firstPerson;
		withPlayer((level, player) -> {
			player.setGameMode(firstPerson ? GameType.CREATIVE : GameType.SPECTATOR);
			// The headphones come off between shots, so only the shot that puts them on is muffled
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
			if (firstPerson) {
				// Only the shot's own item on the hotbar (the Bank Builder, an oil filter, a dye); the parts the Bank Builder builds
				// with move up into the main inventory
				ItemStack held = FIRST_PERSON.get(shot.name())
					.get();
				var inventory = player.getInventory();
				List<ItemStack> parts = new ArrayList<>();
				for (int slot = 0; slot < 9; slot++)
					if (!inventory.getItem(slot)
						.isEmpty() && !inventory.getItem(slot)
							.is(held.getItem())) {
						parts.add(inventory.getItem(slot)
							.copy());
						inventory.setItem(slot, ItemStack.EMPTY);
					}
				int next = 9;
				for (ItemStack part : parts)
					inventory.setItem(next++, part);
				inventory.setItem(0, held);
				inventory.selected = 0;
			}
		});
		// No tutorial toast over the footage
		minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
		minecraft.getTutorial()
			.setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
		if (firstPerson && minecraft.player != null) {
			minecraft.player.getAbilities().flying = true;
			minecraft.player.onUpdateAbilities();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Sound: only the pad the camera is in is heard
	// -----------------------------------------------------------------------------------------------------------------

	/** Sounds that started in the current pad (client thread only), so they can be stopped when the camera moves on. */
	private static final List<SoundInstance> HEARD = new ArrayList<>();

	private static boolean inCurrentPad(SoundInstance sound) {
		if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE || SHOTS.isEmpty())
			return true;
		Scene scene = null;
		String name = SHOTS.get(Math.max(trailerShot, 0))
			.scene();
		for (Scene candidate : SCENES)
			if (candidate.name().equals(name))
				scene = candidate;
		// The wheeled car has no courtyard, and its sounds are made where its structure is kept, not where it is seen
		if (scene == null || scene.bay() == AERO_BAY)
			return true;
		BlockPos pad = pad(scene.bay());
		return sound.getX() >= pad.getX() + PAD_X0 && sound.getX() <= pad.getX() + PAD_X1 + 1
			&& sound.getZ() >= pad.getZ() + PAD_Z0 && sound.getZ() <= pad.getZ() + PAD_Z1 + 1;
	}

	/**
	 * The gameplay clips keep the player's hand and the item in it, but not the hotbar, the crosshair or the name of the item
	 * picked: they read as a game's interface over the footage and the tall cut of the trailer shows them.
	 */
	private static void onRenderGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
		var name = event.getName();
		if (name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR)
			|| name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)
			|| name.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME))
			event.setCanceled(true);
	}

	/**
	 * The trailer is filmed with one lens, whatever the player's setting or the flying camera's widening would make it, so the
	 * framing's sums hold. The hand in the first-person clips keeps its own.
	 */
	private static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
		if (event.usedConfiguredFov() && trailerStart >= 0)
			event.setFOV(ShowcaseRunFraming.FOV);
	}

	/** A sound from another pad never starts: the engines of the other courtyards are not heard in this one. */
	private static void onPlaySound(PlaySoundEvent event) {
		SoundInstance sound = event.getSound();
		if (sound == null)
			return;
		String path = sound.getLocation()
			.getPath();
		// The gameplay clips earn real advancements: neither this mod's sting nor the game's toast sounds are heard over the footage
		if (sound instanceof com.createengines.content.StingSound || path.startsWith("ui.toast") || path.contains("sting")) {
			CreateEngines.LOGGER.info("Showcase: dropped the sound {} (a toast or sting over the footage)", path);
			event.setSound(null);
		} else if (!inCurrentPad(sound))
			event.setSound(null);
		else
			HEARD.add(sound);
	}

	/** Stops the sounds already playing from a pad the camera has left. */
	private static void sweepSounds() {
		var manager = Minecraft.getInstance()
			.getSoundManager();
		HEARD.removeIf(sound -> {
			if (inCurrentPad(sound))
				return !manager.isActive(sound);
			manager.stop(sound);
			return true;
		});
	}

	/**
	 * Every frame, not every tick, so the movement is smooth: the camera is the player, put where the shot says with no
	 * old position to blend from. The server is told every few ticks so the world around the camera stays loaded.
	 */
	private static void onRenderFrame(RenderFrameEvent.Pre event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (syncPending && minecraft.player != null && minecraft.screen == null) {
			syncPending = false;
			minecraft.setScreen(new SyncFlash());
		}
		if (minecraft.player != null) {
			// Recipe and advancement toasts, and the chat's lines about them, never show over the footage
			minecraft.getToasts()
				.clear();
			minecraft.gui.getChat()
				.clearMessages(false);
		}
		if (trailerStart < 0 || trailerOver || minecraft.player == null)
			return;
		double elapsed = (System.nanoTime() - trailerStart) / 1e9;
		double begins = 0;
		for (int i = 0; i < SHOTS.size(); i++) {
			Shot shot = SHOTS.get(i);
			if (elapsed >= begins + shot.seconds()) {
				begins += shot.seconds();
				continue;
			}
			if (i != trailerShot) {
				trailerShot = i;
				trailerStill = false;
				if (shot.time() != NO_TIME)
					server(server -> command(server, "time set " + shot.time()));
				CreateEngines.LOGGER.info("Showcase trailer shot {} {}", i + 1, shot.name());
				FIRED.clear();
				startShot(shot, minecraft);
				sweepSounds();
			}
			for (Cue cue : CUES)
				if (cue.shot().equals(shot.name()) && elapsed - begins >= cue.at() && FIRED.add(cue))
					cue.action().run();
			double t = (elapsed - begins) / shot.seconds();
			double[] pose = pose(shot, t);
			trailerPose = pose;
			LocalPlayer player = minecraft.player;
			double feetY = pose[1] - 1.62;
			player.setPos(pose[0], feetY, pose[2]);
			player.xo = player.xOld = pose[0];
			player.yo = player.yOld = feetY;
			player.zo = player.zOld = pose[2];
			player.setDeltaMovement(0, 0, 0);
			player.setYRot((float) pose[3]);
			player.setXRot((float) pose[4]);
			player.yRotO = (float) pose[3];
			player.xRotO = (float) pose[4];
			// One still from the middle of every shot, to check the framing without watching the whole thing
			if (!trailerStill && t >= 0.5) {
				trailerStill = true;
				shot(String.format(Locale.ROOT, "trailer_%02d_%s", i + 1, shot.name()));
			}
			return;
		}
		// Hold the last frame for a moment, then end the run
		if (elapsed > begins + 1.5) {
			trailerOver = true;
			logEngines("after the trailer");
			minecraft.stop();
		}
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
