package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.EnginePartRenderers;
import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.alternator.AlternatorVisual;
import com.createengines.content.dyno.DynoBlockEntity;
import com.createengines.content.flywheel.CastFlywheelBlockEntity;
import com.createengines.content.flywheel.CastFlywheelVisual;
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
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.supercharger.SuperchargerVisual;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.content.turbo.TurbochargerRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
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
		.validBlocks(CEBlocks.STARTER)
		.renderer(() -> StarterRenderer::new)
		.register();

	public static final BlockEntityEntry<BatteryBlockEntity> BATTERY = REGISTRATE
		.blockEntity("battery", BatteryBlockEntity::new)
		.validBlocks(CEBlocks.BATTERY)
		.register();

	public static final BlockEntityEntry<AlternatorBlockEntity> ALTERNATOR = REGISTRATE
		.blockEntity("alternator", AlternatorBlockEntity::new)
		.visual(() -> AlternatorVisual::create, false)
		.validBlocks(CEBlocks.ALTERNATOR)
		.renderer(() -> KineticBlockEntityRenderer::new)
		.register();

	public static final BlockEntityEntry<CastFlywheelBlockEntity> CAST_FLYWHEEL = REGISTRATE
		.blockEntity("cast_flywheel", CastFlywheelBlockEntity::new)
		.visual(() -> CastFlywheelVisual::create, false)
		.validBlocks(CEBlocks.CAST_FLYWHEEL)
		.renderer(() -> KineticBlockEntityRenderer::new)
		.register();

	public static final BlockEntityEntry<DynoBlockEntity> DYNO = REGISTRATE
		.blockEntity("dyno", DynoBlockEntity::new)
		.validBlocks(CEBlocks.DYNO)
		.register();

	public static final BlockEntityEntry<SumpBlockEntity> SUMP = REGISTRATE
		.blockEntity("sump", SumpBlockEntity::new)
		.validBlocks(CEBlocks.SUMP)
		.register();

	public static final BlockEntityEntry<TurbochargerBlockEntity> TURBOCHARGER = REGISTRATE
		.blockEntity("turbocharger", TurbochargerBlockEntity::new)
		.validBlocks(CEBlocks.TURBOCHARGER, CEBlocks.VARIABLE_TURBOCHARGER)
		.renderer(() -> TurbochargerRenderer::new)
		.register();

	public static final BlockEntityEntry<SuperchargerBlockEntity> SUPERCHARGER = REGISTRATE
		.blockEntity("supercharger", SuperchargerBlockEntity::new)
		.visual(() -> SuperchargerVisual::create, false)
		.validBlocks(CEBlocks.SUPERCHARGER)
		.renderer(() -> KineticBlockEntityRenderer::new)
		.register();

	public static final BlockEntityEntry<TransmissionBlockEntity> TRANSMISSION = REGISTRATE
		.blockEntity("transmission", TransmissionBlockEntity::new)
		.visual(() -> SplitShaftVisual::new, false)
		.validBlocks(CEBlocks.TRANSMISSION)
		.renderer(() -> SplitShaftRenderer::new)
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
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ALTERNATOR.get(), (be, side) -> be.energyOutput());
	}
}
