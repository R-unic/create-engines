package com.createengines.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A tongue of flame thrown out of an exhaust: vanilla's flame, but shot the way it is aimed, as bright as fire whatever
 * the light, and gone in a few ticks where a torch's flame lingers for a second.
 */
public class ExhaustFlameParticle extends TextureSheetParticle {

	protected ExhaustFlameParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
								   double zSpeed) {
		super(level, x, y, z);
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		friction = 0.82f;
		gravity = -0.1f;
		hasPhysics = false;
		quadSize = 0.14f + random.nextFloat() * 0.1f;
		lifetime = 4 + random.nextInt(5);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	public float getQuadSize(float partialTick) {
		float life = Mth.clamp((age + partialTick) / lifetime, 0, 1);
		return quadSize * (1 - life * life * 0.8f);
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
									   double xSpeed, double ySpeed, double zSpeed) {
			ExhaustFlameParticle flame = new ExhaustFlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
			flame.pickSprite(sprites);
			return flame;
		}
	}
}
