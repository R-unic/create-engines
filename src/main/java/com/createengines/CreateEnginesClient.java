package com.createengines;

import com.createengines.registry.CEPartialModels;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = CreateEngines.MOD_ID, dist = Dist.CLIENT)
public class CreateEnginesClient {

	public CreateEnginesClient(IEventBus modEventBus) {
		CEPartialModels.init();
	}
}
