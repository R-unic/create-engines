package com.createengines.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import com.createengines.CreateEngines;
import com.createengines.compat.sable.SableCompat;
import com.createengines.content.SoundProbe;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The sound bench (gradle {@code -PsmokeSound}, run by tools/sound_bench.py, which records the speakers): reference engines
 * one at a time, each heard from the same distance through the same script (start, idle, throttle sweep, blip, load step,
 * stall, restart, a walk away and back), then vanilla and Create sounds from that distance to compare levels with, one
 * engine behind walls and indoors ({@link ShowcaseRunSoundBenchPlaces}) and a room of sixteen. With Create Aeronautics
 * loaded it can instead carry an engine past the listener on a moving structure. Everything that happens is logged with its time, for tools/sound_audit.py. Named like the showcase
 * so the release jar leaves it out (build.gradle).
 * <p>
 * {@code -PsoundScenes=single,v12} runs only those scenes.
 */
public final class ShowcaseRunSoundBench {

	/** How far the listener stands from the crankshaft's line, in blocks: the same for every scene, so levels compare. */
	private static final int LISTEN = 6;
	/** How far the walk goes: past the 32 blocks an engine reaches. */
	private static final int WALK_TO = 34;
	/** Where along the room's grid of singles its first engine stands: a column of the grid, so no other is built over it. */
	private static final int ROOM_FIRST = 8;
	/** Between pads: far past anything heard from the one before. */
	private static final int BAY = 160;
	/** Idle stop of each throttle body, in its dial's steps of 5%: its default, and wide open. */
	static final int IDLE = ThrottleBlockEntity.DEFAULT_IDLE;
	private static final int OPEN = ThrottleBlockEntity.MAX_IDLE;
	/** Mechanical Presses the load step's clutch engages on a banked engine, and on a small one that four would overstress. */
	private static final int PRESSES = 4;
	private static final int FEW_PRESSES = 1;
	private static final int CY = ShowcaseRun.CY;
	private static final int CZ = ShowcaseRun.CZ;

	/** What builds an engine with its corner at {@code at}, with its starter engaged. */
	private interface Builder {
		void build(ServerLevel level, ServerPlayer player, BlockPos at);
	}

	/**
	 * A reference engine. {@code crank} and {@code starterPower} are relative to the pad's corner, {@code crankZ} is the
	 * crank line, {@code loadX} the first free block past the crank's far end, and {@code length} how far the pad reaches
	 * along X. A null {@code style} leaves the engine timed as built.
	 */
	record Rig(String id, String title, Builder builder, BlockPos crank, BlockPos starterPower, int loadX,
		int length, FiringPlanner.Style style, int idle, int presses) {

		Rig idlingAt(int steps) {
			return new Rig(id, title, builder, crank, starterPower, loadX, length, style, steps, presses);
		}

		Rig loadedBy(int count) {
			return new Rig(id, title, builder, crank, starterPower, loadX, length, style, idle, count);
		}
	}

	/** A stretch of the script: at least {@code min} ticks, then until {@code done}, and no more than {@code max}. */
	private record Phase(String scene, String name, int min, int max, Runnable enter, IntConsumer tick,
		BooleanSupplier done) {
	}

	private static final Deque<Phase> PHASES = new ArrayDeque<>();
	private static ShowcaseRunSoundLog log;
	private static boolean worldRequested;
	private static int ticksInWorld = -1;
	private static BlockPos origin = BlockPos.ZERO;
	private static Phase current;
	private static int phaseTicks;

	/** The engine under test: its corner, and its primary crankshaft, or null between scenes. */
	private static BlockPos pad;
	private static Rig rig;
	/** Where the listener is held, and what it looks at. */
	private static Vec3 feet;
	private static float yaw;
	private static float pitch = 8;
	private static int running;
	private static final Map<BlockPos, BlockState> INTAKES = new LinkedHashMap<>();

	private ShowcaseRunSoundBench() {
	}

	static void register() {
		CreateEngines.LOGGER.info("Sound bench enabled");
		NeoForge.EVENT_BUS.addListener(ShowcaseRunSoundBench::onClientTick);
		NeoForge.EVENT_BUS.addListener(ShowcaseRunSoundBench::onPlaySound);
	}

