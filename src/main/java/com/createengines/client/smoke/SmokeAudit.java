package com.createengines.client.smoke;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createengines.CreateEngines;
import com.createengines.client.VisualSmokeTest;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.gametest.TestEngine;
import com.createengines.smoke.PerfSampler;
import com.createengines.smoke.PixelChecks;
import com.createengines.smoke.SmokeReport;
import com.createengines.smoke.audit.ServerAudit;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The unattended client audit ({@code ./gradlew smokeAudit}): starts a client, opens a fresh flat world, and then
 * checks, with no one watching, the things a person playing would notice:
 * <ul>
 * <li>every block state's model, every item, translation, sound, particle and Flywheel partial ({@link ClientAudit});
 * <li>every Ponder page, played to its end ({@link PonderAudit});
 * <li>a fleet of engines running for a minute while frame times, heap, direct memory, particles and sound voices are
 * tracked for leaks and stutter, with the player walking away from them and back;
 * <li>screenshots of the fleet and of a gallery of every block, judged by pixel checks for a blank frame, the
 * missing-texture pink, darkness and (for the running fleet) motion.
 * </ul>
 * Everything goes into a {@link SmokeReport} written to {@code smoke/client/} in the game folder; the Gradle task reads
 * it, scans the log and fails the build if anything failed. A run that never reaches the end writes no report, which
 * is itself a failure.
 */
public final class SmokeAudit {

	public static final String PROPERTY = "create_engines.smokeAudit";
	private static final String TIMEOUT = "create_engines.smokeAudit.timeoutMinutes";

	private static final SmokeReport REPORT = new SmokeReport("client");
	private static final PerfSampler FRAMES = new PerfSampler();
	private static final PerfSampler.Series HEAP = new PerfSampler.Series();
	private static final PerfSampler.Series DIRECT = new PerfSampler.Series();
	private static final PerfSampler.Series PARTICLES = new PerfSampler.Series();
	private static final PerfSampler.Series STREAMING = new PerfSampler.Series();
	private static final PerfSampler.Series STATIC_SOUNDS = new PerfSampler.Series();
	private static final Map<ResourceLocation, Integer> SOUNDS_PLAYED = new TreeMap<>();
	private static final Pattern SOUND_DEBUG = Pattern.compile("(\\d+)/(\\d+) \\+ (\\d+)/(\\d+)");

	private static boolean worldRequested;
	private static int ticksInWorld = -1;
	private static int phaseStart;
	private static int phase;
	private static volatile String phaseName = "starting";
	private static BlockPos origin = BlockPos.ZERO;
	private static boolean measuring;
	private static long lastFrame;
	private static PonderAudit ponder;
	private static final Path DIRECTORY = Path.of("smoke", "client");

	private SmokeAudit() {
	}

