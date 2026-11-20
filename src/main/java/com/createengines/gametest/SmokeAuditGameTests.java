package com.createengines.gametest;

import static com.createengines.gametest.TestEngine.PRIMARY;
import static com.createengines.gametest.TestEngine.STARTER_POWER;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Tuning;
import com.createengines.smoke.SmokeReport;
import com.createengines.smoke.audit.ServerAudit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server half of the smoke audit, run with the other GameTests so it gets their exit code and their server-only
 * JVM (which is also the proof that the mod's common code loads on a dedicated server). Where the other GameTests build
 * one situation and ask one question, these look wide: every block and item, every block state, a crowd of engines at
 * once, the world closing around a running engine, and every tuning knob at both of its ends.
 *
 * <p>Reports are also written to {@code smoke/gametest-<name>/} in the run folder so the findings survive the log.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmokeAuditGameTests {

	/** Fails the test with every problem the report holds, and keeps the report on disk. */
	private static void conclude(GameTestHelper helper, SmokeReport report) {
		report.markCompleted();
		try {
			report.write(Path.of("smoke", "gametest-" + report.run()));
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.warn("Could not write the {} smoke report: {}", report.run(), e.getMessage());
		}
		CreateEngines.LOGGER.info("SMOKE {} finished: {} checks passed, {} warnings, {} failures", report.run(),
			report.count(SmokeReport.Status.PASS), report.count(SmokeReport.Status.WARN), report.count(SmokeReport.Status.FAIL));
		for (var metric : report.metrics()
			.entrySet())
			CreateEngines.LOGGER.info("SMOKE {} metric {} = {}", report.run(), metric.getKey(), metric.getValue());
		int warned = 0;
		for (var problem : report.problems())
			if (problem.status() == SmokeReport.Status.WARN && warned++ < 15)
				CreateEngines.LOGGER.info("SMOKE {} warning [{}] {} - {}", report.run(), problem.category(), problem.name(),
					problem.detail());
		if (warned > 15)
			CreateEngines.LOGGER.info("SMOKE {} and {} more warnings (see smoke/gametest-{}/report.txt)", report.run(), warned - 15,
				report.run());
		if (!report.passed()) {
			StringBuilder message = new StringBuilder(report.count(SmokeReport.Status.FAIL) + " smoke failures:");
			int shown = 0;
			for (var problem : report.problems())
				if (problem.status() == SmokeReport.Status.FAIL) {
					CreateEngines.LOGGER.error("SMOKE {} FAIL [{}] {} - {}", report.run(), problem.category(), problem.name(),
						problem.detail());
					if (shown++ < 12)
						message.append("\n  [")
							.append(problem.category())
							.append("] ")
							.append(problem.name())
							.append(" - ")
							.append(problem.detail());
				}
			helper.fail(message.toString());
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyBlockItemFluidAndDataFileIsComplete(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("content");
		ServerAudit.content(helper.getLevel()
			.getServer(), report);
		conclude(helper, report);
	}

	/**
	 * Every block in every sampled state, placed, queried, ticked, saved, loaded, given garbage data, asked for its
	 * network packet and broken. The area is far from the test structures so nothing here disturbs them.
	 */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void everyBlockSurvivesPlacementTickingSavingAndBreaking(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("lifecycle");
		BlockPos origin = helper.absolutePos(BlockPos.ZERO)
			.offset(6000, 3, 6000);
		ServerAudit.lifecycle(helper.getLevel(), origin, report);
		conclude(helper, report);
	}

	// Crowds and long runs

	/** Allocation limit for the whole server thread over a crowd tick, measured at 45 KB on 2026-11-19, so about five times that. */
	private static final double CROWD_KB_PER_TICK_LIMIT = 256;

	/** Bytes the calling thread has allocated so far, or -1 where the JVM cannot say. */
	private static long serverThreadAllocatedBytes() {
		if (java.lang.management.ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
			&& bean.isThreadAllocatedMemorySupported())
			return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
		return -1;
	}

	private static final int CROWD_COLUMNS = 4;
	private static final int CROWD_ROWS = 4;

	private static EngineSummary summaryAt(ServerLevel level, BlockPos origin) {
		return level.getBlockEntity(origin.offset(PRIMARY)) instanceof CrankshaftBlockEntity crank ? crank.summary() : null;
	}

	/**
	 * Sixteen engines at once, half diesel and half spark, started together. Each must catch and keep running, and the
	 * whole crowd must cost the server a believable amount per tick: engines are simulated every tick, so a cost that
	 * creeps up with every feature shows here long before a player's world stutters.
	 */
	@GameTest(template = "empty_large", batch = "smoke-perf", timeoutTicks = 1200)
	public static void aCrowdOfEnginesRunsTogetherWithinABudget(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("crowd");
		ServerLevel level = helper.getLevel();
		List<BlockPos> engines = new ArrayList<>();
		for (int column = 0; column < CROWD_COLUMNS; column++)
			for (int row = 0; row < CROWD_ROWS; row++) {
				BlockPos at = helper.absolutePos(new BlockPos(1 + column * 11, 1, 1 + row * 11));
				if ((column + row) % 2 == 0)
					TestEngine.build(level, at, true);
				else
					TestEngine.buildSpark(level, at, true);
				engines.add(at);
			}
		long[] startedAt = new long[1];
		long[] allocatedAt = {-1};
		helper.runAtTickTime(300, () -> engines.forEach(at -> level.removeBlock(at.offset(STARTER_POWER), false)));
		helper.runAtTickTime(500, () -> {
			startedAt[0] = System.nanoTime();
			allocatedAt[0] = serverThreadAllocatedBytes();
		});
		helper.runAtTickTime(900, () -> {
			double msPerTick = (System.nanoTime() - startedAt[0]) / 400.0 / 1_000_000.0;
			report.metric("crowd of " + engines.size() + " engines, wall time per server tick", msPerTick, "ms");
			long allocated = serverThreadAllocatedBytes();
			if (allocatedAt[0] >= 0 && allocated >= 0) {
				double kbPerTick = (allocated - allocatedAt[0]) / 400.0 / 1024.0;
				report.metric("crowd of " + engines.size() + " engines, server thread allocation per tick", kbPerTick, "KB");
				report.check("perf", "16 engines allocate under " + CROWD_KB_PER_TICK_LIMIT + " KB a tick on the server thread",
					kbPerTick < CROWD_KB_PER_TICK_LIMIT, String.format("%.1f KB per tick for %d engines", kbPerTick, engines.size()));
			} else
				report.skip("perf", "crowd allocation per tick", "this JVM cannot measure per-thread allocation");
			// A fair budget: the GameTest server runs flat out, so this is the real cost with no sleeping in it
			// Measured at about 0.4 ms for sixteen (2026-11-16); the limit is five times that: room for noise, not for a regression
			report.check("perf", "16 engines cost under 2 ms a tick", msPerTick < 2,
				String.format("%.1f ms per tick for %d engines", msPerTick, engines.size()));
			int running = 0;
			for (BlockPos at : engines) {
				EngineSummary summary = summaryAt(level, at);
				String name = "engine at " + helper.relativePos(at)
					.toShortString();
				if (summary == null) {
					report.fail("crowd", name, "has no engine summary (topology never built it)");
					continue;
				}
				if (summary.state() == EngineState.RUNNING)
					running++;
				report.check("crowd", name + " runs", summary.state() == EngineState.RUNNING, "state " + summary.state() + ", " + summary.rpm() + " rpm");
				report.check("crowd", name + " has finite figures", Float.isFinite(summary.rpm()) && Float.isFinite(summary.availableTorque())
					&& Float.isFinite(summary.fuelPerMinute()), summary.toString());
			}
			report.metric("engines running of " + engines.size(), running, "");
			conclude(helper, report);
		});
	}

	/**
	 * A running engine whose chunk is unloaded and loaded again, which is what happens when a player walks away from a
	 * base and back. Its blocks must come back, the engine must be rebuilt by the topology scan, and it must not be left
	 * wedged. The chunk is far from the player-free test area so it really unloads.
	 */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void aDieselEngineSurvivesItsChunkUnloadingAndLoadingAgain(GameTestHelper helper) {
		chunkRoundTrip(helper, "diesel", 400, false);
	}

	/** The same walk away and back for a spark engine, whose battery and alternator are more state to lose. */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void aSparkEngineSurvivesItsChunkUnloadingAndLoadingAgain(GameTestHelper helper) {
		chunkRoundTrip(helper, "spark", 420, true);
	}

	/** And for a spark engine with a supercharger on its head, turned by its own motor: more blocks, more state, more to forget. */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void aSuperchargedEngineSurvivesItsChunkUnloadingAndLoadingAgain(GameTestHelper helper) {
		chunkRoundTrip(helper, "blower", 440, true);
	}

	/** A supercharger on the head of {@link TestEngine#buildSpark}'s engine, turned by a creative motor beneath it. */
	private static void addBlower(ServerLevel level, BlockPos origin) {
		BlockPos head = origin.offset(TestEngine.HEAD);
		BlockPos blower = head.east();
		level.setBlock(origin.offset(TestEngine.AIR_INTAKE), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(blower.east(), com.createengines.registry.CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, net.minecraft.core.Direction.EAST), 3);
		level.setBlock(blower, com.createengines.registry.CEBlocks.SUPERCHARGER.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y), 3);
		level.setBlock(blower.below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, net.minecraft.core.Direction.UP), 3);
		if (level.getBlockEntity(blower.below()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(128);
		if (level.getBlockEntity(blower) instanceof com.createengines.content.supercharger.SuperchargerBlockEntity charger)
			charger.limit.setValue(com.createengines.content.supercharger.SuperchargerBlockEntity.MAX_LIMIT);
		if (level.getBlockEntity(head) instanceof com.createengines.content.head.CylinderHeadBlockEntity cylinderHead)
			cylinderHead.metering.setValue(com.createengines.content.head.CylinderHeadBlockEntity.MAX_METERING);
	}

	/**
	 * The supercharged engine placed across a chunk border, as most player builds are: half its blocks in one chunk and
	 * half in the next, which load and unload separately.
	 */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void anEngineAcrossAChunkBorderSurvivesItsChunksUnloadingAndLoadingAgain(GameTestHelper helper) {
		chunkRoundTrip(helper, "blower-border", 460, true);
	}

	/**
	 * A chunk can stay loaded without ticking, which is how the edge of a player's simulation distance behaves: blocks are
	 * there, nothing runs. An engine caught in that and then ticked again must carry on, not wake up dead.
	 */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void anEngineInAChunkThatStopsTickingRunsAgainWhenItTicksAgain(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("chunks-limbo");
		ServerLevel level = helper.getLevel();
		int chunkX = 560, chunkZ = 560;
		BlockPos at = new BlockPos(chunkX * 16 + 4, helper.absolutePos(BlockPos.ZERO)
			.getY(), chunkZ * 16 + 4);
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
		// A ticket that keeps the chunk loaded at the lowest level, where nothing ticks, and never expires on its own
		net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> limbo = net.minecraft.server.level.TicketType
			.create("smoke_limbo", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 0);
		level.setChunkForced(chunkX, chunkZ, true);
		TestEngine.build(level, at, true);
		helper.runAtTickTime(300, () -> level.removeBlock(at.offset(STARTER_POWER), false));
		helper.runAtTickTime(700, () -> {
			EngineSummary before = summaryAt(level, at);
			report.check("chunks", "the engine runs before its chunk stops ticking", before != null && before.state() == EngineState.RUNNING,
				"state " + (before == null ? "none" : before.state()));
			level.getChunkSource()
				.addRegionTicket(limbo, chunk, 0, chunk);
			level.setChunkForced(chunkX, chunkZ, false);
		});
		helper.runAtTickTime(900, () -> {
			report.check("chunks", "the chunk is loaded but not ticking", level.hasChunk(chunkX, chunkZ)
				&& !level.getChunkSource()
					.isPositionTicking(chunk.toLong()),
				"loaded " + level.hasChunk(chunkX, chunkZ) + ", ticking " + level.getChunkSource()
					.isPositionTicking(chunk.toLong()));
			level.setChunkForced(chunkX, chunkZ, true);
			level.getChunkSource()
				.removeRegionTicket(limbo, chunk, 0, chunk);
		});
		helper.runAtTickTime(1200, () -> {
			EngineSummary after = summaryAt(level, at);
			report.check("chunks", "the engine runs again once its chunk ticks again", after != null && after.state() == EngineState.RUNNING,
				after == null ? "no engine" : "state " + after.state() + ", " + after.rpm() + " rpm");
			conclude(helper, report);
		});
	}

	/**
	 * As a player walks back: the engine's chunks load one at a time, a moment apart, rather than together. The
	 * engine must find its other half once it arrives.
	 */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void anEngineWhoseChunksLoadOneAfterAnotherStillRuns(GameTestHelper helper) {
		chunkRoundTrip(helper, "diesel-border-staggered", 500, false);
	}

	/** The same, for the supercharged spark engine that showed the problem in the client audit. */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void aSuperchargedEngineWhoseChunksLoadOneAfterAnotherStillRuns(GameTestHelper helper) {
		chunkRoundTrip(helper, "blower-border-staggered", 520, true);
	}

	/** The same, for a plain diesel engine, to tell a supercharger's problem from a border's. */
	@GameTest(template = "empty", batch = "smoke-chunks", timeoutTicks = 2400)
	public static void aDieselEngineAcrossAChunkBorderSurvivesItsChunksUnloadingAndLoadingAgain(GameTestHelper helper) {
		chunkRoundTrip(helper, "diesel-border", 480, false);
	}

	private static void force(ServerLevel level, int chunkX, int chunkZ, int span, boolean forced) {
		for (int i = 0; i < span; i++)
			level.setChunkForced(chunkX + i, chunkZ, forced);
	}

	private static boolean anyLoaded(ServerLevel level, int chunkX, int chunkZ, int span) {
		for (int i = 0; i < span; i++)
			if (level.hasChunk(chunkX + i, chunkZ))
				return true;
		return false;
	}

	private static boolean allLoaded(ServerLevel level, int chunkX, int chunkZ, int span) {
		for (int i = 0; i < span; i++)
			if (!level.hasChunk(chunkX + i, chunkZ))
				return false;
		return true;
	}

	private static void chunkRoundTrip(GameTestHelper helper, String kind, int chunkCoordinate, boolean spark) {
		SmokeReport report = new SmokeReport("chunks-" + kind);
		ServerLevel level = helper.getLevel();
		int chunkX = chunkCoordinate, chunkZ = chunkCoordinate;
		int span = kind.contains("border") ? 2 : 1;
		BlockPos at = new BlockPos(chunkX * 16 + (span == 2 ? 12 : 4), helper.absolutePos(BlockPos.ZERO)
			.getY(), chunkZ * 16 + 4);
		force(level, chunkX, chunkZ, span, true);
		if (spark)
			TestEngine.buildSpark(level, at, true);
		else
			TestEngine.build(level, at, true);
		if (kind.startsWith("blower"))
			addBlower(level, at);
		helper.runAtTickTime(300, () -> level.removeBlock(at.offset(STARTER_POWER), false));
		int[] stage = {0};
		long[] stageStart = {0};
		helper.runAtTickTime(700, () -> {
			EngineSummary before = summaryAt(level, at);
			if (before == null || before.state() != EngineState.RUNNING) {
				report.skip("chunks", "engine unload test", "the engine was not running before the unload: " + before);
				conclude(helper, report);
				return;
			}
			force(level, chunkX, chunkZ, span, false);
			stage[0] = 1;
			stageStart[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			long elapsed = helper.getTick() - stageStart[0];
			switch (stage[0]) {
				case 1 -> {
					if (!anyLoaded(level, chunkX, chunkZ, span)) {
						report.pass("chunks", "the chunk unloaded");
						// A player walking back loads the chunks of a build one after another, not in the same tick
						if (kind.contains("staggered")) {
							force(level, chunkX, chunkZ, 1, true);
							stage[0] = 4;
						} else {
							force(level, chunkX, chunkZ, span, true);
							stage[0] = 2;
						}
						stageStart[0] = helper.getTick();
					} else if (elapsed > 600) {
						report.skip("chunks", "engine unload test", "the chunk would not unload in 600 ticks, so nothing was tested");
						stage[0] = 9;
						conclude(helper, report);
					}
				}
				case 4 -> {
					// The first chunk has had its moment alone; now the rest arrive
					if (elapsed >= 60) {
						force(level, chunkX, chunkZ, span, true);
						stage[0] = 2;
						stageStart[0] = helper.getTick();
					}
				}
				case 2 -> {
					if (allLoaded(level, chunkX, chunkZ, span)) {
						stage[0] = 3;
						stageStart[0] = helper.getTick();
					} else if (elapsed > 600) {
						report.fail("chunks", "the chunk loaded again", "still not loaded 600 ticks after being forced");
						stage[0] = 9;
						conclude(helper, report);
					}
				}
				case 3 -> {
					// Give the topology scan a moment to find the engine again, then see what state it is in
					if (elapsed == 200) {
						report.check("chunks", "the crankshaft came back", level.getBlockEntity(at.offset(PRIMARY)) instanceof CrankshaftBlockEntity,
							"no crankshaft block entity after the reload");
						EngineSummary after = summaryAt(level, at);
						report.check("chunks", "the engine was rebuilt after the reload", after != null, "no engine summary 200 ticks after loading");
						if (after != null)
							report.expect("chunks", "the engine keeps running through the reload", after.state() == EngineState.RUNNING,
								"state " + after.state() + ", " + after.rpm() + " rpm (it may be restarting from cold: decide whether that is intended)");
						stage[0] = 9;
						conclude(helper, report);
					}
				}
				default -> {
				}
			}
		});
	}

	/** One tuning knob and the value to give it, for the sweep below. */
	private record Knob(String name, java.util.function.Consumer<Double> set, double min, double max) {
	}

	/**
	 * Every numeric tuning setting at both ends of its allowed range, and every switch both ways, with an engine running
	 * under each. Nothing may throw, go NaN or run away: settings are edited by players in a text file, where a value
	 * that was never tried is the first one somebody types. In a batch of its own, because the settings are global.
	 */
	@GameTest(template = "empty", batch = "smoke-tuning", timeoutTicks = 20000)
	public static void everyTuningSettingAtItsExtremesLeavesAnEngineSane(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("tuning");
		ServerLevel level = helper.getLevel();
		List<Knob> knobs = List.of(new Knob("precisionBonus", v -> Tuning.precisionBonus = v, 0, 2),
			new Knob("boostStrength", v -> Tuning.boostStrength = v, 0, 2),
			new Knob("rotaryPower", v -> Tuning.rotaryPower = v, 0.25, 2),
			new Knob("fuelConsumption", v -> Tuning.fuelConsumption = v, 0.05, 20),
			new Knob("torsionalFlex", v -> Tuning.torsionalFlex = v, 0, 2),
			new Knob("coolingBonus", v -> Tuning.coolingBonus = v, 0, 3),
			new Knob("heatRecovery", v -> Tuning.heatRecovery = v, 0.1, 10));
		List<Knob> switches = List.of(new Knob("forcedInduction", v -> Tuning.forcedInduction = v > 0, 0, 1),
			new Knob("sparkFouling", v -> Tuning.sparkFouling = v > 0, 0, 1),
			new Knob("oilDilution", v -> Tuning.oilDilution = v > 0, 0, 1),
			new Knob("oilAgeing", v -> Tuning.oilAgeing = v > 0, 0, 1),
			new Knob("engineTemperature", v -> Tuning.engineTemperature = v > 0, 0, 1),
			new Knob("batteryCold", v -> Tuning.batteryCold = v > 0, 0, 1),
			new Knob("crankingDraw", v -> Tuning.crankingDraw = v > 0, 0, 1),
			new Knob("pilotInjection", v -> Tuning.pilotInjection = v > 0, 0, 1),
			new Knob("rotaryEngines", v -> Tuning.rotaryEngines = v > 0, 0, 1),
			new Knob("heatRecoveryEnabled", v -> Tuning.heatRecoveryEnabled = v > 0, 0, 1));
		List<Runnable> variants = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (List<Knob> group : List.of(knobs, switches))
			for (Knob knob : group)
				for (double value : new double[] {knob.min(), knob.max()}) {
					names.add(knob.name() + "=" + value);
					variants.add(() -> {
						Tuning.reset();
						knob.set()
							.accept(value);
					});
				}
		// Combined extremes: everything at its minimum, everything at its maximum, and two seeded mixes, since a setting that is
		// harmless alone can stack with another
		for (int pattern = 0; pattern < 4; pattern++) {
			final int p = pattern;
			names.add("combined pattern " + p);
			variants.add(() -> {
				Tuning.reset();
				java.util.Random mix = new java.util.Random(p);
				for (List<Knob> group : List.of(knobs, switches))
					for (Knob knob : group)
						knob.set().accept(switch (p) {
							case 0 -> knob.min();
							case 1 -> knob.max();
							default -> mix.nextBoolean() ? knob.min() : knob.max();
						});
			});
		}
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
		int perVariant = 500;
		int[] variant = {-1};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			int index = (int) (t / perVariant);
			int within = (int) (t % perVariant);
			if (index >= variants.size()) {
				if (variant[0] != Integer.MAX_VALUE) {
					variant[0] = Integer.MAX_VALUE;
					Tuning.reset();
					conclude(helper, report);
				}
				return;
			}
			if (within == 0) {
				variant[0] = index;
				variants.get(index)
					.run();
				// A fresh engine for each setting, on the same spot
				for (int x = 0; x <= 7; x++)
					for (int y = 0; y <= 6; y++)
						for (int z = 0; z <= 6; z++)
							level.removeBlock(at.offset(x, y, z), false);
				if (index % 2 == 0)
					TestEngine.build(level, at, true);
				else
					TestEngine.buildSpark(level, at, true);
			}
			if (within == 200)
				level.removeBlock(at.offset(STARTER_POWER), false);
			if (within == perVariant - 1) {
				String name = names.get(index);
				EngineSummary summary = summaryAt(level, at);
				if (summary == null) {
					report.fail("tuning", name, "no engine summary");
					return;
				}
				boolean finite = Float.isFinite(summary.rpm()) && Float.isFinite(summary.availableTorque())
					&& Float.isFinite(summary.loadTorque()) && Float.isFinite(summary.fuelPerMinute())
					&& Float.isFinite(summary.temperature()) && Float.isFinite(summary.condition());
				report.check("tuning", name + " keeps every figure finite", finite, summary.toString());
				report.check("tuning", name + " keeps rpm in a sane range", Math.abs(summary.rpm()) < 20000, "rpm " + summary.rpm());
				if (level.getBlockEntity(at.offset(PRIMARY)) instanceof CrankshaftBlockEntity crank)
					report.check("tuning", name + " gives Create finite speed and stress", Float.isFinite(crank.getSpeed())
						&& Float.isFinite(crank.publishedCapacity()) && crank.publishedCapacity() >= 0,
						"speed " + crank.getSpeed() + ", capacity " + crank.publishedCapacity());
			}
		});
	}

	/** One config setting that is not a {@code Tuning} field, with its range and whether an engine may fail to form at its minimum. */
	private record ConfigKnob(String name, net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<? extends Number> value, double min,
							  double max, boolean mayLoseTheEngine) {
	}

	/**
	 * The settings the tuning sweep above does not reach: the stress scale, the inertias, bearing friction, the scan limits and
	 * the alternator, each at both ends of its range and all of them together, with an engine running under each. The scan
	 * limits at their minimum may stop an engine forming at all, which is fine; everything else must run, with finite figures
	 * and finite speed and stress given to Create. Each value is put back afterwards (they live in the shared config).
	 */
	@GameTest(template = "empty", batch = "smoke-config", timeoutTicks = 20000)
	public static void everyConfigSettingAtItsExtremesLeavesAnEngineSane(GameTestHelper helper) {
		SmokeReport report = new SmokeReport("config");
		ServerLevel level = helper.getLevel();
		List<ConfigKnob> knobs = List.of(
			new ConfigKnob("stressPerTorque", com.createengines.config.CEConfig.STRESS_PER_TORQUE, 1, 10000, false),
			new ConfigKnob("journalInertia", com.createengines.config.CEConfig.JOURNAL_INERTIA, 0, 100, false),
			new ConfigKnob("throwInertia", com.createengines.config.CEConfig.THROW_INERTIA, 0, 100, false),
			new ConfigKnob("flywheelInertia", com.createengines.config.CEConfig.FLYWHEEL_INERTIA, 0, 100, false),
			new ConfigKnob("castFlywheelInertia", com.createengines.config.CEConfig.CAST_FLYWHEEL_INERTIA, 0, 100, false),
			new ConfigKnob("bearingFriction", com.createengines.config.CEConfig.BEARING_FRICTION, 0, 10, false),
			new ConfigKnob("maxCrankSegments", com.createengines.config.CEConfig.MAX_CRANK_SEGMENTS, 1, 1024, true),
			new ConfigKnob("maxManifoldBlocks", com.createengines.config.CEConfig.MAX_MANIFOLD_BLOCKS, 1, 4096, true),
			new ConfigKnob("maxCoupledLines", com.createengines.config.CEConfig.MAX_COUPLED_LINES, 1, 64, true),
			new ConfigKnob("maxCouplingWalk", com.createengines.config.CEConfig.MAX_COUPLING_WALK, 1, 8192, true),
			new ConfigKnob("alternatorEnergy", com.createengines.config.CEConfig.ALTERNATOR_ENERGY, 0, 100, false));
		java.util.Map<ConfigKnob, Number> originals = new java.util.LinkedHashMap<>();
		for (ConfigKnob knob : knobs)
			originals.put(knob, knob.value().get());
		List<Runnable> variants = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<Boolean> mayLose = new ArrayList<>();
		for (ConfigKnob knob : knobs)
			for (double value : new double[] {knob.min(), knob.max()}) {
				names.add(knob.name() + "=" + value);
				mayLose.add(knob.mayLoseTheEngine() && value == knob.min());
				variants.add(() -> {
					restore(originals);
					setValue(knob, value);
				});
			}
		for (int pattern = 0; pattern < 3; pattern++) {
			final int p = pattern;
			names.add("combined pattern " + p);
			mayLose.add(true);
			variants.add(() -> {
				restore(originals);
				java.util.Random mix = new java.util.Random(p);
				for (ConfigKnob knob : knobs)
					setValue(knob, switch (p) {
						case 0 -> knob.min();
						case 1 -> knob.max();
						default -> mix.nextBoolean() ? knob.min() : knob.max();
					});
			});
		}
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
		int perVariant = 500;
		boolean[] done = {false};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			int index = (int) (t / perVariant);
			int within = (int) (t % perVariant);
			if (index >= variants.size()) {
				if (!done[0]) {
					done[0] = true;
					restore(originals);
					conclude(helper, report);
				}
				return;
			}
			if (within == 0) {
				variants.get(index).run();
				for (int x = 0; x <= 7; x++)
					for (int y = 0; y <= 6; y++)
						for (int z = 0; z <= 6; z++)
							level.removeBlock(at.offset(x, y, z), false);
				if (index % 2 == 0)
					TestEngine.build(level, at, true);
				else
					TestEngine.buildSpark(level, at, true);
			}
			if (within == 200)
				level.removeBlock(at.offset(STARTER_POWER), false);
			if (within == perVariant - 1) {
				String name = names.get(index);
				EngineSummary summary = summaryAt(level, at);
				if (summary == null) {
					if (mayLose.get(index))
						report.pass("config", name + " (no engine formed, allowed at a scan limit's minimum)");
					else
						report.fail("config", name, "no engine summary");
					return;
				}
				boolean finite = Float.isFinite(summary.rpm()) && Float.isFinite(summary.availableTorque())
					&& Float.isFinite(summary.loadTorque()) && Float.isFinite(summary.fuelPerMinute())
					&& Float.isFinite(summary.temperature()) && Float.isFinite(summary.condition());
				report.check("config", name + " keeps every figure finite", finite, summary.toString());
				report.check("config", name + " keeps rpm in a sane range", Math.abs(summary.rpm()) < 20000, "rpm " + summary.rpm());
				if (level.getBlockEntity(at.offset(PRIMARY)) instanceof CrankshaftBlockEntity crank)
					report.check("config", name + " gives Create finite speed and stress", Float.isFinite(crank.getSpeed())
						&& Float.isFinite(crank.publishedCapacity()) && crank.publishedCapacity() >= 0,
						"speed " + crank.getSpeed() + ", capacity " + crank.publishedCapacity());
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setValue(ConfigKnob knob, double value) {
		var config = (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) knob.value();
		if (knob.value() instanceof net.neoforged.neoforge.common.ModConfigSpec.IntValue)
			config.set((int) Math.round(value));
		else
			config.set(value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void restore(java.util.Map<ConfigKnob, Number> originals) {
		for (var entry : originals.entrySet())
			((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) entry.getKey().value()).set(entry.getValue());
	}
}
