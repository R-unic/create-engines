package com.createengines.client.compat.emi;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.client.compat.ViewerEntries;

import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** A page for one engine fuel, laid out as JEI's is: the fuel in a bucket, and what an engine makes of it. 150 by 96. */
public class FuelEmiRecipe implements EmiRecipe {

	private static final int WIDTH = 150;
	private static final int HEIGHT = 96;

	private final ViewerEntries.Fuel fuel;
	private final EmiStack stack;
	private final ResourceLocation id;

	public FuelEmiRecipe(ViewerEntries.Fuel fuel) {
		this.fuel = fuel;
		this.stack = NeoForgeEmiStack.of(fuel.stack());
		ResourceLocation fluid = BuiltInRegistries.FLUID.getKey(fuel.stack()
			.getFluid());
		this.id = CreateEngines.asResource("/fuel/" + fluid.getNamespace() + "/" + fluid.getPath());
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return CEEmiPlugin.FUELS;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(stack);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of();
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
		widgets.addSlot(stack, 66, 3)
			.recipeContext(this);
		int y = 26;
		for (Component line : ViewerEntries.fuelLines(fuel))
			for (FormattedCharSequence wrapped : Minecraft.getInstance().font.split(line, WIDTH - 6)) {
				widgets.addText(wrapped, 3, y, 0xFFFFFF, true);
				y += 9;
			}
	}
}
