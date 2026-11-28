package com.createengines.client.particle;

import com.createengines.content.effects.FlameMotion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A tongue of flame thrown out of an exhaust. It leaves fast, drawn out into a jet, swells, then slows, curls upward
 * and breaks into rags, cooling from pale yellow through orange and red to a sooty brown, all in under half a second.
 * Its frames are drawn by {@code tools/generate_textures.py}.
 */
public class ExhaustFlameParticle extends StreakParticle {

	private final SpriteSet sprites;
	private final float width;

	protected ExhaustFlameParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
								   double zSpeed, SpriteSet sprites) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		this.sprites = sprites;
		friction = 0.8f;
		gravity = -0.25f;
		hasPhysics = false;
		width = 0.08f + random.nextFloat() * 0.065f;
		quadSize = width;
		lifetime = 5 + random.nextInt(5);
		colour(0);
		setSpriteFromAge(sprites);
	}

	@Override
	protected double halfLength(double halfWidth, double speed) {
		return FlameMotion.tongueHalfLength(halfWidth, speed);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float life = Mth.clamp((age + partialTick) / lifetime, 0, 1);
		// Swells out of the mouth, then burns away
		return width * (life < 0.3f ? 0.6f + life / 0.3f * 0.4f : 1 - (life - 0.3f) * 0.75f);
	}

	@Override
	public void tick() {
		super.tick();
		if (removed)
			return;
		// Flicker: the tail of a jet never holds still
		xd += (random.nextFloat() - 0.5f) * 0.02;
		yd += (random.nextFloat() - 0.5f) * 0.02;
		zd += (random.nextFloat() - 0.5f) * 0.02;
		colour((float) age / lifetime);
		setSpriteFromAge(sprites);
	}

	private void colour(float life) {
		float[] colour = FlameMotion.flameColour(life);
		rCol = colour[0];
		gCol = colour[1];
		bCol = colour[2];
		alpha = life < 0.6f ? 1 : 1 - (life - 0.6f) * 1.6f;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
									   double xSpeed, double ySpeed, double zSpeed) {
			return new ExhaustFlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
		}
	}
}
