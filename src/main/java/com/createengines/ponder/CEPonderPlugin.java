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
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("coupled_cranks", EngineScenes::coupledCranks);
		// The injector's own page is about metering it; it is registered ahead of the cylinder scene so it comes first
		scenes.forComponents(CEItems.FUEL_INJECTOR)
			.addStoryBoard("mixture", EngineScenes::mixture);
		scenes.forComponents(CEBlocks.CYLINDER, CEBlocks.CYLINDER_HEAD, CEItems.PISTON, CEItems.CONNECTING_ROD,
				CEItems.FUEL_INJECTOR)
			.addStoryBoard("cylinder", EngineScenes::cylinder);
		scenes.forComponents(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
			.addStoryBoard("camshaft", EngineScenes::camshaft);
		scenes.forComponents(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
			.addStoryBoard("firing_order", EngineScenes::firingOrder);
		scenes.forComponents(CEBlocks.CAM_LOBE, CEBlocks.CYLINDER, CEBlocks.CYLINDER_HEAD, CEBlocks.CRANK_THROW)
			.addStoryBoard("compact_v", EngineScenes::compactV);
		scenes.forComponents(CEBlocks.INTAKE_MANIFOLD, CEBlocks.EXHAUST_MANIFOLD, CEBlocks.AIR_INTAKE,
				CEBlocks.EXHAUST_OUTLET)
			.addStoryBoard("airflow", EngineScenes::airflow);
		scenes.forComponents(CEBlocks.THROTTLE)
			.addStoryBoard("throttle", EngineScenes::throttle);
		scenes.forComponents(CEBlocks.MAF_SENSOR)
			.addStoryBoard("maf_sensor", EngineScenes::mafSensor);
		scenes.forComponents(CEBlocks.LAMBDA_SENSOR)
			.addStoryBoard("lambda_sensor", EngineScenes::lambdaSensor);
		scenes.forComponents(CEBlocks.REV_LIMITER)
			.addStoryBoard("rev_limiter", EngineScenes::revLimiter);
		scenes.forComponents(CEBlocks.GOVERNOR)
			.addStoryBoard("governor", EngineScenes::governor);
		scenes.forComponents(CEItems.TIMING_TOOL)
			.addStoryBoard("timing_tool", EngineScenes::timingTool);
		scenes.forComponents(CEBlocks.PRECISION_CYLINDER, CEBlocks.PRECISION_CYLINDER_HEAD, CEItems.FORGED_PISTON,
				CEItems.PRECISION_INJECTOR)
			.addStoryBoard("precision_parts", EngineScenes::precisionParts);
		scenes.forComponents(CEBlocks.CYLINDER_HEAD)
			.addStoryBoard("mixture", EngineScenes::mixture);
		scenes.forComponents(CEBlocks.CYLINDER_HEAD, CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("lubrication", EngineScenes::lubrication);
		helper.forComponents(CreateEngines.asResource("engine_oil_bucket"))
			.addStoryBoard("lubrication", EngineScenes::lubrication);
		scenes.forComponents(CEBlocks.BATTERY, CEBlocks.ALTERNATOR, CEItems.SPARK_PLUG)
			.addStoryBoard("spark", EngineScenes::spark);
		scenes.forComponents(CEBlocks.STARTER)
			.addStoryBoard("starter", EngineScenes::starter);
		scenes.forComponents(CEBlocks.TURBOCHARGER)
			.addStoryBoard("turbocharger", EngineScenes::turbocharger);
		scenes.forComponents(CEBlocks.TRANSMISSION)
			.addStoryBoard("transmission", EngineScenes::transmission);
		scenes.forComponents(CEBlocks.SUMP)
			.addStoryBoard("sump", EngineScenes::sump);
	}
}
