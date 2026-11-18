package com.createengines.compat.kubejs;

import java.util.Locale;

import com.createengines.api.EngineStateChangeEvent;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * What a script sees of an {@link EngineStateChangeEvent}: {@code event.level}, {@code event.pos} (the engine's primary
 * crankshaft), {@code event.from} and {@code event.to} as lower case names ({@code 'running'}, {@code 'stalled'}), and
 * {@code event.started} and {@code event.stalled}. A notification only, so a script cannot cancel it.
 */
public class EngineStateKubeEvent implements KubeEvent {

	private final EngineStateChangeEvent event;

	public EngineStateKubeEvent(EngineStateChangeEvent event) {
		this.event = event;
	}

	public Level getLevel() {
		return event.level();
	}

	public BlockPos getPos() {
		return event.primary();
	}

	public String getFrom() {
		return event.from()
			.name()
			.toLowerCase(Locale.ROOT);
	}

	public String getTo() {
		return event.to()
			.name()
			.toLowerCase(Locale.ROOT);
	}

	public boolean isStarted() {
		return event.started();
	}

	public boolean isStalled() {
		return event.stalled();
	}
}
