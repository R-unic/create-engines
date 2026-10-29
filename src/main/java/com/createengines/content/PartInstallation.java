package com.createengines.content;

import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Engine parts that are smaller than a block (pistons, connecting rods, injectors) are installed into their housing
 * block by right-clicking, removed by sneak-right-clicking with an empty hand, and dropped when the housing breaks.
 * Each installed part is a block state property, so installing one changes the block and the engine rescans.
 */
public final class PartInstallation {

	public record Slot(BooleanProperty property, ItemEntry<Item> item) {
	}

	private PartInstallation() {
	}

	public static ItemInteractionResult tryInstall(ItemStack stack, BlockState state, Level level, BlockPos pos,
												   Player player, Slot... slots) {
		for (Slot slot : slots) {
			if (!slot.item().isIn(stack) || state.getValue(slot.property()))
				continue;
			if (!level.isClientSide) {
				level.setBlockAndUpdate(pos, state.setValue(slot.property(), true));
				if (!player.isCreative())
					stack.shrink(1);
				level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/** Removes the last installed part in {@code slots} (the order they are listed is install order). */
	public static boolean tryRemove(BlockState state, Level level, BlockPos pos, Player player, Slot... slots) {
		if (!player.isShiftKeyDown())
			return false;
		for (int i = slots.length - 1; i >= 0; i--) {
			Slot slot = slots[i];
			if (!state.getValue(slot.property()))
				continue;
			if (!level.isClientSide) {
				level.setBlockAndUpdate(pos, state.setValue(slot.property(), false));
				if (!player.isCreative())
					player.getInventory()
						.placeItemBackInInventory(slot.item().asStack());
				level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.6f, 1.4f);
			}
			return true;
		}
		return false;
	}

	public static void dropParts(BlockState state, Level level, BlockPos pos, BlockState newState, Slot... slots) {
		if (level.isClientSide || state.is(newState.getBlock()))
			return;
		for (Slot slot : slots)
			if (state.getValue(slot.property()))
				Block.popResource(level, pos, slot.item().asStack());
	}
}
