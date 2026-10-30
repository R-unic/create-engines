package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.airflow.LambdaSensorBlock;
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
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.limiter.RevLimiterBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.turbo.TurbochargerBlock;
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

	public static final BlockEntry<LambdaSensorBlock> LAMBDA_SENSOR =
		REGISTRATE.block("lambda_sensor", LambdaSensorBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.COLOR_GRAY))
			.transform(TagGen.pickaxeOnly())
			.blockstate(CEBlocks::pipeBlockstate)
			.item()
			.transform(ModelGen.customItemModel())
			.register();

	public static final BlockEntry<RevLimiterBlock> REV_LIMITER =
		REGISTRATE.block("rev_limiter", RevLimiterBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(p -> p.noOcclusion()
				.mapColor(MapColor.METAL))
			.transform(TagGen.pickaxeOnly())
			.blockstate((c, p) -> BlockStateGen.simpleBlock(c, p, s -> AssetLookup.partialBaseModel(c, p)))
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

	private static ModelFile pipeModel(RegistrateBlockstateProvider provider, String name, String part) {
		return provider.models()
			.getExistingFile(provider.modLoc("block/" + name + "/" + part));
	}

	public static void register() {
	}
}
