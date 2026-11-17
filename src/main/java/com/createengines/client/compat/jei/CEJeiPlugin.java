package com.createengines.client.compat.jei;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.client.compat.CompatStatus;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CERecipes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI's way in. JEI finds this by its annotation when it is installed and never loads it otherwise, and nothing in the
 * mod names this class, so a game without JEI never links it. It gives JEI one page type, the refining column's routes,
 * and the blocks that make a column as the things that run them.
 */
@JeiPlugin
public class CEJeiPlugin implements IModPlugin {

	private static final ResourceLocation UID = CreateEngines.asResource("jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new RefiningCategory(registration.getJeiHelpers().getGuiHelper()),
			new FuelCategory(registration.getJeiHelpers().getGuiHelper()), new PartCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		var level = Minecraft.getInstance().level;
		if (level == null)
			return;
		List<RecipeHolder<RefiningRecipe>> routes = level.getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get());
		registration.addRecipes(RefiningCategory.TYPE, routes);
		CompatStatus.refiningRoutesRegistered("jei", routes.size());
		CreateEngines.LOGGER.info("JEI: showing {} refining routes", routes.size());
		var fuels = com.createengines.client.compat.ViewerEntries.fuels();
		var parts = com.createengines.client.compat.ViewerEntries.parts();
		registration.addRecipes(FuelCategory.TYPE, fuels);
		registration.addRecipes(PartCategory.TYPE, parts);
		CompatStatus.pagesRegistered("jei", fuels.size(), parts.size());
		CreateEngines.LOGGER.info("JEI: showing {} fuels and {} parts", fuels.size(), parts.size());
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalysts(RefiningCategory.TYPE, CEBlocks.REBOILER.get(), CEBlocks.COLUMN_SECTION.get(),
			CEBlocks.CONDENSER.get());
	}
}
