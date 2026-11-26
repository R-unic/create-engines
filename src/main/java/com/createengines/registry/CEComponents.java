package com.createengines.registry;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.RecordedBlueprint;

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

	/** The engine recorded in a blueprint, as the blueprint string. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecordedBlueprint>> BLUEPRINT =
		TYPES.register("blueprint", () -> DataComponentType.<RecordedBlueprint>builder()
			.persistent(RecordedBlueprint.CODEC)
			.networkSynchronized(RecordedBlueprint.STREAM_CODEC)
			.build());

	/** How worn a part is, 0 new to 1 spent. Absent on a new part, so every part from before the wear mode is still a new one. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> WEAR =
		TYPES.register("wear", () -> DataComponentType.<Double>builder()
			.persistent(com.mojang.serialization.Codec.doubleRange(0, 1))
			.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.DOUBLE)
			.build());

	/**
	 * Carried by the item of a block that was broken while failed: a cylinder with a thrown rod's wreck in it, or a rotor housing whose apex
	 * seals have failed, so breaking and placing it again is no repair. Absent on every other stack.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FAILED =
		TYPES.register("failed", () -> DataComponentType.<Boolean>builder()
			.persistent(com.mojang.serialization.Codec.BOOL)
			.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
			.build());

	private CEComponents() {
	}

	public static void register(IEventBus modEventBus) {
		TYPES.register(modEventBus);
	}
}
