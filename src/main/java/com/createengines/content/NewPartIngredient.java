package com.createengines.content;

import java.util.stream.Stream;

import com.createengines.registry.CERecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * A recipe ingredient that takes one item only when it is new: {@code {"type": "create_engines:new_part", "item": "..."}}. For a recipe
 * that turns a part that wears into a new thing, which with a plain ingredient would turn a spent or wrecked one into a new one.
 */
public record NewPartIngredient(Item item) implements ICustomIngredient {

	public static final MapCodec<NewPartIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
		.group(BuiltInRegistries.ITEM.byNameCodec()
			.fieldOf("item")
			.forGetter(NewPartIngredient::item))
		.apply(instance, NewPartIngredient::new));

	@Override
	public boolean test(ItemStack stack) {
		return stack.is(item) && WearService.isNew(stack);
	}

	@Override
	public Stream<ItemStack> getItems() {
		return Stream.of(new ItemStack(item));
	}

	/** Not simple: whether a stack matches depends on its data, not on its item alone. */
	@Override
	public boolean isSimple() {
		return false;
	}

	@Override
	public IngredientType<?> getType() {
		return CERecipes.NEW_PART.get();
	}
}
