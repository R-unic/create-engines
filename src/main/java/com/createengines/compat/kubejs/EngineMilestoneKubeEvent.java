package com.createengines.compat.kubejs;

import com.createengines.api.EngineMilestoneEvent;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * What a script sees of an {@link EngineMilestoneEvent}: {@code event.level}, {@code event.pos} (the engine's primary
 * crankshaft) and {@code event.milestone}, a lower case name such as {@code 'engine_running'}. A script should ignore a name
 * it does not know, since new milestones may be added.
 */
public class EngineMilestoneKubeEvent implements KubeEvent {

	private final EngineMilestoneEvent event;

	public EngineMilestoneKubeEvent(EngineMilestoneEvent event) {
		this.event = event;
	}

	public Level getLevel() {
		return event.level();
	}

	public BlockPos getPos() {
		return event.primary();
	}

	public String getMilestone() {
		return event.milestone();
	}
}
