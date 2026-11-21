package com.createengines.api;

import java.util.Optional;
import java.util.function.Supplier;

import com.createengines.content.cylinder.CylinderBlock;
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
	 * Tells this mod that a block made by {@link #cylinder} or {@link #cylinderHead} exists, so its block entity works. Call it
	 * once per block while your mod is being constructed, with your mod's event bus. A block of any other kind fails when the
	 * game asks for it, with a message that names it.
	 */
	public static void register(IEventBus modBus, Supplier<? extends Block> block) {
		modBus.addListener((BlockEntityTypeAddBlocksEvent event) -> {
			Block part = block.get();
			if (part instanceof CylinderBlock)
				event.modify(CEBlockEntities.CYLINDER.get(), part);
			else if (part instanceof CylinderHeadBlock)
				event.modify(CEBlockEntities.CYLINDER_HEAD.get(), part);
			else
				throw new IllegalArgumentException(part + " was not made by PartBlocks.cylinder or PartBlocks.cylinderHead");
		});
	}

	/** The part effect file a block names in the {@code part_effect} data map, or empty if it names none (all of this mod's do). */
	public static Optional<ResourceLocation> partEffect(Block block) {
		String effect = CEDataMaps.partEffect(block);
		return effect == null ? Optional.empty() : Optional.of(ResourceLocation.parse(effect));
	}
}
