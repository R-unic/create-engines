package com.createengines.forge;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * One fuel fluid for the spike, diesel. The rest are registered like it once the fluids are ported properly (the 1.21.1
 * build makes them with Registrate, which is on 1.20.1 as well).
 */
public final class SpikeFluids {

	private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,
		CreateEnginesForge.ID);
	private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS,
		CreateEnginesForge.ID);
	private static final DeferredRegister<Item> BUCKETS = DeferredRegister.create(ForgeRegistries.ITEMS,
		CreateEnginesForge.ID);

	public static final RegistryObject<FluidType> DIESEL_TYPE = TYPES.register("diesel",
		() -> new FluidType(FluidType.Properties.create().density(830).viscosity(2000)));
	public static final RegistryObject<ForgeFlowingFluid> DIESEL = FLUIDS.register("diesel",
		() -> new ForgeFlowingFluid.Source(properties()));
	public static final RegistryObject<ForgeFlowingFluid> DIESEL_FLOWING = FLUIDS.register("flowing_diesel",
		() -> new ForgeFlowingFluid.Flowing(properties()));
	public static final RegistryObject<Item> DIESEL_BUCKET = BUCKETS.register("diesel_bucket",
		() -> new BucketItem(DIESEL, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

	private SpikeFluids() {
	}

	private static ForgeFlowingFluid.Properties properties() {
		return new ForgeFlowingFluid.Properties(DIESEL_TYPE, DIESEL, DIESEL_FLOWING).bucket(DIESEL_BUCKET);
	}

	public static void register(IEventBus bus) {
		TYPES.register(bus);
		FLUIDS.register(bus);
		BUCKETS.register(bus);
	}
}
