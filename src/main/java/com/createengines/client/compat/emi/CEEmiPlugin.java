package com.createengines.client.compat.emi;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.client.compat.CompatStatus;
import com.createengines.client.compat.ViewerEntries;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.createengines.registry.CERecipes;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * EMI's way in. EMI finds this by its annotation when it is installed and never loads it otherwise, and nothing in the
 * mod names this class, so a game without EMI never links it. It gives EMI one category, the refining column's routes,
 * with the blocks that make a column as its workstations.
 */
@EmiEntrypoint
public class CEEmiPlugin implements EmiPlugin {

	public static final EmiRecipeCategory REFINING = new EmiRecipeCategory(CreateEngines.asResource("refining"),
		EmiStack.of(CEBlocks.REBOILER.get()));

	public static final EmiRecipeCategory FUELS = new EmiRecipeCategory(CreateEngines.asResource("fuels"),
		EmiStack.of(CEFluids.DIESEL.getBucket()
			.get()));

	public static final EmiRecipeCategory PARTS = new EmiRecipeCategory(CreateEngines.asResource("parts"),
		EmiStack.of(CEItems.PISTON.get()));

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(FUELS);
		registry.addCategory(PARTS);
		var fuels = ViewerEntries.fuels();
		var parts = ViewerEntries.parts();
		fuels.forEach(fuel -> registry.addRecipe(new FuelEmiRecipe(fuel)));
		parts.forEach(part -> registry.addRecipe(new PartEmiRecipe(part)));
		CompatStatus.pagesRegistered("emi", fuels.size(), parts.size());
		CreateEngines.LOGGER.info("EMI: showing {} fuels and {} parts", fuels.size(), parts.size());
		registry.addCategory(REFINING);
		registry.addWorkstation(REFINING, EmiStack.of(CEBlocks.REBOILER.get()));
		registry.addWorkstation(REFINING, EmiStack.of(CEBlocks.COLUMN_SECTION.get()));
		registry.addWorkstation(REFINING, EmiStack.of(CEBlocks.CONDENSER.get()));

		List<RecipeHolder<RefiningRecipe>> routes = registry.getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get());
		for (RecipeHolder<RefiningRecipe> route : routes)
			registry.addRecipe(new RefiningEmiRecipe(route));
		CompatStatus.refiningRoutesRegistered("emi", routes.size());
		CreateEngines.LOGGER.info("EMI: showing {} refining routes", routes.size());
	}
}
