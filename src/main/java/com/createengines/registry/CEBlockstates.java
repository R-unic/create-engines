package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.CESpriteShifts;
import com.createengines.content.CasingStyle;
import com.createengines.content.EngineCasedCogwheelBlock;
import com.createengines.content.EngineCasedShaftBlock;
import com.createengines.content.EngineCasingBlock;
import com.createengines.content.Encasing;
import com.createengines.content.airflow.HeatExchangerBlock;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
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
import net.neoforged.neoforge.client.model.generators.ModelFile;

/** Block properties, blockstates, models and loot shared by the block registrations. */
final class CEBlockstates {

	private CEBlockstates() {
	}

	/**
	 * What every block here starts from: no occlusion, since none is a full cube, and solid whatever its shape. A shaft, a
	 * cam or a pipe is too slight a shape to count as solid by itself, and water that flows onto a block that is not
	 * solid washes it away like a torch: an engine caught by a flood stops, it does not come apart.
	 */
	static BlockBehaviour.Properties part(BlockBehaviour.Properties properties) {
		return properties.noOcclusion()
			.forceSolidOn();
	}

	/** A shaft part shows its animated model when bare and the shared casing model, turned onto its axis, when cased. */
	static net.neoforged.neoforge.client.model.generators.ModelFile shaftModel(
		com.tterrag.registrate.providers.DataGenContext<net.minecraft.world.level.block.Block, ? extends net.minecraft.world.level.block.Block> c,
		com.tterrag.registrate.providers.RegistrateBlockstateProvider p, net.minecraft.world.level.block.state.BlockState s) {
		if (s.getValue(com.createengines.content.Encasing.ENCASED))
			return p.models()
				.getExistingFile(p.modLoc(s.hasProperty(com.createengines.content.Encasing.WINDOWED)
					&& s.getValue(com.createengines.content.Encasing.WINDOWED) ? "block/engine_casing/window"
						: "block/" + casingModelFolder(s.getValue(Encasing.STYLE)) + "/encased"));
		return AssetLookup.partialBaseModel(c, p);
	}

	/** Degrees a model built facing south is turned to face another way. */
	static int southTurn(Direction facing) {
		return switch (facing) {
			case WEST -> 90;
			case NORTH -> 180;
			case EAST -> 270;
			default -> 0;
		};
	}

