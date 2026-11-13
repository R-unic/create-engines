package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Which basin recipe a Create mixer runs when a heated and a superheated recipe could both take the same contents.
 * Create ranks the recipes that match by how many item ingredients they have, most first, and does not look at heat, and
 * a heated recipe still matches over a superheated burner. A superheated recipe therefore only wins over its heated
 * twin if it asks for more items, which is why superheated ethanol wants a sugar and recycled oil a clay ball.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuelRecipeGameTests {

	private static final BlockPos BURNER = new BlockPos(3, 1, 3);
	private static final BlockPos BASIN = BURNER.above();

	/** A matching recipe's name and its number of item ingredients, which is what Create ranks by. */
	private record Match(String name, int items) {
	}

	@GameTest(template = "empty")
	public static void superheatedEthanolOutranksHeatedOnlyOverASuperheatedBurner(GameTestHelper helper) {
		List<ItemStack> items = List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR));
		FluidStack mash = new FluidStack((Fluid) CEFluids.SUGAR_MASH.getSource(), 250);
		assertRanksFirst(helper, "mixing/ethanol_superheated", HeatLevel.SEETHING, items, mash);
		assertAbsent(helper, "mixing/ethanol_superheated", HeatLevel.KINDLED, items, mash);
		assertRanksFirst(helper, "mixing/ethanol", HeatLevel.KINDLED, items, mash);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void superheatedRecyclingOutranksHeatedOnlyOverASuperheatedBurner(GameTestHelper helper) {
		List<ItemStack> items = List.of(new ItemStack(Items.CHARCOAL), new ItemStack(Items.CLAY_BALL));
		FluidStack used = new FluidStack((Fluid) CEFluids.USED_OIL.getSource(), 400);
		assertRanksFirst(helper, "mixing/engine_oil_recycled_superheated", HeatLevel.SEETHING, items, used);
		assertAbsent(helper, "mixing/engine_oil_recycled_superheated", HeatLevel.KINDLED, items, used);
		assertRanksFirst(helper, "mixing/engine_oil_recycled", HeatLevel.KINDLED, items, used);
		helper.succeed();
	}

	/** Without the extra item the heated recipe is the one that runs, even over a superheated burner. */
	@GameTest(template = "empty")
	public static void withoutTheExtraItemTheHeatedRecipeRuns(GameTestHelper helper) {
		assertRanksFirst(helper, "mixing/ethanol", HeatLevel.SEETHING, List.of(new ItemStack(Items.APPLE)),
			new FluidStack((Fluid) CEFluids.SUGAR_MASH.getSource(), 250));
		assertRanksFirst(helper, "mixing/engine_oil_recycled", HeatLevel.SEETHING, List.of(new ItemStack(Items.CHARCOAL)),
			new FluidStack((Fluid) CEFluids.USED_OIL.getSource(), 400));
		helper.succeed();
	}

	private static void assertRanksFirst(GameTestHelper helper, String recipe, HeatLevel heat, List<ItemStack> items,
		FluidStack fluid) {
		List<Match> matches = matches(helper, heat, items, fluid);
		int most = matches.stream()
			.mapToInt(Match::items)
			.max()
			.orElse(-1);
		List<String> leaders = matches.stream()
			.filter(m -> m.items() == most)
			.map(Match::name)
			.toList();
		if (!leaders.equals(List.of(recipe)))
			helper.fail("Over " + heat + " " + recipe + " should be the one recipe with the most items, but the matches are "
				+ matches);
	}

	private static void assertAbsent(GameTestHelper helper, String recipe, HeatLevel heat, List<ItemStack> items,
		FluidStack fluid) {
		List<Match> matches = matches(helper, heat, items, fluid);
		if (matches.stream()
			.anyMatch(m -> m.name()
				.equals(recipe)))
			helper.fail(recipe + " should need more heat than " + heat + ", but the matches are " + matches);
	}

	/** Fills a basin over a burner at {@code heat} and lists this mod's recipes that Create would take for it. */
	private static List<Match> matches(GameTestHelper helper, HeatLevel heat, List<ItemStack> items, FluidStack fluid) {
		helper.setBlock(BURNER, Blocks.AIR);
		helper.setBlock(BASIN, Blocks.AIR);
		helper.setBlock(BURNER, AllBlocks.BLAZE_BURNER.getDefaultState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
		helper.setBlock(BASIN, AllBlocks.BASIN.getDefaultState());
		var level = helper.getLevel();
		BlockPos basinPos = helper.absolutePos(BASIN);
		var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, basinPos, Direction.UP);
		var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, basinPos, Direction.UP);
		helper.assertTrue(fluids != null && inventory != null, "The basin should take items and fluid");
		helper.assertTrue(fluids.fill(fluid, FluidAction.EXECUTE) == fluid.getAmount(), "The basin should take the fluid");
		for (ItemStack stack : items)
			helper.assertTrue(ItemHandlerHelper.insertItem(inventory, stack.copy(), false)
				.isEmpty(), "The basin should take " + stack);

		BasinBlockEntity basin = helper.getBlockEntity(BASIN);
		List<Match> matches = new ArrayList<>();
		for (RecipeHolder<?> holder : level.getRecipeManager()
			.getRecipes())
			if (holder.id()
				.getNamespace()
				.equals(CreateEngines.MOD_ID) && holder.value() instanceof Recipe<?> recipe
				&& BasinRecipe.match(basin, recipe))
				matches.add(new Match(holder.id()
					.getPath(), recipe.getIngredients()
						.size()));
		return matches;
	}
}
