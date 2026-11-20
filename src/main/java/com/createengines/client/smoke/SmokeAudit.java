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
import com.createengines.smoke.RunningWindow;
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
	/** Sounds that fire by chance, so the fleet may or may not start them; see the end of {@link #fleet}. */
	private static final java.util.List<String> CHANCE_SOUNDS = java.util.List.of("metal_tick", "preignition_knock", "flare_whoosh");
	private static final java.util.List<String> startedByHand = new java.util.ArrayList<>();
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
				checkpoint();
				phaseStart = ticksInWorld + 1;
				if (phase > 4)
					finish(true);
			}
		} catch (Throwable throwable) {
			REPORT.fail("run", "phase '" + phaseName + "' threw", throwable.toString());
			CreateEngines.LOGGER.error("SMOKE AUDIT FAIL in phase {}", phaseName, throwable);
			phase++;
			checkpoint();
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
		recordFleetHistory();
	}

	/** One short line per sample for every fleet engine, so a fleet check that fails says how the engine got there, not just where it ended. */
	private static final StringBuilder FLEET_HISTORY = new StringBuilder();
	private static final String[] lastSeen = new String[16];
	/** When each fleet engine was last seen running: the "is running" fleet check judges a window, not an instant (see {@link RunningWindow}). */
	private static final RunningWindow[] RUNNING_SEEN = new RunningWindow[16];
	static {
		for (int i = 0; i < RUNNING_SEEN.length; i++)
			RUNNING_SEEN[i] = new RunningWindow();
	}
	private static int auditTick;

	/** Every change of a fleet engine's state or health, with the audit tick it was seen on: a stutter that a 50-tick sample would miss. */
	private static void recordTransitions(int t) {
		var level = Minecraft.getInstance().level;
		if (level == null || origin == null)
			return;
		for (int i = 0; i < FLEET.size(); i++)
			if (level.getBlockEntity(origin.offset(FLEET.get(i))
				.offset(TestEngine.PRIMARY)) instanceof CrankshaftBlockEntity crank && crank.engineSummary() != null) {
				EngineSummary summary = crank.engineSummary();
				RUNNING_SEEN[i].observe(t, summary.state() == EngineState.RUNNING);
				if (FLEET_HISTORY.length() > 6000)
					continue;
				String now = summary.state()
					.name()
					.charAt(0) + "" + (summary.cause() == null ? "" : summary.cause()
						.name()
						.charAt(0));
				if (!now.equals(lastSeen[i])) {
					FLEET_HISTORY.append(" t")
						.append(t)
						.append(" e")
						.append(i)
						.append(' ')
						.append(now)
						.append(Math.round(summary.rpm()));
					lastSeen[i] = now;
				}
			}
	}

	private static void recordFleetHistory() {
		var level = Minecraft.getInstance().level;
		if (level == null || origin == null || FLEET_HISTORY.length() > 6000)
			return;
		FLEET_HISTORY.append('|');
		for (int i = 0; i < FLEET.size(); i++)
			if (level.getBlockEntity(origin.offset(FLEET.get(i))
				.offset(TestEngine.PRIMARY)) instanceof CrankshaftBlockEntity crank && crank.engineSummary() != null) {
				EngineSummary s = crank.engineSummary();
				FLEET_HISTORY.append(i)
					.append(':')
					.append(s.state()
						.name()
						.charAt(0))
					.append(s.cause() == null ? "" : s.cause()
						.name()
						.charAt(0))
					.append(Math.round(s.rpm()))
					.append(' ');
			}
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
		auditTick = t;
		if (t > 0)
			recordTransitions(t);
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
				buildRefinery(level);
			});
			measuring = true;
		}
		if (t == 40)
			VisualSmokeTest.server(server -> feedRefinery(server.overworld()));
		// The whistle only starts for a player within earshot (48 blocks), and the turbo engine is further than that from the camera's
		// home: stand beside it while the wheel is up to speed, before the boost is dumped, or the whistle is never heard
		if (t == 250)
			view(FLEET.get(4).getX() - 1.5, 4.0, -2.0, -18, 5);
		// ...and back home before the soot trap is poked below, which is heard only from there
		if (t == 305)
			view(-1.5, 4.0, -2.0, -18, 5);
		// What the effects run does to hear the turbo: dump its boost, then shut the fuel rack so anti-lag has to crack
		if (t == 400)
			VisualSmokeTest.server(server -> server.overworld()
				.setBlock(origin.offset(FLEET.get(4))
					.offset(TestEngine.EXHAUST)
					.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
		if (t == 430)
			VisualSmokeTest.server(server -> server.overworld()
				.setBlock(origin.offset(FLEET.get(4))
					.offset(new BlockPos(4, 5, 3)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3));
		// ...then open the rack again, so the turbo engine is judged running like the rest, not left coasting by the trigger
		if (t == 480)
			VisualSmokeTest.server(server -> server.overworld()
				.removeBlock(origin.offset(FLEET.get(4))
					.offset(new BlockPos(4, 5, 3)), false));
		// A soot trap catching soot makes its own sound; poke it as the game tests do rather than tune an engine to foul
		if (t == 300)
			VisualSmokeTest.server(server -> server.overworld()
				.setBlock(origin.offset(-2, 0, -4), com.createengines.registry.CEBlocks.SOOT_TRAP.getDefaultState(), 3));
		if (t == 320)
			VisualSmokeTest.server(server -> {
				if (server.overworld()
					.getBlockEntity(origin.offset(-2, 0, -4)) instanceof com.createengines.content.muffler.SootTrapBlockEntity trap)
					trap.catchSoot(com.createengines.engine.sim.SootCollector.MB_PER_SOOT * 3);
			});
		// Engines burn fuel (the supercharged one a tank in about 600 ticks) and a stalled engine does not restart by itself, so top them
		// all up on a schedule: what is judged afterwards is the reload, not an empty tank
		if (t >= 300 && t < 1100 && t % 150 == 0)
			VisualSmokeTest.server(server -> {
				var level = server.overworld();
				for (int i = 0; i < FLEET.size(); i++)
					if (level.getBlockEntity(origin.offset(FLEET.get(i))
						.offset(TestEngine.HEAD)) instanceof com.createengines.content.head.CylinderHeadBlockEntity head) {
						// The spark engine burns gasoline, the rest diesel; the dry engine (3) is dry on purpose and stays so
						var fuel = i == 5 ? com.createengines.registry.CEFluids.GASOLINE.get() : com.createengines.registry.CEFluids.DIESEL.get();
						head.tank()
							.fill(new net.neoforged.neoforge.fluids.FluidStack(fuel.getSource(), 250),
								net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
						if (i != 3)
							head.oilTank()
								.fill(new net.neoforged.neoforge.fluids.FluidStack(com.createengines.registry.CEFluids.ENGINE_OIL.get()
									.getSource(), 250), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
					}
			});
		if (t > 0 && t % 300 == 0)
			checkpoint();
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
		// The noise-cancelling headphones, on the real sound card with the fleet's sounds playing: on, checked, off, checked
		if (t == 770)
			HeadphonesAudit.wear(true);
		if (t == 772)
			HeadphonesAudit.frontView(true);
		if (t == 780)
			HeadphonesAudit.shoot("headphones_worn");
		if (t == 790)
			HeadphonesAudit.checkWorn(REPORT);
		if (t == 792) {
			HeadphonesAudit.wear(false);
			HeadphonesAudit.frontView(false);
		}
		if (t == 799)
			HeadphonesAudit.checkRemoved(REPORT);
		// Curios row only: the headphones in a Curios head slot, in steps a few ticks apart so the client sees each change
		if (t == 801)
			IntegrationAudit.curios(REPORT, 0);
		if (net.neoforged.fml.ModList.get()
			.isLoaded("curios")) {
			if (t == 802)
				HeadphonesAudit.frontView(true);
			if (t == 808)
				HeadphonesAudit.shoot("headphones_curios");
		}
		if (t == 810)
			IntegrationAudit.curios(REPORT, 1);
		if (t == 812) {
			IntegrationAudit.curios(REPORT, 2);
			HeadphonesAudit.frontView(false);
		}
		if (t == 818)
			IntegrationAudit.curios(REPORT, 3);
		if (t == 800) {
			checkEngines("before walking away");
			// KubeJS and CC: Tweaked rows only: the other mod found this mod's plugin or peripheral and they work together
			IntegrationAudit.run(REPORT, origin.offset(TestEngine.PRIMARY));
		}
		// Walk far away, so every chunk of the fleet unloads on the client, and stay a while
		if (t == 820)
			VisualSmokeTest.server(server -> VisualSmokeTest.command(server, String.format(Locale.ROOT, "tp @a %d %d %d",
				origin.getX() + FAR.getX(), origin.getY() + 2, origin.getZ() + FAR.getZ())));
		if (t == 960) {
			awaySounds = soundCounts();
			int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
			REPORT.check("lifecycle", "the fleet's block entities leave the client when the player is far away",
				minecraft.level.getBlockEntity(origin.offset(TestEngine.PRIMARY)) == null,
					"the client still has the crankshaft block entity with the player at " + minecraft.player.blockPosition()
						.toShortString() + " (" + chunkX + "," + chunkZ + ")");
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
		// The toast sting plays when an advancement is earned; earn them all, last, so no toast is in a picture
		if (t == 1160)
			VisualSmokeTest.server(server -> VisualSmokeTest.command(server, "advancement grant @a everything"));
		// The cooling tick, the pre-ignition knock and the flare's whoosh are chance events by design (a few per second on the right
		// engine, now and then at a flare), so a fleet of any size can go a whole run without one. Each is started by hand here when the fleet did not, which proves its sound
		// event and files play; a sound that is not random must still be started by the fleet itself.
		if (t == 1190)
			for (String name : CHANCE_SOUNDS) {
				var id = ResourceLocation.fromNamespaceAndPath(CreateEngines.MOD_ID, name);
				boolean played;
				synchronized (SOUNDS_PLAYED) {
					played = SOUNDS_PLAYED.containsKey(id);
				}
				if (!played) {
					var event = BuiltInRegistries.SOUND_EVENT.get(id);
					minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), event,
						net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.0f, false);
					startedByHand.add(name);
				}
			}
		if (t == 1200) {
			boolean allHeard;
			synchronized (SOUNDS_PLAYED) {
				allHeard = startedByHand.stream()
					.allMatch(name -> SOUNDS_PLAYED.containsKey(ResourceLocation.fromNamespaceAndPath(CreateEngines.MOD_ID, name)));
			}
			REPORT.check("sound", "chance sounds the fleet did not start play when started by hand", allHeard,
				"started by hand but never heard by the sound engine: " + startedByHand);
			sample(true);
			measuring = false;
			conclusions();
			return true;
		}
		return false;
	}

	/** The refinery of the refinery smoke run: a 2x2 column over a seething burner, with a flare stack on its condenser. */
	private static void buildRefinery(net.minecraft.world.level.Level level) {
		var burner = com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL;
		BlockPos base = origin.offset(-8, 0, 2);
		for (int x = 0; x < 2; x++)
			for (int z = 0; z < 2; z++) {
				level.setBlock(base.offset(x, 0, z), com.createengines.registry.CEBlocks.HEAT_EXCHANGER.getDefaultState()
					.setValue(burner, com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED), 3);
				level.setBlock(base.offset(x, 1, z), com.createengines.registry.CEBlocks.REBOILER.getDefaultState(), 3);
				level.setBlock(base.offset(x, 2, z), com.createengines.registry.CEBlocks.COLUMN_SECTION.getDefaultState(), 3);
				level.setBlock(base.offset(x, 3, z), com.createengines.registry.CEBlocks.COLUMN_SECTION.getDefaultState(), 3);
				level.setBlock(base.offset(x, 4, z), com.createengines.registry.CEBlocks.CONDENSER.getDefaultState(), 3);
			}
		level.setBlock(base.offset(0, 5, 0), com.createengines.registry.CEBlocks.FLARE_STACK.getDefaultState(), 3);
	}

	private static void feedRefinery(net.minecraft.server.level.ServerLevel level) {
		BlockPos base = origin.offset(-8, 0, 2);
		var feed = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, base.above(),
			net.minecraft.core.Direction.UP);
		if (feed != null)
			feed.fill(new net.neoforged.neoforge.fluids.FluidStack(com.createengines.registry.CEFluids.PLANT_OIL.get()
				.getSource(), 1000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		if (level.getBlockEntity(base.above()) instanceof com.createengines.content.refinery.ColumnPortBlockEntity port)
			port.insertCatalyst(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 16));
	}

	/** What an engine says about itself, with the diagnosis the goggles would give, for a failure message. */
	private static String describe(EngineSummary summary) {
		return "state " + summary.state() + ", " + summary.rpm() + " rpm, " + summary.health() + " (" + summary.cause() + "), fuel "
			+ summary.fuelPerMinute() + "/min, lubrication " + summary.lubrication() + ", firing " + summary.firing() + "/" + summary.cylinders();
	}

	private static void checkEngines(String when) {
		var level = Minecraft.getInstance().level;
		com.createengines.CreateEngines.LOGGER.info("FLEETHISTORY {}: {}", when, FLEET_HISTORY);
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
					describe(summary));
				case 3 -> REPORT.pass("fleet", name);
				default -> {
					// Judged over a window: a rich boosted single-cylinder engine stutters for ~20 ticks now and then and is still running
					boolean ran = running || RUNNING_SEEN[i].ranWithin(auditTick, RunningWindow.DEFAULT_WINDOW);
					REPORT.check("fleet", name + " is running", ran,
						ran ? describe(summary) : describe(summary) + "; not seen running for " + RunningWindow.DEFAULT_WINDOW
							+ " ticks; history (audit tick, engine, state+cause initial, rpm): " + FLEET_HISTORY);
				}
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
		REPORT.metric("heap growth over the fleet run (fitted)", HEAP.slope() * Math.max(0, HEAP.size() - 1), "MB");
		REPORT.metric("direct memory growth over the fleet run (fitted)", DIRECT.slope() * Math.max(0, DIRECT.size() - 1), "MB");
		REPORT.metric("heap readings", HEAP.size(), "");
		REPORT.metric("particles at peak", PARTICLES.max(), "");
		REPORT.metric("streaming sound voices at peak", STREAMING.max(), "");
		REPORT.metric("static sound voices at peak", STATIC_SOUNDS.max(), "");
		REPORT.check("perf", "frames were measured", FRAMES.count() > 200, "only " + FRAMES.count() + " frames in the whole fleet phase");
		REPORT.expect("perf", "frame time p95 stays under 100 ms with six engines", FRAMES.percentileMillis(0.95) < 100,
			String.format(Locale.ROOT, "p95 %.1f ms", FRAMES.percentileMillis(0.95)));
		REPORT.expect("perf", "frame time p99 stays under 250 ms", FRAMES.percentileMillis(0.99) < 250,
			String.format(Locale.ROOT, "p99 %.1f ms", FRAMES.percentileMillis(0.99)));
		// Limits set from two measured runs (2026-11-19): the settled heap grew 6.5 MB over the run and direct memory fell 1.3 MB, so
		// these are about eight and twelve times the measurement, and well under what a leak that matters (the 1.2.0 one) adds
		REPORT.check("perf", "heap does not climb while engines run", !PerfSampler.climbing(HEAP, 50),
			"settled heap readings (MB): " + HEAP.values());
		REPORT.check("perf", "direct memory does not climb while engines run (the 1.2.0 sound bug)", !PerfSampler.climbing(DIRECT, 16),
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
		recipeViewers();
	}

	/**
	 * In a compatibility row that loads JEI or EMI, the viewer must have found this mod's plugin and been given every
	 * refining route. Without the viewer there is nothing to check, and the plugin classes are never touched.
	 */
	private static void recipeViewers() {
		var level = Minecraft.getInstance().level;
		if (level == null)
			return;
		int routes = level.getRecipeManager().getAllRecipesFor(com.createengines.registry.CERecipes.REFINING.get()).size();
		// A compatibility row names the viewer it is meant to load, so a row that silently loaded nothing fails
		String required = System.getProperty("create_engines.smokeAudit.requireViewer");
		if (required != null)
			REPORT.check("compat", required + " is loaded in the row that is meant to test it",
				net.neoforged.fml.ModList.get().isLoaded(required), "the row names " + required + " but the game has no such mod");
		for (String viewer : List.of("jei", "emi")) {
			if (!net.neoforged.fml.ModList.get().isLoaded(viewer))
				continue;
			int given = com.createengines.client.compat.CompatStatus.refiningRoutes(viewer);
			REPORT.metric(viewer + " refining routes shown", given, "");
			int fuels = com.createengines.client.compat.ViewerEntries.fuels().size();
			int parts = com.createengines.client.compat.ViewerEntries.parts().size();
			REPORT.check("compat", viewer + " was given every fuel and every part page",
				fuels >= 4 && parts > 0 && com.createengines.client.compat.CompatStatus.fuelPages(viewer) == fuels
					&& com.createengines.client.compat.CompatStatus.partPages(viewer) == parts,
				viewer + " should show " + fuels + " fuels and " + parts + " parts, but its plugin registered "
					+ com.createengines.client.compat.CompatStatus.fuelPages(viewer) + " and "
					+ com.createengines.client.compat.CompatStatus.partPages(viewer) + " (-1 means the plugin never ran)");
			REPORT.check("compat", viewer + " was given every refining route", routes > 0 && given == routes,
				viewer + " is installed and the game has " + routes + " refining routes, but its plugin registered " + given
					+ " (-1 means the plugin never ran)");
		}
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

	/**
	 * Saves what has been found so far, not yet marked complete. A crash (a missing particle sprite crashes the game the
	 * moment one spawns) would otherwise throw away every finding made before it, leaving "it crashed" and nothing about why.
	 */
	private static void checkpoint() {
		try {
			REPORT.write(DIRECTORY);
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.warn("Could not save the interim smoke report: {}", e.getMessage());
		}
	}

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
