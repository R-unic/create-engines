package com.createengines.registry;

import static com.createengines.registry.CEBlockstates.*;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.plate.MakerPlateBlock;
import com.createengines.content.raincap.RainCapBlock;
import com.createengines.content.Encasing;
import com.createengines.content.EngineGlow;
import com.createengines.content.airflow.HeatExchangerBlock;
import com.createengines.content.airflow.MafSensorBlock;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlock;
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.engine.sim.BatteryTier;
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
import com.createengines.content.airfilter.AirFilterBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.muffler.MufflerBlock;
import com.createengines.content.muffler.SootTrapBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.refinery.ColumnPortBlock;
import com.createengines.content.refinery.ColumnSectionBlock;
import com.createengines.content.refinery.FlareStackBlock;
import com.createengines.content.radiator.RadiatorBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.supercharger.VariableSuperchargerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.VariableTurbochargerBlock;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

public final class CEBlocks {

	static {
		REGISTRATE.setCreativeTab(CECreativeTabs.MAIN);
	}

	public static final BlockEntry<CrankshaftBlock> CRANKSHAFT = REGISTRATE.block("crankshaft", CrankshaftBlock::new)
		.transform(CEBlockstates::engineReadouts)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlockstates::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CrankThrowBlock> CRANK_THROW = REGISTRATE.block("crank_throw", CrankThrowBlock::new)
		.transform(CEBlockstates::engineReadouts)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlockstates::casedShaftPart)
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
				.transform(CEBlockstates::engineReadouts)
			.loot(CEBlockstates::keepsWear)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE)
				.lightLevel(EngineGlow::light))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> {
				boolean injector = s.getValue(RotorHousingBlock.HAS_INJECTOR);
				boolean plug = s.getValue(RotorHousingBlock.HAS_SPARK_PLUG);
				boolean coil = plug && s.getValue(RotorHousingBlock.HAS_COIL);
				if (coil)
					return injector ? AssetLookup.partialBaseModel(c, p, "injector", "plug", "coil")
						: AssetLookup.partialBaseModel(c, p, "plug", "coil");
				if (injector && plug)
					return AssetLookup.partialBaseModel(c, p, "injector", "plug");
				return plug ? AssetLookup.partialBaseModel(c, p, "plug")
					: AssetLookup.partialBaseModel(c, p, injector ? "injector" : "");
			}, Encasing.WINDOWED, Encasing.STYLE))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<CylinderBlock> CYLINDER = REGISTRATE.block("cylinder", CylinderBlock::new)
		.transform(CEBlockstates::casedPart)
		.loot(CEBlockstates::keepsWear)
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
				.transform(CEBlockstates::casedPart)
			.loot(CEBlockstates::keepsWear)
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
		.transform(CEBlockstates::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CamLobeBlock> CAM_LOBE = REGISTRATE.block("cam_lobe", CamLobeBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.transform(CEBlockstates::casedShaftPart)
			.blockstate((c, p) -> axisIgnoringPaint(c, p, s -> shaftModel(c, p, s)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<com.createengines.content.airflow.PlainManifoldBlock> INTAKE_MANIFOLD =
		REGISTRATE.block("intake_manifold", p -> new com.createengines.content.airflow.PlainManifoldBlock(p, PortKind.INTAKE))
				.transform(CEBlockstates::casedPart)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlockstates::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<ThrottleBlock> THROTTLE =
		REGISTRATE.block("throttle", ThrottleBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlockstates::pipeBlockstate)
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
			.blockstate(CEBlockstates::pipeBlockstate)
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
			.blockstate(CEBlockstates::heatExchangerBlockstate)
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
				.transform(CEBlockstates::casedPart)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> part(p)
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlockstates::pipeBlockstate)
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
		.loot(CEBlockstates::keepsWear)
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlockstates::batteryBlockstate)
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Half a battery's capacity and proof against the cold: 100 sparks, joins into a bank with any other battery. */
	public static final BlockEntry<BatteryBlock> DRY_CELL = REGISTRATE
		.block("dry_cell", p -> new BatteryBlock(p, BatteryTier.DRY_CELL))
		.loot(CEBlockstates::keepsWear)
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_ORANGE))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlockstates::batteryBlockstate)
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Four batteries in one block: 800 sparks, but the cold holds back up to three quarters of it. The bank for an electric motor. */
	public static final BlockEntry<BatteryBlock> LARGE_BATTERY = REGISTRATE
		.block("large_battery", p -> new BatteryBlock(p, BatteryTier.LARGE))
		.loot(CEBlockstates::keepsWear)
		.transform(DisplaySource.displaySource(CEDisplaySources.BATTERY_CHARGE))
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_GRAY))
		.transform(TagGen.pickaxeOnly())
		.blockstate(CEBlockstates::batteryBlockstate)
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

	/** A closed coolant loop that gives an engine's heat to the air, and warms a basin on top of it. */
	public static final BlockEntry<RadiatorBlock> RADIATOR = REGISTRATE.block("radiator", RadiatorBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> part(p)
			.mapColor(MapColor.COLOR_ORANGE))
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
			.loot(CEBlockstates::keepsWear)
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
			.loot(CEBlockstates::keepsWear)
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

	/** Cleans the air an engine breathes, in packs with Pollution of the Realms. Always registered, so no world loses a block. */
	public static final BlockEntry<AirFilterBlock> AIR_FILTER =
		REGISTRATE.block("air_filter", AirFilterBlock::new)
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

	static {
		// here, so the casings are registered between the soot trap and the transmission
		CECasingBlocks.register();
	}

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

	public static void register() {
	}
}
