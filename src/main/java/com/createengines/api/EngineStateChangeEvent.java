package com.createengines.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Posted on {@code NeoForge.EVENT_BUS}, on the server thread, when an engine changes what it is doing: a start is a change
 * to {@link EngineStatus#RUNNING}, a stall one to {@link EngineStatus#STALLED}. It is a notification only and cannot be
 * cancelled. A listener that throws is logged and ignored, never allowed to stop the engine's tick. <b>Preview:</b> the API
 * may still change until 2.0.
 */
public class EngineStateChangeEvent extends Event {

	private final Level level;
	private final BlockPos primary;
	private final EngineStatus from;
	private final EngineStatus to;

	public EngineStateChangeEvent(Level level, BlockPos primary, EngineStatus from, EngineStatus to) {
		this.level = level;
		this.primary = primary;
		this.from = from;
		this.to = to;
	}

	public Level level() {
		return level;
	}

	/** The engine's primary crankshaft block, which names the engine. */
	public BlockPos primary() {
		return primary;
	}

	public EngineStatus from() {
		return from;
	}

	public EngineStatus to() {
		return to;
	}

	/** The engine has just begun to run. */
	public boolean started() {
		return to == EngineStatus.RUNNING && from != EngineStatus.OVERLOADED;
	}

	/** The engine has just stalled. */
	public boolean stalled() {
		return to == EngineStatus.STALLED && from != EngineStatus.STALLED;
	}
}
