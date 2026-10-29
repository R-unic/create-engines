package com.createengines.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.createengines.CreateEngines;
import com.createengines.content.EnginePoses;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid, off unless the {@code visualSmokeTest} run is used: creates a flat creative world, builds the
 * single-cylinder {@link TestEngine}, takes screenshots from fixed viewpoints while it cranks and
 * runs (some wearing Engineer's Goggles), then quits. Screenshots land in {@code run/screenshots}.
 */
public final class VisualSmokeTest {

	private static final String PROPERTY = "create_engines.visualSmokeTest";

	private record Step(int tick, Runnable action) {
	}

	/** Where the spark engine stands, relative to the first. */
	private static final BlockPos SPARK_OFFSET = new BlockPos(12, 0, 0);

	private static final List<Step> STEPS = new ArrayList<>();
	private static boolean worldRequested;
	private static int ticksInWorld = -1;
	private static BlockPos origin = BlockPos.ZERO;

	private VisualSmokeTest() {
	}

	public static void register() {
		if (!Boolean.getBoolean(PROPERTY))
			return;
		CreateEngines.LOGGER.info("Visual smoke test enabled");
		NeoForge.EVENT_BUS.addListener(VisualSmokeTest::onClientTick);
		NeoForge.EVENT_BUS.addListener(VisualSmokeTest::onRenderFrame);
		schedule();
	}

	private static void schedule() {
		STEPS.add(new Step(40, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 3))
					.getY();
				origin = new BlockPos(0, y, 3);
				command(server, "gamerule sendCommandFeedback false");
				command(server, "gamerule doDaylightCycle false");
				command(server, "gamerule doMobSpawning false");
				command(server, "time set noon");
				TestEngine.build(level, origin, true);
			});
		}));

		// Cranking on the starter: from the intake side, then from the camshaft side
		frames("cranking_front", 100, 2.5, 2.0, -1.2, 0, 9.5f);
		frames("cranking_back", 140, 2.5, 2.0, 6.2, 180, 9.5f);
		STEPS.add(new Step(180, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(195, () -> shot("overview_cranking")));

		// Release the starter; the engine should carry on by itself
		STEPS.add(new Step(260, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		frames("running_front", 400, 2.5, 2.0, -1.2, 0, 9.5f);
		frames("running_back", 440, 2.5, 2.0, 6.2, 180, 9.5f);
		frames("valves", 480, -0.5, 4.6, 4.5, -116.6f, 19.7f);
		// Looking straight down the crank axis, so the throw turns in the picture plane
		frames("along_crank", 500, -1.5, 2.0, 2.5, -90, 8.8f);
		STEPS.add(new Step(525, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(538, () -> shot("overview_running")));

		// Goggles on each component
		STEPS.add(new Step(540, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("crankshaft", 545, 1.5, 2.0, 0.4, 0, 28);
		goggles("cylinder", 565, 2.5, 2.0, -0.5, 0, 2.3f);
		goggles("cam_lobe", 585, 2.5, 6.5, 1.6, 0, 59.5f);
		goggles("head", 605, 4.5, 4.0, 1.0, 45, 0);
		goggles("starter", 625, 1.5, 0.0, 0.0, 0, 2.7f);
		// Ponder scenes, which draw engine parts with the fallback renderers rather than Flywheel
		ponder("crankshaft", 660, CEBlocks.CRANK_THROW::asStack, 160, 420);
		ponder("cylinder", 1120, CEBlocks.CYLINDER::asStack, 300, 600);
		ponder("camshaft", 1780, CEBlocks.CAM_LOBE::asStack, 300, 560);
		ponder("airflow", 2400, CEBlocks.AIR_INTAKE::asStack, 340, 450);
		ponder("starter", 2900, CEBlocks.STARTER::asStack, 220, 420);
		// Buckets in the hotbar: each fluid layer should wear its own colour, not the grey of the water texture
		STEPS.add(new Step(3340, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> {
				for (String bucket : List.of("plant_oil_bucket", "diesel_bucket", "engine_oil_bucket"))
					command(server, "give @a create_engines:" + bucket);
			});
			view(2.5, 2.0, -0.5, 0, 2.3f);
		}));
		STEPS.add(new Step(3365, () -> shot("buckets")));

		// A piston with no connecting rod isn't tied to the crank, so it must stay put while the crank turns
		STEPS.add(new Step(3370, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos cylinder = origin.offset(TestEngine.CYLINDER);
				level.setBlock(cylinder, level.getBlockState(cylinder)
					.setValue(CylinderBlock.HAS_ROD, false), 3);
				level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
			});
		}));
		frames("no_rod", 3400, 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("lubrication", 3450, () -> CEFluids.ENGINE_OIL.getBucket()
			.orElseThrow()
			.getDefaultInstance(), 150, 330, 560, 700, 880);

		// A spark engine beside it: gasoline, a spark plug, a battery on the crank's shaft line, and real pipes
		// leading to its intake and exhaust openings
		STEPS.add(new Step(4400, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos spark = origin.offset(SPARK_OFFSET);
				TestEngine.buildSpark(level, spark, true);
				level.removeBlock(spark.offset(TestEngine.AIR_INTAKE), false);
				level.removeBlock(spark.offset(TestEngine.EXHAUST), false);
				BlockPos intake = spark.offset(TestEngine.AIR_INTAKE);
				level.setBlock(intake.north(), pipe(CEBlocks.INTAKE_MANIFOLD.get(), Direction.SOUTH, Direction.WEST), 3);
				level.setBlock(intake.north()
					.west(), CEBlocks.AIR_INTAKE.getDefaultState()
						.setValue(PortOpeningBlock.FACING, Direction.WEST),
					3);
				level.setBlock(intake, pipe(CEBlocks.INTAKE_MANIFOLD.get(), Direction.SOUTH, Direction.NORTH), 3);
				BlockPos exhaust = spark.offset(TestEngine.EXHAUST);
				level.setBlock(exhaust.above(2), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.UP), 3);
				level.setBlock(exhaust.above(), pipe(CEBlocks.EXHAUST_MANIFOLD.get(), Direction.UP, Direction.DOWN), 3);
				level.setBlock(exhaust, pipe(CEBlocks.EXHAUST_MANIFOLD.get(), Direction.EAST, Direction.UP), 3);
			});
		}));
		STEPS.add(new Step(4700, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(SPARK_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		frames("spark_front", 4900, SPARK_OFFSET.getX() + 2.5, 2.0, -1.2, 0, 9.5f);
		frames("spark_pipes", 4950, SPARK_OFFSET.getX() - 1.5, 6.0, -2.5, -35, 32);
		STEPS.add(new Step(5000, () -> view(SPARK_OFFSET.getX() - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(5015, () -> shot("spark_overview")));
		STEPS.add(new Step(5020, () -> Minecraft.getInstance().options.hideGui = false));
		goggles("spark_battery", 5025, SPARK_OFFSET.getX() + 1.5, 3.0, 0.4, 0, 28);
		goggles("spark_head", 5045, SPARK_OFFSET.getX() + 4.5, 4.0, 1.0, 45, 0);
		goggles("spark_cylinder", 5065, SPARK_OFFSET.getX() + 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("spark", 5100, CEBlocks.ALTERNATOR::asStack, 250, 450, 650);
		STEPS.add(new Step(5900, () -> Minecraft.getInstance()
			.stop()));
	}

	private static BlockState pipe(Block block, Direction... open) {
		BlockState state = block.defaultBlockState();
		for (Direction direction : open)
			state = state.setValue(ManifoldBlock.CONNECTIONS.get(direction), true);
		return state;
	}

	/** Four screenshots two ticks apart from one viewpoint, to see things move. */
	private static void frames(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		for (int i = 0; i < 4; i++) {
			int frame = i;
			STEPS.add(new Step(tick + 15 + i * 2, () -> shot(name + "_" + frame)));
		}
	}

	private static void ponder(String name, int tick, Supplier<ItemStack> component, int... shotsAfter) {
		STEPS.add(new Step(tick, () -> ScreenOpener.open(PonderUI.of(component.get()))));
		for (int after : shotsAfter)
			STEPS.add(new Step(tick + after, () -> shot("ponder_" + name + "_" + after)));
		STEPS.add(new Step(tick + shotsAfter[shotsAfter.length - 1] + 5, () -> Minecraft.getInstance()
			.setScreen(null)));
	}

	private static void goggles(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		STEPS.add(new Step(tick + 15, () -> shot("goggles_" + name)));
	}

	// Animation smoothness: while the engine cranks, fires and speeds up, compare each frame's drawn crank angle with
	// how far its speed says it should have turned since the last frame
	private static final int MEASURE_FROM = 60;
	private static final int MEASURE_TO = 520;
	private static float lastTime = Float.NaN;
	private static float lastAngle;
	private static float lastCreateAngle;
	private static float worstJump;
	private static float worstCreateJump;
	private static int speedChanges;
	private static float lastSpeed;

	private static void onRenderFrame(RenderFrameEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || ticksInWorld < MEASURE_FROM)
			return;
		if (ticksInWorld > MEASURE_TO) {
			if (!Float.isNaN(lastTime)) {
				CreateEngines.LOGGER.info(
					"Smoke test smoothness: worst crank jump {} degrees beyond its speed over {} speed changes; "
						+ "time x speed would have jumped {} degrees", worstJump, speedChanges, worstCreateJump);
				lastTime = Float.NaN;
			}
			return;
		}
		if (!(minecraft.level.getBlockEntity(origin.offset(TestEngine.THROW)) instanceof CrankshaftBlockEntity crank))
			return;
		float time = AnimationTickHolder.getRenderTime(minecraft.level);
		float speed = crank.getTheoreticalSpeed();
		float angle = EnginePoses.crankAngle(crank, time);
		float createAngle = CrankGeometry.renderAngle(time, speed);
		if (!Float.isNaN(lastTime) && time > lastTime) {
			float expected = (time - lastTime) * speed * 3 / 10;
			float jump = Math.abs(Mth.wrapDegrees(angle - lastAngle - expected));
			worstJump = Math.max(worstJump, jump);
			worstCreateJump = Math.max(worstCreateJump, Math.abs(Mth.wrapDegrees(createAngle - lastCreateAngle - expected)));
			if (speed != lastSpeed)
				speedChanges++;
		}
		lastTime = time;
		lastAngle = angle;
		lastCreateAngle = createAngle;
		lastSpeed = speed;
	}

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
		LevelSettings settings = new LevelSettings("engine_smoke_" + System.currentTimeMillis(), GameType.CREATIVE,
			false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(0, false, false);
		minecraft.createWorldOpenFlows()
			.createFreshLevel(settings.levelName(), settings, options, registries -> registries
				.registryOrThrow(Registries.WORLD_PRESET)
				.getHolderOrThrow(WorldPresets.FLAT)
				.value()
				.createWorldDimensions(), minecraft.screen);
	}

	/** Stands the camera on an invisible barrier at a position relative to the test structure's origin. */
	private static void view(double x, double y, double z, float yaw, float pitch) {
		double wx = origin.getX() + x;
		double wy = origin.getY() + y;
		double wz = origin.getZ() + z;
		server(server -> {
			command(server, String.format(java.util.Locale.ROOT, "setblock %d %d %d minecraft:barrier",
				(int) Math.floor(wx), (int) Math.floor(wy) - 1, (int) Math.floor(wz)));
			command(server, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", wx, wy, wz, yaw,
				pitch));
		});
	}

	private static void shot(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Screenshot.grab(minecraft.gameDirectory, "engine_" + name + ".png", minecraft.getMainRenderTarget(),
			message -> CreateEngines.LOGGER.info("Smoke test screenshot: {}", message.getString()));
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
