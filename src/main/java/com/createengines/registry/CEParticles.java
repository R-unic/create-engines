package com.createengines.registry;

import com.createengines.CreateEngines;
import com.createengines.content.effects.GasParticleOptions;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own particles. What each one is drawn from is in {@code assets/create_engines/particles}: vanilla's flame
 * and spark, and for gas a few white puffs drawn like vanilla's campfire smoke so they can be tinted any colour. How
 * they move is in {@code client.particle}.
 */
public final class CEParticles {

	private static final DeferredRegister<ParticleType<?>> PARTICLES =
		DeferredRegister.create(Registries.PARTICLE_TYPE, CreateEngines.MOD_ID);

	/** A puff of gas in any colour that swells and thins away: exhaust, vapour, steam. */
	public static final DeferredHolder<ParticleType<?>, ParticleType<GasParticleOptions>> GAS =
		PARTICLES.register("gas", () -> new ParticleType<>(false) {
			@Override
			public MapCodec<GasParticleOptions> codec() {
				return GasParticleOptions.CODEC;
			}

			@Override
			public StreamCodec<? super RegistryFriendlyByteBuf, GasParticleOptions> streamCodec() {
				return GasParticleOptions.STREAM_CODEC;
			}
		});
	/** A tongue of flame thrown from an exhaust, gone in a few ticks. */
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXHAUST_FLAME =
		PARTICLES.register("exhaust_flame", () -> new SimpleParticleType(false));
	/** A spark of hot metal that falls and cools. */
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK =
		PARTICLES.register("spark", () -> new SimpleParticleType(false));

	private CEParticles() {
	}

	public static void register(IEventBus modEventBus) {
		PARTICLES.register(modEventBus);
	}
}
