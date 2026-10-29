package com.createengines;

import org.slf4j.Logger;

import com.createengines.config.CEConfig;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CECreativeTabs;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;
import com.createengines.registry.CESounds;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CreateEngines.MOD_ID)
public class CreateEngines {
	public static final String MOD_ID = "create_engines";
	public static final Logger LOGGER = LogUtils.getLogger();

	// Our tab lists every entry itself, as Create's do. Registrate's default would also add each one to search, and
	// search already collects them from our tab, so the duplicates crash the creative inventory.
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
		.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

	public CreateEngines(IEventBus modEventBus, ModContainer modContainer) {
		REGISTRATE.registerEventListeners(modEventBus);

		CECreativeTabs.register(modEventBus);
		CEBlocks.register();
		CEItems.register();
		CEFluids.register();
		CEBlockEntities.register();
		CESounds.register(modEventBus);
		CELang.register();

		modEventBus.addListener(CEDataMaps::register);
		modEventBus.addListener(CEBlockEntities::registerCapabilities);
		modContainer.registerConfig(ModConfig.Type.SERVER, CEConfig.SPEC);

		NeoForge.EVENT_BUS.addListener(EngineTopology::onLevelTick);
		NeoForge.EVENT_BUS.addListener(EngineTopology::onLevelUnload);
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
