package com.createengines.client.smoke;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.createengines.api.EngineMilestoneEvent;
import com.createengines.api.EngineStateChangeEvent;
import com.createengines.api.EngineStatus;
import com.createengines.smoke.SmokeReport;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * In a compatibility row that loads KubeJS or CC: Tweaked, the other mod must have found this mod's plugin or peripheral and
 * the two must work together. With neither mod loaded there is nothing to check and no class of theirs is touched: each mod's
 * checks live in a class of their own that is only named once the mod is known to be there.
 */
final class IntegrationAudit {

	private IntegrationAudit() {
	}

	/** The mod each compatibility row is meant to load, from the row's system property. */
	static void run(SmokeReport report, BlockPos engine) {
		String required = System.getProperty("create_engines.smokeAudit.requireIntegration");
		if (required != null)
			report.check("compat", required + " is loaded in the row that is meant to test it", ModList.get()
				.isLoaded(required), "the row names " + required + " but the game has no such mod");
		if (ModList.get()
			.isLoaded("kubejs"))
			Kube.check(report);
		if (ModList.get()
			.isLoaded("computercraft"))
			Cc.check(report, engine);
	}

	/** Runs on the integrated server thread and waits for it, which is safe from the render thread. */
	private static <T> T onServer(java.util.function.Supplier<T> job) throws Exception {
		var server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server == null)
			throw new IllegalStateException("no integrated server");
		return server.submit(job)
			.get(30, TimeUnit.SECONDS);
	}

	private static final class Kube {

		/**
		 * KubeJS found the plugin if a script listener for the mod's event is reached when the public event is posted (the
		 * plugin's own init is what connects the two), and the event group was registered with it.
		 */
		static void check(SmokeReport report) {
			var handler = com.createengines.compat.kubejs.CreateEnginesEvents.STATE_CHANGED;
			var milestone = com.createengines.compat.kubejs.CreateEnginesEvents.MILESTONE;
			AtomicInteger states = new AtomicInteger();
			AtomicInteger milestones = new AtomicInteger();
			StringBuilder seen = new StringBuilder();
			try {
				handler.listenJava(dev.latvian.mods.kubejs.script.ScriptType.SERVER, null, event -> {
					if (event instanceof com.createengines.compat.kubejs.EngineStateKubeEvent state)
						seen.append(state.getFrom())
							.append("->")
							.append(state.getTo())
							.append(state.isStarted() ? " started" : "");
					states.incrementAndGet();
					return null;
				});
				milestone.listenJava(dev.latvian.mods.kubejs.script.ScriptType.SERVER, null, event -> {
					if (event instanceof com.createengines.compat.kubejs.EngineMilestoneKubeEvent m)
						seen.append(" ")
							.append(m.getMilestone());
					milestones.incrementAndGet();
					return null;
				});
				var level = Minecraft.getInstance().level;
				BlockPos at = BlockPos.ZERO;
				onServer(() -> {
					var serverLevel = Minecraft.getInstance()
						.getSingleplayerServer()
						.getLevel(level.dimension());
					NeoForge.EVENT_BUS.post(new EngineStateChangeEvent(serverLevel, at, EngineStatus.CRANKING, EngineStatus.RUNNING));
					NeoForge.EVENT_BUS.post(new EngineMilestoneEvent(serverLevel, at, "engine_running"));
					return null;
				});
				report.check("compat", "KubeJS gives a script the state change the engine posted", states.get() == 1
					&& seen.toString().contains("cranking->running started"), "the script listener was reached " + states.get()
						+ " times (seen: " + seen + "); 0 means KubeJS never loaded the plugin");
				report.check("compat", "KubeJS gives a script the milestone the engine posted", milestones.get() == 1
					&& seen.toString().contains("engine_running"), "the script listener was reached " + milestones.get() + " times");
			} catch (Exception e) {
				report.check("compat", "KubeJS integration runs", false, e.toString());
			} finally {
				handler.clear(dev.latvian.mods.kubejs.script.ScriptType.SERVER);
				milestone.clear(dev.latvian.mods.kubejs.script.ScriptType.SERVER);
			}
		}
	}

	private static final class Cc {

		/** The crankshaft, the cylinder, the head and the housing offer a peripheral, and it answers for the whole engine. */
		static void check(SmokeReport report, BlockPos engine) {
			try {
				var dimension = Minecraft.getInstance().level.dimension();
				String answer = onServer(() -> {
					var level = Minecraft.getInstance()
						.getSingleplayerServer()
						.getLevel(dimension);
					var peripheral = level.getCapability(dan200.computercraft.api.peripheral.PeripheralCapability.get(), engine, null);
					if (peripheral == null)
						return "no peripheral on the crankshaft at " + engine;
					if (!"create_engines_engine".equals(peripheral.getType()))
						return "the peripheral's type is " + peripheral.getType();
					var table = ((com.createengines.compat.computercraft.EnginePeripheral) peripheral).getEngine();
					if (table == null)
						return "the engine did not answer (nil)";
					if (!(table.get("cylinders") instanceof Integer cylinders) || cylinders < 1)
						return "the engine reports " + table.get("cylinders") + " cylinders";
					if (!(table.get("rpm") instanceof Float rpm) || rpm < 0)
						return "the engine reports " + table.get("rpm") + " rpm";
					String status = ((com.createengines.compat.computercraft.EnginePeripheral) peripheral).getStatus();
					return status != null && status.equals(status.toLowerCase(java.util.Locale.ROOT)) ? "" : "the status is " + status;
				});
				report.check("compat", "CC: Tweaked sees a peripheral on the engine that reads the engine", answer.isEmpty(), answer);
			} catch (Exception e) {
				report.check("compat", "CC: Tweaked integration runs", false, e.toString());
			}
		}
	}
}