	/** Every sound the game starts is logged: a noise in the recording with none logged was another program's. */
	private static void onPlaySound(PlaySoundEvent event) {
		if (log != null && event.getSound() != null)
			log.event("play", ",\"sound\":\"" + event.getSound()
				.getLocation() + "\"");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The engines

	private static Rig bank(String id, String title, BankBuilderItem.Layout layout, int throwsCount,
		ShowcaseRun.Kit kit, FiringPlanner.Style style) {
		Builder builder = (level, player, at) -> {
			ShowcaseRunBuild.bank(level, player, at, layout, throwsCount, kit);
			// Open exhausts: the reference is the engine, not its mufflers
			for (BlockPos pos : blocks(at, throwsCount + 12))
				if (level.getBlockState(pos)
					.is(CEBlocks.MUFFLER.get()))
					ShowcaseRunBuild.pipe(level, pos, CEBlocks.EXHAUST_MANIFOLD.get());
		};
		return new Rig(id, title, builder, new BlockPos(1, CY, CZ), new BlockPos(0, CY - 1, CZ), throwsCount + 6,
			throwsCount + 12, style, IDLE, PRESSES);
	}

	private static Rig bench(String id, String title, Builder builder) {
		return new Rig(id, title, builder, TestEngine.PRIMARY, TestEngine.STARTER_POWER, 6, 12, null, IDLE, FEW_PRESSES);
	}

	/** A throttle body where the engine's air intake opening was, and the opening moved out in front of it. */
	private static void throttle(ServerLevel level, BlockPos intake) {
		level.setBlock(intake, CEBlocks.THROTTLE.getDefaultState()
			.setValue(ManifoldBlock.CONNECTIONS.get(Direction.NORTH), true)
			.setValue(ManifoldBlock.CONNECTIONS.get(Direction.SOUTH), true), Block.UPDATE_ALL);
		level.setBlock(intake.north(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
	}

	private static List<Rig> rigs() {
		ShowcaseRun.Kit spark = ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.GASOLINE);
		return List.of(
			bench("single", "Slow single (diesel)", (level, player, at) -> {
				TestEngine.build(level, at, true);
				throttle(level, at.offset(TestEngine.AIR_INTAKE));
			}),
			bank("i4", "Inline four", BankBuilderItem.Layout.INLINE, 4, spark, FiringPlanner.Style.FLAT_PLANE),
			bank("v8_cross", "Cross-plane V8", BankBuilderItem.Layout.VEE, 4, spark, FiringPlanner.Style.CROSS_PLANE),
			bank("v8_flat", "Flat-plane V8", BankBuilderItem.Layout.VEE, 4, spark, FiringPlanner.Style.FLAT_PLANE),
			bank("flat4", "Flat four", BankBuilderItem.Layout.FLAT, 2, spark, FiringPlanner.Style.FLAT_PLANE),
			bank("vtwin", "V-twin", BankBuilderItem.Layout.VEE, 1, spark, FiringPlanner.Style.CAMS_ONLY)
				.loadedBy(2),
			bank("i6_diesel", "Inline six diesel", BankBuilderItem.Layout.INLINE, 6,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.DIESEL), FiringPlanner.Style.SMOOTH),
			bank("v12", "V12", BankBuilderItem.Layout.VEE, 6, spark, FiringPlanner.Style.SMOOTH),
			// Throttled to a fifth the rotor runs too lean to light, so it idles wider open than the rest
			bench("rotary", "Single rotor", (level, player, at) -> {
				TestEngine.buildRotary(level, at, true);
				throttle(level, at.offset(TestEngine.ROTARY_INTAKE));
			}).idlingAt(14),
			bank("turbo_i4", "Turbocharged inline four", BankBuilderItem.Layout.INLINE, 4,
				new ShowcaseRun.Kit(ShowcaseRun.Fuel.GASOLINE, 1, false, 0, false, false, false),
				FiringPlanner.Style.FLAT_PLANE));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The script

	private static void schedule() {
		String wanted = System.getProperty("create_engines.visualSmokeTest.sound.scenes", "")
			.trim();
		Set<String> only = wanted.isEmpty() ? Set.of() : Arrays.stream(wanted.split(","))
			.map(String::trim)
			.collect(Collectors.toSet());
		// The recorder starts when it sees this line, and needs a moment before the first clap
		step("setup", "ready", 100, () -> CreateEngines.LOGGER.info("SOUNDBENCH ready"));
		clap("clap_start");
		int bay = 1;
		for (Rig each : rigs()) {
			if (only.isEmpty() || only.contains(each.id()))
				engineScene(each, bay);
			bay++;
		}
		if (only.isEmpty() || only.contains("reference"))
			referenceScene(bay);
		bay++;
		bay = ShowcaseRunSoundBenchPlaces.schedule(only, bay, rigs().get(1));
		if (only.isEmpty() || only.contains("soak"))
			ShowcaseRunSoundBenchSoak.schedule(bay);
		bay++;
		// Last of all: whatever the room leaves sounding must not get into any other scene
		if (only.isEmpty() || only.contains("room"))
			roomScene(bay);
		clap("clap_end");
		step("setup", "done", 40, () -> CreateEngines.LOGGER.info("SOUNDBENCH done"));
		step("setup", "quit", 20, () -> Minecraft.getInstance()
			.stop());
	}

	/** A script stretch in which {@code tick} is told how far in it is. */
	static void during(String scene, String name, int ticks, IntConsumer tick) {
		PHASES.add(new Phase(scene, name, ticks, ticks, null, tick, null));
	}

	static void step(String scene, String name, int ticks, Runnable enter) {
		PHASES.add(new Phase(scene, name, ticks, ticks, enter, null, null));
	}

	/** A clap through the game's own sound, twice a run, so the audit can line the recording up with the log. */
	private static void clap(String name) {
		step("setup", name, 60, () -> {
			log.event("clap", "");
			Minecraft.getInstance()
				.getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0F, 1.0F));
		});
	}

