package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The head gaskets' recipes load and make what they say (2.4): rubber is sawn from a Mechanical Belt, cardboard is crafted from one of Create's
 * Cardboard, and copper is a Rubber Gasket with a copper sheet deployed and pressed on each side. A recipe that fails to parse simply
 * never loads (the 2.3.0 copper one did), so each is looked up by id.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasketRecipeGameTests {

	@GameTest(template = "empty")
	public static void aSawCutsTwoRubberGasketsFromABelt(GameTestHelper helper) {
		CuttingRecipe cutting = recipe(helper, "rubber_gasket", CuttingRecipe.class);
		helper.assertTrue(cutting.getIngredients().size() == 1, "one ingredient");
		helper.assertTrue(cutting.getIngredients().get(0).test(AllItems.BELT_CONNECTOR.asStack()), "cut from a Mechanical Belt");
		helper.assertTrue(!cutting.getIngredients().get(0).test(new ItemStack(Items.SLIME_BALL)), "slime no longer makes it");
		ItemStack out = cutting.getRollableResults().get(0).getStack();
		helper.assertTrue(out.is(CEItems.RUBBER_GASKET.get()) && out.getCount() == 2, "two rubber gaskets, got " + out);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void oneCardboardMakesACardboardGasketAndPaperDoesNot(GameTestHelper helper) {
		var level = helper.getLevel();
		var manager = level.getRecipeManager();
		CraftingInput cardboard = CraftingInput.of(1, 1, List.of(AllItems.CARDBOARD.asStack()));
		var made = manager.getRecipeFor(RecipeType.CRAFTING, cardboard, level).orElseThrow(() -> new AssertionError("no recipe for cardboard"));
		helper.assertTrue(made.value().assemble(cardboard, level.registryAccess()).is(CEItems.CARDBOARD_GASKET.get()), "cardboard makes the gasket");
		CraftingInput paper = CraftingInput.of(3, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(Items.PAPER)));
		boolean paperMakesIt = manager.getRecipeFor(RecipeType.CRAFTING, paper, level)
			.map(h -> h.value().assemble(paper, level.registryAccess()).is(CEItems.CARDBOARD_GASKET.get()))
			.orElse(false);
		helper.assertFalse(paperMakesIt, "three paper no longer make a cardboard gasket");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aCopperGasketIsARubberOneWithASheetPressedOnEachSide(GameTestHelper helper) {
		SequencedAssemblyRecipe assembly = recipe(helper, "copper_gasket", SequencedAssemblyRecipe.class);
		helper.assertTrue(assembly.getIngredient().test(CEItems.RUBBER_GASKET.asStack()), "starts from a Rubber Gasket");
		helper.assertTrue(assembly.getTransitionalItem().is(CEItems.INCOMPLETE_COPPER_GASKET.get()), "goes through the incomplete gasket");
		helper.assertTrue(assembly.getLoops() == 1, "one pass");
		helper.assertTrue(assembly.getResultItem(helper.getLevel().registryAccess()).is(CEItems.COPPER_GASKET.get()), "makes a Copper Gasket");
		List<SequencedRecipe<?>> steps = assembly.getSequence();
		helper.assertTrue(steps.size() == 4, "four steps, got " + steps.size());
		deploysSheet(helper, steps.get(0));
		helper.assertTrue(steps.get(1).getRecipe() instanceof PressingRecipe, "step 2 presses");
		deploysSheet(helper, steps.get(2));
		helper.assertTrue(steps.get(3).getRecipe() instanceof PressingRecipe, "step 4 presses");
		helper.succeed();
	}

	private static void deploysSheet(GameTestHelper helper, SequencedRecipe<?> step) {
		helper.assertTrue(step.getRecipe() instanceof DeployerApplicationRecipe, "a deploying step");
		var ingredients = step.getRecipe().getIngredients();
		helper.assertTrue(ingredients.stream().anyMatch(i -> i.test(AllItems.COPPER_SHEET.asStack())), "deploys a copper sheet");
	}

	private static <T extends Recipe<?>> T recipe(GameTestHelper helper, String path, Class<T> type) {
		var holder = helper.getLevel().getRecipeManager().byKey(CreateEngines.asResource(path))
			.orElseThrow(() -> new AssertionError("recipe " + path + " did not load"));
		if (!type.isInstance(holder.value()))
			throw new AssertionError("recipe " + path + " is a " + holder.value().getClass().getSimpleName() + ", not a " + type.getSimpleName());
		return type.cast(holder.value());
	}
}
