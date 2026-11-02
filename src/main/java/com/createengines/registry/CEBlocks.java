package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

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
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.VariableTurbochargerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public final class CEBlocks {

	static {
		REGISTRATE.setCreativeTab(CECreativeTabs.MAIN);
	}

	public static final BlockEntry<CrankshaftBlock> CRANKSHAFT = REGISTRATE.block("crankshaft", CrankshaftBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CrankThrowBlock> CRANK_THROW = REGISTRATE.block("crank_throw", CrankThrowBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/**
	 * A Wankel rotor housing: a crankshaft link that is its own cylinder and head, with ports instead of valves. It
	 * takes a rotor, an injector and a spark plug by hand, like a head.
	 */
	public static final BlockEntry<RotorHousingBlock> ROTOR_HOUSING =
		REGISTRATE.block("rotor_housing", RotorHousingBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_ORANGE))
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
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CylinderHeadBlock> CYLINDER_HEAD =
		REGISTRATE.block("cylinder_head", CylinderHeadBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_ORANGE))
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
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** A four-valve head: it breathes better and lights the charge sooner than the crude one. */
	public static final BlockEntry<CylinderHeadBlock> PRECISION_CYLINDER_HEAD =
		REGISTRATE.block("precision_cylinder_head", p -> new CylinderHeadBlock(p, true))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.GOLD))
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
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<CamLobeBlock> CAM_LOBE = REGISTRATE.block("cam_lobe", CamLobeBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<ManifoldBlock> INTAKE_MANIFOLD =
		REGISTRATE.block("intake_manifold", p -> new ManifoldBlock(p, PortKind.INTAKE))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<ThrottleBlock> THROTTLE =
		REGISTRATE.block("throttle", ThrottleBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.GOLD))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<ManifoldBlock> EXHAUST_MANIFOLD =
		REGISTRATE.block("exhaust_manifold", p -> new ManifoldBlock(p, PortKind.EXHAUST))
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<PortOpeningBlock> AIR_INTAKE =
		REGISTRATE.block("air_intake", p -> new PortOpeningBlock(p, PortKind.INTAKE))
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
				s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<StarterBlock> STARTER = REGISTRATE.block("starter", StarterBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.directionalBlockIgnoresWaterlogged(c, p,
			s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<BatteryBlock> BATTERY = REGISTRATE.block("battery", BatteryBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<AlternatorBlock> ALTERNATOR = REGISTRATE.block("alternator", AlternatorBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	/** Set against an engine, it shows the engine's torque and power curves. */
	public static final BlockEntry<DynoBlock> DYNO = REGISTRATE.block("dyno", DynoBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	/** Set against an engine, it keeps the last ten minutes of its speed, load and warmth and every time it changed state. */
	public static final BlockEntry<BlackBoxBlock> BLACK_BOX = REGISTRATE.block("black_box", BlackBoxBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.COLOR_ORANGE))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<SumpBlock> SUMP = REGISTRATE.block("sump", SumpBlock::new)
		.initialProperties(SharedProperties::softMetal)
		.properties(p -> p.noOcclusion()
			.mapColor(MapColor.METAL))
		.transform(TagGen.pickaxeOnly())
		.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
		.item()
		.transform(ModelGen.customItemModel())
		.register();

	public static final BlockEntry<TurbochargerBlock> TURBOCHARGER =
		REGISTRATE.block("turbocharger", TurbochargerBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
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
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<IntercoolerBlock> INTERCOOLER =
		REGISTRATE.block("intercooler", IntercoolerBlock::new)
			.initialProperties(SharedProperties::copperMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_ORANGE))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<TransmissionBlock> TRANSMISSION =
		REGISTRATE.block("transmission", TransmissionBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.axisBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	private CEBlocks() {
	}

	/** A pipe is a core with an arm model on each side it is connected through. */
	private static <T extends ManifoldBlock> void pipeBlockstate(DataGenContext<Block, T> context,
																 RegistrateBlockstateProvider provider) {
		String name = context.getName();
		var builder = provider.getMultipartBuilder(context.get());
		if (name.endsWith("_manifold"))
			// The pipes have a fitting of their own for a straight run, a bend and a tee, picked by which sides are open
			for (int mask = 0; mask < 1 << Direction.values().length; mask++) {
				var part = builder.part()
					.modelFile(pipeModel(provider, name, coreFor(mask)))
					.addModel();
				for (Direction direction : Direction.values())
					part.condition(ManifoldBlock.CONNECTIONS.get(direction), (mask >> direction.ordinal() & 1) == 1);
				part.end();
			}
		else
			builder.part()
				.modelFile(pipeModel(provider, name, "core"))
				.addModel()
				.end();
		for (Direction direction : Direction.values())
			builder.part()
				.modelFile(pipeModel(provider, name, "arm_" + direction.getName()))
				.addModel()
				.condition(ManifoldBlock.CONNECTIONS.get(direction), true)
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
