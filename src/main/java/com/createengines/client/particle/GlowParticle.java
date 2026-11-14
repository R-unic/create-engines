package com.createengines.client.particle;

import com.createengines.content.effects.GlowParticleOptions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * A soft blob that blends from one colour to another as it ages: the flame, hot charge and air inside a cylinder. The
 * additive kind is added to what is behind it, so overlapping blobs build up to white-hot like real flame, and is lit
 * as brightly as fire whatever the light. The other kind is plain haze.
 */
public class GlowParticle extends TextureSheetParticle {

	/** Blobs added to what is behind them, so crowds of them brighten instead of covering each other. */
	private static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
			RenderSystem.depthMask(false);
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
				com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}
	};

	private final GlowParticleOptions options;

	protected GlowParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
						   GlowParticleOptions options, SpriteSet sprites) {
		super(level, x, y, z);
		this.options = options;
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		friction = 0.88f;
		gravity = 0;
		hasPhysics = false;
		quadSize = options.size() * (0.8f + random.nextFloat() * 0.4f);
		lifetime = Math.max(2, Math.round(options.lifetime() * (0.8f + random.nextFloat() * 0.4f)));
		roll = oRoll = random.nextFloat() * Mth.TWO_PI;
		rCol = options.from()
			.x();
		gCol = options.from()
			.y();
		bCol = options.from()
			.z();
		alpha = 0;
		pickSprite(sprites);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return options.additive() ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return options.additive() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float life = Mth.clamp((age + partialTick) / lifetime, 0, 1);
		return quadSize * Mth.lerp(life, 1, options.growth());
	}

	@Override
	public void tick() {
		// Swirling turbulence: a smooth flow field that is nearly the same for neighbouring blobs, so a crowd of them
		// moves as one eddying cloud instead of a spray of unrelated dots
		double t = age * 0.35;
		double push = 0.006 + quadSize * 0.05;
		xd += Math.sin(y * 19 + t + z * 7) * push;
		yd += Math.sin(z * 17 + t * 1.3 + x * 5) * push;
		zd += Math.sin(x * 21 + t * 0.8 + y * 6) * push;
		oRoll = roll;
		roll += 0.04f * (random.nextFloat() - 0.3f);
		super.tick();
		if (removed)
			return;
		float life = (float) age / lifetime;
		rCol = Mth.lerp(life, options.from()
			.x(), options.to()
				.x());
		gCol = Mth.lerp(life, options.from()
			.y(), options.to()
				.y());
		bCol = Mth.lerp(life, options.from()
			.z(), options.to()
				.z());
		float fadeOut = 1 - life * life;
		alpha = options.alpha() * Math.min(1, (age + 1) / 2f) * fadeOut;
	}

	public static class Provider implements ParticleProvider<GlowParticleOptions> {

		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(GlowParticleOptions options, ClientLevel level, double x, double y, double z,
									   double xSpeed, double ySpeed, double zSpeed) {
			return new GlowParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, sprites);
		}
	}
}
