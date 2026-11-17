package com.createengines.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Posted on {@code NeoForge.EVENT_BUS}, on the server thread, the first time an engine reaches something an advancement
 * waits for (a first running engine, a twelve-cylinder, a redline). It may be posted again for the same milestone after the
 * engine is rebuilt or the world is reloaded. The names are the milestone names in lower case; new ones may be added, and
 * a listener should ignore a name it does not know. A notification only, and a listener that throws is logged and ignored.
 * <b>Preview:</b> the API may still change until 2.0.
 */
public class EngineMilestoneEvent extends Event {

	private final Level level;
	private final BlockPos primary;
	private final String milestone;

	public EngineMilestoneEvent(Level level, BlockPos primary, String milestone) {
		this.level = level;
		this.primary = primary;
		this.milestone = milestone;
	}

	public Level level() {
		return level;
	}

	public BlockPos primary() {
		return primary;
	}

	/** The milestone's name in lower case with underscores, such as {@code engine_running}. */
	public String milestone() {
		return milestone;
	}
}
