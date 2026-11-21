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
	public static void makesTellsWhichFluidsAColumnProduces(GameTestHelper helper) {
		for (Object made : new Object[] {CEFluids.DIESEL.getSource(), CEFluids.GASOLINE.getSource(),
			CEFluids.ETHANOL.getSource(), CEFluids.KEROSENE.getSource()})
			if (!RefiningRecipe.makes(helper.getLevel(), of(made, 1)))
				helper.fail("a column should make " + made);
		for (Object not : new Object[] {CEFluids.PLANT_OIL.getSource(), CEFluids.ENGINE_OIL.getSource()})
			if (RefiningRecipe.makes(helper.getLevel(), of(not, 1)))
				helper.fail("no column route makes " + not);
		if (RefiningRecipe.makes(helper.getLevel(), FluidStack.EMPTY))
			helper.fail("nothing is not made by a column");
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
	public static void dieselGoesToKeroseneWithGlowstoneAndToGasolineOtherwise(GameTestHelper helper) {
		var level = helper.getLevel();
		FluidStack diesel = of(CEFluids.DIESEL.getSource(), 1000);
		Object none = RefiningRecipe.find(level, diesel, Heat.HEATED, net.minecraft.world.item.ItemStack.EMPTY).orElseThrow().value().product().getFluid();
		Object glow = RefiningRecipe.find(level, diesel, Heat.HEATED, new net.minecraft.world.item.ItemStack(Items.GLOWSTONE_DUST)).orElseThrow().value().product().getFluid();
		Object blaze = RefiningRecipe.find(level, diesel, Heat.HEATED, new net.minecraft.world.item.ItemStack(Items.BLAZE_POWDER)).orElseThrow().value().product().getFluid();
		Object stone = RefiningRecipe.find(level, diesel, Heat.HEATED, new net.minecraft.world.item.ItemStack(Items.STONE)).orElseThrow().value().product().getFluid();
		if (none != CEFluids.GASOLINE.getSource())
			helper.fail("an empty catalyst slot over diesel is the gasoline route, as it always was, got " + none);
		if (glow != CEFluids.KEROSENE.getSource())
			helper.fail("glowstone dust should take diesel to kerosene, got " + glow);
		if (blaze != CEFluids.GASOLINE.getSource())
			helper.fail("blaze powder should take diesel to gasoline, got " + blaze);
		if (stone != CEFluids.GASOLINE.getSource())
			helper.fail("an item no route takes changes nothing, got " + stone);
		if (RefiningRecipe.find(level, diesel, Heat.HEATED).orElseThrow().value().product().getFluid() != CEFluids.GASOLINE.getSource())
			helper.fail("the three-argument find is the old one: gasoline");
		RefiningRoute kerosene = RefiningRecipe.find(level, diesel, Heat.HEATED, new net.minecraft.world.item.ItemStack(Items.GLOWSTONE_DUST))
			.orElseThrow().value().route();
		if (kerosene.feedMb() != 1000 || kerosene.productMb() != 1100 || kerosene.catalystFeedMb() != 1000
			|| kerosene.heat() != Heat.HEATED || kerosene.speed() != 0.85)
			helper.fail("kerosene route read as " + kerosene);
		if (RefiningRecipe.find(level, of(CEFluids.PLANT_OIL.getSource(), 1000), Heat.HEATED,
			new net.minecraft.world.item.ItemStack(Items.GLOWSTONE_DUST)).orElseThrow().value().product().getFluid() != CEFluids.DIESEL.getSource())
			helper.fail("glowstone changes nothing for a feed it has no route for");
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

	private static int hintFor(GameTestHelper helper, Object feed, Object product) {
		for (var hint : com.createengines.content.refinery.BasinHints.hintsFor(helper.getLevel(),
			java.util.List.of(of(feed, 1000))))
			if (hint.product().getFluid() == product)
				return hint.percent();
		return 0;
	}

	@GameTest(template = "empty")
	public static void aBasinHoldingFeedIsToldWhatAColumnWouldGive(GameTestHelper helper) {
		int diesel = hintFor(helper, CEFluids.PLANT_OIL.getSource(), CEFluids.DIESEL.getSource());
		int gasoline = hintFor(helper, CEFluids.DIESEL.getSource(), CEFluids.GASOLINE.getSource());
		int ethanol = hintFor(helper, CEFluids.SUGAR_MASH.getSource(), CEFluids.ETHANOL.getSource());
		if (hintFor(helper, CEFluids.DIESEL.getSource(), CEFluids.KEROSENE.getSource()) != 10)
			helper.fail("diesel should be told a column makes 10% more kerosene too");
		if (diesel != 10 || gasoline != 10)
			helper.fail("diesel and gasoline should be told 10% more, got " + diesel + " and " + gasoline);
		if (ethanol != 21)
			helper.fail("ethanol should be told 21% more, got " + ethanol);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBasinHintNeverFiresForNothingOrForAProduct(GameTestHelper helper) {
		if (!com.createengines.content.refinery.BasinHints.hintsFor(helper.getLevel(), java.util.List.of()).isEmpty())
			helper.fail("an empty basin has no hint");
		if (!com.createengines.content.refinery.BasinHints.hintsFor(helper.getLevel(),
			java.util.List.of(FluidStack.EMPTY, of(CEFluids.GASOLINE.getSource(), 1000))).isEmpty())
			helper.fail("empty and product fluids have no hint");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theShippedBasinEthanolIsWeakenedAndBiodieselAsksLess(GameTestHelper helper) {
		var manager = helper.getLevel().getRecipeManager();
		int heated = ethanolPer250(manager, "mixing/ethanol");
		int superheated = ethanolPer250(manager, "mixing/ethanol_superheated");
		if (heated != 35 || superheated != 70)
			helper.fail("basin ethanol should be 35 and 70 per 250 mB of mash, got " + heated + " and " + superheated);
		helper.succeed();
	}

	private static int ethanolPer250(net.minecraft.world.item.crafting.RecipeManager manager, String path) {
		var holder = manager.byKey(CreateEngines.asResource(path)).orElseThrow();
		var recipe = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) holder.value();
		return recipe.getFluidResults().get(0).getAmount();
	}
}
