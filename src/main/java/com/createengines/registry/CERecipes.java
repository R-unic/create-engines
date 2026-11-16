package com.createengines.registry;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.RefiningRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types of our own: today only the refining column's routes. */
public final class CERecipes {

	private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, CreateEngines.MOD_ID);
	private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
		DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateEngines.MOD_ID);

	public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipe>> REFINING =
		TYPES.register("refining", () -> RecipeType.simple(CreateEngines.asResource("refining")));
	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefiningRecipe>> REFINING_SERIALIZER =
		SERIALIZERS.register("refining", RefiningRecipe.Serializer::new);

	private CERecipes() {
	}

	public static void register(IEventBus modEventBus) {
		TYPES.register(modEventBus);
		SERIALIZERS.register(modEventBus);
	}
}
