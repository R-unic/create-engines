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
		if (ModList.get()
			.isLoaded("jade"))
			Jade.check(report, engine);
	}

	/**
	 * The headphones in a Curios head slot, in four steps a few ticks apart so the client has time to see each change: 0 puts
	 * them in the slot, 1 checks that they count as worn and muffle, 2 takes them out, 3 checks that they no longer do. Does
	 * nothing without Curios.
	 */
	static void curios(SmokeReport report, int step) {
		if (ModList.get()
			.isLoaded("curios"))
			Curios.step(report, step);
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

	private static final class Curios {

		static void step(SmokeReport report, int step) {
			try {
				switch (step) {
					case 0, 2 -> {
						boolean on = step == 0;
						String problem = onServer(() -> {
							var player = Minecraft.getInstance()
								.getSingleplayerServer()
								.getPlayerList()
								.getPlayers()
								.get(0);
							var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
							if (inventory.isEmpty())
								return "the player has no Curios inventory";
							inventory.get()
								.setEquippedCurio("head", 0, on ? new net.minecraft.world.item.ItemStack(com.createengines.registry.CEItems.HEADPHONES.get())
									: net.minecraft.world.item.ItemStack.EMPTY);
							return "";
						});
						report.check("compat", "Curios takes the headphones " + (on ? "into" : "out of") + " its head slot", problem.isEmpty(), problem);
					}
					case 1, 3 -> {
						boolean on = step == 1;
						var player = Minecraft.getInstance().player;
						report.check("compat", "Curios: the headphones " + (on ? "in" : "out of") + " a Curios head slot " + (on ? "count as worn" : "no longer count"),
							com.createengines.content.headphones.HeadphoneWear.isWorn(player) == on,
							"the client sees them " + (on ? "not worn" : "still worn") + " (the head slot holds " + player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) + ")");
						report.check("compat", "Curios: the head slot itself is empty, so it is the Curios slot that counts",
							player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
								.isEmpty(), "the game's head slot holds " + player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
						var audit = com.createengines.client.HeadphoneMuffle.audit();
						report.check("compat", "Curios: world sounds are " + (on ? "muffled" : "not muffled") + " (" + audit.muffledNow() + " of " + audit.muffled() + ")",
							on ? audit.muffled() > 0 && audit.muffledNow() == audit.muffled() : audit.muffledNow() == 0,
							audit.muffledNow() + " of " + audit.muffled() + " world sounds are muffled");
					}
					default -> {
					}
				}
			} catch (Exception e) {
				report.check("compat", "Curios integration runs (step " + step + ")", false, e.toString());
			}
		}
	}

	private static final class Jade {

		/**
		 * Jade found the plugin if it handed our provider out for each of the four engine blocks (it does so only for what a plugin
		 * registered), and what the server would send for a running engine turns into lines on the client.
		 */
		static void check(SmokeReport report, BlockPos engine) {
			try {
				var registration = snownee.jade.impl.WailaClientRegistration.instance();
				var provider = com.createengines.compat.jade.EngineJadeProvider.INSTANCE;
				var blocks = java.util.Map.<String, net.minecraft.world.level.block.Block>of(
					"crankshaft", com.createengines.registry.CEBlocks.CRANKSHAFT.get(),
					"cylinder", com.createengines.registry.CEBlocks.CYLINDER.get(),
					"cylinder head", com.createengines.registry.CEBlocks.CYLINDER_HEAD.get(),
					"rotor housing", com.createengines.registry.CEBlocks.ROTOR_HOUSING.get());
				blocks.forEach((name, block) -> report.check("compat", "Jade shows the engine overlay over a " + name,
					registration.getBlockProviders(block, p -> true)
						.contains(provider), "Jade gave no engine provider for the " + name + " block; 0 means it never found the plugin"));
				var dimension = Minecraft.getInstance().level.dimension();
				var lines = onServer(() -> {
					var level = Minecraft.getInstance()
						.getSingleplayerServer()
						.getLevel(dimension);
					var data = new net.minecraft.nbt.CompoundTag();
					com.createengines.api.EngineApi.viewAt(level, engine)
						.ifPresent(view -> data.put(com.createengines.compat.jade.EngineTooltip.KEY, com.createengines.compat.jade.EngineTooltip.write(view)));
					return com.createengines.compat.jade.EngineTooltip.lines(data)
						.stream()
						.map(net.minecraft.network.chat.Component::getString)
						.toList();
				});
				report.check("compat", "Jade's tooltip for the running engine has lines (" + lines + ")", !lines.isEmpty()
					&& !lines.get(0)
						.contains("create_engines."), "the engine at " + engine + " gave " + lines);
			} catch (Exception | LinkageError e) {
				report.check("compat", "Jade integration runs", false, e.toString());
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
