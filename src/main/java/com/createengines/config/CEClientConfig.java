package com.createengines.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player effects: what an engine looks and sounds like, which costs a player's frame rate and nobody else's. */
public final class CEClientConfig {

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.BooleanValue PARTICLES;
	public static final ModConfigSpec.DoubleValue BANG_VOLUME;
	public static final ModConfigSpec.DoubleValue EFFECT_DENSITY;
	public static final ModConfigSpec.DoubleValue BOOST_VOLUME;
	public static final ModConfigSpec.DoubleValue ENGINE_NOTE_VOLUME;
	public static final ModConfigSpec.DoubleValue MECHANICAL_VOLUME;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.push("effects");
		PARTICLES = builder.comment("Flame, sparks, smoke, steam and the gas drawn through a cylinder. Off, engines run without particles.")
			.define("engineParticles", true);
		BANG_VOLUME = builder.comment("Volume of a cylinder's firing bang. 0 mutes it.")
			.defineInRange("bangVolume", 1.0, 0.0, 2.0);
		ENGINE_NOTE_VOLUME = builder.comment("Volume of the engine's exhaust note, made up from its speed and firing order as it runs.",
				"0 mutes it, and each cylinder's bang plays at full volume instead.")
			.defineInRange("engineNoteVolume", 1.0, 0.0, 2.0);
		MECHANICAL_VOLUME = builder.comment("Volume of an engine's own noises besides its exhaust: the tick of its valve gear, the squeal of",
				"dry bearings, the cough as it catches and dies, and hot metal ticking as it cools. 0 mutes them.")
			.defineInRange("mechanicalVolume", 1.0, 0.0, 2.0);
		BOOST_VOLUME = builder.comment("Volume of turbocharger whistle and blow-off and the supercharger whine. 0 mutes them.")
			.defineInRange("boostVolume", 1.0, 0.0, 2.0);
		EFFECT_DENSITY = builder.comment("How often each cylinder may bang and puff, against the default. Below 1 thins the",
				"effects out (kind to frame rate on a big engine), above 1 allows more.")
			.defineInRange("effectDensity", 1.0, 0.1, 4.0);
		builder.pop();
		SPEC = builder.build();
	}

	private CEClientConfig() {
	}

	public static boolean particles() {
		return PARTICLES.get();
	}

	public static float bangVolume() {
		return BANG_VOLUME.get()
			.floatValue();
	}

	public static float engineNoteVolume() {
		return ENGINE_NOTE_VOLUME.get()
			.floatValue();
	}

	public static float mechanicalVolume() {
		return MECHANICAL_VOLUME.get()
			.floatValue();
	}

	public static float boostVolume() {
		return BOOST_VOLUME.get()
			.floatValue();
	}

	public static double effectDensity() {
		return EFFECT_DENSITY.get();
	}
}
