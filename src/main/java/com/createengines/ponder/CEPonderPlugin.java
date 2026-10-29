package com.createengines.ponder;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CEPonderPlugin implements PonderPlugin {

	@Override
	public String getModId() {
		return CreateEngines.MOD_ID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> scenes = helper.withKeyFunction(RegistryEntry::getId);

		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("crankshaft", EngineScenes::crankshaft);
		scenes.forComponents(CEBlocks.CYLINDER, CEBlocks.CYLINDER_HEAD, CEItems.PISTON, CEItems.CONNECTING_ROD,
				CEItems.FUEL_INJECTOR)
			.addStoryBoard("cylinder", EngineScenes::cylinder);
		scenes.forComponents(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
			.addStoryBoard("camshaft", EngineScenes::camshaft);
		scenes.forComponents(CEBlocks.INTAKE_MANIFOLD, CEBlocks.EXHAUST_MANIFOLD, CEBlocks.AIR_INTAKE,
				CEBlocks.EXHAUST_OUTLET)
			.addStoryBoard("airflow", EngineScenes::airflow);
		scenes.forComponents(CEBlocks.CYLINDER_HEAD, CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("lubrication", EngineScenes::lubrication);
		helper.forComponents(CreateEngines.asResource("engine_oil_bucket"))
			.addStoryBoard("lubrication", EngineScenes::lubrication);
		scenes.forComponents(CEBlocks.BATTERY, CEItems.SPARK_PLUG)
			.addStoryBoard("spark", EngineScenes::spark);
		scenes.forComponents(CEBlocks.STARTER)
			.addStoryBoard("starter", EngineScenes::starter);
	}
}
