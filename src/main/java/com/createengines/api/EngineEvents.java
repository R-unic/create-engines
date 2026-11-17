package com.createengines.api;

import com.createengines.CreateEngines;
import com.createengines.engine.EngineState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * How the engine reports to the public events. A listener (another mod's, or a script's) that throws must not stop an
 * engine ticking, so every post is guarded and a failure is logged.
 */
public final class EngineEvents {

	private EngineEvents() {
	}

	public static void stateChanged(Level level, BlockPos primary, EngineState from, EngineState to) {
		post(new EngineStateChangeEvent(level, primary, EngineStatus.valueOf(from.name()), EngineStatus.valueOf(to.name())));
	}

	public static void milestone(Level level, BlockPos primary, String milestone) {
		post(new EngineMilestoneEvent(level, primary, milestone));
	}

	private static void post(Event event) {
		try {
			NeoForge.EVENT_BUS.post(event);
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.error("A listener of {} failed; the engine carries on", event.getClass()
				.getSimpleName(), e);
		}
	}
}
