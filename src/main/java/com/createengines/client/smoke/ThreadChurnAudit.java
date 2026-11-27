package com.createengines.client.smoke;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createengines.CreateEngines;
import com.createengines.client.SoundStopRace;
import com.createengines.client.VisualSmokeTest;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.gametest.TestEngine;
import com.createengines.smoke.SmokeReport;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Verification track item 6, the part no unit test can reach: the real sound engine and its thread, and the render thread
 * working while the integrated server ticks on its own. Beside running engines, for a minute or so, the client keeps pulling
 * the sound engine out from under the playing voices and throwing away what it has drawn, while the server thread keeps
 * breaking and rebuilding the engines being heard and drawn and reloads its data. An exception or an error on any thread
 * fails the run through the log scan; what is checked here is that everything is still alive and has come back afterwards.
 *
 * <p>It cannot prove there is no race: it gives one many chances to happen.
 */
final class ThreadChurnAudit {

	/** Ticks of disruption, then ticks left alone before the verdict. */
	private static final int CHURN = 900;
	private static final int END = CHURN + 300;
	/** A voice is given 20 ticks to start and its owner rests 40 after one ends, so this is time enough to be heard again. */
	private static final int RECOVERY = 100;
	/** The ticks the sound engine is disrupted at; each is judged {@link #RECOVERY} ticks later. */
	private static final int[] SOUND_BREAKS = {120, 240, 360, 600, 720};
	private static final String[] SOUND_BREAK_NAMES = {"the sound engine reloaded", "every sound was stopped",
		"the sound engine reloaded twice in two ticks", "sound paused, resumed and reloaded", "the sound engine reloaded again"};
	/** Resource packs reload here (F3+T): models, textures and the sound engine, under the running engines. */
	private static final int RESOURCE_RELOAD = 460;
	private static final Pattern VOICES = Pattern.compile("(\\d+)/(\\d+) \\+ (\\d+)/(\\d+)");
	/** The diesel is left alone to be heard; these two are broken and rebuilt all through the churn. */
	private static final int HEARD = 0;
	private static final int[] REBUILT = {1, 4};

	private final SmokeReport report;
	private final BlockPos origin;
	private final List<BlockPos> fleet;
	private final AtomicInteger breaks = new AtomicInteger();
	private final AtomicInteger rebuilds = new AtomicInteger();
	private final AtomicInteger dataReloads = new AtomicInteger();
	private CompletableFuture<Void> resources;
	/** Ticks after each sound break at which a note was first heard again, or -1. */
	private final int[] cameBackAfter = {-1, -1, -1, -1, -1};
	private int serverStart;
	private int serverTicks = -1;
	private int frames;
	private int racesBefore;
	private int rendererResets;
	private int streamingPeak;
	private int staticPeak;
	private double directStart;
	private final java.util.function.Consumer<RenderFrameEvent.Pre> frameCounter = event -> frames++;

	ThreadChurnAudit(SmokeReport report, BlockPos origin, List<BlockPos> fleet) {
		this.report = report;
		this.origin = origin;
		this.fleet = fleet;
	}

