package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.EnginePartRenderers;
import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.alternator.AlternatorVisual;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.dyno.DynoBlockEntity;
import com.createengines.content.flywheel.CastFlywheelBlockEntity;
import com.createengines.content.flywheel.CastFlywheelVisual;
import com.createengines.content.flywheel.DataFlywheelVisual;
import com.createengines.content.flywheel.DualMassFlywheelVisual;
import com.createengines.content.alternator.AlternatorRenderer;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.camshaft.CamshaftVisual;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftVisual;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.cylinder.CylinderVisual;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.head.CylinderHeadVisual;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.rotor.RotorHousingVisual;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.starter.StarterRenderer;
import com.createengines.content.starter.StarterVisual;
import com.createengines.content.refinery.ColumnPortBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.supercharger.SuperchargerVisual;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.content.blueprint.FramedBlueprintBlockEntity;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.content.plate.MakerPlateRenderer;
import com.createengines.content.raincap.RainCapBlockEntity;
import com.createengines.content.muffler.SootTrapBlockEntity;
import com.createengines.content.jerrycan.JerryCanItem;
import com.createengines.content.raincap.RainCapRenderer;
import com.createengines.content.turbo.TurbochargerRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class CEBlockEntities {

	public static final BlockEntityEntry<CrankshaftBlockEntity> CRANKSHAFT = REGISTRATE
		.blockEntity("crankshaft", CrankshaftBlockEntity::new)
		.visual(() -> CrankshaftVisual::new, false)
		.validBlocks(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
		.renderer(() -> EnginePartRenderers.Crankshaft::new)
		.register();

	public static final BlockEntityEntry<RotorHousingBlockEntity> ROTOR_HOUSING = REGISTRATE
		.blockEntity("rotor_housing", RotorHousingBlockEntity::new)
		.visual(() -> RotorHousingVisual::new, false)
		.validBlocks(CEBlocks.ROTOR_HOUSING)
		.renderer(() -> EnginePartRenderers.RotorHousing::new)
		.register();

	public static final BlockEntityEntry<CamshaftBlockEntity> CAMSHAFT = REGISTRATE
		.blockEntity("camshaft", CamshaftBlockEntity::new)
		.visual(() -> CamshaftVisual::new, false)
		.validBlocks(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
		.renderer(() -> EnginePartRenderers.Camshaft::new)
		.register();

	public static final BlockEntityEntry<CylinderBlockEntity> CYLINDER = REGISTRATE
		.blockEntity("cylinder", CylinderBlockEntity::new)
		.visual(() -> CylinderVisual::new)
		.validBlocks(CEBlocks.CYLINDER, CEBlocks.PRECISION_CYLINDER)
		.renderer(() -> EnginePartRenderers.Cylinder::new)
		.register();

	public static final BlockEntityEntry<CylinderHeadBlockEntity> CYLINDER_HEAD = REGISTRATE
		.blockEntity("cylinder_head", CylinderHeadBlockEntity::new)
		.visual(() -> CylinderHeadVisual::new)
		.validBlocks(CEBlocks.CYLINDER_HEAD, CEBlocks.PRECISION_CYLINDER_HEAD)
		.renderer(() -> EnginePartRenderers.Head::new)
		.register();

	public static final BlockEntityEntry<ThrottleBlockEntity> THROTTLE = REGISTRATE
		.blockEntity("throttle", ThrottleBlockEntity::new)
		.validBlocks(CEBlocks.THROTTLE)
		.register();

	public static final BlockEntityEntry<MafSensorBlockEntity> MAF_SENSOR = REGISTRATE
		.blockEntity("maf_sensor", MafSensorBlockEntity::new)
		.validBlocks(CEBlocks.MAF_SENSOR)
		.register();

	public static final BlockEntityEntry<HeatExchangerBlockEntity> HEAT_EXCHANGER = REGISTRATE
		.blockEntity("heat_exchanger", HeatExchangerBlockEntity::new)
		.validBlocks(CEBlocks.HEAT_EXCHANGER)
		.register();

	public static final BlockEntityEntry<GovernorBlockEntity> GOVERNOR = REGISTRATE
		.blockEntity("governor", GovernorBlockEntity::new)
		.validBlocks(CEBlocks.GOVERNOR)
		.register();

	public static final BlockEntityEntry<StarterBlockEntity> STARTER = REGISTRATE
		.blockEntity("starter", StarterBlockEntity::new)
		.visual(() -> StarterVisual::create, false)
		.validBlocks(CEBlocks.STARTER, CEBlocks.HEAVY_STARTER)
		.renderer(() -> StarterRenderer::new)
		.register();

	public static final BlockEntityEntry<BatteryBlockEntity> BATTERY = REGISTRATE
		.blockEntity("battery", BatteryBlockEntity::new)
		.validBlocks(CEBlocks.BATTERY, CEBlocks.DRY_CELL, CEBlocks.LARGE_BATTERY)
		.register();

	public static final BlockEntityEntry<AlternatorBlockEntity> ALTERNATOR = REGISTRATE
		.blockEntity("alternator", AlternatorBlockEntity::new)
		.visual(() -> AlternatorVisual::create, false)
		.validBlocks(CEBlocks.ALTERNATOR)
		.renderer(() -> AlternatorRenderer::new)
		.register();

	public static final BlockEntityEntry<CastFlywheelBlockEntity> CAST_FLYWHEEL = REGISTRATE
		.blockEntity("cast_flywheel", CastFlywheelBlockEntity::new)
		.visual(() -> CastFlywheelVisual::create, false)
		.validBlocks(CEBlocks.CAST_FLYWHEEL)
		.renderer(() -> EnginePartRenderers.CastFlywheel::new)
		.register();

	public static final BlockEntityEntry<CastFlywheelBlockEntity> DUAL_MASS_FLYWHEEL = REGISTRATE
		.blockEntity("dual_mass_flywheel", CastFlywheelBlockEntity::new)
		.visual(() -> DualMassFlywheelVisual::create, false)
		.validBlocks(CEBlocks.DUAL_MASS_FLYWHEEL)
		.renderer(() -> EnginePartRenderers.DualMassFlywheel::new)
		.register();

	/**
	 * The block entity of the flywheels a pack or an addon makes with {@code PartBlocks.flywheel}. None of this mod's own blocks
	 * is valid for it: an addon's blocks are added to it when the addon registers them.
	 */
	public static final BlockEntityEntry<CastFlywheelBlockEntity> DATA_FLYWHEEL = REGISTRATE
		.blockEntity("data_flywheel", CastFlywheelBlockEntity::new)
		.visual(() -> DataFlywheelVisual::create, false)
		.renderer(() -> KineticBlockEntityRenderer::new)
		.register();

	public static final BlockEntityEntry<DynoBlockEntity> DYNO = REGISTRATE
		.blockEntity("dyno", DynoBlockEntity::new)
		.validBlocks(CEBlocks.DYNO)
		.register();

	public static final BlockEntityEntry<BlackBoxBlockEntity> BLACK_BOX = REGISTRATE
		.blockEntity("black_box", BlackBoxBlockEntity::new)
		.validBlocks(CEBlocks.BLACK_BOX)
		.register();

	public static final BlockEntityEntry<SumpBlockEntity> SUMP = REGISTRATE
		.blockEntity("sump", SumpBlockEntity::new)
		.validBlocks(CEBlocks.SUMP)
		.register();

	/** Reboilers and Condensers: the parts of a refining column that hold tanks and take pipes. */
	public static final BlockEntityEntry<ColumnPortBlockEntity> COLUMN_PORT = REGISTRATE
		.blockEntity("column_port", ColumnPortBlockEntity::new)
		.validBlocks(CEBlocks.REBOILER, CEBlocks.CONDENSER)
		.register();

	/** The soot a Soot Trap has caught. */
	public static final BlockEntityEntry<SootTrapBlockEntity> SOOT_TRAP = REGISTRATE
		.blockEntity("soot_trap", SootTrapBlockEntity::new)
		.validBlocks(CEBlocks.SOOT_TRAP)
		.register();

	public static final BlockEntityEntry<RainCapBlockEntity> RAIN_CAP = REGISTRATE
		.blockEntity("rain_cap", RainCapBlockEntity::new)
		.validBlocks(CEBlocks.RAIN_CAP)
		.renderer(() -> RainCapRenderer::new)
		.register();

	public static final BlockEntityEntry<FramedBlueprintBlockEntity> FRAMED_BLUEPRINT = REGISTRATE
		.blockEntity("framed_blueprint", FramedBlueprintBlockEntity::new)
		.validBlocks(CEBlocks.FRAMED_BLUEPRINT)
		.register();

	public static final BlockEntityEntry<MakerPlateBlockEntity> MAKER_PLATE = REGISTRATE
		.blockEntity("maker_plate", MakerPlateBlockEntity::new)
		.validBlocks(CEBlocks.MAKER_PLATE)
		.renderer(() -> MakerPlateRenderer::new)
		.register();

	public static final BlockEntityEntry<TurbochargerBlockEntity> TURBOCHARGER = REGISTRATE
		.blockEntity("turbocharger", TurbochargerBlockEntity::new)
		.validBlocks(CEBlocks.TURBOCHARGER, CEBlocks.VARIABLE_TURBOCHARGER)
		.renderer(() -> TurbochargerRenderer::new)
		.register();

	public static final BlockEntityEntry<SuperchargerBlockEntity> SUPERCHARGER = REGISTRATE
		.blockEntity("supercharger", SuperchargerBlockEntity::new)
		.visual(() -> SuperchargerVisual::create, false)
		.validBlocks(CEBlocks.SUPERCHARGER, CEBlocks.VARIABLE_SUPERCHARGER)
		.renderer(() -> EnginePartRenderers.Supercharger::new)
		.register();

	public static final BlockEntityEntry<TransmissionBlockEntity> TRANSMISSION = REGISTRATE
		.blockEntity("transmission", TransmissionBlockEntity::new)
		.visual(() -> SplitShaftVisual::new, false)
		.validBlocks(CEBlocks.TRANSMISSION)
		.renderer(() -> SplitShaftRenderer::new)
		.register();

	/** The blocks Create puts in its own casings, as ours: a shaft or cog clad in Engine Casing. */
	public static final BlockEntityEntry<com.createengines.content.PaintedShaftBlockEntity> ENCASED_SHAFT = REGISTRATE
		.blockEntity("engine_cased_shaft", com.createengines.content.PaintedShaftBlockEntity::new)
		.visual(() -> ShaftVisual::new, false)
		.validBlocks(CEBlocks.ENGINE_CASED_SHAFT, CEBlocks.ENGINE_RIVETED_CASED_SHAFT, CEBlocks.ENGINE_LOUVRED_CASED_SHAFT,
			CEBlocks.ENGINE_WINDOW_CASED_SHAFT)
		.renderer(() -> ShaftRenderer::new)
		.register();

	public static final BlockEntityEntry<com.createengines.content.PaintedCogBlockEntity> ENCASED_COGWHEEL = REGISTRATE
		.blockEntity("engine_cased_cogwheel", com.createengines.content.PaintedCogBlockEntity::new)
		.visual(() -> EncasedCogVisual::small, false)
		.validBlocks(CEBlocks.ENGINE_CASED_COGWHEEL, CEBlocks.ENGINE_RIVETED_CASED_COGWHEEL,
			CEBlocks.ENGINE_LOUVRED_CASED_COGWHEEL, CEBlocks.ENGINE_WINDOW_CASED_COGWHEEL)
		.renderer(() -> EncasedCogRenderer::small)
		.register();

	public static final BlockEntityEntry<com.createengines.content.PaintedCogBlockEntity> ENCASED_LARGE_COGWHEEL = REGISTRATE
		.blockEntity("engine_cased_large_cogwheel", com.createengines.content.PaintedCogBlockEntity::new)
		.visual(() -> EncasedCogVisual::large, false)
		.validBlocks(CEBlocks.ENGINE_CASED_LARGE_COGWHEEL, CEBlocks.ENGINE_RIVETED_CASED_LARGE_COGWHEEL,
			CEBlocks.ENGINE_LOUVRED_CASED_LARGE_COGWHEEL, CEBlocks.ENGINE_WINDOW_CASED_LARGE_COGWHEEL)
		.renderer(() -> EncasedCogRenderer::large)
		.register();

	/** Holds only the dye of the casing on a plain manifold; see {@link com.createengines.content.airflow.PlainManifoldBlock}. */
	public static final BlockEntityEntry<com.createengines.content.airflow.ManifoldBlockEntity> MANIFOLD = REGISTRATE
		.blockEntity("manifold", com.createengines.content.airflow.ManifoldBlockEntity::new)
		.validBlocks(CEBlocks.INTAKE_MANIFOLD, CEBlocks.EXHAUST_MANIFOLD)
		.register();

	private CEBlockEntities() {
	}

	public static void register() {
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CYLINDER_HEAD.get(),
			(be, side) -> be.fluidCapability());
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROTOR_HOUSING.get(),
			(be, side) -> be.fluidCapability());
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SUMP.get(), (be, side) -> be.fluidCapability());
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COLUMN_PORT.get(), (be, side) -> be.fluidCapability());
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COLUMN_PORT.get(), (be, side) -> be.itemCapability());
		event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> JerryCanItem.handler(stack),
			CEItems.JERRY_CAN.get());
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SOOT_TRAP.get(), (be, side) -> be.itemCapability());
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ALTERNATOR.get(), (be, side) -> be.energyOutput());
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY.get(), (be, side) -> be.energyOutput());
		// Only named when ComputerCraft is loaded, so a game without it never touches a ComputerCraft class
		if (net.neoforged.fml.ModList.get()
			.isLoaded("computercraft"))
			com.createengines.compat.computercraft.ComputerCraftCompat.register(event);
	}
}
