package com.createengines.registry;

import com.createengines.CreateEngines;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events; what each one plays is in {@code assets/create_engines/sounds.json}. The recordings themselves are
 * synthesized by {@code tools/generate_sounds.py}.
 */
public final class CESounds {

	private static final DeferredRegister<SoundEvent> SOUNDS =
		DeferredRegister.create(Registries.SOUND_EVENT, CreateEngines.MOD_ID);

	/** A cylinder's charge igniting at the top of its compression stroke. */
	public static final DeferredHolder<SoundEvent, SoundEvent> CYLINDER_FIRE = register("cylinder_fire");
	/** The starter motor turning an engine over: a seamless loop, played for as long as it is cranking. */
	public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_WHIR = register("starter_whir");

	private CESounds() {
	}

	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CreateEngines.asResource(name)));
	}

	public static void register(IEventBus modEventBus) {
		SOUNDS.register(modEventBus);
	}
}
