package com.createengines.client.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.refinery.RefiningDisplay;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CELang;
import com.createengines.registry.CERecipes;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
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
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * A page for one refining route: the feed (every fluid of a tag, one at a time), the catalyst under the arrow, the
 * product, and under them what the route needs and how it runs. Laid out on a 130 by 82 area.
 */
public class RefiningCategory implements IRecipeCategory<RecipeHolder<RefiningRecipe>> {

	public static final RecipeType<RecipeHolder<RefiningRecipe>> TYPE = RecipeType.createFromVanilla(CERecipes.REFINING.get());

	private static final int WIDTH = 130;
	private static final int HEIGHT = 82;
	/** Where the three rows of text start. */
	private static final int TEXT_TOP = 52;
	private static final int TEXT_COLOR = 0xFF808080;

	private final IDrawable icon;

	public RefiningCategory(IGuiHelper guiHelper) {
		this.icon = guiHelper.createDrawableItemLike(CEBlocks.REBOILER.get());
	}

	@Override
	public RecipeType<RecipeHolder<RefiningRecipe>> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return CELang.translate("recipe.refining.title").component();
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
	public @Nullable ResourceLocation getRegistryName(RecipeHolder<RefiningRecipe> recipe) {
		return recipe.id();
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RefiningRecipe> holder, IFocusGroup focuses) {
		RefiningRecipe route = holder.value();

		builder.addSlot(RecipeIngredientRole.INPUT, 13, 7)
			.setStandardSlotBackground()
			.setFluidRenderer(route.feed().amount(), false, 16, 16)
			.addIngredients(NeoForgeTypes.FLUID_STACK, RefiningDisplay.feeds(route));

		builder.addSlot(RecipeIngredientRole.OUTPUT, 101, 7)
			.setOutputSlotBackground()
			.setFluidRenderer(route.product().getAmount(), false, 16, 16)
			.addIngredient(NeoForgeTypes.FLUID_STACK, route.product());

		RefiningRecipe.Catalyst catalyst = route.catalyst();
		if (catalyst != null)
			builder.addSlot(RecipeIngredientRole.INPUT, 57, 29)
				.setStandardSlotBackground()
				.addIngredients(catalyst.ingredient())
				.addRichTooltipCallback((view, tooltip) -> tooltip.add(RefiningDisplay.catalystTooltip(catalyst)));
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<RefiningRecipe> holder, IFocusGroup focuses) {
		builder.addRecipeArrow().setPosition(53, 7);
	}

	@Override
	public void draw(RecipeHolder<RefiningRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		RefiningRecipe route = holder.value();
		Font font = Minecraft.getInstance().font;
		Component speed = RefiningDisplay.speed(route.speed());
		if (route.catalyst() != null)
			speed = speed.copy().append(" · ").append(RefiningDisplay.catalystLine(route.catalyst()));
		List<Component> lines = List.of(RefiningDisplay.heat(route.heat()), speed, RefiningDisplay.ratio(route));
		for (int i = 0; i < lines.size(); i++)
			graphics.drawCenteredString(font, lines.get(i), WIDTH / 2, TEXT_TOP + i * 10, TEXT_COLOR);
	}
}
