package com.createengines.client.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.client.compat.ViewerEntries;
import com.createengines.registry.CELang;

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

/** A page for one engine part, laid out as JEI's is: the part, the blocks it is installed into, and how. 150 by 84. */
public class PartEmiRecipe implements EmiRecipe {

	private static final int WIDTH = 150;
	private static final int HEIGHT = 84;

	private final ViewerEntries.Part part;
	private final EmiStack stack;
	private final List<EmiStack> blocks = new ArrayList<>();
	private final ResourceLocation id;

	public PartEmiRecipe(ViewerEntries.Part part) {
		this.part = part;
		this.stack = EmiStack.of(part.part());
		part.blocks()
			.forEach(block -> blocks.add(EmiStack.of(block)));
		this.id = CreateEngines.asResource("/part/" + BuiltInRegistries.ITEM.getKey(part.part()
			.getItem())
			.getPath());
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return CEEmiPlugin.PARTS;
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
	public List<EmiIngredient> getCatalysts() {
		return new ArrayList<>(blocks);
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
		widgets.addSlot(stack, 3, 3)
			.recipeContext(this);
		for (int i = 0; i < blocks.size(); i++)
			widgets.addSlot(blocks.get(i), 39 + i * 20, 3);
		widgets.addText(CELang.translate("recipe.part.fits")
			.component(), 39, 24, 0xFFFFFF, true);
		int y = 38;
		for (Component line : ViewerEntries.partLines(part))
			for (FormattedCharSequence wrapped : Minecraft.getInstance().font.split(line, WIDTH - 6)) {
				widgets.addText(wrapped, 3, y, 0xFFFFFF, true);
				y += 9;
			}
	}
}
