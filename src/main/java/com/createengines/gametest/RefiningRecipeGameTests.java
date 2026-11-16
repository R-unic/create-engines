package com.createengines.gametest;

import java.util.Optional;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.engine.sim.Heat;
import com.createengines.engine.sim.RefiningRoute;
import com.createengines.registry.CEFluids;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The refining column's recipe type: the shipped routes load, and a base picks the route its heat allows. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RefiningRecipeGameTests {

	private static FluidStack of(Object fluid, int amount) {
		return new FluidStack((Fluid) fluid, amount);
	}

	private static RecipeHolder<RefiningRecipe> pick(GameTestHelper helper, Object feed, Heat heat) {
		Optional<RecipeHolder<RefiningRecipe>> found = RefiningRecipe.find(helper.getLevel(), of(feed, 1000), heat);
		if (found.isEmpty())
			helper.fail("no refining route takes that feed at " + heat);
		return found.get();
	}

	@GameTest(template = "empty")
	public static void everyShippedRouteLoadsWithItsFluidsAndCatalyst(GameTestHelper helper) {
		RecipeHolder<RefiningRecipe> diesel = pick(helper, CEFluids.PLANT_OIL.getSource(), Heat.HEATED);
		RecipeHolder<RefiningRecipe> gasoline = pick(helper, CEFluids.DIESEL.getSource(), Heat.HEATED);
		RecipeHolder<RefiningRecipe> ethanol = pick(helper, CEFluids.SUGAR_MASH.getSource(), Heat.HEATED);

		if (diesel.value().product().getFluid() != CEFluids.DIESEL.getSource())
			helper.fail("plant oil should refine to diesel");
		if (gasoline.value().product().getFluid() != CEFluids.GASOLINE.getSource())
			helper.fail("diesel should refine to gasoline");
		if (ethanol.value().product().getFluid() != CEFluids.ETHANOL.getSource())
			helper.fail("sugar mash should refine to ethanol");

		if (!diesel.value().catalyst().ingredient().test(new net.minecraft.world.item.ItemStack(Items.COAL)))
			helper.fail("diesel should take coal as its catalyst");
		if (!diesel.value().catalyst().ingredient().test(new net.minecraft.world.item.ItemStack(Items.CHARCOAL)))
			helper.fail("diesel should take charcoal too: the tag is minecraft:coals");
		if (!gasoline.value().catalyst().ingredient().test(new net.minecraft.world.item.ItemStack(Items.BLAZE_POWDER)))
			helper.fail("gasoline should take blaze powder as its catalyst");
		if (!ethanol.value().catalyst().ingredient().test(new net.minecraft.world.item.ItemStack(Items.NETHER_WART)))
			helper.fail("ethanol should take nether wart as its catalyst");
		if (diesel.value().catalyst().ingredient().test(new net.minecraft.world.item.ItemStack(Items.STONE)))
			helper.fail("stone is no catalyst");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theRecipeFilesGiveTheSameNumbersTheSimulationRuns(GameTestHelper helper) {
		RefiningRoute diesel = pick(helper, CEFluids.PLANT_OIL.getSource(), Heat.HEATED).value().route();
		if (diesel.feedMb() != 1000 || diesel.productMb() != 1100 || diesel.catalystFeedMb() != 1000
			|| diesel.heat() != Heat.HEATED || diesel.speed() != 1)
			helper.fail("diesel route read as " + diesel);
		RefiningRoute gasoline = pick(helper, CEFluids.DIESEL.getSource(), Heat.HEATED).value().route();
		if (gasoline.productMb() != 1100 || gasoline.speed() != 0.75)
			helper.fail("gasoline route read as " + gasoline);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSuperheatedBasePicksTheBetterEthanolRouteAndAHeatedOneThePlainRoute(GameTestHelper helper) {
		RefiningRoute heated = pick(helper, CEFluids.SUGAR_MASH.getSource(), Heat.HEATED).value().route();
		RefiningRoute superheated = pick(helper, CEFluids.SUGAR_MASH.getSource(), Heat.SUPERHEATED).value().route();
		if (heated.heat() != Heat.HEATED || superheated.heat() != Heat.SUPERHEATED)
			helper.fail("expected the heated route at heated and the superheated route at superheated: "
				+ heated.heat() + ", " + superheated.heat());
		if (superheated.productMb() != 2 * heated.productMb())
			helper.fail("superheated ethanol should give twice the heated yield: " + superheated + " against " + heated);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aColdBaseHasNoRoute(GameTestHelper helper) {
		if (RefiningRecipe.find(helper.getLevel(), of(CEFluids.PLANT_OIL.getSource(), 1000), Heat.NONE).isPresent())
			helper.fail("no route should run over a cold base");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFluidNoRouteTakesFindsNothing(GameTestHelper helper) {
		if (RefiningRecipe.find(helper.getLevel(), of(CEFluids.GASOLINE.getSource(), 1000), Heat.SUPERHEATED).isPresent())
			helper.fail("gasoline is a product, not a feed");
		helper.succeed();
	}
}
