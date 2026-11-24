package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.CESpriteShifts;
import com.createengines.content.CasingStyle;
import com.createengines.content.EngineCasedCogwheelBlock;
import com.createengines.content.EngineCasedShaftBlock;
import com.createengines.content.EngineCasingBlock;
import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.plate.MakerPlateBlock;
import com.createengines.content.raincap.RainCapBlock;
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
import com.createengines.engine.sim.BatteryTier;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.crankshaft.CrankThrowBlock;
import com.createengines.content.blackbox.BlackBoxBlock;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.dyno.DynoBlock;
import com.createengines.content.flywheel.CastFlywheelBlock;
import com.createengines.content.flywheel.DualMassFlywheelBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.muffler.MufflerBlock;
import com.createengines.content.muffler.SootTrapBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.refinery.ColumnPortBlock;
import com.createengines.content.refinery.ColumnSectionBlock;
import com.createengines.content.refinery.FlareStackBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.supercharger.VariableSuperchargerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.VariableTurbochargerBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
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
		if (s.getValue(com.createengines.content.Encasing.ENCASED))
			return p.models()
				.getExistingFile(p.modLoc(s.hasProperty(com.createengines.content.Encasing.WINDOWED)
					&& s.getValue(com.createengines.content.Encasing.WINDOWED) ? "block/engine_casing/window"
						: "block/" + casingModelFolder(s.getValue(Encasing.STYLE)) + "/encased"));
		return AssetLookup.partialBaseModel(c, p);
	}

	public static final BlockEntry<CrankshaftBlock> CRANKSHAFT = REGISTRATE.block("crankshaft", CrankshaftBlock::new)
		.transform(CEBlocks::engineReadouts)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
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
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
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
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> {
				boolean injector = s.getValue(RotorHousingBlock.HAS_INJECTOR);
				boolean plug = s.getValue(RotorHousingBlock.HAS_SPARK_PLUG);
				if (injector && plug)
					return AssetLookup.partialBaseModel(c, p, "injector", "plug");
				return plug ? AssetLookup.partialBaseModel(c, p, "plug")
					: AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
			}, Encasing.WINDOWED, Encasing.STYLE))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<CylinderBlock> CYLINDER = REGISTRATE.block("cylinder", CylinderBlock::new)
		.transform(CEBlocks::casedPart)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> directionalIgnoringPaint(c, p,
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
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p, s -> headModel(c, p, s)))
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
			.blockstate((c, p) -> directionalIgnoringPaint(c, p,
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
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p, s -> headModel(c, p, s)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<CamshaftBlock> CAMSHAFT = REGISTRATE.block("camshaft", CamshaftBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CamLobeBlock> CAM_LOBE = REGISTRATE.block("cam_lobe", CamLobeBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlocks::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<com.createengines.content.airflow.PlainManifoldBlock> INTAKE_MANIFOLD =
		REGISTRATE.block("intake_manifold", p -> new com.createengines.content.airflow.PlainManifoldBlock(p, PortKind.INTAKE))
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

	public static final BlockEntry<com.createengines.content.airflow.PlainManifoldBlock> EXHAUST_MANIFOLD =
		REGISTRATE.block("exhaust_manifold", p -> new com.createengines.content.airflow.PlainManifoldBlock(p, PortKind.EXHAUST))
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

	/** A hinged cap on the end of an exhaust outlet: it flaps with each pulse and drops shut when the engine stops. */
	public static final BlockEntry<RainCapBlock> RAIN_CAP = REGISTRATE.block("rain_cap", RainCapBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_GRAY))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** A brass plate screwed to an engine, with its name, its builder and the figures of its last measured run. */
	public static final BlockEntry<MakerPlateBlock> MAKER_PLATE = REGISTRATE.block("maker_plate", MakerPlateBlock::new)
		.lang("Maker's Plate")
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.GOLD))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** A frame that hangs a recorded blueprint on a wall as its drawing, with the figures from its Maker's Plate. */
	public static final BlockEntry<FramedBlueprintBlock> FRAMED_BLUEPRINT =
		REGISTRATE.block("framed_blueprint", FramedBlueprintBlock::new)
			.lang("Framed Blueprint")
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_BLUE))
			.transform(TagGen.pickaxeOnly())
				.blockstate((c, p) -> p.getVariantBuilder(c.get())
					.forAllStates(s -> {
						Direction facing = s.getValue(FramedBlueprintBlock.FACING);
						int mask = (s.getValue(FramedBlueprintBlock.JOIN_DOWN) ? 1 : 0) | (s.getValue(FramedBlueprintBlock.JOIN_LEFT) ? 2 : 0)
							| (s.getValue(FramedBlueprintBlock.JOIN_UP) ? 4 : 0) | (s.getValue(FramedBlueprintBlock.JOIN_RIGHT) ? 8 : 0);
						// The same turns the frame has always had: x then y, for the face it hangs on
						int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
						int y = facing.getAxis()
							.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
						return ConfiguredModel.builder()
							.modelFile(p.models()
								.getExistingFile(p.modLoc("block/framed_blueprint/c" + mask)))
							.rotationX(x)
							.rotationY(y)
							.build();
					}))
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
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlocks::batteryBlockstate)
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Half a battery's capacity and proof against the cold: 100 sparks, joins into a bank with any other battery. */
	public static final BlockEntry<BatteryBlock> DRY_CELL = REGISTRATE
		.block("dry_cell", p -> new BatteryBlock(p, BatteryTier.DRY_CELL))
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_ORANGE))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlocks::batteryBlockstate)
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Four batteries in one block: 800 sparks, but the cold holds back up to three quarters of it. The bank for an electric motor. */
	public static final BlockEntry<BatteryBlock> LARGE_BATTERY = REGISTRATE
		.block("large_battery", p -> new BatteryBlock(p, BatteryTier.LARGE))
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_GRAY))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlocks::batteryBlockstate)
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

	/**
	 * Two masses on a spring pack: a light one on the crank for quick revs, a heavy one behind it that smooths the network's
	 * torque once the engine is past the springs' resonance.
	 */
	public static final BlockEntry<DualMassFlywheelBlock> DUAL_MASS_FLYWHEEL =
		REGISTRATE.block("dual_mass_flywheel", DualMassFlywheelBlock::new)
			.lang("Dual-Mass Flywheel")
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

	/** The base of a refining column: takes the feed and the catalyst, and sits on a Blaze Burner or an Exhaust Heat Exchanger. */
	public static final BlockEntry<ColumnPortBlock> REBOILER =
		REGISTRATE.block("reboiler", p -> new ColumnPortBlock(p, com.createengines.engine.sim.ColumnShape.Part.REBOILER))
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** The body of a refining column, stacked on a Reboiler under a Condenser. */
	public static final BlockEntry<ColumnSectionBlock> COLUMN_SECTION =
		REGISTRATE.block("column_section", ColumnSectionBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> {
				// The body, lit or dark, and an arm on top or underneath for each side a diagonal neighbour is on
				var parts = p.getMultipartBuilder(c.get());
				for (boolean active : new boolean[] {false, true})
					parts.part()
						.modelFile(p.models()
							.getExistingFile(p.modLoc(active ? "block/column_section/active" : "block/column_section/block")))
						.addModel()
						.condition(ColumnSectionBlock.ACTIVE, active)
						.end();
				for (ColumnSectionBlock.Lean lean : ColumnSectionBlock.Lean.values()) {
					if (lean.direction() == null)
						continue;
					parts.part()
						.modelFile(p.models()
							.getExistingFile(p.modLoc("block/column_section/wedge")))
						.rotationY(southTurn(lean.direction()))
						.addModel()
						.condition(ColumnSectionBlock.LEAN_UP, lean)
						.end();
					// The brace is built for a lean to the south, which is away from the section it hangs from
					parts.part()
						.modelFile(p.models()
							.getExistingFile(p.modLoc("block/column_section/brace")))
						.rotationY(southTurn(lean.direction()
							.getOpposite()))
						.addModel()
						.condition(ColumnSectionBlock.LEAN_DOWN, lean)
						.end();
				}
			})
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** The top of a refining column: the product is piped off here. */
	public static final BlockEntry<ColumnPortBlock> CONDENSER =
		REGISTRATE.block("condenser", p -> new ColumnPortBlock(p, com.createengines.engine.sim.ColumnShape.Part.CONDENSER))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.mapColor(MapColor.COLOR_ORANGE)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** Degrees a model built facing south is turned to face another way. */
	private static int southTurn(Direction facing) {
		return switch (facing) {
			case WEST -> 90;
			case NORTH -> 180;
			case EAST -> 270;
			default -> 0;
		};
	}

	/** A chimney with a flame that burns while the column under it is working. Cosmetic. */
	public static final BlockEntry<FlareStackBlock> FLARE_STACK =
		REGISTRATE.block("flare_stack", FlareStackBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.COLOR_GRAY)
				.noOcclusion()
				.lightLevel(s -> s.getValue(FlareStackBlock.LIT) ? 12 : 0))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> p.getVariantBuilder(c.get())
				.forAllStates(s -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
					.modelFile(p.models()
						.getExistingFile(p.modLoc(s.getValue(FlareStackBlock.LIT) ? "block/flare_stack/lit"
							: "block/flare_stack/block")))
					.build()))
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

	/** A filter canister in an exhaust run that catches the soot a rich engine would send out of the pipe. */
	public static final BlockEntry<SootTrapBlock> SOOT_TRAP =
		REGISTRATE.block("soot_trap", SootTrapBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_BLACK))
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
				.blockstate((c, p) -> simpleIgnoringPaint(c, p, s -> AssetLookup.partialBaseModel(c, p)))
				.onRegister(CreateRegistrate.connectedTextures(() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.ENGINE_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity(
					(block, cc) -> cc.makeCasing(block, CESpriteShifts.ENGINE_CASING)))
				.item()
				.transform(ModelGen.customItemModel())
				.register();

	/** Engine Casing with a rivet in every corner of every plate: the plain casing, dressed up. Joins its own kind. */
	public static final BlockEntry<EngineCasingBlock> ENGINE_RIVETED_CASING =
			REGISTRATE.block("engine_riveted_casing", EngineCasingBlock::new)
				.lang("Riveted Engine Casing")
				.initialProperties(SharedProperties::softMetal)
				.properties(p -> p.mapColor(MapColor.METAL))
				.transform(TagGen.pickaxeOnly())
				.blockstate((c, p) -> simpleIgnoringPaint(c, p, s -> AssetLookup.partialBaseModel(c, p)))
				.onRegister(CreateRegistrate.connectedTextures(
					() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.ENGINE_RIVETED_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity(
					(block, cc) -> cc.makeCasing(block, CESpriteShifts.ENGINE_RIVETED_CASING)))
				.item()
				.transform(ModelGen.customItemModel())
				.register();

	/** Engine Casing slatted like a vent, to let an engine bay look as if it breathes. Joins its own kind. */
	public static final BlockEntry<EngineCasingBlock> ENGINE_LOUVRED_CASING =
			REGISTRATE.block("engine_louvred_casing", EngineCasingBlock::new)
				.lang("Louvred Engine Casing")
				.initialProperties(SharedProperties::softMetal)
				.properties(p -> p.mapColor(MapColor.METAL))
				.transform(TagGen.pickaxeOnly())
				.blockstate((c, p) -> simpleIgnoringPaint(c, p, s -> AssetLookup.partialBaseModel(c, p)))
				.onRegister(CreateRegistrate.connectedTextures(
					() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.ENGINE_LOUVRED_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity(
					(block, cc) -> cc.makeCasing(block, CESpriteShifts.ENGINE_LOUVRED_CASING)))
				.item()
				.transform(ModelGen.customItemModel())
				.register();

	/**
	 * Engine Casing with a pane of glass in each face. As a block it is a plain glazed cube; used on a crankshaft or a
	 * camshaft part it clads it with the part still showing through the glass, turning.
	 */
	public static final BlockEntry<EngineCasingBlock> ENGINE_WINDOW_CASING =
			REGISTRATE.block("engine_window_casing", EngineCasingBlock::new)
				.lang("Window Engine Casing")
				.initialProperties(SharedProperties::softMetal)
				.properties(p -> part(p)
					.mapColor(MapColor.METAL))
				.transform(TagGen.pickaxeOnly())
				.blockstate((c, p) -> simpleIgnoringPaint(c, p, s -> AssetLookup.partialBaseModel(c, p)))
				.onRegister(CreateRegistrate.connectedTextures(
					() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.ENGINE_WINDOW_CASING)))
				.onRegister(CreateRegistrate.casingConnectivity(
					(block, cc) -> cc.makeCasing(block, CESpriteShifts.ENGINE_WINDOW_CASING)))
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
			.blockstate((c, p) -> axisIgnoringPaint(c, p,
				s -> p.models().getExistingFile(p.modLoc("block/engine_cased_shaft/block"))))
			.onRegister(CreateRegistrate.connectedTextures(() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.ENGINE_CASING)))
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

	/*
	 * Every other casing encases shafts and cogs too: one block per casing and part, so Create's encasing (use the casing
	 * on the part, wrench it off) finds the right one by the casing in hand. They share the cased shaft's and cogs' block
	 * entities. The riveted and louvred ones use the plate on the casing faces; the window one shows the part through
	 * its glass.
	 */
	public static final BlockEntry<EngineCasedShaftBlock> ENGINE_RIVETED_CASED_SHAFT =
		casedShaft("engine_riveted_cased_shaft", CasingStyle.RIVETED, ENGINE_RIVETED_CASING);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_RIVETED_CASED_COGWHEEL =
		casedCogwheelBlock("engine_riveted_cased_cogwheel", CasingStyle.RIVETED, ENGINE_RIVETED_CASING, false,
			AllBlocks.COGWHEEL);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_RIVETED_CASED_LARGE_COGWHEEL =
		casedCogwheelBlock("engine_riveted_cased_large_cogwheel", CasingStyle.RIVETED, ENGINE_RIVETED_CASING, true,
			AllBlocks.LARGE_COGWHEEL);
	public static final BlockEntry<EngineCasedShaftBlock> ENGINE_LOUVRED_CASED_SHAFT =
		casedShaft("engine_louvred_cased_shaft", CasingStyle.LOUVRED, ENGINE_LOUVRED_CASING);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_LOUVRED_CASED_COGWHEEL =
		casedCogwheelBlock("engine_louvred_cased_cogwheel", CasingStyle.LOUVRED, ENGINE_LOUVRED_CASING, false,
			AllBlocks.COGWHEEL);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_LOUVRED_CASED_LARGE_COGWHEEL =
		casedCogwheelBlock("engine_louvred_cased_large_cogwheel", CasingStyle.LOUVRED, ENGINE_LOUVRED_CASING, true,
			AllBlocks.LARGE_COGWHEEL);
	public static final BlockEntry<EngineCasedShaftBlock> ENGINE_WINDOW_CASED_SHAFT =
		casedShaft("engine_window_cased_shaft", CasingStyle.WINDOW, ENGINE_WINDOW_CASING);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_WINDOW_CASED_COGWHEEL =
		casedCogwheelBlock("engine_window_cased_cogwheel", CasingStyle.WINDOW, ENGINE_WINDOW_CASING, false,
			AllBlocks.COGWHEEL);
	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_WINDOW_CASED_LARGE_COGWHEEL =
		casedCogwheelBlock("engine_window_cased_large_cogwheel", CasingStyle.WINDOW, ENGINE_WINDOW_CASING, true,
			AllBlocks.LARGE_COGWHEEL);

	public static final BlockEntry<TransmissionBlock> TRANSMISSION =
		REGISTRATE.block("transmission", TransmissionBlock::new)
			.transform(DisplaySource.displaySource(CEDisplaySources.TRANSMISSION_GEAR))
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

	/**
	 * Create's axis blockstate, without a variant for every dye a block carries as state: the paint is a tint and changes
	 * no model, so listing it would make seventeen copies of each variant. A clad part keeps its dye in a block entity and
	 * has no such property, so for it this is Create's blockstate unchanged.
	 */
	private static <T extends Block> void axisIgnoringPaint(DataGenContext<Block, T> c, RegistrateBlockstateProvider p,
		java.util.function.Function<BlockState, net.neoforged.neoforge.client.model.generators.ModelFile> model,
		net.minecraft.world.level.block.state.properties.Property<?>... alsoIgnored) {
		var ignored = new java.util.ArrayList<net.minecraft.world.level.block.state.properties.Property<?>>(
			java.util.List.of(alsoIgnored));
		if (c.get().defaultBlockState().hasProperty(Encasing.PAINT))
			ignored.add(Encasing.PAINT);
		p.getVariantBuilder(c.get())
			.forAllStatesExcept(state -> {
				Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);
				return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
					.modelFile(model.apply(state))
					.rotationX(axis == Direction.Axis.Y ? 0 : 90)
					.rotationY(axis == Direction.Axis.X ? 90 : axis == Direction.Axis.Z ? 180 : 0)
					.build();
			}, ignored.toArray(new net.minecraft.world.level.block.state.properties.Property<?>[0]));
	}

	/** One model for every state, without a variant for every dye (see {@link #axisIgnoringPaint}). */
	private static <T extends Block> void simpleIgnoringPaint(DataGenContext<Block, T> c,
		RegistrateBlockstateProvider p,
		java.util.function.Function<BlockState, net.neoforged.neoforge.client.model.generators.ModelFile> model) {
		p.getVariantBuilder(c.get())
			.forAllStatesExcept(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
				.modelFile(model.apply(state))
				.build(), paintIgnored(c.get()));
	}

	/** Create's directional blockstate, without a variant for every dye (see {@link #axisIgnoringPaint}). */
	private static <T extends Block> void directionalIgnoringPaint(DataGenContext<Block, T> c,
		RegistrateBlockstateProvider p,
		java.util.function.Function<BlockState, net.neoforged.neoforge.client.model.generators.ModelFile> model) {
		p.getVariantBuilder(c.get())
			.forAllStatesExcept(state -> {
				Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
				return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
					.modelFile(model.apply(state))
					.rotationX(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90)
					.rotationY(facing.getAxis()
						.isVertical() ? 0 : (((int) facing.toYRot()) + 180) % 360)
					.build();
			}, paintIgnored(c.get()));
	}

	/** The dye property when a block carries it as state (the plain casings do), else nothing to ignore. */
	private static net.minecraft.world.level.block.state.properties.Property<?>[] paintIgnored(Block block) {
		return block.defaultBlockState().hasProperty(Encasing.PAINT)
			? new net.minecraft.world.level.block.state.properties.Property<?>[] { Encasing.PAINT }
			: new net.minecraft.world.level.block.state.properties.Property<?>[0];
	}

	/**
	 * A cylinder head's model for the parts fitted: an assist sits on an injector and a coil on a plug, so a state with either
	 * without what it sits on is not a real one (only a command makes it) and draws as the head without it.
	 */
	private static net.neoforged.neoforge.client.model.generators.ModelFile headModel(
		DataGenContext<Block, CylinderHeadBlock> c, RegistrateBlockstateProvider p, BlockState s) {
		boolean injector = s.getValue(CylinderHeadBlock.HAS_INJECTOR);
		boolean plug = s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG);
		boolean assist = injector && s.getValue(CylinderHeadBlock.HAS_ASSIST);
		boolean coil = plug && s.getValue(CylinderHeadBlock.HAS_COIL);
		if (assist)
			return plug ? (coil ? AssetLookup.partialBaseModel(c, p, "injector_assist", "plug", "coil")
				: AssetLookup.partialBaseModel(c, p, "injector_assist", "plug"))
				: AssetLookup.partialBaseModel(c, p, "injector_assist");
		if (injector && plug)
			return coil ? AssetLookup.partialBaseModel(c, p, "injector", "plug", "coil")
				: AssetLookup.partialBaseModel(c, p, "injector", "plug");
		if (plug)
			return coil ? AssetLookup.partialBaseModel(c, p, "plug", "coil") : AssetLookup.partialBaseModel(c, p, "plug");
		return AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
	}

	/** The model a cylinder shows: its own, or a plain casing cube once clad. */
	private static net.neoforged.neoforge.client.model.generators.ModelFile cylinderModel(
		com.tterrag.registrate.providers.DataGenContext<Block, ? extends Block> c, RegistrateBlockstateProvider p,
		BlockState s) {
		return s.getValue(Encasing.ENCASED) ? p.models()
			.getExistingFile(p.modLoc("block/" + casingModelFolder(s.getValue(Encasing.STYLE)) + "/block"))
			: AssetLookup.partialBaseModel(c, p);
	}

	/** The model folder of the casing block a plate is made of (plain casing keeps its original folder). */
	private static String casingModelFolder(CasingStyle style) {
		return style == CasingStyle.PLAIN ? "engine_casing" : "engine_" + style.getSerializedName() + "_casing";
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
			.onRegister(CreateRegistrate.connectedTextures(() -> CESpriteShifts.STYLED_CASING_BEHAVIOUR.get()))
			.onRegister(CreateRegistrate.casingConnectivity(
				(block, cc) -> cc.make(block, CESpriteShifts.ENGINE_CASING, faces)));
	}

	/** A Create shaft clad in one of the other casings: the same block as the plain cased shaft with its own plate. */
	private static BlockEntry<EngineCasedShaftBlock> casedShaft(String name, CasingStyle style,
		BlockEntry<EngineCasingBlock> casing) {
		return REGISTRATE.block(name, p -> new EngineCasedShaftBlock(p, casing::get))
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> axisIgnoringPaint(c, p,
				s -> p.models().getExistingFile(p.modLoc("block/" + name + "/block"))))
			.onRegister(CreateRegistrate.connectedTextures(
				() -> CESpriteShifts.CASING_BEHAVIOUR.apply(CESpriteShifts.forStyle(style))))
			.onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, CESpriteShifts.forStyle(style),
				(s, f) -> f.getAxis() != s.getValue(RotatedPillarKineticBlock.AXIS))))
			.transform(EncasingRegistry.addVariantTo(AllBlocks.SHAFT))
			.loot((p, b) -> p.dropOther(b, AllBlocks.SHAFT.get()))
			.register();
	}

	/** A Create cogwheel clad in one of the other casings (see {@link #casedShaft}). */
	private static <E extends Block & com.simibubi.create.content.decoration.encasing.EncasableBlock> BlockEntry<EngineCasedCogwheelBlock> casedCogwheelBlock(String name, CasingStyle style,
		BlockEntry<EngineCasingBlock> casing, boolean large, BlockEntry<E> cog) {
		return REGISTRATE.block(name, p -> new EngineCasedCogwheelBlock(p, large, casing::get))
			.transform(CEBlocks.casedCogwheel(name, style))
			.transform(EncasingRegistry.addVariantTo(cog))
			.loot((p, b) -> p.dropOther(b, cog.get()))
			.register();
	}

	private static <B extends EncasedCogwheelBlock, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<BlockBuilder<B, P>> casedCogwheel(
		String name) {
		return casedCogwheel(name, CasingStyle.PLAIN);
	}

	/** An encased cogwheel: Create's models, drawn with Engine Casing, and the casing's ends joining the casing beside. */
	private static <B extends EncasedCogwheelBlock, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<BlockBuilder<B, P>> casedCogwheel(
		String name, CasingStyle style) {
		return builder -> builder.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.mapColor(MapColor.METAL)
				.noOcclusion())
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> {
				boolean top = s.getValue(EncasedCogwheelBlock.TOP_SHAFT);
				boolean bottom = s.getValue(EncasedCogwheelBlock.BOTTOM_SHAFT);
				String suffix = top && bottom ? "_top_bottom" : top ? "_top" : bottom ? "_bottom" : "";
				return p.models()
					.getExistingFile(p.modLoc("block/" + name + "/block" + suffix));
			}))
			.onRegister(CreateRegistrate.connectedTextures(
				() -> CESpriteShifts.COG_BEHAVIOUR.apply(CESpriteShifts.forStyle(style))))
			.onRegister(CreateRegistrate.casingConnectivity((block, cc) -> cc.make(block, CESpriteShifts.forStyle(style),
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
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_OIL))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_BOOST))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_TORQUE))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_FAULT))
			.transform(DisplaySource.displaySource(CEDisplaySources.ENGINE_SMOOTHNESS));
	}

	/** A pipe is a core with an arm model on each side it is connected through. */
	private static <T extends ManifoldBlock> void pipeBlockstate(DataGenContext<Block, T> context,
																 RegistrateBlockstateProvider provider) {
		String name = context.getName();
		var builder = provider.getMultipartBuilder(context.get());
		for (CasingStyle style : new CasingStyle[] {CasingStyle.PLAIN, CasingStyle.RIVETED, CasingStyle.LOUVRED})
			builder.part()
				.modelFile(provider.models()
					.getExistingFile(provider.modLoc("block/" + casingModelFolder(style) + "/block")))
				.addModel()
				.condition(Encasing.ENCASED, true)
				.condition(Encasing.STYLE, style)
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

	/**
	 * The battery: the model for the way its neighbours join it (one of 32, {@code block_jNESWU} with a 1 for each joined side),
	 * and over it the lamp strip on each side's lid, dark and lit to the blockstate's charge. A side that is joined to another
	 * battery has no lid face, so it has no lamps.
	 */
	private static void batteryBlockstate(DataGenContext<Block, BatteryBlock> context, RegistrateBlockstateProvider provider) {
		var builder = provider.getMultipartBuilder(context.get());
		BooleanProperty[] joins = { BatteryBlock.NORTH, BatteryBlock.EAST, BatteryBlock.SOUTH, BatteryBlock.WEST, BatteryBlock.UP };
		for (int bits = 0; bits < 32; bits++) {
			StringBuilder name = new StringBuilder("j");
			for (int i = 0; i < joins.length; i++)
				name.append((bits >> i & 1) != 0 ? '1' : '0');
			var part = builder.part()
				.modelFile(AssetLookup.partialBaseModel(context, provider, name.toString()))
				.addModel();
			for (int i = 0; i < joins.length; i++)
				part.condition(joins[i], (bits >> i & 1) != 0);
			part.end();
		}
		String[] directions = { "north", "east", "south", "west" };
		for (int i = 0; i < 4; i++) {
			builder.part()
				.modelFile(AssetLookup.partialBaseModel(context, provider, "dark", directions[i]))
				.addModel()
				.condition(joins[i], false)
				.end();
			for (int stage = 1; stage <= BatteryChemistry.GAUGE_STAGES; stage++)
				builder.part()
					.modelFile(AssetLookup.partialBaseModel(context, provider, "gauge", String.valueOf(stage), directions[i]))
					.addModel()
					.condition(joins[i], false)
					.condition(BatteryBlock.CHARGE, stage)
					.end();
		}
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
