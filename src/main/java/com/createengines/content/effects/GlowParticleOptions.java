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
 * A soft blob of light or haze that changes colour as it ages: what drifts about inside a cylinder. Added together
 * with what is behind it ({@code additive}) it is flame; drawn normally it is mist.
 *
 * @param from     colour when new, each part 0 to 1
 * @param to       colour when it is gone
 * @param size     half its width in blocks when new
 * @param alpha    how solid it is at its most, 0 to 1
 * @param growth   its size when gone, as a share of its size when new: under 1 it shrinks as it is drawn in
 * @param lifetime ticks it lasts, give or take a fifth
 * @param additive whether it glows (and is as bright as fire whatever the light) rather than shades
 */
public record GlowParticleOptions(Vector3f from, Vector3f to, float size, float alpha, float growth, int lifetime,
	boolean additive) implements ParticleOptions {

	public static final MapCodec<GlowParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ExtraCodecs.VECTOR3F.fieldOf("from")
				.forGetter(GlowParticleOptions::from),
			ExtraCodecs.VECTOR3F.fieldOf("to")
				.forGetter(GlowParticleOptions::to),
			Codec.FLOAT.fieldOf("size")
				.forGetter(GlowParticleOptions::size),
			Codec.FLOAT.fieldOf("alpha")
				.forGetter(GlowParticleOptions::alpha),
			Codec.FLOAT.fieldOf("growth")
				.forGetter(GlowParticleOptions::growth),
			Codec.INT.fieldOf("lifetime")
				.forGetter(GlowParticleOptions::lifetime),
			Codec.BOOL.fieldOf("additive")
				.forGetter(GlowParticleOptions::additive))
		.apply(instance, GlowParticleOptions::new));

	/** Written by hand: {@code StreamCodec.composite} stops at six fields. */
	public static final StreamCodec<RegistryFriendlyByteBuf, GlowParticleOptions> STREAM_CODEC = StreamCodec.of(
		(buf, o) -> {
			ByteBufCodecs.VECTOR3F.encode(buf, o.from);
			ByteBufCodecs.VECTOR3F.encode(buf, o.to);
			buf.writeFloat(o.size);
			buf.writeFloat(o.alpha);
			buf.writeFloat(o.growth);
			buf.writeVarInt(o.lifetime);
			buf.writeBoolean(o.additive);
		}, buf -> new GlowParticleOptions(ByteBufCodecs.VECTOR3F.decode(buf), ByteBufCodecs.VECTOR3F.decode(buf),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));

	/** A glowing blob: flame, embers, hot charge. Colours are 0xRRGGBB. */
	public static GlowParticleOptions fire(int from, int to, float size, float alpha, float growth, int lifetime) {
		return new GlowParticleOptions(rgb(from), rgb(to), size, alpha, growth, lifetime, true);
	}

	/** A shaded blob: air, mist, smoke. Colours are 0xRRGGBB. */
	public static GlowParticleOptions mist(int from, int to, float size, float alpha, float growth, int lifetime) {
		return new GlowParticleOptions(rgb(from), rgb(to), size, alpha, growth, lifetime, false);
	}

	private static Vector3f rgb(int code) {
		return new Vector3f((code >> 16 & 255) / 255f, (code >> 8 & 255) / 255f, (code & 255) / 255f);
	}

	@Override
	public ParticleType<GlowParticleOptions> getType() {
		return CEParticles.GLOW.get();
	}
}
