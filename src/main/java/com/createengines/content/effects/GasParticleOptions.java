package com.createengines.content.effects;

import org.joml.Vector3f;

import com.createengines.registry.CEParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * A puff of gas: exhaust smoke, the vapour off a cold engine, the steam of rain on a hot one, the draught at an air
 * intake. It swells as it ages and thins away to nothing, which no vanilla smoke does in a colour of your choosing.
 *
 * @param color    its colour, each part 0 to 1
 * @param scale    how big it gets: at 1 it swells to a little over half a block across
 * @param alpha    how solid it is when new, 0 to 1
 * @param lift     how hard it rises: 1 climbs as fast as vanilla smoke, 0 hangs where its speed leaves it
 * @param lifetime ticks it lasts, give or take a fifth
 */
public record GasParticleOptions(Vector3f color, float scale, float alpha, float lift, int lifetime)
	implements ParticleOptions {

	public static final MapCodec<GasParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ExtraCodecs.VECTOR3F.fieldOf("color")
				.forGetter(GasParticleOptions::color),
			Codec.FLOAT.fieldOf("scale")
				.forGetter(GasParticleOptions::scale),
			Codec.FLOAT.fieldOf("alpha")
				.forGetter(GasParticleOptions::alpha),
			Codec.FLOAT.fieldOf("lift")
				.forGetter(GasParticleOptions::lift),
			Codec.INT.fieldOf("lifetime")
				.forGetter(GasParticleOptions::lifetime))
		.apply(instance, GasParticleOptions::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, GasParticleOptions> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VECTOR3F, GasParticleOptions::color, ByteBufCodecs.FLOAT, GasParticleOptions::scale,
		ByteBufCodecs.FLOAT, GasParticleOptions::alpha, ByteBufCodecs.FLOAT, GasParticleOptions::lift,
		ByteBufCodecs.VAR_INT, GasParticleOptions::lifetime, GasParticleOptions::new);

	public GasParticleOptions(float red, float green, float blue, float scale, float alpha, float lift, int lifetime) {
		this(new Vector3f(red, green, blue), scale, alpha, lift, lifetime);
	}

	@Override
	public ParticleType<GasParticleOptions> getType() {
		return CEParticles.GAS.get();
	}
}
