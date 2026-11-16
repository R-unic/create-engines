package com.createengines.content.refinery;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.Heat;
import com.createengines.engine.sim.RefiningRoute;
import com.createengines.registry.CERecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * One route a refining column can run, as a data file in {@code data/<namespace>/recipe/refining/}:
 * <pre>{@code
 * {
 *   "type": "create_engines:refining",
 *   "feed": { "fluid": "create_engines:plant_oil", "amount": 1000 },
 *   "product": { "id": "create_engines:diesel", "amount": 1100 },
 *   "heat": "heated",
 *   "speed": 1.0,
 *   "catalyst": { "ingredient": { "tag": "minecraft:coals" }, "feed_per_item": 1000 }
 * }
 * }</pre>
 * {@code feed} and {@code product} give the ratio: 1000 mB of feed makes 1100 mB of product here, however fast the
 * column runs. {@code feed} may name a fluid tag instead of a fluid. {@code heat} is {@code none}, {@code heated} or
 * {@code superheated}, and is optional (it defaults to {@code heated}), like {@code speed} (how fast this route runs
 * against the column's top rate, default 1). {@code catalyst} is optional: one item of it is used up for every
 * {@code feed_per_item} mB of feed.
 * <p>
 * The format joins the 1.x promise on the day it ships, so a field may be added but never changed or removed.
 */
public final class RefiningRecipe implements Recipe<RefiningRecipe.NoInput> {

	/** Refining reads no items from a crafting grid: the column holds its own tanks. */
	public record NoInput() implements RecipeInput {
		@Override
		public ItemStack getItem(int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public int size() {
			return 0;
		}
	}

	/** An item the route uses up: one for every {@code feedPerItem} mB of feed. */
	public record Catalyst(Ingredient ingredient, int feedPerItem) {
		public static final Codec<Catalyst> CODEC = RecordCodecBuilder.create(i -> i.group(
				Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Catalyst::ingredient),
				ExtraCodecs.POSITIVE_INT.fieldOf("feed_per_item").forGetter(Catalyst::feedPerItem))
			.apply(i, Catalyst::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Catalyst> STREAM_CODEC = StreamCodec.composite(
			Ingredient.CONTENTS_STREAM_CODEC, Catalyst::ingredient,
			ByteBufCodecs.VAR_INT, Catalyst::feedPerItem,
			Catalyst::new);
	}

	private static final Codec<Heat> HEAT_CODEC = Codec.STRING.comapFlatMap(
		name -> {
			Heat heat = Heat.fromName(name);
			return heat == null ? DataResult.error(() -> "Unknown heat '" + name + "', expected none, heated or superheated")
				: DataResult.success(heat);
		}, Heat::serializedName);
	private static final StreamCodec<RegistryFriendlyByteBuf, Heat> HEAT_STREAM_CODEC =
		ByteBufCodecs.idMapper(i -> Heat.values()[i], Heat::ordinal).cast();

	private final SizedFluidIngredient feed;
	private final FluidStack product;
	private final Heat heat;
	private final double speed;
	@Nullable
	private final Catalyst catalyst;

	public RefiningRecipe(SizedFluidIngredient feed, FluidStack product, Heat heat, double speed, Optional<Catalyst> catalyst) {
		this.feed = feed;
		this.product = product;
		this.heat = heat;
		this.speed = speed;
		this.catalyst = catalyst.orElse(null);
	}

	public SizedFluidIngredient feed() {
		return feed;
	}

	public FluidStack product() {
		return product;
	}

	public Heat heat() {
		return heat;
	}

	public double speed() {
		return speed;
	}

	@Nullable
	public Catalyst catalyst() {
		return catalyst;
	}

	/** The numbers the simulation runs on. */
	public RefiningRoute route() {
		return new RefiningRoute(feed.amount(), product.getAmount(), heat, catalyst == null ? 0 : catalyst.feedPerItem(), speed);
	}

	/**
	 * The route a base of this heat runs for this feed (whatever the amount, which is a tank's content and not a batch): among the recipes that take it and ask no more heat than the base
	 * has, the one that asks for the most, since a hotter base is what that recipe is for.
	 */
	public static Optional<RecipeHolder<RefiningRecipe>> find(Level level, FluidStack feed, Heat baseHeat) {
		List<RecipeHolder<RefiningRecipe>> all = level.getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get());
		return all.stream()
			.filter(r -> r.value().feed.ingredient().test(feed) && baseHeat.satisfies(r.value().heat))
			.max(Comparator.<RecipeHolder<RefiningRecipe>>comparingInt(r -> r.value().heat.ordinal())
				.thenComparingDouble(r -> r.value().route().yield()));
	}

	/** Whether any route takes this fluid as feed, at any heat. */
	public static boolean takesFeed(Level level, FluidStack feed) {
		return !feed.isEmpty() && level.getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get()).stream()
			.anyMatch(r -> r.value().feed.ingredient().test(feed));
	}

	/** Whether any route uses this item as its catalyst. */
	public static boolean takesCatalyst(Level level, ItemStack stack) {
		return !stack.isEmpty() && level.getRecipeManager().getAllRecipesFor(CERecipes.REFINING.get()).stream()
			.anyMatch(r -> r.value().catalyst != null && r.value().catalyst.ingredient().test(stack));
	}

	@Override
	public boolean matches(NoInput input, Level level) {
		return false;
	}

	@Override
	public ItemStack assemble(NoInput input, HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CERecipes.REFINING_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return CERecipes.REFINING.get();
	}

	public static final class Serializer implements RecipeSerializer<RefiningRecipe> {
		private static final MapCodec<RefiningRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				SizedFluidIngredient.FLAT_CODEC.fieldOf("feed").forGetter(RefiningRecipe::feed),
				FluidStack.CODEC.fieldOf("product").forGetter(RefiningRecipe::product),
				HEAT_CODEC.optionalFieldOf("heat", Heat.HEATED).forGetter(RefiningRecipe::heat),
				Codec.doubleRange(0.01, 100).optionalFieldOf("speed", 1.0).forGetter(RefiningRecipe::speed),
				Catalyst.CODEC.optionalFieldOf("catalyst").forGetter(r -> Optional.ofNullable(r.catalyst)))
			.apply(i, RefiningRecipe::new));
		private static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> STREAM_CODEC = StreamCodec.composite(
			SizedFluidIngredient.STREAM_CODEC, RefiningRecipe::feed,
			FluidStack.STREAM_CODEC, RefiningRecipe::product,
			HEAT_STREAM_CODEC, RefiningRecipe::heat,
			ByteBufCodecs.DOUBLE, RefiningRecipe::speed,
			ByteBufCodecs.optional(Catalyst.STREAM_CODEC), r -> Optional.ofNullable(r.catalyst),
			RefiningRecipe::new);

		@Override
		public MapCodec<RefiningRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
