package com.createengines.content.refinery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.ColumnAdvantage;
import com.createengines.engine.sim.Heat;
import com.createengines.registry.CELang;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * The goggle line on Create's Basin that says a refining column would do better with the feed it holds. Called from
 * a mixin ({@code BasinGoggleMixin}), so everything here fails soft: a missing level, an odd recipe or a changed Create
 * method gives no line, never an exception. Client only.
 */
public final class BasinHints {

	private BasinHints() {
	}

	/** Adds a line for each fuel a column would make better from what the basin's input tank holds. */
	public static void addTo(BasinBlockEntity basin, List<Component> tooltip) {
		try {
			Level level = basin.getLevel();
			if (level == null)
				return;
			for (Hint hint : hintsFor(level, inputFluids(basin)))
				GoggleLines.add(CELang.translate("goggles.basin.column", hint.percent(), hint.product()
					.getHoverName()
					.getString())
					.style(ChatFormatting.GOLD), tooltip, 1);
		} catch (RuntimeException e) {
			// A changed Create or an odd recipe: say nothing rather than break the basin's own goggles
		}
	}

	private static List<FluidStack> inputFluids(BasinBlockEntity basin) {
		SmartFluidTankBehaviour input = basin.getTanks()
			.getFirst();
		if (input == null)
			return List.of();
		return java.util.Arrays.stream(input.getTanks())
			.map(SmartFluidTankBehaviour.TankSegment::getRenderedFluid)
			.filter(f -> !f.isEmpty())
			.toList();
	}

	/** One hint: the fuel a column would make and how much more of it. */
	public record Hint(FluidStack product, int percent) {
	}

	/** The hints for these feed fluids, one for each product, the biggest gain first when several routes share one. */
	public static List<Hint> hintsFor(Level level, List<FluidStack> feeds) {
		Map<String, Hint> byProduct = new LinkedHashMap<>();
		List<RecipeHolder<RefiningRecipe>> routes = level.getRecipeManager()
			.getAllRecipesFor(com.createengines.registry.CERecipes.REFINING.get());
		for (FluidStack feed : feeds) {
			// Water is in half the basins in a world (methanol's column route takes it), and a hint on every one of them is noise
			if (feed.getFluid() == net.minecraft.world.level.material.Fluids.WATER)
				continue;
			for (RecipeHolder<RefiningRecipe> holder : routes) {
				RefiningRecipe route = holder.value();
				if (!route.feed()
					.ingredient()
					.test(feed))
					continue;
				double basinYield = basinYield(level, feed, route);
				int percent = ColumnAdvantage.percentMore(route.route()
					.yield(), basinYield);
				if (percent <= 0)
					continue;
				String key = route.product()
					.getFluid()
					.toString();
				Hint known = byProduct.get(key);
				if (known == null || known.percent() < percent)
					byProduct.put(key, new Hint(route.product(), percent));
			}
		}
		return List.copyOf(byProduct.values());
	}

	/**
	 * Product per mB of feed of the basin's plain one-in, one-out recipe for this feed and product at the route's heat,
	 * or 0 when there is none. Blends with a second fluid in or out (biodiesel) are a different job and do not count.
	 */
	private static double basinYield(Level level, FluidStack feed, RefiningRecipe route) {
		double best = 0;
		RecipeType<?> mixing = com.simibubi.create.AllRecipeTypes.MIXING.getType();
		for (RecipeHolder<?> holder : level.getRecipeManager()
			.getRecipes()) {
			Recipe<?> recipe = holder.value();
			if (!(recipe instanceof ProcessingRecipe<?, ?> processing)
				|| processing.getType() != mixing)
				continue;
			List<SizedFluidIngredient> in = processing.getFluidIngredients();
			List<FluidStack> out = processing.getFluidResults();
			if (in.size() != 1 || out.size() != 1)
				continue;
			Heat heat = Heat.fromName(processing.getRequiredHeat()
				.getSerializedName());
			if (heat != route.heat() || !in.get(0)
				.ingredient()
				.test(feed) || !FluidStack.isSameFluid(out.get(0), route.product()))
				continue;
			int fed = in.get(0)
				.amount();
			if (fed > 0)
				best = Math.max(best, (double) out.get(0)
					.getAmount() / fed);
		}
		return best;
	}
}
