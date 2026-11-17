package com.createengines.client.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.client.compat.ViewerEntries;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** A page for one engine part: the part, the blocks it is installed into, and how. */
public class PartCategory implements IRecipeCategory<ViewerEntries.Part> {

	public static final RecipeType<ViewerEntries.Part> TYPE =
		RecipeType.create(CreateEngines.MOD_ID, "parts", ViewerEntries.Part.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 84;
	private static final int TEXT_COLOR = 0xFF808080;

	private final IDrawable icon;

	public PartCategory(IGuiHelper guiHelper) {
		this.icon = guiHelper.createDrawableItemLike(CEItems.PISTON.get());
	}

	@Override
	public RecipeType<ViewerEntries.Part> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return CELang.translate("recipe.parts.title")
			.component();
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public @Nullable ResourceLocation getRegistryName(ViewerEntries.Part part) {
		return BuiltInRegistries.ITEM.getKey(part.part()
			.getItem());
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, ViewerEntries.Part part, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 4, 4)
			.setStandardSlotBackground()
			.addItemStack(part.part());
		for (int i = 0; i < part.blocks()
			.size(); i++)
			builder.addSlot(RecipeIngredientRole.CATALYST, 40 + i * 20, 4)
				.setStandardSlotBackground()
				.addItemStack(part.blocks()
					.get(i));
	}

	@Override
	public void draw(ViewerEntries.Part part, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, CELang.translate("recipe.part.fits")
			.component(), 40, 25, TEXT_COLOR, false);
		int y = 38;
		for (Component line : ViewerEntries.partLines(part))
			for (FormattedCharSequence wrapped : font.split(line, WIDTH - 6)) {
				graphics.drawString(font, wrapped, 3, y, TEXT_COLOR, false);
				y += 9;
			}
	}
}
