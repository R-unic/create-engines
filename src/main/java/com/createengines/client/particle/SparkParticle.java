package com.createengines.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A spark of hot metal: one square pixel of the world's size or a little under, thrown the way it is aimed. It falls,
 * and cools from yellow through orange to a dull red before it goes out. As bright as fire whatever the light.
 */
public class SparkParticle extends TextureSheetParticle {

	protected SparkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
							double zSpeed) {
		super(level, x, y, z);
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		friction = 0.96f;
		gravity = 0.9f;
		quadSize = 0.02f + random.nextFloat() * 0.02f;
		lifetime = 6 + random.nextInt(11);
		rCol = 1;
		gCol = 0.9f;
		bCol = 0.45f;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public void tick() {
		super.tick();
		float life = (float) age / lifetime;
		gCol = 0.9f - 0.6f * life;
		bCol = 0.45f * (1 - life) * (1 - life);
		rCol = 1 - 0.25f * life * life;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
									   double xSpeed, double ySpeed, double zSpeed) {
			SparkParticle spark = new SparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
			spark.pickSprite(sprites);
			return spark;
		}
	}
}
