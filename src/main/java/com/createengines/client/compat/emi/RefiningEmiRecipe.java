package com.createengines.client.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.createengines.content.refinery.RefiningDisplay;
import com.createengines.content.refinery.RefiningRecipe;

import dev.emi.emi.api.neoforge.NeoForgeEmiIngredient;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * A page for one refining route, laid out as JEI's is: feed, arrow and product on top, the catalyst under the arrow, and
 * three lines of text under them. A 130 by 76 area.
 */
public class RefiningEmiRecipe implements EmiRecipe {

	private static final int WIDTH = 130;
	private static final int HEIGHT = 76;
	private static final int TEXT_TOP = 46;

	private final RecipeHolder<RefiningRecipe> holder;
	private final RefiningRecipe route;
	private final EmiIngredient feed;
	private final EmiStack product;
	private final EmiIngredient catalyst;

	public RefiningEmiRecipe(RecipeHolder<RefiningRecipe> holder) {
		this.holder = holder;
		this.route = holder.value();
		this.feed = NeoForgeEmiIngredient.of(route.feed());
		this.product = NeoForgeEmiStack.of(route.product());
		this.catalyst = route.catalyst() == null ? null : EmiIngredient.of(route.catalyst().ingredient());
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return CEEmiPlugin.REFINING;
	}

	@Override
	public ResourceLocation getId() {
		return holder.id();
	}

	@Override
	public RecipeHolder<?> getBackingRecipe() {
		return holder;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		List<EmiIngredient> inputs = new ArrayList<>();
		inputs.add(feed);
		if (catalyst != null)
			inputs.add(catalyst);
		return inputs;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(product);
	}

	@Override
	public int getDisplayWidth() {
		return WIDTH;
	}

	@Override
	public int getDisplayHeight() {
		return HEIGHT;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(feed, 12, 6).recipeContext(this);
		widgets.addFillingArrow(53, 6, 5000);
		widgets.addSlot(product, 100, 6).large(false).recipeContext(this);

		Component speed = RefiningDisplay.speed(route.speed());
		if (catalyst != null) {
			RefiningRecipe.Catalyst used = route.catalyst();
			widgets.addSlot(catalyst, 56, 26).appendTooltip(RefiningDisplay.catalystTooltip(used)).recipeContext(this);
			speed = speed.copy().append(" · ").append(RefiningDisplay.catalystLine(used));
		}

		List<Component> lines = List.of(RefiningDisplay.heat(route.heat()), speed, RefiningDisplay.ratio(route));
		for (int i = 0; i < lines.size(); i++)
			widgets.addText(lines.get(i), WIDTH / 2, TEXT_TOP + i * 10, 0xFFFFFF, true)
				.horizontalAlign(TextWidget.Alignment.CENTER);
	}
}
