package com.createengines.client.particle;

import java.util.List;

import com.createengines.content.effects.FlameMotion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A spark of hot metal, drawn as a streak as long as it is fast. It falls, bounces and skids off what it hits, cools
 * from white through yellow and orange to a dull red, and flickers before it goes out. Water puts it out.
 */
public class SparkParticle extends StreakParticle {

	private static final float DRAG = 0.96f;
	private static final double FALL = 0.036;

	protected SparkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
							double zSpeed) {
		super(level, x, y, z, xSpeed, ySpeed, zSpeed);
		setSize(0.02f, 0.02f);
		quadSize = 0.014f + random.nextFloat() * 0.012f;
		// A few fly on long enough to bounce about on the floor
		lifetime = random.nextInt(5) == 0 ? 20 + random.nextInt(12) : 7 + random.nextInt(10);
		colour(0);
	}

	@Override
	protected double halfLength(double width, double speed) {
		return FlameMotion.sparkHalfLength(width, speed);
	}

	/** Moves and collides by hand: vanilla's particles stop for good where they land, and a spark bounces. */
	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime || !level.getFluidState(BlockPos.containing(x, y, z))
			.isEmpty()) {
			remove();
			return;
		}
		yd -= FALL;
		Vec3 wanted = new Vec3(xd, yd, zd);
		Vec3 moved = Entity.collideBoundingBox(null, wanted, getBoundingBox(), level, List.of());
		setBoundingBox(getBoundingBox().move(moved));
		setLocationFromBoundingbox();
		double[] after = FlameMotion.bounce(new double[] {wanted.x, wanted.y, wanted.z},
			new double[] {moved.x, moved.y, moved.z});
		xd = after[0] * DRAG;
		yd = after[1] * DRAG;
		zd = after[2] * DRAG;
		colour((float) age / lifetime);
	}

	private void colour(float life) {
		float[] colour = FlameMotion.sparkColour(life);
		rCol = colour[0];
		gCol = colour[1];
		bCol = colour[2];
		alpha = life > 0.65f && random.nextBoolean() ? 0.45f : 1;
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