	/**
	 * Create's axis blockstate, without a variant for every dye a block carries as state: the paint is a tint and changes
	 * no model, so listing it would make seventeen copies of each variant. A clad part keeps its dye in a block entity and
	 * has no such property, so for it this is Create's blockstate unchanged.
	 */
	static <T extends Block> void axisIgnoringPaint(DataGenContext<Block, T> c, RegistrateBlockstateProvider p,
		java.util.function.Function<BlockState, net.neoforged.neoforge.client.model.generators.ModelFile> model,
		net.minecraft.world.level.block.state.properties.Property<?>... alsoIgnored) {
		var ignored = new java.util.ArrayList<net.minecraft.world.level.block.state.properties.Property<?>>(
			java.util.List.of(alsoIgnored));
		ignored.addAll(java.util.List.of(paintIgnored(c.get())));
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
	static <T extends Block> void simpleIgnoringPaint(DataGenContext<Block, T> c,
		RegistrateBlockstateProvider p,
		java.util.function.Function<BlockState, net.neoforged.neoforge.client.model.generators.ModelFile> model) {
		p.getVariantBuilder(c.get())
			.forAllStatesExcept(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
				.modelFile(model.apply(state))
				.build(), paintIgnored(c.get()));
	}

	/** Create's directional blockstate, without a variant for every dye (see {@link #axisIgnoringPaint}). */
	static <T extends Block> void directionalIgnoringPaint(DataGenContext<Block, T> c,
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

	/**
	 * The properties no block model depends on, so the blockstate file need not list them: the dye, when a block carries it as
	 * state (the plain casings do), and which piston, rotor and rings are fitted, which only the renderer draws.
	 */
	static net.minecraft.world.level.block.state.properties.Property<?>[] paintIgnored(Block block) {
		BlockState state = block.defaultBlockState();
		return java.util.stream.Stream.<net.minecraft.world.level.block.state.properties.Property<?>>of(Encasing.PAINT,
				CylinderBlock.COMPRESSION, CylinderBlock.RINGS, RotorHousingBlock.COMPRESSION)
			.filter(state::hasProperty)
			.toArray(net.minecraft.world.level.block.state.properties.Property<?>[]::new);
	}

	/**
	 * A cylinder head's model for the parts fitted: an assist sits on an injector and a coil on a plug, so a state with either
	 * without what it sits on is not a real one (only a command makes it) and draws as the head without it.
	 */
	static net.neoforged.neoforge.client.model.generators.ModelFile headModel(
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
	static net.neoforged.neoforge.client.model.generators.ModelFile cylinderModel(
		com.tterrag.registrate.providers.DataGenContext<Block, ? extends Block> c, RegistrateBlockstateProvider p,
		BlockState s) {
		return s.getValue(Encasing.ENCASED) ? p.models()
			.getExistingFile(p.modLoc("block/" + casingModelFolder(s.getValue(Encasing.STYLE)) + "/block"))
			: AssetLookup.partialBaseModel(c, p);
	}

	/** The model folder of the casing block a plate is made of (plain casing keeps its original folder). */
	static String casingModelFolder(CasingStyle style) {
		return style == CasingStyle.PLAIN ? "engine_casing" : "engine_" + style.getSerializedName() + "_casing";
	}

	/** A part that Engine Casing covers whole: every face of it joins the casing beside it once clad. */
	static <B extends Block, P> BlockBuilder<B, P> casedPart(BlockBuilder<B, P> builder) {
		return casedFaces(builder, (state, face) -> state.getValue(Encasing.ENCASED));
	}

	/** A shaft part that casing covers: the four faces along its axis join the casing beside them. */
	static <B extends Block, P> BlockBuilder<B, P> casedShaftPart(BlockBuilder<B, P> builder) {
		return casedFaces(builder, (state, face) -> state.getValue(Encasing.ENCASED)
			&& face.getAxis() != state.getValue(RotatedPillarKineticBlock.AXIS));
	}

	static <B extends Block, P> BlockBuilder<B, P> casedFaces(BlockBuilder<B, P> builder,
																	 BiPredicate<BlockState, Direction> faces) {
		return builder
			.onRegister(CreateRegistrate.connectedTextures(() -> CESpriteShifts.STYLED_CASING_BEHAVIOUR.get()))
			.onRegister(CreateRegistrate.casingConnectivity(
				(block, cc) -> cc.make(block, CESpriteShifts.ENGINE_CASING, faces)));
	}

	/** A Create shaft clad in one of the other casings: the same block as the plain cased shaft with its own plate. */
	static BlockEntry<EngineCasedShaftBlock> casedShaft(String name, CasingStyle style,
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
	static <E extends Block & com.simibubi.create.content.decoration.encasing.EncasableBlock> BlockEntry<EngineCasedCogwheelBlock> casedCogwheelBlock(String name, CasingStyle style,
		BlockEntry<EngineCasingBlock> casing, boolean large, BlockEntry<E> cog) {
		return REGISTRATE.block(name, p -> new EngineCasedCogwheelBlock(p, large, casing::get))
			.transform(CEBlockstates.casedCogwheel(name, style))
			.transform(EncasingRegistry.addVariantTo(cog))
			.loot((p, b) -> p.dropOther(b, cog.get()))
			.register();
	}

	static <B extends EncasedCogwheelBlock, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<BlockBuilder<B, P>> casedCogwheel(
		String name) {
		return casedCogwheel(name, CasingStyle.PLAIN);
	}

	/** An encased cogwheel: Create's models, drawn with Engine Casing, and the casing's ends joining the casing beside. */
	static <B extends EncasedCogwheelBlock, P> com.tterrag.registrate.util.nullness.NonNullUnaryOperator<BlockBuilder<B, P>> casedCogwheel(
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
	static <B extends Block, P> BlockBuilder<B, P> engineReadouts(BlockBuilder<B, P> builder) {
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
	static <T extends ManifoldBlock> void pipeBlockstate(DataGenContext<Block, T> context,
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
	/** A block that drops itself, its item keeping what the wear mode has done to it (see {@code CEComponents.WEAR} and {@code FAILED}). */
	static <B extends Block> void keepsWear(com.tterrag.registrate.providers.loot.RegistrateBlockLootTables tables, B block) {
		tables.add(block, net.minecraft.world.level.storage.loot.LootTable.lootTable()
			.withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
				.setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
				.when(net.minecraft.world.level.storage.loot.predicates.ExplosionCondition.survivesExplosion())
				.add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block)
					.apply(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction
						.copyComponents(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction.Source.BLOCK_ENTITY)
						.include(CEComponents.WEAR.get())
						.include(CEComponents.FAILED.get())))));
	}

	static void batteryBlockstate(DataGenContext<Block, BatteryBlock> context, RegistrateBlockstateProvider provider) {
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
	static void heatExchangerBlockstate(DataGenContext<Block, HeatExchangerBlock> context,
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
	static String coreFor(int mask) {
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

	static ModelFile pipeModel(RegistrateBlockstateProvider provider, String name, String part) {
		return provider.models()
			.getExistingFile(provider.modLoc("block/" + name + "/" + part));
	}
}
