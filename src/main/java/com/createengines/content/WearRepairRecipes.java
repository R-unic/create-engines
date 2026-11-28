package com.createengines.content;

import java.util.Optional;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * Lets a Create Deployer holding a Service Kit set a lightly worn part right on a belt, in a basin or on a depot, the way it applies a
 * casing: the part is the item being processed and the kit is the tool, which takes one use of its durability.
 * <p>
 * Create looks recipes up by item and cannot say "wear between a tenth and a third", so the repair is offered to the Deployer as a recipe
 * made on the spot from the very part in front of it (see {@link DeployerRecipeSearchEvent}). It is made only for a single part that a kit
 * could set right, so a stack of worn parts is not all repaired for one use, and nothing is made for a part that is new or too worn.
 * The creative kit is offered for anything worn or failed, sets it to new and is kept.
 * <p>
 * Nothing is registered with the recipe manager, so it is not in JEI or EMI.
 */
public final class WearRepairRecipes {

	/** Above the priority an ordinary recipe is added at, so a repair is never lost to one that happens to match the same item. */
	private static final int PRIORITY = 100;

	private WearRepairRecipes() {
	}

	public static void onDeployer(DeployerRecipeSearchEvent event) {
		RecipeWrapper inventory = event.getInventory();
		ItemStack processed = inventory.getItem(0);
		ItemStack tool = inventory.getItem(1);
		if (!repairs(processed, tool))
			return;
		event.addRecipe(() -> Optional.of(repair(processed, tool)), PRIORITY);
	}

	/** Whether a kit held to this part would set it right: a single part, lightly worn. */
	public static boolean repairs(ItemStack processed, ItemStack tool) {
		if (CreativeService.isKit(tool))
			return processed.getCount() == 1 && !WearService.isNew(processed);
		return processed.getCount() == 1 && CEItems.SERVICE_KIT.isIn(tool) && processed.getItem() instanceof WornPartItem
			&& Wear.repairable(WearService.wearOf(processed));
	}

	/**
	 * The recipe that sets this part right with this kit: the same part at the repaired wear. The kit is deliberately not marked as kept:
	 * Create leaves a kept tool completely untouched, and spends a use of the durability of a damageable one that is not, which is what a
	 * kit is for.
	 */
	public static RecipeHolder<DeployerApplicationRecipe> repair(ItemStack worn, ItemStack tool) {
		boolean creative = CreativeService.isKit(tool);
		ItemStack fixed = creative ? CreativeService.mend(worn.copyWithCount(1))
			: WearService.withWear(worn.copyWithCount(1), Wear.repaired(WearService.wearOf(worn)));
		var id = CreateEngines.asResource("wear_repair");
		var builder = new ItemApplicationRecipe.Builder<DeployerApplicationRecipe>(DeployerApplicationRecipe::new, id);
		// A tool that is not kept and cannot take damage is used up, so the creative kit must be kept
		if (creative)
			builder.toolNotConsumed();
		DeployerApplicationRecipe recipe = builder
			.require(Ingredient.of(worn.getItem()))
			.require(Ingredient.of(tool.getItem()))
			.output(fixed)
			.build();
		return new RecipeHolder<>(id, recipe);
	}
}
