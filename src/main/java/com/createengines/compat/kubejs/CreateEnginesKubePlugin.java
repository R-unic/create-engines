package com.createengines.compat.kubejs;

import com.createengines.api.EngineMilestoneEvent;
import com.createengines.api.EngineStateChangeEvent;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The KubeJS plugin (named in {@code kubejs.plugins.txt}, which KubeJS reads, so nothing in the mod itself names a KubeJS
 * class): it adds the {@link CreateEnginesEvents} group and the {@link CreateEnginesBinding} global, and passes the mod's two
 * public events on to scripts.
 */
public class CreateEnginesKubePlugin implements KubeJSPlugin {

	@Override
	public void init() {
		NeoForge.EVENT_BUS.addListener(CreateEnginesKubePlugin::onStateChanged);
		NeoForge.EVENT_BUS.addListener(CreateEnginesKubePlugin::onMilestone);
	}

	@Override
	public void registerEvents(EventGroupRegistry registry) {
		registry.register(CreateEnginesEvents.GROUP);
	}

	@Override
	public void registerBindings(BindingRegistry bindings) {
		bindings.add("CreateEngines", CreateEnginesBinding.class);
	}

	private static void onStateChanged(EngineStateChangeEvent event) {
		if (CreateEnginesEvents.STATE_CHANGED.hasListeners())
			CreateEnginesEvents.STATE_CHANGED.post(new EngineStateKubeEvent(event));
	}

	private static void onMilestone(EngineMilestoneEvent event) {
		if (CreateEnginesEvents.MILESTONE.hasListeners())
			CreateEnginesEvents.MILESTONE.post(new EngineMilestoneKubeEvent(event));
	}
}
