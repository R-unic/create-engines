package com.createengines.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * The script events, in server scripts:
 * <pre>
 * CreateEnginesEvents.stateChanged(event => { if (event.stalled) console.log('stalled at ' + event.pos) })
 * CreateEnginesEvents.milestone(event => { ... })
 * </pre>
 */
public interface CreateEnginesEvents {

	EventGroup GROUP = EventGroup.of("CreateEnginesEvents");

	/** An engine began running, stalled, or otherwise changed what it is doing. */
	EventHandler STATE_CHANGED = GROUP.server("stateChanged", () -> EngineStateKubeEvent.class);

	/** An engine reached something an advancement waits for. */
	EventHandler MILESTONE = GROUP.server("milestone", () -> EngineMilestoneKubeEvent.class);
}