	boolean tick(int t) {
		Minecraft minecraft = Minecraft.getInstance();
		if (t == 0) {
			NeoForge.EVENT_BUS.addListener(frameCounter);
			racesBefore = SoundStopRace.count();
			serverStart = minecraft.getSingleplayerServer()
				.getTickCount();
			VisualSmokeTest.server(server -> {
				var level = server.overworld();
				VisualSmokeTest.command(server, String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f -18 5",
					origin.getX() - 1.5, origin.getY() + 4, origin.getZ() - 2.0));
				build(level, HEARD);
				for (int i : REBUILT)
					build(level, i);
			});
		}
		if (t > 0 && t % 100 == 0)
			refuel();
		int[] voices = voices();
		streamingPeak = Math.max(streamingPeak, voices[2]);
		staticPeak = Math.max(staticPeak, voices[0]);
		if (t == 100) {
			report.check("threads", "an engine note is playing before the churn", noteCount() > 0,
				"no engine of the fleet has its note playing, so the churn would prove nothing");
			directStart = settledDirect();
		}
		// Looked for on every tick of the window, not just its last: when the server stalls, the client's game time (which the
		// voices rest by) is set back, and a note that came back can be resting again. The first tick is left out, as a reload
		// asked for twice is not over until it.
		for (int i = 0; i < SOUND_BREAKS.length; i++) {
			int since = t - SOUND_BREAKS[i];
			if (since > 1 && since <= RECOVERY && cameBackAfter[i] < 0 && noteCount() > 0)
				cameBackAfter[i] = since;
			if (since == RECOVERY) {
				report.metric("thread churn: ticks until a note was back, break " + (i + 1), cameBackAfter[i], "");
				report.check("threads", "an engine note comes back after " + SOUND_BREAK_NAMES[i], cameBackAfter[i] >= 0,
					"no engine note was playing at any time in the " + RECOVERY + " ticks after");
			}
		}
		var sounds = minecraft.getSoundManager();
		if (t == SOUND_BREAKS[0] || t == SOUND_BREAKS[4])
			sounds.reload();
		if (t == SOUND_BREAKS[1])
			sounds.stop();
		if (t == SOUND_BREAKS[2] || t == SOUND_BREAKS[2] + 1)
			sounds.reload();
		if (t == SOUND_BREAKS[3])
			sounds.pause();
		if (t == SOUND_BREAKS[3] + 1) {
			sounds.resume();
			sounds.reload();
		}
		if (t > 100 && t < CHURN) {
			// Throwing the drawn chunks and Flywheel's visuals away while the server changes the blocks they are made from
			if (t % 35 == 0) {
				minecraft.levelRenderer.allChanged();
				rendererResets++;
			}
			// One of the two engines loses a part, and six ticks later is whole again
			int which = REBUILT[t / 12 % REBUILT.length];
			if (t % 12 == 0) {
				BlockPos part = new BlockPos[] {TestEngine.PRIMARY, TestEngine.CYLINDER, TestEngine.HEAD, TestEngine.THROW,
					TestEngine.EXHAUST}[t / 24 % 5];
				VisualSmokeTest.server(server -> {
					server.overworld()
						.setBlock(origin.offset(fleet.get(which))
							.offset(part), Blocks.AIR.defaultBlockState(), 3);
					breaks.incrementAndGet();
				});
			}
			if (t % 12 == 6)
				VisualSmokeTest.server(server -> {
					build(server.overworld(), which);
					rebuilds.incrementAndGet();
				});
			// The data maps the engines and the client both read are swapped under them
			if (t % 270 == 150)
				VisualSmokeTest.server(server -> {
					VisualSmokeTest.command(server, "reload");
					dataReloads.incrementAndGet();
				});
		}
		if (t == RESOURCE_RELOAD)
			resources = minecraft.reloadResourcePacks();
		if (t == CHURN)
			VisualSmokeTest.server(server -> {
				for (int i : REBUILT) {
					build(server.overworld(), i);
					VisualSmokeTest.command(server, "kill @e[type=item]");
				}
			});
		// With its starter powered an engine reads as cranking: cut the power so that "running" means on its own
		if (t == CHURN + 100)
			VisualSmokeTest.server(server -> {
				for (int i : new int[] {HEARD, REBUILT[0], REBUILT[1]})
					server.overworld()
						.removeBlock(origin.offset(fleet.get(i))
							.offset(TestEngine.STARTER_POWER), false);
			});
		if (t == CHURN + 100)
			serverTicks = minecraft.getSingleplayerServer()
				.getTickCount() - serverStart;
		if (t < END || resources != null && !resources.isDone() && t < END + 600)
			return false;
		conclude(t);
		NeoForge.EVENT_BUS.unregister(frameCounter);
		return true;
	}

	private void conclude(int t) {
		report.metric("thread churn: engine parts broken", breaks.get(), "");
		report.metric("thread churn: data reloads", dataReloads.get(), "");
		report.metric("thread churn: server ticks in " + (CHURN + 100) + " client ticks", serverTicks, "");
		report.metric("thread churn: frames drawn", frames, "");
		report.check("threads", "the server broke and rebuilt engines while the client drew them",
			breaks.get() >= 60 && rebuilds.get() >= 60 && dataReloads.get() >= 3 && rendererResets >= 20,
			"broken " + breaks.get() + ", rebuilt " + rebuilds.get() + ", data reloads " + dataReloads.get()
				+ ", renderer resets " + rendererResets);
		report.check("threads", "the server thread kept ticking through the churn", serverTicks > (CHURN + 100) / 2,
			serverTicks + " server ticks in " + (CHURN + 100) + " client ticks");
		report.check("threads", "the client kept drawing through the churn", frames > 200, frames + " frames");
		report.check("threads", "the resource reload under running engines finished",
			resources != null && resources.isDone() && !resources.isCompletedExceptionally(),
			resources == null ? "it never started" : "done " + resources.isDone() + ", failed " + resources.isCompletedExceptionally());
		var level = Minecraft.getInstance().level;
		for (int i : new int[] {HEARD, REBUILT[0], REBUILT[1]}) {
			EngineSummary summary = level.getBlockEntity(primary(i)) instanceof CrankshaftBlockEntity crank
				? crank.engineSummary() : null;
			report.check("threads", "engine " + i + " is running on the client after the churn",
				summary != null && summary.state() == EngineState.RUNNING && Float.isFinite(summary.rpm()), String.valueOf(summary));
		}
		// The error that race leaves in the log is too rare to show by its absence that the fix still applies
		report.check("threads", "every sound reload went through the stop race fix",
			SoundStopRace.count() - racesBefore >= SOUND_BREAKS.length,
			"the mixin on SoundEngine.stopAll ran " + (SoundStopRace.count() - racesBefore) + " times");
		report.check("threads", "an engine note is playing after the churn", noteCount() > 0, "none after " + t + " ticks");
		// Three engine notes, and the music and weather streams the game may have going
		report.check("threads", "streaming voices stayed bounded through the churn", streamingPeak <= 8, "peaked at " + streamingPeak);
		report.check("threads", "static voices stayed below the channel limit through the churn", staticPeak < 240,
			"peaked at " + staticPeak);
		double grown = settledDirect() - directStart;
		report.metric("thread churn: direct memory growth", grown, "MB");
		// A leak of a stream buffer per replaced voice or per reload shows here; the limit is the fleet run's
		report.check("threads", "direct memory did not climb through the churn", grown < 16, "grew " + grown + " MB");
		CreateEngines.LOGGER.info("Thread churn done: {} breaks, {} server ticks, {} frames", breaks.get(), serverTicks, frames);
	}

	private BlockPos primary(int engine) {
		return origin.offset(fleet.get(engine))
			.offset(TestEngine.PRIMARY);
	}

	private void build(net.minecraft.server.level.ServerLevel level, int engine) {
		// The starter keeps its power, so an engine that was broken comes back turning
		TestEngine.build(level, origin.offset(fleet.get(engine)), true);
		fuel(level, engine);
	}

	private void refuel() {
		VisualSmokeTest.server(server -> {
			fuel(server.overworld(), HEARD);
			for (int i : REBUILT)
				fuel(server.overworld(), i);
		});
	}

	private void fuel(net.minecraft.server.level.ServerLevel level, int engine) {
		if (level.getBlockEntity(origin.offset(fleet.get(engine))
			.offset(TestEngine.HEAD)) instanceof com.createengines.content.head.CylinderHeadBlockEntity head) {
			var action = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
			head.tank()
				.fill(new net.neoforged.neoforge.fluids.FluidStack(com.createengines.registry.CEFluids.DIESEL.get()
					.getSource(), 250), action);
			head.oilTank()
				.fill(new net.neoforged.neoforge.fluids.FluidStack(com.createengines.registry.CEFluids.ENGINE_OIL.get()
					.getSource(), 250), action);
		}
	}

	private int noteCount() {
		int playing = 0;
		for (int i = 0; i < fleet.size(); i++)
			if (EngineNoteSound.isPlayingFor(primary(i)))
				playing++;
		return playing;
	}

	/** Static channels in use, their limit, streaming channels in use, their limit. */
	private static int[] voices() {
		Matcher matcher = VOICES.matcher(Minecraft.getInstance()
			.getSoundManager()
			.getDebugString());
		return matcher.find() ? new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
			Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))} : new int[4];
	}

	private static double settledDirect() {
		System.gc();
		for (var pool : java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class))
			if (pool.getName()
				.equals("direct"))
				return pool.getMemoryUsed() / 1048576.0;
		return 0;
	}
}
