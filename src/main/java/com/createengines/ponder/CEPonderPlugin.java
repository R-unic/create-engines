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
		scenes.forComponents(CEBlocks.STARTER, CEBlocks.BATTERY, CEBlocks.CYLINDER_HEAD, CEBlocks.AIR_INTAKE)
			.addStoryBoard("spark", TroubleshootingScenes::whyWontItStart);
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CYLINDER, CEBlocks.EXHAUST_OUTLET)
			.addStoryBoard("spark", TroubleshootingScenes::runningBadly);
		scenes.forComponents(CEBlocks.TURBOCHARGER)
			.addStoryBoard("turbocharger", EngineScenes::turbocharger);
		scenes.forComponents(CEBlocks.SUPERCHARGER, CEBlocks.INTERCOOLER)
			.addStoryBoard("supercharger", EngineScenes::supercharger);
		scenes.forComponents(CEBlocks.TURBOCHARGER)
			.addStoryBoard("supercharger", EngineScenes::supercharger);
		scenes.forComponents(CEBlocks.ROTOR_HOUSING, CEItems.ROTOR)
			.addStoryBoard("rotor", EngineScenes::rotor);
		scenes.forComponents(CEBlocks.SUMP, CEItems.OIL_FILTER)
			.addStoryBoard("sump", EngineScenes::sump);
		scenes.forComponents(CEBlocks.CAST_FLYWHEEL)
			.addStoryBoard("flywheel", EngineScenes::flywheel);
		scenes.forComponents(CEItems.BANK_BUILDER)
			.addStoryBoard("bank_builder", EngineScenes::bankBuilder);
		scenes.forComponents(CEBlocks.DYNO)
			.addStoryBoard("dyno", EngineScenes::dyno);
		scenes.forComponents(CEBlocks.BLACK_BOX)
			.addStoryBoard("black_box", EngineScenes::blackBox);
		scenes.forComponents(CEBlocks.VARIABLE_TURBOCHARGER, CEBlocks.TURBOCHARGER)
			.addStoryBoard("turbo_tuning", EngineScenes::turboTuning);
		scenes.forComponents(CEBlocks.HEAT_EXCHANGER)
			.addStoryBoard("heat_exchanger", EngineScenes::heatExchanger);
		scenes.forComponents(CEBlocks.HEAT_EXCHANGER)
			.addStoryBoard("fuels", EngineScenes::fuels);
		for (String fuel : new String[] {"plant_oil", "diesel", "gasoline", "sugar_mash", "ethanol", "glycerin", "used_oil", "coolant"})
			helper.forComponents(CreateEngines.asResource(fuel + "_bucket"))
				.addStoryBoard("fuels", EngineScenes::fuels);
	}
}
