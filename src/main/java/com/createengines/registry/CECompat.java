package com.createengines.registry;

import com.simibubi.create.api.boiler.BoilerHeater;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** What the mod tells Create's own registries about its blocks, once at setup. */
public final class CECompat {

	private CECompat() {
	}

	public static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			// The exchanger carries a blaze burner's heat level property, so a boiler reads it exactly as it reads a
			// burner: kindled is one level of boiler, seething is two, smouldering only keeps the water warm
			BoilerHeater.REGISTRY.register(CEBlocks.HEAT_EXCHANGER.get(), BoilerHeater.BLAZE_BURNER);
		});
	}
}
