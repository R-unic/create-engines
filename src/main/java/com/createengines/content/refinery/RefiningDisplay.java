package com.createengines.content.refinery;

import java.util.Arrays;
import java.util.List;

import com.createengines.engine.sim.Heat;
import com.createengines.registry.CELang;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What a refining route looks like in a recipe viewer: the words and the stacks, with no viewer in sight. JEI and EMI
 * (client/compat) both draw their pages from this, so a route reads the same in either and a change to the wording is
 * made once. Common code on purpose, so the GameTests can check it against the shipped routes.
 */
public final class RefiningDisplay {

	private RefiningDisplay() {
	}

	/** Every fluid the feed accepts, each at the route's amount: one for a fluid, several for a tag. */
	public static List<FluidStack> feeds(RefiningRecipe route) {
		return Arrays.stream(route.feed().getFluids()).filter(f -> !f.isEmpty()).toList();
	}

	/** The base heat the route asks for, in the words a column's goggles use. */
	public static Component heat(Heat heat) {
		return CELang.translate("goggles.column.heat." + heat.serializedName()).component();
	}

	/** How fast the route runs against the column's top rate, as a whole percent: 50% for a speed of 0.5. */
	public static Component speed(double speed) {
		return CELang.translate("recipe.refining.speed", percent(speed)).component();
	}

	/** The route's ratio: so much feed in, so much product out. */
	public static Component ratio(RefiningRecipe route) {
		return CELang.translate("recipe.refining.ratio", route.feed().amount(), route.product().getAmount()).component();
	}

	/** The short line under a catalyst slot: one item for every so many mB of feed. */
	public static Component catalystLine(RefiningRecipe.Catalyst catalyst) {
		return CELang.translate("recipe.refining.catalyst", catalyst.feedPerItem()).component();
	}

	/** The longer sentence for the catalyst slot's tooltip. */
	public static Component catalystTooltip(RefiningRecipe.Catalyst catalyst) {
		return CELang.translate("recipe.refining.catalyst.tooltip", catalyst.feedPerItem()).component();
	}

	/** A speed as a whole percent. Never below 1, so a very slow route does not read as 0%. */
	public static int percent(double speed) {
		return Math.max(1, (int) Math.round(speed * 100));
	}
}
