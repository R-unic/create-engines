package com.createengines.client;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.gametest.TestEngine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
		STEPS.add(new Step(650, () -> Minecraft.getInstance()
			.stop()));
	}

	/** Four screenshots two ticks apart from one viewpoint, to see things move. */
	private static void frames(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		for (int i = 0; i < 4; i++) {
			int frame = i;
			STEPS.add(new Step(tick + 15 + i * 2, () -> shot(name + "_" + frame)));
		}
	}

	private static void goggles(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		STEPS.add(new Step(tick + 15, () -> shot("goggles_" + name)));
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
