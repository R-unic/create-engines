package com.createengines.client.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.client.compat.ViewerEntries;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CELang;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** A page for one engine fuel: the fuel in a bucket, and what an engine makes of it (energy, ignition, cold start, boost). */
public class FuelCategory implements IRecipeCategory<ViewerEntries.Fuel> {

	public static final RecipeType<ViewerEntries.Fuel> TYPE =
		RecipeType.create(CreateEngines.MOD_ID, "fuels", ViewerEntries.Fuel.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 96;
	private static final int TEXT_COLOR = 0xFF808080;

	private final IDrawable icon;

	public FuelCategory(IGuiHelper guiHelper) {
		this.icon = guiHelper.createDrawableItemLike(CEFluids.DIESEL.getBucket()
			.get());
	}

	@Override
	public RecipeType<ViewerEntries.Fuel> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return CELang.translate("recipe.fuels.title")
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
	public @Nullable ResourceLocation getRegistryName(ViewerEntries.Fuel fuel) {
		return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fuel.stack()
			.getFluid());
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, ViewerEntries.Fuel fuel, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 67, 4)
			.setStandardSlotBackground()
			.setFluidRenderer(fuel.stack()
				.getAmount(), false, 16, 16)
			.addIngredient(NeoForgeTypes.FLUID_STACK, fuel.stack());
	}

	@Override
	public void draw(ViewerEntries.Fuel fuel, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		int y = 26;
		for (Component line : ViewerEntries.fuelLines(fuel))
			for (FormattedCharSequence wrapped : font.split(line, WIDTH - 6)) {
				graphics.drawString(font, wrapped, 3, y, TEXT_COLOR, false);
				y += 9;
			}
	}

	/** The pages of the viewer, as a list for the plugin to hand over. */
	static List<ViewerEntries.Fuel> entries() {
		return ViewerEntries.fuels();
	}
}