	/** Builds, times and starts {@code each} on the pad in {@code bay}, with the listener {@code distance} blocks off. */
	static void startedEngine(String id, Rig each, int bay, int distance) {
		step(id, "build", 60, () -> {
			rig = each;
			pad = origin.offset(bay * BAY, 0, 0);
			INTAKES.clear();
			listenAt(pad.getX() + each.length() / 2.0 - 1, pad.getZ() + each.crank()
				.getZ() + 0.5 - distance);
			teleport();
			onServer((level, player) -> {
				// What the Bank Builder lays only if its holder carries it: the cam line and the plugs
				for (ItemStack kit : List.of(new ItemStack(CEBlocks.CAM_LOBE.get(), 64),
					new ItemStack(CEBlocks.CAMSHAFT.get(), 64), new ItemStack(CEItems.SPARK_PLUG.get(), 64)))
					player.getInventory()
						.add(kit);
				each.builder()
					.build(level, player, pad);
				// The starter waits for the timing; the load line is declutched by its redstone block
				level.removeBlock(pad.offset(each.starterPower()), false);
				loadLine(level, pad, each);
				setThrottles(level, each.idle());
				fuel(level);
			});
		});
		// Timed while it cranks, as the showcase does it: the Timing Tool needs to know which way the crank turns
		PHASES.add(new Phase(id, "time", 100, 100, () -> engageStarter(), t -> {
			if (t == 60 && each.style() != null)
				onServer((level, player) -> TimingToolItem.apply(level, pad.offset(each.crank()), each.style()));
		}, null));
		start(id, "start");
	}

