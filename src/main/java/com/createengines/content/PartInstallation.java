package com.createengines.content;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
					   @Nullable ItemEntry<Item> gradeItem, @Nullable BooleanProperty requires, @Nullable String needs) {

		public Slot(BooleanProperty property, ItemEntry<Item> item) {
			this(property, item, null, null, null, null);
		}

		public Slot(BooleanProperty property, ItemEntry<Item> item, @Nullable BooleanProperty grade,
					@Nullable ItemEntry<Item> gradeItem) {
			this(property, item, grade, gradeItem, null, null);
		}

		/** A part that only fits where {@code requires} is already fitted (an injector assist on a head with an injector). */
		public Slot(BooleanProperty property, ItemEntry<Item> item, BooleanProperty requires) {
			this(property, item, null, null, requires, "needs_injector");
		}

		/** The same, saying {@code needs} (a lang key under {@code create_engines.part.}) when the part it sits on is missing. */
		public Slot(BooleanProperty property, ItemEntry<Item> item, BooleanProperty requires, String needs) {
			this(property, item, null, null, requires, needs);
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
			if (slot.requires() != null && !state.getValue(slot.requires()) && slot.item().isIn(stack)) {
				// Say why it will not go in, instead of leaving the click to do nothing
				if (!level.isClientSide)
					player.displayClientMessage(Component.translatable("create_engines.part." + slot.needs()), true);
				return ItemInteractionResult.sidedSuccess(level.isClientSide);
			}
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
		return tryRemove(state, level, pos, player, Dresser.NONE, slots);
	}

	/** Gives a part that has come out of a block anything it carries with it: the wear it had, for a part that wears. */
	@FunctionalInterface
	public interface Dresser {
		Dresser NONE = (slot, part) -> part;

		ItemStack dress(Slot slot, ItemStack part);
	}

	/** As above, with {@code dresser} giving the part that comes out its wear (and the one that drops when the block breaks). */
	public static boolean tryRemove(BlockState state, Level level, BlockPos pos, Player player, Dresser dresser, Slot... slots) {
		if (!player.isShiftKeyDown())
			return false;
		for (int i = slots.length - 1; i >= 0; i--) {
			Slot slot = slots[i];
			if (!state.getValue(slot.property()))
				continue;
			if (!level.isClientSide) {
				ItemStack part = dresser.dress(slot, slot.fitted(state));
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

	/**
	 * What a schematic cannon must be given to build {@code state}: the housing block and every part fitted in it. A
	 * fitted part is only a block state flag, which Create would otherwise price at nothing, so a schematic of a finished
	 * engine would come with its pistons, rods, injectors, plugs and rotors for free.
	 */
	public static ItemRequirement requirement(BlockState state, Slot... slots) {
		ItemRequirement needed = new ItemRequirement(ItemUseType.CONSUME, state.getBlock()
			.asItem());
		for (Slot slot : slots)
			if (state.getValue(slot.property()))
				needed = needed.union(new ItemRequirement(ItemUseType.CONSUME, slot.fitted(state)));
		return needed;
	}

	public static void dropParts(BlockState state, Level level, BlockPos pos, BlockState newState, Slot... slots) {
		dropParts(state, level, pos, newState, Dresser.NONE, slots);
	}

	/** As above, with the parts that drop carrying the wear {@code dresser} gives them. */
	public static void dropParts(BlockState state, Level level, BlockPos pos, BlockState newState, Dresser dresser, Slot... slots) {
		if (level.isClientSide || state.is(newState.getBlock()))
			return;
		for (Slot slot : slots)
			if (state.getValue(slot.property()))
				Block.popResource(level, pos, dresser.dress(slot, slot.fitted(state)));
	}
}
