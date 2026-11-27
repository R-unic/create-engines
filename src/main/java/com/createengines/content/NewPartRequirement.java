package com.createengines.content;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a machine that builds from a plan (a Schematicannon, the blueprint builder) must be paid for a part that wears: a new one. Create
 * matches by item alone, so a worn or wrecked one would be taken as payment and a new one built from it, which is a free repair.
 */
public final class NewPartRequirement extends ItemRequirement.StackRequirement {

	public NewPartRequirement(ItemStack stack) {
		super(stack, ItemRequirement.ItemUseType.CONSUME);
	}

	@Override
	public boolean matches(ItemStack other) {
		return super.matches(other) && WearService.isNew(other);
	}

	/** A requirement for one of this stack, which only a new one pays. */
	public static ItemRequirement of(ItemStack stack) {
		return new ItemRequirement(new NewPartRequirement(stack));
	}

	/** What a block that wears whole (a battery, a turbocharger) costs to build: its own item, new. */
	public static ItemRequirement block(BlockState state) {
		return of(new ItemStack(state.getBlock()
			.asItem()));
	}
}
