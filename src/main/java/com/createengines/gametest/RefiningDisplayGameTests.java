package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.RefiningDisplay;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.engine.sim.Heat;
import com.createengines.registry.CERecipes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the JEI and EMI pages are drawn from. The pages themselves need a client with the viewer loaded (a compatibility
 * row, docs/COMPAT.md); this checks, on every shipped route, the stacks and wording both of them take from
 * {@link RefiningDisplay}.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RefiningDisplayGameTests {

	private static List<RecipeHolder<RefiningRecipe>> routes(GameTestHelper helper) {
		List<RecipeHolder<RefiningRecipe>> routes = helper.getLevel().getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get());
		if (routes.size() < 4)
			helper.fail("expected the four shipped refining routes, found " + routes.size());
		return routes;
	}

	private static String key(GameTestHelper helper, Component component) {
		if (component.getContents() instanceof TranslatableContents translatable)
			return translatable.getKey();
		helper.fail("expected translated wording, got " + component);
		return "";
	}

	@GameTest(template = "empty")
	public static void everyRouteOffersItsFeedAtTheRouteAmountAndItsProduct(GameTestHelper helper) {
		for (RecipeHolder<RefiningRecipe> holder : routes(helper)) {
			RefiningRecipe route = holder.value();
			var feeds = RefiningDisplay.feeds(route);
			if (feeds.isEmpty())
				helper.fail(holder.id() + " shows no feed fluid");
			for (var feed : feeds)
				if (feed.getAmount() != route.feed().amount())
					helper.fail(holder.id() + " shows " + feed.getAmount() + " mB of " + feed.getHoverName().getString()
						+ " but the route takes " + route.feed().amount());
			if (route.product().isEmpty())
				helper.fail(holder.id() + " shows no product");
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theWordingIsTranslatedAndCarriesTheRouteNumbers(GameTestHelper helper) {
		for (RecipeHolder<RefiningRecipe> holder : routes(helper)) {
			RefiningRecipe route = holder.value();
			String heat = key(helper, RefiningDisplay.heat(route.heat()));
			if (!heat.equals("create_engines.goggles.column.heat." + route.heat().serializedName()))
				helper.fail(holder.id() + " names its heat with " + heat);
			if (!(RefiningDisplay.ratio(route).getContents() instanceof TranslatableContents ratio)
				|| !ratio.getKey().equals("create_engines.recipe.refining.ratio")
				|| !List.of(route.feed().amount(), route.product().getAmount()).equals(List.of(ratio.getArgs())))
				helper.fail(holder.id() + " has a ratio line that does not carry " + route.feed().amount() + " and "
					+ route.product().getAmount());
			if (!(RefiningDisplay.speed(route.speed()).getContents() instanceof TranslatableContents speed)
				|| !List.of(RefiningDisplay.percent(route.speed())).equals(List.of(speed.getArgs())))
				helper.fail(holder.id() + " has a speed line that does not carry " + RefiningDisplay.percent(route.speed()) + "%");
			if (route.catalyst() != null) {
				key(helper, RefiningDisplay.catalystLine(route.catalyst()));
				key(helper, RefiningDisplay.catalystTooltip(route.catalyst()));
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyHeatHasAWordAndSpeedReadsAsAWholePercent(GameTestHelper helper) {
		for (Heat heat : Heat.values())
			if (!key(helper, RefiningDisplay.heat(heat)).endsWith("." + heat.serializedName()))
				helper.fail("no wording for " + heat);
		if (RefiningDisplay.percent(0.5) != 50 || RefiningDisplay.percent(1.0) != 100 || RefiningDisplay.percent(2.5) != 250)
			helper.fail("a speed of 0.5, 1 and 2.5 should read 50%, 100% and 250%");
		if (RefiningDisplay.percent(0.001) != 1)
			helper.fail("a very slow route should read 1%, not 0%");
		helper.succeed();
	}
}
