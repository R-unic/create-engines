package com.createengines.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CreateEnginesForge.ID)
public class CreateEnginesForge {
	public static final String ID = "create_engines";

	private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);

	public static final RegistryObject<Block> CRANK =
		BLOCKS.register("spike_crank", () -> new SpikeCrankBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion()));
	public static final RegistryObject<Item> CRANK_ITEM =
		ITEMS.register("spike_crank", () -> new BlockItem(CRANK.get(), new Item.Properties()));
	public static final RegistryObject<BlockEntityType<SpikeCrankBlockEntity>> CRANK_BE =
		BLOCK_ENTITIES.register("spike_crank", () -> BlockEntityType.Builder
			.of((pos, state) -> new SpikeCrankBlockEntity(CreateEnginesForge.CRANK_BE.get(), pos, state), CRANK.get()).build(null));

	public CreateEnginesForge() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(bus);
		ITEMS.register(bus);
		BLOCK_ENTITIES.register(bus);
		SpikeFluids.register(bus);
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
			(net.minecraftforge.event.AddReloadListenerEvent event) -> {
				event.addListener(new FuelData());
				event.addListener(new PackData());
			});
	}
}
