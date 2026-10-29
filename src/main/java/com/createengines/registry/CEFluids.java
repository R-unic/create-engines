package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import java.util.function.Consumer;

import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class CEFluids {

	private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
	private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

	private static final int PLANT_OIL_COLOUR = 0xFF6E4A1A;
	private static final int DIESEL_COLOUR = 0xFFC7A93B;
	private static final int GASOLINE_COLOUR = 0xFFE6D27A;
	private static final int ENGINE_OIL_COLOUR = 0xFF2B2622;

	public static final FluidEntry<BaseFlowingFluid.Flowing> PLANT_OIL =
		REGISTRATE.fluid("plant_oil", STILL, FLOW, tinted(PLANT_OIL_COLOUR))
			.lang("Plant Oil")
			.properties(b -> b.viscosity(1500)
				.density(920))
			.fluidProperties(p -> p.levelDecreasePerBlock(2)
				.tickRate(20)
				.slopeFindDistance(3)
				.explosionResistance(100f))
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
			.build()
			.bucket()
			.model(CEFluids::bucketModel)
			.build()
			.register();

	public static final FluidEntry<BaseFlowingFluid.Flowing> DIESEL =
		REGISTRATE.fluid("diesel", STILL, FLOW, tinted(DIESEL_COLOUR))
			.lang("Diesel")
			.properties(b -> b.viscosity(1200)
				.density(840))
			.fluidProperties(p -> p.levelDecreasePerBlock(2)
				.tickRate(15)
				.slopeFindDistance(3)
				.explosionResistance(100f))
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW))
			.build()
			.bucket()
			.model(CEFluids::bucketModel)
			.build()
			.register();

	/** Too hard to light by compression alone: it burns only in an engine with spark plugs. */
	public static final FluidEntry<BaseFlowingFluid.Flowing> GASOLINE =
		REGISTRATE.fluid("gasoline", STILL, FLOW, tinted(GASOLINE_COLOUR))
			.lang("Gasoline")
			.properties(b -> b.viscosity(600)
				.density(740))
			.fluidProperties(p -> p.levelDecreasePerBlock(2)
				.tickRate(10)
				.slopeFindDistance(4)
				.explosionResistance(100f))
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.COLOR_YELLOW))
			.build()
			.bucket()
			.model(CEFluids::bucketModel)
			.build()
			.register();

	/** Lubricates an engine's moving parts: a thick, dark oil, made from plant oil. Not a fuel. */
	public static final FluidEntry<BaseFlowingFluid.Flowing> ENGINE_OIL =
		REGISTRATE.fluid("engine_oil", STILL, FLOW, tinted(ENGINE_OIL_COLOUR))
			.lang("Engine Oil")
			.properties(b -> b.viscosity(2500)
				.density(880))
			.fluidProperties(p -> p.levelDecreasePerBlock(2)
				.tickRate(25)
				.slopeFindDistance(2)
				.explosionResistance(100f))
			.source(BaseFlowingFluid.Source::new)
			.block()
			.properties(p -> p.mapColor(MapColor.COLOR_BLACK))
			.build()
			.bucket()
			.model(CEFluids::bucketModel)
			.build()
			.register();

	private CEFluids() {
	}

	public static void register() {
	}

	public static boolean isEngineOil(FluidStack stack) {
		return !stack.isEmpty() && stack.getFluid()
			.isSame(ENGINE_OIL.get());
	}

	/** A standard bucket with the fluid drawn inside, so buckets need no textures of their own. */
	private static void bucketModel(DataGenContext<Item, BucketItem> context, RegistrateItemModelProvider provider) {
		provider.withExistingParent(context.getName(), ResourceLocation.fromNamespaceAndPath("neoforge", "item/bucket"))
			.customLoader(DynamicFluidContainerModelBuilder::begin)
			.fluid(context.get().content);
	}

	/** Water's greyscale textures tinted to a fixed colour. */
	private static FluidBuilder.FluidTypeFactory tinted(int argb) {
		return (properties, still, flow) -> new FluidType(properties) {
			@Override
			public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
				consumer.accept(new IClientFluidTypeExtensions() {
					@Override
					public ResourceLocation getStillTexture() {
						return still;
					}

					@Override
					public ResourceLocation getFlowingTexture() {
						return flow;
					}

					@Override
					public int getTintColor() {
						return argb;
					}

					@Override
					public int getTintColor(FluidStack stack) {
						return argb;
					}

					@Override
					public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
						return argb;
					}
				});
			}
		};
	}
}
