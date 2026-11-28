package com.createengines.ponder;

import com.createengines.CreateEngines;
import com.createengines.registry.CECasingBlocks;
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
			.addStoryBoard("crankshaft", CrankScenes::crankshaft);
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("coupled_cranks", DrivelineScenes::coupledCranks);
		// The injector's own page is about metering it; it is registered ahead of the cylinder scene so it comes first
		scenes.forComponents(CEItems.FUEL_INJECTOR)
			.addStoryBoard("mixture", AirFuelScenes::mixture);
		scenes.forComponents(CEBlocks.CYLINDER, CEBlocks.CYLINDER_HEAD, CEItems.PISTON, CEItems.CONNECTING_ROD,
				CEItems.FUEL_INJECTOR)
			.addStoryBoard("cylinder", CrankScenes::cylinder);
		scenes.forComponents(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
			.addStoryBoard("camshaft", CrankScenes::camshaft);
		scenes.forComponents(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
			.addStoryBoard("firing_order", CrankScenes::firingOrder);
		scenes.forComponents(CEBlocks.CAM_LOBE, CEBlocks.CYLINDER, CEBlocks.CYLINDER_HEAD, CEBlocks.CRANK_THROW)
			.addStoryBoard("compact_v", CrankScenes::compactV);
		scenes.forComponents(CEBlocks.INTAKE_MANIFOLD, CEBlocks.EXHAUST_MANIFOLD, CEBlocks.AIR_INTAKE,
				CEBlocks.EXHAUST_OUTLET)
			.addStoryBoard("airflow", AirFuelScenes::airflow);
		scenes.forComponents(CEBlocks.AIR_FILTER)
			.addStoryBoard("airflow", AirFilterScenes::airFilter);
		scenes.forComponents(CEBlocks.THROTTLE)
			.addStoryBoard("throttle", AirFuelScenes::throttle);
		scenes.forComponents(CEBlocks.MAF_SENSOR)
			.addStoryBoard("maf_sensor", AirFuelScenes::mafSensor);
		scenes.forComponents(CEBlocks.GOVERNOR)
			.addStoryBoard("governor", DrivelineScenes::governor);
		scenes.forComponents(CEItems.TIMING_TOOL)
			.addStoryBoard("timing_tool", ToolScenes::timingTool);
		scenes.forComponents(CEBlocks.PRECISION_CYLINDER, CEBlocks.PRECISION_CYLINDER_HEAD, CEItems.FORGED_PISTON,
				CEItems.PRECISION_INJECTOR)
			.addStoryBoard("precision_parts", ToolScenes::precisionParts);
		scenes.forComponents(CEItems.HIGH_COMPRESSION_PISTON, CEItems.LOW_COMPRESSION_PISTON, CEItems.PISTON_RING,
				CEItems.HIGH_COMPRESSION_ROTOR, CEItems.LOW_COMPRESSION_ROTOR, CEItems.FORGED_PISTON, CEItems.ROTOR)
			.addStoryBoard("precision_parts", PistonScenes::compressionAndRings);
		scenes.forComponents(CEBlocks.CYLINDER_HEAD)
			.addStoryBoard("mixture", AirFuelScenes::mixture);
		scenes.forComponents(CEBlocks.CYLINDER_HEAD, CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
			.addStoryBoard("lubrication", StartingOilScenes::lubrication);
		helper.forComponents(CreateEngines.asResource("engine_oil_bucket"))
			.addStoryBoard("lubrication", StartingOilScenes::lubrication);
		scenes.forComponents(CEBlocks.BATTERY, CEBlocks.ALTERNATOR, CEItems.SPARK_PLUG)
			.addStoryBoard("spark", AirFuelScenes::spark);
		scenes.forComponents(CEBlocks.BATTERY, CEBlocks.DRY_CELL, CEBlocks.LARGE_BATTERY)
			.addStoryBoard("spark", BatteryScenes::batterySizes);
		scenes.forComponents(CEItems.INJECTOR_ASSIST, CEItems.IGNITION_COIL, CEBlocks.CYLINDER_HEAD)
			.addStoryBoard("spark", BatteryScenes::electricParts);
		scenes.forComponents(CEBlocks.STARTER)
			.addStoryBoard("starter", StartingOilScenes::starter);
		scenes.forComponents(CEBlocks.HEAVY_STARTER)
			.addStoryBoard("starter", StartingOilScenes::heavyStarter);
		scenes.forComponents(CEBlocks.STARTER, CEBlocks.BATTERY, CEBlocks.CYLINDER_HEAD, CEBlocks.AIR_INTAKE)
			.addStoryBoard("spark", TroubleshootingScenes::whyWontItStart);
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CYLINDER, CEBlocks.EXHAUST_OUTLET)
			.addStoryBoard("spark", TroubleshootingScenes::runningBadly);
		scenes.forComponents(CEBlocks.TURBOCHARGER)
			.addStoryBoard("turbocharger", InductionScenes::turbocharger);
		scenes.forComponents(CEBlocks.SUPERCHARGER, CEBlocks.VARIABLE_SUPERCHARGER, CEBlocks.INTERCOOLER)
			.addStoryBoard("supercharger", InductionScenes::supercharger);
		scenes.forComponents(CEBlocks.TURBOCHARGER)
			.addStoryBoard("supercharger", InductionScenes::supercharger);
		scenes.forComponents(CEBlocks.TRANSMISSION)
			.addStoryBoard("transmission", DrivelineScenes::transmission);
		scenes.forComponents(CEBlocks.ROTOR_HOUSING, CEItems.ROTOR)
			.addStoryBoard("rotor", DrivelineScenes::rotor);
		scenes.forComponents(CEBlocks.RADIATOR)
			.addStoryBoard("radiator", InductionScenes::radiator);
		scenes.forComponents(CEBlocks.SUMP, CEItems.OIL_FILTER)
			.addStoryBoard("sump", StartingOilScenes::sump);
		scenes.forComponents(CEBlocks.CAST_FLYWHEEL, CEBlocks.DUAL_MASS_FLYWHEEL)
			.addStoryBoard("flywheel", DrivelineScenes::flywheel);
		scenes.forComponents(CEItems.BANK_BUILDER)
			.addStoryBoard("bank_builder", ToolScenes::bankBuilder);
		scenes.forComponents(CEBlocks.DYNO)
			.addStoryBoard("dyno", ToolScenes::dyno);
		scenes.forComponents(CEBlocks.MAKER_PLATE)
			.addStoryBoard("maker_plate", ToolScenes::makerPlate);
		scenes.forComponents(CEBlocks.BLACK_BOX)
			.addStoryBoard("black_box", ToolScenes::blackBox);
		scenes.forComponents(CEBlocks.VARIABLE_TURBOCHARGER, CEBlocks.TURBOCHARGER)
			.addStoryBoard("turbo_tuning", InductionScenes::turboTuning);
		scenes.forComponents(CEBlocks.MUFFLER)
			.addStoryBoard("muffler", ExhaustCasingScenes::muffler);
		scenes.forComponents(CEBlocks.RAIN_CAP)
			.addStoryBoard("rain_cap", ExhaustCasingScenes::rainCap);
		scenes.forComponents(CEItems.HEADPHONES)
			.addStoryBoard("rain_cap", HeadphonesScenes::headphones);
		scenes.forComponents(CEItems.SERVICE_KIT, CEItems.CREATIVE_SERVICE_KIT, CEItems.STOCK_GASKET, CEItems.CARDBOARD_GASKET, CEItems.RUBBER_GASKET, CEItems.COPPER_GASKET,
			CEItems.PISTON, CEItems.FORGED_PISTON, CEItems.CONNECTING_ROD, CEItems.LIGHT_CONNECTING_ROD, CEItems.HEAVY_CONNECTING_ROD,
			CEItems.HIGH_COMPRESSION_PISTON, CEItems.LOW_COMPRESSION_PISTON, CEItems.APEX_SEALS)
			.addStoryBoard("rain_cap", WearScenes::wearAndTear);
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CAST_FLYWHEEL, CEBlocks.DUAL_MASS_FLYWHEEL, CEBlocks.TURBOCHARGER, CEBlocks.ROTOR_HOUSING)
			.addStoryBoard("rain_cap", AeronauticsScenes::movingEngine);
		scenes.forComponents(CECasingBlocks.ENGINE_CASING)
			.addStoryBoard("engine_casing", ExhaustCasingScenes::engineCasing);
		scenes.forComponents(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW, CEBlocks.ROTOR_HOUSING)
			.addStoryBoard("engine_readouts", ToolScenes::engineReadouts);
		scenes.forComponents(CEBlocks.HEAT_EXCHANGER)
			.addStoryBoard("heat_exchanger", InductionScenes::heatExchanger);
		scenes.forComponents(CEBlocks.HEAT_EXCHANGER)
			.addStoryBoard("fuels", AirFuelScenes::fuels);
		scenes.forComponents(CEBlocks.REBOILER, CEBlocks.COLUMN_SECTION, CEBlocks.CONDENSER, CEBlocks.FLARE_STACK)
			.addStoryBoard("refinery", RefineryScenes::refinery);
		scenes.forComponents(CEBlocks.REBOILER, CEBlocks.COLUMN_SECTION, CEBlocks.CONDENSER)
			.addStoryBoard("refinery_lean", RefineryScenes::refineryLean);
		scenes.forComponents(CEBlocks.SOOT_TRAP, CEItems.SOOT)
			.addStoryBoard("soot_trap", RefineryScenes::sootTrap);
		scenes.forComponents(CEItems.JERRY_CAN)
			.addStoryBoard("jerry_can", RefineryScenes::jerryCan);
		scenes.forComponents(CEItems.BLUEPRINT, CEBlocks.FRAMED_BLUEPRINT)
			.addStoryBoard("blueprint", BlueprintScenes::blueprint);
		// A burnable fuel opens on what it is for, then how it is made: the column first, the basin recipes after.
		for (String fuel : new String[] {"plant_oil", "diesel", "gasoline", "kerosene", "ethanol", "methanol"})
			helper.forComponents(CreateEngines.asResource(fuel + "_bucket"))
				.addStoryBoard("rain_cap", FuelScenes::choosingAFuel)
				.addStoryBoard("refinery", RefineryScenes::refinery)
				.addStoryBoard("fuels", AirFuelScenes::fuels);
		// The rest are ingredients and by-products, not fuels to choose between: only how they are made.
		for (String fuel : new String[] {"sugar_mash", "glycerin", "used_oil", "coolant"})
			helper.forComponents(CreateEngines.asResource(fuel + "_bucket"))
				.addStoryBoard("fuels", AirFuelScenes::fuels);
	}
}
