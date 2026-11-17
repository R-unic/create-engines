package com.createengines.client.compat.emi;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.client.compat.CompatStatus;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.registry.CEBlocks;
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

	@Override
	public void register(EmiRegistry registry) {
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