	public static void register() {
		if (!Boolean.getBoolean(PROPERTY))
			return;
		CreateEngines.LOGGER.info("Smoke audit enabled");
		NeoForge.EVENT_BUS.addListener(SmokeAudit::onClientTick);
		NeoForge.EVENT_BUS.addListener(SmokeAudit::onRenderFrame);
		NeoForge.EVENT_BUS.addListener(SmokeAudit::onPlaySound);
		long minutes = Long.getLong(TIMEOUT, 25);
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(minutes * 60_000L);
			} catch (InterruptedException e) {
				return;
			}
			REPORT.fail("run", "the audit timed out", "still in phase '" + phaseName + "' after " + minutes + " minutes");
			finish(false);
			Runtime.getRuntime()
				.halt(3);
		}, "smoke-audit-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	// Events

	private static void onPlaySound(PlaySoundEvent event) {
		if (event.getSound() != null && CreateEngines.MOD_ID.equals(event.getSound()
			.getLocation()
			.getNamespace()))
			synchronized (SOUNDS_PLAYED) {
				SOUNDS_PLAYED.merge(event.getSound()
					.getLocation(), 1, Integer::sum);
			}
	}

	private static void onRenderFrame(RenderFrameEvent.Pre event) {
		long now = System.nanoTime();
		if (measuring && lastFrame != 0)
			FRAMES.record(now - lastFrame);
		lastFrame = now;
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!worldRequested) {
			if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
				worldRequested = true;
				minecraft.options.pauseOnLostFocus = false;
				phaseName = "creating the world";
				VisualSmokeTest.createWorld(minecraft);
			}
			return;
		}
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null)
			return;
		ticksInWorld++;
		int t = ticksInWorld - phaseStart;
		try {
			boolean done = switch (phase) {
				case 0 -> setup(t);
				case 1 -> content(t);
				case 2 -> ponderPhase(t);
				case 3 -> fleet(t);
				case 4 -> gallery(t);
				default -> true;
			};
			if (done) {
				phase++;
				phaseStart = ticksInWorld + 1;
				if (phase > 4)
					finish(true);
			}
		} catch (Throwable throwable) {
			REPORT.fail("run", "phase '" + phaseName + "' threw", throwable.toString());
			CreateEngines.LOGGER.error("SMOKE AUDIT FAIL in phase {}", phaseName, throwable);
			phase++;
			phaseStart = ticksInWorld + 1;
			if (phase > 4)
				finish(true);
		}
	}

	// Phases

	private static boolean setup(int t) {
		phaseName = "setup";
		Minecraft minecraft = Minecraft.getInstance();
		if (t == 0) {
			minecraft.options.hideGui = true;
			// The client's own settings must not decide what a run sees: a short view distance would unload the fleet
			minecraft.options.renderDistance()
				.set(10);
			VisualSmokeTest.server(server -> {
				var level = server.overworld();
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 3))
					.getY();
				origin = new BlockPos(0, y, 3);
				VisualSmokeTest.command(server, "gamerule sendCommandFeedback false");
				VisualSmokeTest.command(server, "gamerule doDaylightCycle false");
				VisualSmokeTest.command(server, "gamerule doMobSpawning false");
				VisualSmokeTest.command(server, "time set noon");
				VisualSmokeTest.command(server, "weather clear");
			});
		}
		return t >= 40;
	}

	private static boolean content(int t) {
		phaseName = "content";
		ClientAudit.run(REPORT);
		return true;
	}

	private static boolean ponderPhase(int t) {
		phaseName = "ponder";
		if (ponder == null)
			ponder = new PonderAudit();
		// One storyboard a tick, so the game keeps ticking and the watchdog sees progress
		if (!ponder.done()) {
			ponder.playNext(REPORT);
			return false;
		}
		ponder.coverage(REPORT);
		return true;
	}

	/** Where each engine of the fleet stands, relative to the first. */
	private static final List<BlockPos> FLEET = List.of(BlockPos.ZERO, new BlockPos(12, 0, 0), new BlockPos(24, 0, 0),
		new BlockPos(36, 0, 0), new BlockPos(48, 0, 0), new BlockPos(60, 0, 0));
	private static final String[] FLEET_NAMES = {"diesel", "rich diesel", "flooded", "dry", "turbo diesel", "supercharged spark"};

	private static int[] soundCounts() {
		Matcher matcher = SOUND_DEBUG.matcher(Minecraft.getInstance()
			.getSoundManager()
			.getDebugString());
		return matcher.find() ? new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
			Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))} : new int[4];
	}

	private static double directMegabytes() {
		for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
			if (pool.getName()
				.equals("direct"))
				return pool.getMemoryUsed() / 1048576.0;
		return 0;
	}

	private static int particles() {
		try {
			return Integer.parseInt(Minecraft.getInstance().particleEngine.countParticles()
				.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void sample(boolean settleHeap) {
		if (settleHeap)
			HEAP.add(PerfSampler.settledHeapMegabytes());
		DIRECT.add(directMegabytes());
		PARTICLES.add(particles());
		int[] sounds = soundCounts();
		STATIC_SOUNDS.add(sounds[0]);
		STREAMING.add(sounds[2]);
	}

	private static int[] baselineSounds = new int[4];
	private static int[] awaySounds = new int[4];
	private static int[] frameShot;
	private static int frameShotWidth;
	private static int frameShotHeight;

	private static final BlockPos FAR = new BlockPos(3000, 0, 3000);

	private static boolean fleet(int t) {
		phaseName = "fleet";
		var minecraft = Minecraft.getInstance();
		if (t == 0) {
			baselineSounds = soundCounts();
			sample(true);
			VisualSmokeTest.server(server -> {
				var level = server.overworld();
				TestEngine.build(level, origin, true);
				BlockPos rich = origin.offset(FLEET.get(1));
				TestEngine.build(level, rich, true);
				if (level.getBlockEntity(rich.offset(TestEngine.HEAD)) instanceof com.createengines.content.head.CylinderHeadBlockEntity head)
					head.metering.setValue(com.createengines.content.head.CylinderHeadBlockEntity.MAX_METERING);
				BlockPos flooded = origin.offset(FLEET.get(2));
				TestEngine.build(level, flooded, true);
				BlockPos mouth = flooded.offset(TestEngine.AIR_INTAKE)
					.north();
				for (var side : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
					net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN})
					level.setBlock(mouth.relative(side), Blocks.GLASS.defaultBlockState(), 3);
				level.setBlock(mouth, Blocks.WATER.defaultBlockState(), 3);
				TestEngine.build(level, origin.offset(FLEET.get(3)), true, false);
				BlockPos turbo = origin.offset(FLEET.get(4));
				TestEngine.build(level, turbo, true);
				VisualSmokeTest.addTurbo(level, turbo);
				BlockPos blown = origin.offset(FLEET.get(5));
				TestEngine.buildSpark(level, blown, true);
				VisualSmokeTest.addBlower(level, blown);
			});
			measuring = true;
		}
		if (t == 5)
			view(-1.5, 4.0, -2.0, -18, 5);
		if (t == 200)
			VisualSmokeTest.server(server -> {
				for (BlockPos offset : FLEET)
					server.overworld()
						.removeBlock(origin.offset(offset)
							.offset(TestEngine.STARTER_POWER), false);
			});
		if (t > 0 && t % 50 == 0)
			sample(t % 100 == 0);
		if (t == 120)
			capture("fleet_cranking", false);
		if (t == 640)
			view(-1.5, 4.0, -2.0, -18, 5);
		if (t == 660)
			capture("fleet_running_a", true);
		if (t == 666)
			motion(capture("fleet_running_b", true));
		if (t == 700)
			view(FLEET.get(4).getX() - 1.5, 4.0, -2.0, -18, 5);
		if (t == 720)
			capture("fleet_turbo", true);
		if (t == 740)
			view(FLEET.get(5).getX() - 1.5, 4.0, -2.0, -18, 5);
		if (t == 760)
			capture("fleet_supercharged", true);
		if (t == 800)
			checkEngines("before walking away");
		// Walk far away, so every chunk of the fleet unloads on the client, and stay a while
		if (t == 820)
			VisualSmokeTest.server(server -> VisualSmokeTest.command(server, String.format(Locale.ROOT, "tp @a %d %d %d",
				origin.getX() + FAR.getX(), origin.getY() + 2, origin.getZ() + FAR.getZ())));
		if (t == 960) {
			awaySounds = soundCounts();
			int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
			REPORT.check("lifecycle", "the fleet's chunks unload on the client when the player is far away",
				!minecraft.level.hasChunk(chunkX, chunkZ), "the client still holds the chunk 3000 blocks from the player");
			REPORT.check("lifecycle", "no engine voices are left playing with the fleet unloaded", awaySounds[2] <= baselineSounds[2] + 1,
				"streaming voices: " + awaySounds[2] + " away, " + baselineSounds[2] + " before the engines existed");
			REPORT.expect("lifecycle", "no machine sounds are left playing with the fleet unloaded", awaySounds[0] <= baselineSounds[0] + 2,
				"static voices: " + awaySounds[0] + " away, " + baselineSounds[0] + " before the engines existed");
			sample(true);
			view(-1.5, 4.0, -2.0, -18, 5);
		}
		if (t == 1150) {
			checkEngines("after walking back");
			capture("fleet_after_return", true);
		}
		if (t > 960 && t < 1200 && t % 50 == 0)
			sample(false);
		if (t == 1200) {
			sample(true);
			measuring = false;
			conclusions();
			return true;
		}
		return false;
	}

	private static void checkEngines(String when) {
		var level = Minecraft.getInstance().level;
		for (int i = 0; i < FLEET.size(); i++) {
			BlockPos at = origin.offset(FLEET.get(i))
				.offset(TestEngine.PRIMARY);
			String name = FLEET_NAMES[i] + " engine " + when;
			if (!(level.getBlockEntity(at) instanceof CrankshaftBlockEntity crank)) {
				REPORT.fail("fleet", name, "no crankshaft block entity on the client at " + at);
				continue;
			}
			EngineSummary summary = crank.engineSummary();
			if (summary == null) {
				REPORT.fail("fleet", name, "the client has no engine summary: it was never synced");
				continue;
			}
			boolean running = summary.state() == EngineState.RUNNING;
			switch (i) {
				case 2 -> REPORT.check("fleet", name + " stays dead (its intake is under water)", !running,
					"state " + summary.state() + ", " + summary.rpm() + " rpm");
				case 3 -> REPORT.pass("fleet", name);
				default -> REPORT.check("fleet", name + " is running", running, "state " + summary.state() + ", " + summary.rpm() + " rpm");
			}
			REPORT.check("fleet", name + " has finite figures", Float.isFinite(summary.rpm()) && Float.isFinite(summary.temperature()),
				summary.toString());
		}
	}

	/** Judges the whole run once the fleet has finished. */
	private static void conclusions() {
		REPORT.metric("frames measured", FRAMES.count(), "");
		REPORT.metric("frame time median", FRAMES.percentileMillis(0.5), "ms");
		REPORT.metric("frame time p95", FRAMES.percentileMillis(0.95), "ms");
		REPORT.metric("frame time p99", FRAMES.percentileMillis(0.99), "ms");
		REPORT.metric("frame time worst", FRAMES.maxMillis(), "ms");
		REPORT.metric("heap after engines (settled)", HEAP.last(), "MB");
		REPORT.metric("direct memory after engines", DIRECT.last(), "MB");
		REPORT.metric("particles at peak", PARTICLES.max(), "");
		REPORT.metric("streaming sound voices at peak", STREAMING.max(), "");
		REPORT.metric("static sound voices at peak", STATIC_SOUNDS.max(), "");
		REPORT.check("perf", "frames were measured", FRAMES.count() > 200, "only " + FRAMES.count() + " frames in the whole fleet phase");
		REPORT.expect("perf", "frame time p95 stays under 100 ms with six engines", FRAMES.percentileMillis(0.95) < 100,
			String.format(Locale.ROOT, "p95 %.1f ms", FRAMES.percentileMillis(0.95)));
		REPORT.expect("perf", "frame time p99 stays under 250 ms", FRAMES.percentileMillis(0.99) < 250,
			String.format(Locale.ROOT, "p99 %.1f ms", FRAMES.percentileMillis(0.99)));
		REPORT.check("perf", "heap does not climb while engines run", !PerfSampler.climbing(HEAP, 200),
			"settled heap readings (MB): " + HEAP.values());
		REPORT.check("perf", "direct memory does not climb while engines run (the 1.2.0 sound bug)", !PerfSampler.climbing(DIRECT, 48),
			"direct buffer readings (MB): " + DIRECT.values());
		REPORT.check("perf", "particle count stays bounded", PARTICLES.max() < 12000, "peaked at " + PARTICLES.max());
		REPORT.check("perf", "streaming sound voices do not pile up", !PerfSampler.climbing(STREAMING, 6) && STREAMING.max() <= 8,
			"readings: " + STREAMING.values());
		REPORT.check("perf", "static sound voices stay below the channel limit", STATIC_SOUNDS.max() < 240, "peaked at " + STATIC_SOUNDS.max());
		var never = new java.util.TreeSet<String>();
		synchronized (SOUNDS_PLAYED) {
			for (var id : BuiltInRegistries.SOUND_EVENT.keySet())
				if (ServerAudit.ours(id) && !SOUNDS_PLAYED.containsKey(id))
					never.add(id.getPath());
			REPORT.metric("this mod's sound events started", SOUNDS_PLAYED.size(), "");
		}
		REPORT.expect("sound", "every sound event starts somewhere in the run", never.isEmpty(),
			"never started: " + never + " (they exist but nothing in the fleet triggered them)");
	}

	private static boolean gallery(int t) {
		phaseName = "gallery";
		var blocks = new ArrayList<Block>();
		for (Block block : ServerAudit.blocks())
			if (!(block instanceof LiquidBlock))
				blocks.add(block);
		int perRow = 16;
		int rows = (blocks.size() + perRow - 1) / perRow;
		int rowsPerShot = 3;
		int shots = (rows + rowsPerShot - 1) / rowsPerShot;
		BlockPos base = origin.offset(0, 0, 40);
		if (t == 0) {
			measuring = true;
			VisualSmokeTest.server(server -> {
				var level = server.overworld();
				for (int i = 0; i < blocks.size(); i++) {
					BlockPos pos = base.offset(i % perRow * 2, 0, i / perRow * 2);
					level.setBlock(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
					level.setBlock(pos, blocks.get(i)
						.defaultBlockState(), 3);
				}
			});
			REPORT.metric("blocks in the gallery", blocks.size(), "");
		}
		for (int shot = 0; shot < shots; shot++) {
			int at = 40 + shot * 30;
			double centreZ = base.getZ() - origin.getZ() + (shot * rowsPerShot + rowsPerShot / 2.0) * 2;
			if (t == at)
				view(perRow - 0.5, 14, centreZ, 0, 82);
			if (t == at + 20) {
				int[] pixels = capture("gallery_" + shot, true);
				REPORT.expect("render", "gallery band " + shot + " shows lit blocks", pixels != null, "no frame");
			}
		}
		if (t == 40 + shots * 30 + 10) {
			measuring = false;
			return true;
		}
		return false;
	}

	// Cameras and pictures

	private static void view(double x, double y, double z, float yaw, float pitch) {
		double wx = origin.getX() + x, wy = origin.getY() + y, wz = origin.getZ() + z;
		VisualSmokeTest.server(server -> {
			VisualSmokeTest.command(server, String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier",
				(int) Math.floor(wx), (int) Math.floor(wy) - 1, (int) Math.floor(wz)));
			VisualSmokeTest.command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", wx, wy, wz, yaw, pitch));
		});
	}

	/** Takes a screenshot, saves it for a human to look at when a check fails, and judges it. Returns its pixels. */
	private static int[] capture(String name, boolean expectScene) {
		Minecraft minecraft = Minecraft.getInstance();
		try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
			int[] pixels = PixelChecks.fromAbgr(image.getPixelsRGBA());
			int width = image.getWidth(), height = image.getHeight();
			try {
				Path shots = DIRECTORY.resolve("shots");
				Files.createDirectories(shots);
				image.writeToFile(shots.resolve(name + ".png"));
			} catch (IOException e) {
				CreateEngines.LOGGER.warn("Could not keep screenshot {}: {}", name, e.getMessage());
			}
			var stats = PixelChecks.analyse(pixels, width, height);
			REPORT.check("render", name + " is not a blank frame", !stats.blank(),
				String.format(Locale.ROOT, "%.1f%% of it is one colour", stats.dominantFraction() * 100));
			REPORT.check("render", name + " has no missing-texture pink", !stats.hasMissingTexture(),
				String.format(Locale.ROOT, "%.3f%% of the frame is the missing-texture colour", stats.magentaFraction() * 100));
			if (expectScene)
				REPORT.check("render", name + " is not black", !stats.nearlyBlack(),
					String.format(Locale.ROOT, "mean brightness %.1f", stats.meanLuma()));
			frameShotWidth = width;
			frameShotHeight = height;
			int[] previous = frameShot;
			frameShot = pixels;
			lastShotBefore = previous;
			return pixels;
		}
	}

	private static int[] lastShotBefore;

	/** The two running frames, a few ticks apart, must differ: a frozen picture means the game or the animation stopped. */
	private static void motion(int[] second) {
		if (second == null || lastShotBefore == null || lastShotBefore.length != second.length) {
			REPORT.skip("render", "running engines move between frames", "no pair of frames to compare");
			return;
		}
		double changed = PixelChecks.changedFraction(lastShotBefore, second, 24);
		REPORT.metric("pixels that changed between two running frames", changed * 100, "%");
		REPORT.expect("render", "running engines move between frames", changed > 0.0005,
			String.format(Locale.ROOT, "only %.4f%% of pixels changed in 6 ticks: the scene looks frozen", changed * 100));
	}

	// The end

	private static boolean finished;

	private static synchronized void finish(boolean stopGame) {
		if (finished)
			return;
		finished = true;
		phaseName = "finishing";
		try {
			if (REPORT.count(SmokeReport.Status.PASS) + REPORT.count(SmokeReport.Status.FAIL) == 0)
				REPORT.fail("run", "the audit checked nothing", "no check ran at all");
			if (stopGame)
				REPORT.markCompleted();
			REPORT.write(DIRECTORY);
			CreateEngines.LOGGER.info("SMOKE AUDIT finished: {} passed, {} warnings, {} failures; report in {}",
				REPORT.count(SmokeReport.Status.PASS), REPORT.count(SmokeReport.Status.WARN), REPORT.count(SmokeReport.Status.FAIL),
				DIRECTORY.toAbsolutePath());
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.error("SMOKE AUDIT FAIL could not write the report", e);
		}
		if (stopGame)
			Minecraft.getInstance()
				.execute(() -> Minecraft.getInstance()
					.stop());
	}
}
