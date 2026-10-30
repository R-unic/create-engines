package com.createengines.content;

import org.jetbrains.annotations.Nullable;

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
 * <p>
 * A slot can also take a precision version of its part. That fills the same property and sets a second one beside it,
 * so everything that asks only whether a piston or an injector is fitted needn't care which it is.
 */
public final class PartInstallation {

	/**
	 * @param property  set while any part is fitted in this slot
	 * @param item      the ordinary part
	 * @param grade     set as well as {@code property} while the precision part is fitted, or null if there is none
	 * @param gradeItem the precision part
	 */
	public record Slot(BooleanProperty property, ItemEntry<Item> item, @Nullable BooleanProperty grade,
					   @Nullable ItemEntry<Item> gradeItem) {

		public Slot(BooleanProperty property, ItemEntry<Item> item) {
			this(property, item, null, null);
		}

		/** The item that comes out of this slot as it stands in {@code state}. */
		ItemStack fitted(BlockState state) {
			return grade != null && state.getValue(grade) ? gradeItem.asStack() : item.asStack();
		}
	}

	private PartInstallation() {
	}

	public static ItemInteractionResult tryInstall(ItemStack stack, BlockState state, Level level, BlockPos pos,
												   Player player, Slot... slots) {
		for (Slot slot : slots) {
			if (state.getValue(slot.property()))
				continue;
			BlockState next;
			if (slot.item().isIn(stack))
				next = state.setValue(slot.property(), true);
			else if (slot.gradeItem() != null && slot.gradeItem().isIn(stack))
				next = state.setValue(slot.property(), true)
					.setValue(slot.grade(), true);
			else
				continue;
			if (!level.isClientSide) {
				level.setBlockAndUpdate(pos, next);
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
				ItemStack part = slot.fitted(state);
				BlockState next = state.setValue(slot.property(), false);
				if (slot.grade() != null)
					next = next.setValue(slot.grade(), false);
				level.setBlockAndUpdate(pos, next);
				if (!player.isCreative())
					player.getInventory()
						.placeItemBackInInventory(part);
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
				Block.popResource(level, pos, slot.fitted(state));
	}
}
