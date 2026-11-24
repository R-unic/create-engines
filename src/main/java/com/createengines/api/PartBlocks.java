package com.createengines.api;

import java.util.Optional;
import java.util.function.Supplier;

import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.flywheel.DataFlywheelBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEDataMaps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/**
 * How an addon adds a cylinder or a head of its own, without naming a class of this mod that may change: make the block with
 * {@link #cylinder} or {@link #cylinderHead}, register it with the game as usual, tell this mod about it with
 * {@link #register}, and name its part effect in the {@code part_effect} data map (see docs/ADDONS.md, "A cylinder of your own").
 * <b>Added in API 1.1</b>; nothing here is removed or changed before the next major version.
 */
public final class PartBlocks {

	private PartBlocks() {
	}

	/**
	 * A new cylinder block with the behaviour of this mod's own cylinders: it takes a piston and a rod, sits on a crank throw
	 * and is read by the engine scanner. Give it your own properties, model and texture.
	 */
	public static Block cylinder(BlockBehaviour.Properties properties) {
		return new CylinderBlock(properties);
	}

	/** A new cylinder head block with the behaviour of this mod's own heads: injector, spark plug, fluids, ports. */
	public static Block cylinderHead(BlockBehaviour.Properties properties) {
		return new CylinderHeadBlock(properties);
	}

	/**
	 * A new flywheel block: a disc on a shaft on the engine's crank line, like the Cast Flywheel. <b>Added in API 1.2.</b> Its
	 * masses are not in code but in the {@code flywheel} data map ({@code data/<namespace>/data_maps/block/flywheel.json}: a
	 * {@code primary_inertia}, and optionally a {@code secondary_inertia} behind a spring pack, as the Dual-Mass Flywheel has),
	 * so a pack can retune it. A flywheel the map does not name adds nothing. Its look is its own block model, modelled
	 * along the Y axis and turned by its block state like any rotated block, and it turns with the shaft. Give it your own
	 * properties, model, texture and recipe; register it as for {@link #cylinder}.
	 */
	public static Block flywheel(BlockBehaviour.Properties properties) {
		return new DataFlywheelBlock(properties);
	}

	/**
	 * Tells this mod that a block made by {@link #cylinder}, {@link #cylinderHead} or {@link #flywheel} exists, so its block
	 * entity works. Call it once per block while your mod is being constructed, with your mod's event bus. A block of any
	 * other kind fails when the game asks for it, with a message that names it.
	 */
	public static void register(IEventBus modBus, Supplier<? extends Block> block) {
		modBus.addListener((BlockEntityTypeAddBlocksEvent event) -> {
			Block part = block.get();
			if (part instanceof CylinderBlock)
				event.modify(CEBlockEntities.CYLINDER.get(), part);
			else if (part instanceof CylinderHeadBlock)
				event.modify(CEBlockEntities.CYLINDER_HEAD.get(), part);
			else if (part instanceof DataFlywheelBlock)
				event.modify(CEBlockEntities.DATA_FLYWHEEL.get(), part);
			else
				throw new IllegalArgumentException(part
					+ " was not made by PartBlocks.cylinder, PartBlocks.cylinderHead or PartBlocks.flywheel");
		});
	}

	/** The part effect file a block names in the {@code part_effect} data map, or empty if it names none (all of this mod's do). */
	public static Optional<ResourceLocation> partEffect(Block block) {
		String effect = CEDataMaps.partEffect(block);
		return effect == null ? Optional.empty() : Optional.of(ResourceLocation.parse(effect));
	}
}
