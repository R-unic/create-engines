package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.registry.CEBlocks;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create: Petrochem beside this mod: its refined fuels run our engines through the common fluid tags it fills itself
 * ({@code c:diesel}, {@code c:gasoline}, {@code c:kerosene}), the rest of its fluids do not burn here, and the fuel recipes of
 * its own engines accept ours. Nothing of ours names a Petrochem fluid.
 * <p>
 * Without Petrochem loaded (the normal GameTest run) the tests only pass over. With {@code create_engines.requirePetrochem} set,
 * which {@code runGameTestServerPetrochem} does and loads the jar for, a missing mod is a failure.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PetrochemCompatGameTests {

	private static final boolean REQUIRED = Boolean.getBoolean("create_engines.requirePetrochem");

	/** True, after passing the test, when Petrochem is not here and this run does not ask for it. */
	private static boolean absent(GameTestHelper helper) {
		if (ModList.get()
			.isLoaded("petrochem"))
			return false;
		helper.assertTrue(!REQUIRED, "The run requires Petrochem but it is not loaded");
		helper.succeed();
		return true;
	}

	private static Fluid fluid(GameTestHelper helper, String id) {
		Fluid fluid = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id))
			.orElse(null);
		helper.assertTrue(fluid != null, "Petrochem is loaded but has no fluid " + id);
		return fluid;
	}

	private static void burnsAs(GameTestHelper helper, String id, FuelSpec expected) {
		FuelSpec actual = CEDataMaps.fuel(fluid(helper, id));
		helper.assertTrue(actual == expected, id + " should burn as " + expected + " but burns as " + actual);
	}

	@GameTest(template = "empty")
	public static void petrochemFuelsBurnAsTheFuelsTheyAre(GameTestHelper helper) {
		if (absent(helper))
			return;
		burnsAs(helper, "petrochem:diesel", FuelSpec.DIESEL);
		burnsAs(helper, "petrochem:flowing_diesel", FuelSpec.DIESEL);
		burnsAs(helper, "petrochem:gasoline", FuelSpec.GASOLINE);
		burnsAs(helper, "petrochem:flowing_gasoline", FuelSpec.GASOLINE);
		burnsAs(helper, "petrochem:kerosene", FuelSpec.KEROSENE);
		burnsAs(helper, "petrochem:flowing_kerosene", FuelSpec.KEROSENE);
		// Its crude, its intermediates and its turbine fuel stay out
		for (String id : new String[] {"petrochem:petroleum", "petrochem:fuel_oil", "petrochem:lpg", "petrochem:light_naphta",
			"petrochem:heavy_naphta"})
			helper.assertTrue(CEDataMaps.fuel(fluid(helper, id)) == null,
				id + " is not an engine fuel but burns as " + CEDataMaps.fuel(fluid(helper, id)));
		helper.succeed();
	}

	/** Its lubricant oils an engine as engine oil does, and an oil exchange burns it off: it never becomes used oil. */
	@GameTest(template = "empty")
	public static void petrochemLubricantOilsAnEngineAndNeverBecomesUsedOil(GameTestHelper helper) {
		if (absent(helper))
			return;
		for (String id : new String[] {"petrochem:lubricant", "petrochem:flowing_lubricant"}) {
			double lubricity = CEFluids.lubricity(new FluidStack(fluid(helper, id), 1000));
			helper.assertTrue(lubricity == 1, id + " should oil as well as engine oil, lubricity " + lubricity);
		}
		BlockPos at = new BlockPos(1, 2, 1);
		helper.setBlock(at, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity sump = helper.getBlockEntity(at);
		int filled = sump.fluidCapability()
			.fill(new FluidStack(fluid(helper, "petrochem:lubricant"), 800), FluidAction.EXECUTE);
		helper.assertTrue(filled == 800 && sump.tank()
			.getFluidAmount() == 800, "The oil tank should take its lubricant, took " + filled);
		sump.exchange.setValue(SumpBlockEntity.EXCHANGE_RATES.length - 1);
		double swapped = sump.exchange(10);
		helper.assertTrue(swapped > 0 && sump.tank()
			.getFluidAmount() == 800 - (int) swapped, "The exchange should have drawn lubricant, swapped " + swapped);
		helper.assertTrue(sump.usedTank()
			.isEmpty(), "No used oil should come of it: " + sump.usedTank()
				.getFluidAmount() + " mB");
		helper.succeed();
	}

	/** Reads Petrochem's loaded fuel recipes; it does not run one of its engines. */
	@GameTest(template = "empty")
	public static void petrochemEngineRecipesAcceptOurFuels(GameTestHelper helper) {
		if (absent(helper))
			return;
		accepts(helper, "petrochem:diesel_engine_fuel", CEFluids.DIESEL.get()
			.getSource());
		accepts(helper, "petrochem:gasoline_engine_fuel", CEFluids.GASOLINE.get()
			.getSource());
		accepts(helper, "petrochem:gasoline_engine_fuel", CEFluids.KEROSENE.get()
			.getSource());
		helper.succeed();
	}

	private static void accepts(GameTestHelper helper, String type, Fluid ours) {
		ResourceLocation wanted = ResourceLocation.parse(type);
		FluidStack stack = new FluidStack(ours, 1000);
		boolean accepted = helper.getLevel()
			.getRecipeManager()
			.getRecipes()
			.stream()
			.map(holder -> holder.value())
			.filter(recipe -> wanted.equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())))
			.anyMatch(recipe -> recipe instanceof ProcessingRecipe<?, ?> processing && processing.getFluidIngredients()
				.stream()
				.anyMatch(ingredient -> ingredient.ingredient()
					.test(stack)));
		helper.assertTrue(accepted, "No " + type + " recipe accepts " + BuiltInRegistries.FLUID.getKey(ours));
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aDieselEngineRunsOnPetrochemDiesel(GameTestHelper helper) {
		if (absent(helper))
			return;
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		runsOn(helper, fluid(helper, "petrochem:diesel"));
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aSparkEngineRunsOnPetrochemGasoline(GameTestHelper helper) {
		if (absent(helper))
			return;
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		runsOn(helper, fluid(helper, "petrochem:gasoline"));
	}

	/** Swaps whatever is in the head's tank for {@code fuel}, lets go of the starter and expects the engine to carry on. */
	private static void runsOn(GameTestHelper helper, Fluid fuel) {
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.tank()
			.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
		int filled = head.tank()
			.fill(new FluidStack(fuel, 250), FluidAction.EXECUTE);
		helper.assertTrue(filled == 250, "The head should take " + BuiltInRegistries.FLUID.getKey(fuel) + ", took " + filled);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(700, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			EngineSummary summary = crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"Engine should run on " + BuiltInRegistries.FLUID.getKey(fuel) + ": " + summary);
			helper.assertTrue(head.tank()
				.getFluid()
				.getFluid() == fuel && head.tank()
				.getFluidAmount() < 250, "It should have burnt some of the fuel it was given: " + head.tank()
				.getFluidAmount());
			helper.succeed();
		});
	}
}
