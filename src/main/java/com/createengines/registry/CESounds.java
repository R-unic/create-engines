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
	/** An engine's exhaust note, made up as it plays; the recording named in sounds.json is never read. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_NOTE = register("engine_note");
	/** The starter motor turning an engine over: a seamless loop, played for as long as it is cranking. */
	public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_WHIR = register("starter_whir");

	/** A turbocharger's wheel spooling: a seamless loop, pitched with the wheel's speed. */
	public static final DeferredHolder<SoundEvent, SoundEvent> TURBO_WHISTLE = register("turbo_whistle");
	/** A blow-off valve dumping boost, with the flutter of the compressor surging. */
	public static final DeferredHolder<SoundEvent, SoundEvent> TURBO_BLOWOFF = register("turbo_blowoff");
	/** A supercharger's gears and lobes: a seamless loop, pitched with its shaft. */
	public static final DeferredHolder<SoundEvent, SoundEvent> SUPERCHARGER_WHINE = register("supercharger_whine");

	/** An engine catching on the starter: its first few firings, each sooner and harder than the last. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_CATCH = register("engine_catch");
	/** An engine dying: its last firings, a knock as the crank rocks to a stop, and a sigh from the exhaust. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_STALL = register("engine_stall");
	/** Unburned fuel lighting in a hot exhaust: the crack of an afterfire. */
	public static final DeferredHolder<SoundEvent, SoundEvent> EXHAUST_POP = register("exhaust_pop");
	/** Hot metal ticking as it cools, for a while after an engine stops. */
	public static final DeferredHolder<SoundEvent, SoundEvent> METAL_TICK = register("metal_tick");
	/** Valve gear ticking over the hum of the gears: a seamless loop, pitched with the crank. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_CLATTER = register("engine_clatter");
	/** Bearings running dry: a seamless loop, as loud as the engine is short of oil. */
	public static final DeferredHolder<SoundEvent, SoundEvent> BEARING_SQUEAL = register("bearing_squeal");

	private CESounds() {
	}

	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CreateEngines.asResource(name)));
	}

	public static void register(IEventBus modEventBus) {
		SOUNDS.register(modEventBus);
	}
}