	private static void engineScene(Rig each, int bay) {
		String id = each.id();
		startedEngine(id, each, bay, LISTEN);
		step(id, "idle", 160, null);
		PHASES.add(new Phase(id, "sweep", 240, 240, null, t -> {
			if (t % 4 == 0) {
				double up = t < 140 ? t / 140.0 : Math.max(0, 1 - (t - 140) / 100.0);
				onServer((level, player) -> setThrottles(level, (int) Math.round(each.idle() + (OPEN - each.idle()) * up)));
			}
		}, null));
		step(id, "settle", 100, null);
		PHASES.add(new Phase(id, "blip", 120, 120, null, t -> {
			if (t == 20 || t == 32)
				onServer((level, player) -> setThrottles(level, t == 20 ? OPEN : each.idle()));
		}, null));
		step(id, "load_approach", 100, () -> onServer((level, player) -> setThrottles(level, (each.idle() + OPEN) / 2)));
		step(id, "load_on", 120, () -> onServer((level, player) -> level.removeBlock(pad.offset(each.loadX(),
			each.crank()
				.getY() + 1,
			each.crank()
				.getZ()), false)));
		step(id, "load_off", 100, () -> onServer((level, player) -> level.setBlock(pad.offset(each.loadX(), each.crank()
			.getY() + 1,
			each.crank()
				.getZ()), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL)));
		// Taking its air away stops it; a heavy flywheel takes its time
		PHASES.add(new Phase(id, "stall", 100, 700, () -> onServer((level, player) -> {
			setThrottles(level, each.idle());
			for (BlockPos pos : blocks(pad, each.length())) {
				BlockState state = level.getBlockState(pos);
				if (state.is(CEBlocks.AIR_INTAKE.get())) {
					INTAKES.put(pos.immutable(), state);
					level.setBlock(pos, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}), null, () -> summary() == null || Math.abs(summary().rpm()) < 1));
		step(id, "stalled", 40, () -> onServer((level, player) -> INTAKES
			.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL))));
		start(id, "restart");
		step(id, "restarted", 100, null);
		walk(id, "walk_away", LISTEN, WALK_TO);
		walk(id, "walk_back", WALK_TO, LISTEN);
		clearScene(id);
	}

	/** Takes the pad's engine away. */
	static void clearScene(String id) {
		step(id, "clear", 40, () -> {
			BlockPos corner = pad;
			int length = rig.length();
			rig = null;
			onServer((level, player) -> clear(level, corner, length));
		});
	}

	private static void engageStarter() {
		onServer((level, player) -> level.setBlock(pad.offset(rig.starterPower()), Blocks.REDSTONE_BLOCK
			.defaultBlockState(), Block.UPDATE_ALL));
	}

	/** Engages the starter until every cylinder has been firing for two seconds, then lets it go. */
	private static void start(String id, String name) {
		PHASES.add(new Phase(id, name, 40, 500, () -> {
			running = 0;
			engageStarter();
		}, null, () -> running >= 40));
		step(id, name + "_release", 60, () -> onServer((level, player) -> level.removeBlock(pad.offset(rig
			.starterPower()), false)));
	}

	private static void walk(String id, String name, int from, int to) {
		PHASES.add(new Phase(id, name, 220, 220, null, t -> {
			double distance = from + (to - from) * Math.min(1, t / 200.0);
			feet = new Vec3(feet.x, feet.y, pad.getZ() + rig.crank()
				.getZ() + 0.5 - distance);
		}, null));
	}

	/**
	 * One single, then fifteen more round it, heard from the same spot: the audit holds the room to no more than 6 dB above
	 * the one.
	 */
	private static void roomScene(int bay) {
		// Its first engine is the one nearest the listener: that is the crankshaft the log follows
		Rig one = new Rig("room", "Sixteen singles", null, TestEngine.PRIMARY.east(ROOM_FIRST),
			TestEngine.STARTER_POWER.east(ROOM_FIRST), 0, 12, null, IDLE, 0);
		step("room", "build", 60, () -> {
			rig = one;
			pad = origin.offset(bay * BAY, 0, 0);
			listenAt(pad.getX() + ROOM_FIRST + 3.5, pad.getZ() + TestEngine.PRIMARY.getZ() + 0.5 - LISTEN);
			teleport();
			onServer((level, player) -> {
				TestEngine.build(level, pad.offset(ROOM_FIRST, 0, 0), true);
				fuel(level);
			});
		});
		PHASES.add(new Phase("room", "start_one", 80, 400, null, null, () -> ticksRunning(pad.offset(ROOM_FIRST, 0, 0))));
		step("room", "release_one", 100, () -> onServer((level, player) -> level.removeBlock(pad.offset(ROOM_FIRST, 0, 0)
			.offset(TestEngine.STARTER_POWER), false)));
		step("room", "one", 300, null);
		step("room", "start_rest", 240, () -> onServer((level, player) -> {
			for (BlockPos corner : roomCorners())
				if (!corner.equals(pad.offset(ROOM_FIRST, 0, 0)))
					TestEngine.build(level, corner, true);
			fuel(level);
		}));
		step("room", "release_rest", 200, () -> onServer((level, player) -> {
			for (BlockPos corner : roomCorners())
				level.removeBlock(corner.offset(TestEngine.STARTER_POWER), false);
		}));
		step("room", "sixteen", 600, null);
		step("room", "clear", 40, ShowcaseRunSoundBench::clearRoom);
		// With every engine removed there must be nothing left to hear
		step("room", "gone", 140, null);
	}

	/** Takes a room of sixteen away. */
	static void clearRoom() {
		BlockPos corner = pad;
		rig = null;
		onServer((level, player) -> {
			for (BlockPos x = corner; x.getX() <= corner.getX() + 36; x = x.east(8))
				for (int z = 0; z <= 18; z += 6)
					clear(level, x.south(z), 8);
		});
	}

	/** Makes {@code bay} the pad under test, with {@code of} the engine the log follows. */
	static void useBay(Rig of, int bay) {
		rig = of;
		pad = origin.offset(bay * BAY, 0, 0);
	}

	/** Waits for the room's engine at {@code offset} from the pad to be running. */
	static void startedRoomEngine(String scene, String name, BlockPos offset) {
		PHASES.add(new Phase(scene, name, 80, 400, null, null, () -> ticksRunning(pad.offset(offset))));
	}

	static List<BlockPos> roomCorners() {
		List<BlockPos> corners = new ArrayList<>();
		for (int row = 0; row < 4; row++)
			for (int column = 0; column < 4; column++)
				corners.add(pad.offset(column * 8, 0, row * 6));
		return corners;
	}

	/** Sounds from the game and from Create, from the listening distance, to hold the engines' loudness against. */
	private static void referenceScene(int bay) {
		step("reference", "build", 80, () -> {
			rig = null;
			pad = origin.offset(bay * BAY, 0, 0);
			listenAt(pad.getX() + 0.5, pad.getZ() + CZ + 0.5 - LISTEN);
			teleport();
		});
		step("reference", "silence", 100, null);
		repeated("furnace", 160, 20, () -> play(SoundEvents.FURNACE_FIRE_CRACKLE, 1.0f));
		// One pass of the loop, at the loudest a cart at full speed plays it
		step("reference", "minecart", 160, () -> play(SoundEvents.MINECART_RIDING, 0.7f));
		repeated("press", 180, 30, () -> AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playAt(Minecraft.getInstance().level,
			soundAt(), 1.0f, 1.0f, false));
	}

	private static void repeated(String name, int ticks, int every, Runnable action) {
		PHASES.add(new Phase("reference", name, ticks, ticks, null, t -> {
			if (t % every == 10)
				action.run();
		}, null));
	}

	private static Vec3 soundAt() {
		return new Vec3(feet.x, pad.getY() + CY + 0.5, feet.z + LISTEN);
	}

	private static void play(SoundEvent sound, float volume) {
		Vec3 at = soundAt();
		Minecraft.getInstance().level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, 1.0f, false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The world

	static List<BlockPos> blocks(BlockPos corner, int length) {
		List<BlockPos> all = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-3, 0, CZ - 7), corner.offset(length, CY + 5, CZ + 6)))
			all.add(pos.immutable());
		return all;
	}

	private static void clear(ServerLevel level, BlockPos corner, int length) {
		for (BlockPos pos : blocks(corner, length))
			if (!level.getBlockState(pos)
				.isAir())
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS
					| Block.UPDATE_KNOWN_SHAPE);
	}

	/** Past the crank's far end: a clutch, held open by a redstone block above it, and a line of presses it can engage. */
	private static void loadLine(ServerLevel level, BlockPos corner, Rig of) {
		int y = of.crank()
			.getY();
		int z = of.crank()
			.getZ();
		level.setBlock(corner.offset(of.loadX(), y + 1, z), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(corner.offset(of.loadX(), y, z), AllBlocks.CLUTCH.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X)
			.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
		for (int i = 1; i <= of.presses(); i++)
			level.setBlock(corner.offset(of.loadX() + i, y, z), AllBlocks.MECHANICAL_PRESS.getDefaultState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), Block.UPDATE_ALL);
	}

	private static void setThrottles(ServerLevel level, int steps) {
		if (rig == null)
			return;
		for (BlockPos pos : blocks(pad, rig.length()))
			if (level.getBlockEntity(pos) instanceof ThrottleBlockEntity body)
				body.idle.setValue(steps);
	}

	/** Keeps every head and rotor housing on the pad full of its fuel and of oil. */
	static void fuel(ServerLevel level) {
		if (rig == null)
			return;
		if (rig.id()
			.equals("room")) {
			for (BlockPos corner : roomCorners())
				if (level.getBlockEntity(corner.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
					fill(head.tank(), head.oilTank(), false);
			return;
		}
		boolean spark = !rig.id()
			.equals("single") && !rig.id()
				.endsWith("diesel");
		for (BlockPos pos : blocks(pad, rig.length())) {
			if (level.getBlockEntity(pos) instanceof CylinderHeadBlockEntity head)
				fill(head.tank(), head.oilTank(), spark);
			else if (level.getBlockEntity(pos) instanceof RotorHousingBlockEntity housing)
				fill(housing.tank(), housing.oilTank(), true);
		}
	}

	private static void fill(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank,
		net.neoforged.neoforge.fluids.capability.templates.FluidTank oil, boolean spark) {
		tank.fill(new FluidStack(spark ? CEFluids.GASOLINE.get()
			.getSource() : CEFluids.DIESEL.get()
				.getSource(),
			CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
		oil.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
			.getSource(), CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
	}

	/** Marks the run as one not to be trusted. */
	static void failed(String why) {
		CreateEngines.LOGGER.error("SOUNDBENCH FAIL {}", why);
		log.event("fail", "");
	}

	static void onServer(java.util.function.BiConsumer<ServerLevel, ServerPlayer> action) {
		VisualSmokeTest.server(server -> {
			try {
				action.accept(server.overworld(), server.getPlayerList()
					.getPlayers()
					.get(0));
			} catch (RuntimeException e) {
				CreateEngines.LOGGER.error("SOUNDBENCH FAIL a step on the server threw", e);
				log.event("fail", "");
			}
		});
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The listener

	static void listenAt(double x, double z) {
		feet = new Vec3(x, pad.getY() + CY - 0.6, z);
		yaw = 0;
	}

	static void teleport() {
		Vec3 to = feet;
		VisualSmokeTest.server(server -> VisualSmokeTest.command(server, String.format(Locale.ROOT,
			"tp @a %.2f %.2f %.2f %.1f %.1f", to.x, to.y, to.z, yaw, pitch)));
	}

	/** The listener floats where the script puts it. */
	private static void hold(Minecraft minecraft) {
		if (feet == null || minecraft.player.position()
			.distanceTo(feet) > 40)
			return;
		minecraft.player.getAbilities().flying = true;
		minecraft.player.setDeltaMovement(Vec3.ZERO);
		minecraft.player.moveTo(feet.x, feet.y, feet.z, yaw, pitch);
	}

	/** The corner of the pad under test: where {@link Rig}'s positions count from. */
	static BlockPos pad() {
		return pad;
	}

	/** The engine under test has been moved, its blocks keeping their places beside each other: its corner is now here. */
	static void movedTo(BlockPos corner) {
		pad = corner;
	}

	private static EngineSummary summary() {
		Minecraft minecraft = Minecraft.getInstance();
		if (rig == null || minecraft.level == null)
			return null;
		return minecraft.level.getBlockEntity(pad.offset(rig.crank())) instanceof CrankshaftBlockEntity crank
			? crank.summary() : null;
	}

	/** Whether the single built at {@code corner} is running. */
	private static boolean ticksRunning(BlockPos corner) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.level.getBlockEntity(corner.offset(TestEngine.PRIMARY))
			instanceof CrankshaftBlockEntity crank && crank.summary() != null && crank.summary()
				.state() == EngineState.RUNNING;
	}

	private static void logState() {
		EngineSummary now = summary();
		if (now == null)
			return;
		// An engine that has caught still says CRANKING while its starter is engaged: what tells is that all of it fires
		boolean on = now.state() == EngineState.RUNNING || now.state() == EngineState.OVERLOADED
			|| now.state() == EngineState.CRANKING && now.cylinders() > 0 && now.firing() == now.cylinders();
		running = on && Math.abs(now.rpm()) > 15 ? running + 1 : 0;
		BlockPos at = pad.offset(rig.crank());
		log.event("state", String.format(Locale.ROOT,
			",\"engine\":\"%d %d %d\",\"state\":\"%s\",\"rpm\":%.1f,\"load\":%.1f,\"available\":%.1f,\"cylinders\":%d,"
				+ "\"firing\":%d,\"distance\":%.1f,\"health\":\"%s\",\"cause\":\"%s\"",
			at.getX(), at.getY(), at.getZ(), now.state(), Math.abs(now.rpm()), now.loadTorque(), now.availableTorque(),
			now.cylinders(), now.firing(), feet.distanceTo(SableCompat.worldPosition(Minecraft.getInstance().level, at)),
			now.health(), now.cause()));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The run

	private static void begin(Minecraft minecraft) {
		log = new ShowcaseRunSoundLog(minecraft.gameDirectory.toPath()
			.resolve("sound-bench")
			.resolve("events.jsonl"));
		SoundProbe.listen(log);
		// Only the sounds under test: the recording is of whatever the speakers play
		for (SoundSource source : SoundSource.values())
			minecraft.options.getSoundSourceOptionInstance(source)
				.set(source == SoundSource.MASTER || source == SoundSource.BLOCKS ? 1.0 : 0.0);
		minecraft.options.hideGui = true;
		VisualSmokeTest.server(server -> {
			var level = server.overworld();
			origin = new BlockPos(0, level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.ZERO)
				.getY(), 0);
			for (String command : List.of("gamerule sendCommandFeedback false", "gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false", "gamerule doMobSpawning false", "time set noon", "weather clear",
				"gamemode creative @a"))
				VisualSmokeTest.command(server, command);
		});
		schedule();
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!worldRequested) {
			if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
				worldRequested = true;
				minecraft.options.pauseOnLostFocus = false;
				VisualSmokeTest.createWorld(minecraft);
			}
			return;
		}
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null)
			return;
		ticksInWorld++;
		if (ticksInWorld < 60)
			return;
		if (ticksInWorld == 60)
			begin(minecraft);
		hold(minecraft);
		if (ticksInWorld % 40 == 0)
			onServer((level, player) -> fuel(level));
		advance();
		logState();
		log.flush();
	}

	private static void advance() {
		if (current != null) {
			phaseTicks++;
			boolean timedOut = phaseTicks >= current.max();
			boolean finished = phaseTicks >= current.min() && (current.done() == null || current.done()
				.getAsBoolean());
			if (!timedOut && !finished) {
				if (current.tick() != null)
					current.tick()
						.accept(phaseTicks);
				return;
			}
			if (!finished) {
				CreateEngines.LOGGER.warn("SOUNDBENCH {} {} gave up after {} ticks", current.scene(), current.name(),
					phaseTicks);
				log.event("timeout", ",\"scene\":\"" + current.scene() + "\",\"phase\":\"" + current.name() + "\"");
			}
		}
		current = PHASES.poll();
		phaseTicks = 0;
		if (current == null)
			return;
		CreateEngines.LOGGER.info("SOUNDBENCH {} {}", current.scene(), current.name());
		log.event("phase", ",\"scene\":\"" + current.scene() + "\",\"phase\":\"" + current.name() + "\"");
		if (current.enter() != null)
			current.enter()
				.run();
	}
}
