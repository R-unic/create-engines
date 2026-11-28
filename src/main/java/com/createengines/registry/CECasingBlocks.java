package com.createengines.registry;

import static com.createengines.registry.CEBlockstates.*;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.CESpriteShifts;
import com.createengines.content.CasingStyle;
import com.createengines.content.EngineCasedCogwheelBlock;
import com.createengines.content.EngineCasedShaftBlock;
import com.createengines.content.EngineCasingBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.ModelGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.material.MapColor;

/** The engine casing in its styles, and the shafts and cogwheels clad in it. Registered from the middle of
 * {@link CEBlocks}, which keeps the order the creative tab lists them in. */
public final class CECasingBlocks {

	private CECasingBlocks() {
	}

	/** Registers the casings, where {@link CEBlocks} calls it. */
	static void register() {
	}

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
			.transform(CEBlockstates.casedCogwheel("engine_cased_cogwheel"))
			.transform(EncasingRegistry.addVariantTo(AllBlocks.COGWHEEL))
			.loot((p, b) -> p.dropOther(b, AllBlocks.COGWHEEL.get()))
			.register();

	public static final BlockEntry<EngineCasedCogwheelBlock> ENGINE_CASED_LARGE_COGWHEEL =
		REGISTRATE.block("engine_cased_large_cogwheel", p -> new EngineCasedCogwheelBlock(p, true, ENGINE_CASING::get))
			.transform(CEBlockstates.casedCogwheel("engine_cased_large_cogwheel"))
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
}
