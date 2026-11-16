package com.createengines.registry;

import com.createengines.CreateEngines;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data of our own: today only what a Jerry Can holds. */
public final class CEComponents {

	private static final DeferredRegister<DataComponentType<?>> TYPES =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateEngines.MOD_ID);

	/** The fluid in a Jerry Can. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> CAN_FLUID =
		TYPES.register("can_fluid", () -> DataComponentType.<SimpleFluidContent>builder()
			.persistent(SimpleFluidContent.CODEC)
			.networkSynchronized(SimpleFluidContent.STREAM_CODEC)
			.cacheEncoding()
			.build());

	private CEComponents() {
	}

	public static void register(IEventBus modEventBus) {
		TYPES.register(modEventBus);
	}
}
