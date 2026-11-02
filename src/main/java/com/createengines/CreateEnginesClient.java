package com.createengines;

import java.util.List;

import com.createengines.client.VisualSmokeTest;
import com.createengines.config.CEClientConfig;
import com.createengines.ponder.CEPonderPlugin;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEPartialModels;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

@Mod(value = CreateEngines.MOD_ID, dist = Dist.CLIENT)
public class CreateEnginesClient {

	public CreateEnginesClient(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.CLIENT, CEClientConfig.SPEC);
		CEPartialModels.init();
		modEventBus.addListener(CreateEnginesClient::registerBucketColours);
		PonderIndex.addPlugin(new CEPonderPlugin());
		VisualSmokeTest.register();
	}

	/**
	 * The fluid layer of a bucket is drawn in the fluid's own tint, but only if the bucket has a colour handler that
	 * asks for it; without one every bucket's fluid is the grey of the water texture it is drawn from.
	 */
	private static void registerBucketColours(RegisterColorHandlersEvent.Item event) {
		for (FluidEntry<?> fluid : List.of(CEFluids.PLANT_OIL, CEFluids.DIESEL, CEFluids.GASOLINE, CEFluids.ENGINE_OIL, CEFluids.SUGAR_MASH,
			CEFluids.ETHANOL, CEFluids.GLYCERIN, CEFluids.USED_OIL, CEFluids.COOLANT))
			fluid.getBucket()
				.ifPresent(bucket -> event.register(new DynamicFluidContainerModel.Colors(), bucket));
	}
}
