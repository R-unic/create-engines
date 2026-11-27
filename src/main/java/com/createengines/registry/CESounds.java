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
	/** A stopping crank fetching up against compression and rocking back, after a stall the engine's note has played. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_SETTLE = register("engine_settle");
	/** An engine that is cold, or was long on the starter, labouring through many coughs before it takes hold. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_CATCH_HARD = register("engine_catch_hard");
	/** An engine dragged to a halt by its load: heavy, slowing thuds into a hard stop. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_STALL_LUGGED = register("engine_stall_lugged");
	/** An engine running out of fuel: a long, thin sputter. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_STALL_STARVED = register("engine_stall_starved");
	/** Unburned fuel lighting in a hot exhaust: the crack of an afterfire. */
	public static final DeferredHolder<SoundEvent, SoundEvent> EXHAUST_POP = register("exhaust_pop");
	/** A charge that boost lit early, spat out of the exhaust with a flash: a quick hiss of burning gas and a fizz. */
	public static final DeferredHolder<SoundEvent, SoundEvent> EXHAUST_SPIT = register("exhaust_spit");
	/** Hot metal ticking as it cools, for a while after an engine stops. */
	public static final DeferredHolder<SoundEvent, SoundEvent> METAL_TICK = register("metal_tick");
	/** A charge lighting itself early under boost: a quick run of hard metallic knocks at the cylinder, in wear mode. */
	public static final DeferredHolder<SoundEvent, SoundEvent> PREIGNITION_KNOCK = register("preignition_knock");
	/** Valve gear ticking over the hum of the gears. No longer played (author, 2026-11-26); the ID stays, as no sound ID is removed. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_CLATTER = register("engine_clatter");
	/** Bearings running dry: a seamless loop, as loud as the engine is short of oil. */
	public static final DeferredHolder<SoundEvent, SoundEvent> BEARING_SQUEAL = register("bearing_squeal");
	/** Air gulped through an intake mouth: a seamless loop that swells with each intake pulse, heard from the mouth. */
	public static final DeferredHolder<SoundEvent, SoundEvent> INTAKE_ROAR = register("intake_roar");

	/** A pinch of soot dropping into a Soot Trap. */
	public static final DeferredHolder<SoundEvent, SoundEvent> SOOT_CATCH = register("soot_catch");
	/** A Flare Stack's flame catching and settling, as it burns off a working column's gas. */
	public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_WHOOSH = register("flare_whoosh");

	/** The wear mode's loudest failure: a connecting rod going through the head, with the clang of the casting splitting and the grind of the wreck. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ROD_THROWN = register("rod_thrown");
	/** The wear mode's: a head gasket blowing, a hard pop and hot gas hissing out past the joint. */
	public static final DeferredHolder<SoundEvent, SoundEvent> GASKET_BLOW = register("gasket_blow");
	/** The wear mode's: a piston running dry crying out, a short wavering shriek, played more often the nearer it is to seizing. */
	public static final DeferredHolder<SoundEvent, SoundEvent> SEIZE_WARNING = register("seize_warning");
	/** The wear mode's: a piston seizing from running dry, a short rising shriek cut off by a hard clunk. */
	public static final DeferredHolder<SoundEvent, SoundEvent> PISTON_SEIZE = register("piston_seize");

	/** The wear mode's: a rotor's apex seals failing, a snap, the loose strip clattering round the chamber and the wheeze of blow-by. */
	public static final DeferredHolder<SoundEvent, SoundEvent> APEX_FAIL = register("apex_fail");

	/** The wear mode's: a cylinder head warping from heat, a sharp crack of hot metal and steam hissing from the joint. */
	public static final DeferredHolder<SoundEvent, SoundEvent> HEAD_WARP = register("head_warp");

	/** The wear mode's: a turbocharger failing, its wheel rubbing the housing as it screams down. */
	public static final DeferredHolder<SoundEvent, SoundEvent> TURBO_FAIL = register("turbo_fail");

	/** The rev-up that plays for a goal or a challenge: made up as it plays, so the recording named in sounds.json is never read. */
	public static final DeferredHolder<SoundEvent, SoundEvent> ADVANCEMENT_STING = register("advancement_sting");

	private CESounds() {
	}

	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CreateEngines.asResource(name)));
	}

	public static void register(IEventBus modEventBus) {
		SOUNDS.register(modEventBus);
	}
}
