package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.CESpriteShifts;
import com.createengines.content.EngineCasedCogwheelBlock;
import com.createengines.content.EngineCasedShaftBlock;
import com.createengines.content.EngineCasingBlock;
import com.createengines.content.Encasing;
import com.createengines.content.EngineGlow;
import com.createengines.content.airflow.HeatExchangerBlock;
import com.createengines.content.airflow.MafSensorBlock;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlock;
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.crankshaft.CrankThrowBlock;
import com.createengines.content.blackbox.BlackBoxBlock;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.dyno.DynoBlock;
import com.createengines.content.flywheel.CastFlywheelBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.muffler.MufflerBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.supercharger.VariableSuperchargerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.VariableTurbochargerBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;

import java.util.function.BiPredicate;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public final class CEBlocks {

	static {
		REGISTRATE.setCreativeTab(CECreativeTabs.MAIN);
	}

	/**
	 * What every block here starts from: no occlusion, since none is a full cube, and solid whatever its shape. A shaft, a
	 * cam or a pipe is too slight a shape to count as solid by itself, and water that flows onto a block that is not
	 * solid washes it away like a torch: an engine caught by a flood stops, it does not come apart.
	 */
	private static BlockBehaviour.Properties part(BlockBehaviour.Properties properties) {
		return properties.noOcclusion()
			.forceSolidOn();
	}

	/** A shaft part shows its animated model when bare and the shared casing model, turned onto its axis, when cased. */
	private static net.neoforged.neoforge.client.model.generators.ModelFile shaftModel(
		com.tterrag.registrate.providers.DataGenContext<net.minecraft.world.level.block.Block, ? extends net.minecraft.world.level.block.Block> c,
		com.tterrag.registrate.providers.RegistrateBlockstateProvider p, net.minecraft.world.level.block.state.BlockState s) {
		return s.getValue(com.createengines.content.Encasing.ENCASED)
			? p.models()
				.getExistingFile(p.modLoc("block/engine_casing/encased"))
			: AssetLookup.partialBaseModel(c, p);
	}

	public static final BlockEntry<CrankshaftBlock> CRANKSHAFT = REGISTRATE.block("crankshaft", CrankshaftBlock::new)
		.transform(CEBlocks::engineReadouts)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CrankThrowBlock> CRANK_THROW = REGISTRATE.block("crank_throw", CrankThrowBlock::new)
		.transform(CEBlocks::engineReadouts)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/**
	 * A Wankel rotor housing: a crankshaft link that is its own cylinder and head, with ports instead of valves. It
	 * takes a rotor, an injector and a spark plug by hand, like a head.
	 */
	public static final BlockEntry<RotorHousingBlock> ROTOR_HOUSING =
		REGISTRATE.block("rotor_housing", RotorHousingBlock::new)
				.transform(CEBlocks::engineReadouts)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE)
				.lightLevel(EngineGlow::light))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> {
				boolean injector = s.getValue(RotorHousingBlock.HAS_INJECTOR);
				boolean plug = s.getValue(RotorHousingBlock.HAS_SPARK_PLUG);
				if (injector && plug)
					return AssetLookup.partialBaseModel(c, p, "injector", "plug");
				return plug ? AssetLookup.partialBaseModel(c, p, "plug")
					: AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
			}))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<CylinderBlock> CYLINDER = REGISTRATE.block("cylinder", CylinderBlock::new)
		.transform(CEBlocks::casedPart)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> cylinderModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CylinderHeadBlock> CYLINDER_HEAD =
		REGISTRATE.block("cylinder_head", CylinderHeadBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE)
				.lightLevel(EngineGlow::light))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p, s -> {
				boolean injector = s.getValue(CylinderHeadBlock.HAS_INJECTOR);
				boolean plug = s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG);
				if (injector && plug)
					return AssetLookup.partialBaseModel(c, p, "injector", "plug");
				return plug ? AssetLookup.partialBaseModel(c, p, "plug")
					: AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
			}))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A brass, liner-bored cylinder: higher compression and less wall friction than the crude one. */
	public static final BlockEntry<CylinderBlock> PRECISION_CYLINDER =
		REGISTRATE.block("precision_cylinder", p -> new CylinderBlock(p, true))
				.transform(CEBlocks::casedPart)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> cylinderModel(c, p, s)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A four-valve head: it breathes better and lights the charge sooner than the crude one. */
	public static final BlockEntry<CylinderHeadBlock> PRECISION_CYLINDER_HEAD =
		REGISTRATE.block("precision_cylinder_head", p -> new CylinderHeadBlock(p, true))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.GOLD)
				.lightLevel(EngineGlow::light))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p, s -> {
				boolean injector = s.getValue(CylinderHeadBlock.HAS_INJECTOR);
				boolean plug = s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG);
				if (injector && plug)
					return AssetLookup.partialBaseModel(c, p, "injector", "plug");
				return plug ? AssetLookup.partialBaseModel(c, p, "plug")
					: AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
			}))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<CamshaftBlock> CAMSHAFT = REGISTRATE.block("camshaft", CamshaftBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CamLobeBlock> CAM_LOBE = REGISTRATE.block("cam_lobe", CamLobeBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<ManifoldBlock> INTAKE_MANIFOLD =
		REGISTRATE.block("intake_manifold", p -> new ManifoldBlock(p, PortKind.INTAKE))
				.transform(CEBlocks::casedPart)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<ThrottleBlock> THROTTLE =
		REGISTRATE.block("throttle", ThrottleBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<MafSensorBlock> MAF_SENSOR =
		REGISTRATE.block("maf_sensor", MafSensorBlock::new)
			.lang("MAF Sensor")
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<HeatExchangerBlock> HEAT_EXCHANGER =
		REGISTRATE.block("heat_exchanger", HeatExchangerBlock::new)
			.lang("Exhaust Heat Exchanger")
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE)
				.lightLevel(HeatExchangerBlock::light))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::heatExchangerBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<GovernorBlock> GOVERNOR =
		REGISTRATE.block("governor", GovernorBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<ManifoldBlock> EXHAUST_MANIFOLD =
		REGISTRATE.block("exhaust_manifold", p -> new ManifoldBlock(p, PortKind.EXHAUST))
				.transform(CEBlocks::casedPart)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<PortOpeningBlock> AIR_INTAKE =
		REGISTRATE.block("air_intake", p -> new PortOpeningBlock(p, PortKind.INTAKE))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<PortOpeningBlock> EXHAUST_OUTLET =
		REGISTRATE.block("exhaust_outlet", p -> new PortOpeningBlock(p, PortKind.EXHAUST))
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<StarterBlock> STARTER = REGISTRATE.block("starter", StarterBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<StarterBlock> HEAVY_STARTER = REGISTRATE
		.block("heavy_starter", p -> new StarterBlock(p, StarterBlock.HEAVY_STRENGTH, true))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.GOLD))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<BatteryBlock> BATTERY = REGISTRATE.block("battery", BatteryBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<AlternatorBlock> ALTERNATOR = REGISTRATE.block("alternator", AlternatorBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** An iron disc on the crankshaft's line: cheap rotating mass that evens out the engine's power strokes. */
	public static final BlockEntry<CastFlywheelBlock> CAST_FLYWHEEL =
		REGISTRATE.block("cast_flywheel", CastFlywheelBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** Set against an engine, it shows the engine's torque and power curves. */
	public static final BlockEntry<DynoBlock> DYNO = REGISTRATE.block("dyno", DynoBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Set against an engine, it keeps the last ten minutes of its speed, load and warmth and every time it changed state. */
	public static final BlockEntry<BlackBoxBlock> BLACK_BOX = REGISTRATE.block("black_box", BlackBoxBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_ORANGE))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<SumpBlock> SUMP = REGISTRATE.block("sump", SumpBlock::new)
		.transform(DisplaySource.displaySource(CEDisplaySources.SUMP_LEVELS))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<TurbochargerBlock> TURBOCHARGER =
		REGISTRATE.block("turbocharger", TurbochargerBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A turbocharger whose turbine has variable vanes: it spools sooner and chokes the exhaust less at full flow. */
	public static final BlockEntry<VariableTurbochargerBlock> VARIABLE_TURBOCHARGER =
		REGISTRATE.block("variable_turbocharger", VariableTurbochargerBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<SuperchargerBlock> SUPERCHARGER =
		REGISTRATE.block("supercharger", SuperchargerBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A supercharger with a variable-ratio drive and tight rotors: it boosts sooner, takes less power and runs cooler. */
	public static final BlockEntry<VariableSuperchargerBlock> VARIABLE_SUPERCHARGER =
		REGISTRATE.block("variable_supercharger", VariableSuperchargerBlock::new)
			.lang("Variable-Drive Supercharger")
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<IntercoolerBlock> INTERCOOLER =
		REGISTRATE.block("intercooler", IntercoolerBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A baffled canister in an exhaust run: it quiets the engine's exhaust note, bangs and pops, and costs it nothing. */
	public static final BlockEntry<MufflerBlock> MUFFLER =
		REGISTRATE.block("muffler", MufflerBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** Plain riveted plates to build a cowl round an engine with: it does nothing but hide the sprawl. */
	public static final BlockEntry<EngineCasingBlock> ENGINE_CASING =
			REGISTRATE.block("engine_casing", EngineCasingBlock::new)
				.initialProperties(SharedProperties::softMetal)
				.properties(p -> p.mapColor(MapColor.METAL))
				.transform(TagGen.pickaxeOnly())
				.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
				.onRegister(CreateRegistrate.connectedTextures(() -> new EncasedCTBehaviour(CESpriteShifts.ENGINE_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity(
					(block, cc) -> cc.makeCasing(block, CESpriteShifts.ENGINE_CASING)))
				.item()
				.transform(ModelGen.customItemModel())
				.register();

	/** A Create shaft in Engine Casing: use the casing on it, wrench it off. Drops the shaft when broken, as Create's do. */
	public static final BlockEntry<EngineCasedShaftBlock> ENGINE_CASED_SHAFT =
		REGISTRATE.block("engine_cased_shaft", p -> new EngineCasedShaftBlock(p, ENGINE_CASING::get))
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p,
				s -> p.models().getExistingFile(p.modLoc("block/engine_cased_shaft/block"))))
			.onRegister(CreateRegistrate.connectedTextures(() -> new EncasedCTBehaviour(CESpriteShifts.ENGINE_CASING)))
			.onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, CESpriteShifts.ENGINE_CASING,
				(s, f) -> f.getAxis() != s.getValue(RotatedPillarKineticBlock.AXIS))))
			.transform(EncasingRegistry.addVariantTo(AllBlocks.SHAFT))
			.loot((p, b) -> p.dropOther(b, AllBlocks.SHAFT.get()))
			.register();

	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_CASED_COGWHEEL =
		REGISTRATE.block("engine_cased_cogwheel", p -> new EngineCasedCogwheelBlock(p, false, ENGINE_CASING::get))
			.transform(CEBlocks.casedCogwheel("engine_cased_cogwheel"))
			.transform(EncasingRegistry.addVariantTo(AllBlocks.COGWHEEL))
			.loot((p, b) -> p.dropOther(b, AllBlocks.COGWHEEL.get()))
			.register();

	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_CASED_LARGE_COGWHEEL =
		REGISTRATE.block("engine_cased_large_cogwheel", p -> new EngineCasedCogwheelBlock(p, true, ENGINE_CASING::get))
			.transform(CEBlocks.casedCogwheel("engine_cased_large_cogwheel"))
			.transform(EncasingRegistry.addVariantTo(AllBlocks.LARGE_COGWHEEL))
			.loot((p, b) -> p.dropOther(b, AllBlocks.LARGE_COGWHEEL.get()))
			.register();

	public static final BlockEntry<TransmissionBlock> TRANSMISSION =
		REGISTRATE.block("transmission", TransmissionBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	private CEBlocks() {
	}

	/** The model a cylinder shows: its own, or a plain casing cube once clad. */
	private static net.neoforged.neoforge.client.model.generators.ModelFile cylinderModel(
		com.tterrag.registrate.providers.DataGenContext<Block, ? extends Block> c, RegistrateBlockstateProvider p,
		BlockState s) {
		return s.getValue(Encasing.ENCASED) ? p.models()
			.getExistingFile(p.modLoc("block/engine_casing/block")) : AssetLookup.partialBaseModel(c, p);
	}

	/** A part that Engine Casing covers whole: every face of it joins the casing beside it once clad. */
	private static <B extends Block, P> BlockBuilder<B, P> casedPart(BlockBuilder<B, P> builder) {
		return casedFaces(builder, (state, face) -> state.getValue(Encasing.ENCASED));
	}

	/** A shaft part that casing covers: the four faces along its axis join the casing beside them. */
	private static <B extends Block, P> BlockBuilder<B, P> casedShaftPart(BlockBuilder<B, P> builder) {
		return casedFaces(builder, (state, face) -> state.getValue(Encasing.ENCASED)
			&& face.getAxis() != state.getValue(RotatedPillarKineticBlock.AXIS));
	}

	private static <B extends Block, P> BlockBuilder<B, P> casedFaces(BlockBuilder<B, P> builder,
																	 BiPredicate<BlockState, Direction> faces) {
		return builder
			.onRegister(CreateRegistrate.connectedTextures(() -> new EncasedCTBehaviour(CESpriteShifts.ENGINE_CASING)))
			.onRegister(CreateRegistrate.casingConnectivity(
				(block, cc) -> cc.make(block, CESpriteShifts.ENGINE_CASING, faces)));
	}

	/** An encased cogwheel: Create's models, drawn with Engine Casing, and the casing's ends joining the casing beside. */
	private static <B extends EncasedCogwheelBlock, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<BlockBuilder<B, P>> casedCogwheel(
		String name) {
		return builder -> builder.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> {
				boolean top = s.getValue(EncasedCogwheelBlock.TOP_SHAFT);
				boolean bottom = s.getValue(EncasedCogwheelBlock.BOTTOM_SHAFT);
				String suffix = top && bottom ? "_top_bottom" : top ? "_top" : bottom ? "_bottom" : "";
				return p.models()
					.getExistingFile(p.modLoc("block/" + name + "/block" + suffix));
			}))
			.onRegister(CreateRegistrate.connectedTextures(
				() -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour(
					CESpriteShifts.ENGINE_CASING)))
			.onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, CESpriteShifts.ENGINE_CASING,
				(s, f) -> f.getAxis() == s.getValue(RotatedPillarKineticBlock.AXIS)
					&& !s.getValue(f.getAxisDirection() == Direction.AxisDirection.POSITIVE
						? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT))));
	}

	/** What a Display Link set against a crankshaft block of an engine can show. */
	private static <B extends Block, P> BlockBuilder<B, P> engineReadouts(BlockBuilder<B, P> builder) {
		return builder.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_STATUS))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_SPEED))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_POWER))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_LOAD))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_TEMPERATURE))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_OIL));
	}

	/** A pipe is a core with an arm model on each side it is connected through. */
	private static <T extends ManifoldBlock> void pipeBlockstate(DataGenContext<Block, T> context,
																 RegistrateBlockstateProvider provider) {
		String name = context.getName();
		var builder = provider.getMultipartBuilder(context.get());
		builder.part()
			.modelFile(provider.models()
				.getExistingFile(provider.modLoc("block/engine_casing/block")))
			.addModel()
			.condition(Encasing.ENCASED, true)
			.end();
		if (name.endsWith("_manifold"))
			// The pipes have a fitting of their own for a straight run, a bend and a tee, picked by which sides are open
			for (int mask = 0; mask < 1 << Direction.values().length; mask++) {
				var part = builder.part()
					.modelFile(pipeModel(provider, name, coreFor(mask)))
					.addModel();
				for (Direction direction : Direction.values())
					part.condition(ManifoldBlock.CONNECTIONS.get(direction), (mask >> direction.ordinal() & 1) == 1);
				part.condition(Encasing.ENCASED, false)
					.end();
			}
		else
			builder.part()
				.modelFile(pipeModel(provider, name, "core"))
				.addModel()
				.condition(Encasing.ENCASED, false)
				.end();
		for (Direction direction : Direction.values())
			builder.part()
				.modelFile(pipeModel(provider, name, "arm_" + direction.getName()))
				.addModel()
				.condition(ManifoldBlock.CONNECTIONS.get(direction), true)
				.condition(Encasing.ENCASED, false)
				.end();
	}

	/** A pipe that also glows by its heat level: an overlay of hot metal over the fins at kindled and at seething. */
	private static void heatExchangerBlockstate(DataGenContext<Block, HeatExchangerBlock> context,
												RegistrateBlockstateProvider provider) {
		pipeBlockstate(context, provider);
		var builder = provider.getMultipartBuilder(context.get());
		builder.part()
			.modelFile(pipeModel(provider, context.getName(), "glow_kindled"))
			.addModel()
			.condition(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED)
			.end();
		builder.part()
			.modelFile(pipeModel(provider, context.getName(), "glow_seething"))
			.addModel()
			.condition(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING)
			.end();
	}

	/**
	 * The core model for a pipe open on the sides in {@code mask} (bit per {@link Direction#ordinal()}): a plain
	 * coupling for a straight run, a cast elbow for a bend, a tee fitting across the run for a branch, and the bulged
	 * node for everything else.
	 */
	private static String coreFor(int mask) {
		int open = Integer.bitCount(mask);
		for (Direction.Axis axis : Direction.Axis.values()) {
			Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
			boolean through = (mask >> positive.ordinal() & 1) == 1 && (mask >> positive.getOpposite().ordinal() & 1) == 1;
			if (through && open == 2)
				return "core_straight_" + axis.getName();
			if (through && open == 3)
				return "core_tee_" + axis.getName();
		}
		return open == 2 ? "core_elbow" : "core";
	}

	private static ModelFile pipeModel(RegistrateBlockstateProvider provider, String name, String part) {
		return provider.models()
			.getExistingFile(provider.modLoc("block/" + name + "/" + part));
	}

	public static void register() {
	}
}
