package com.createengines.client.particle;

import com.createengines.content.effects.GasParticleOptions;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * A puff of gas. It leaves with the speed it is given and soon loses it, rises as fast as it is hot, swells to twice
 * the size it started and thins away as it goes. Each puff is one of a few white clouds drawn like vanilla's campfire
 * smoke ({@code tools/generate_textures.py}), tinted to its colour. Underwater it is gone at once: what comes out of
 * an opening there is bubbles.
 */
public class GasParticle extends TextureSheetParticle {

	/** Half the width, in blocks, a puff of scale 1 swells to: three times a puff of vanilla smoke. */
	private static final float FULL_SIZE = 0.3f;
	/** Share of its full size a puff starts at. */
	private static final float STARTING_SIZE = 0.45f;
	/** The upside-down gravity of vanilla smoke, which a lift of 1 matches. */
	private static final float VANILLA_SMOKE_LIFT = 0.25f;
	/** Ticks over which a new puff fades in, so it does not pop into being. */
	private static final float FADE_IN = 2;

	private final float solid;

	protected GasParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
						  GasParticleOptions options, SpriteSet sprites) {
		super(level, x, y, z);
		this.solid = options.alpha();
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		friction = 0.9f;
		gravity = -options.lift() * VANILLA_SMOKE_LIFT;
		// One puff is never quite the shade of the next
		float shade = 0.9f + random.nextFloat() * 0.2f;
		rCol = Mth.clamp(options.color()
			.x() * shade, 0, 1);
		gCol = Mth.clamp(options.color()
			.y() * shade, 0, 1);
		bCol = Mth.clamp(options.color()
			.z() * shade, 0, 1);
		alpha = 0;
		quadSize = FULL_SIZE * options.scale() * (0.85f + random.nextFloat() * 0.3f);
		lifetime = Math.max(2, Math.round(options.lifetime() * (0.8f + random.nextFloat() * 0.4f)));
		pickSprite(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public float getQuadSize(float partialTick) {
		float life = Mth.clamp((age + partialTick) / lifetime, 0, 1);
		return quadSize * (STARTING_SIZE + (1 - STARTING_SIZE) * (float) Math.sqrt(life));
	}

	@Override
	public void tick() {
		super.tick();
		if (removed)
			return;
		float life = (float) age / lifetime;
		alpha = solid * Math.min(1, age / FADE_IN) * (1 - life * life);
		if ((age & 3) == 0 && !level.getFluidState(BlockPos.containing(x, y, z))
			.isEmpty())
			remove();
	}

	public static class Provider implements ParticleProvider<GasParticleOptions> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(GasParticleOptions options, ClientLevel level, double x, double y, double z,
									   double xSpeed, double ySpeed, double zSpeed) {
			return new GasParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, sprites);
		}
	}
}
