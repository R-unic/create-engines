package com.createengines.content.effects;

import com.createengines.config.CEClientConfig;
import com.createengines.registry.CEParticles;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The fire an engine throws: licks of flame, showers of sparks and the fireball of a backfire. Client side only in
 * effect, since it only adds particles. All of it flickers, so the photosensitive setting leaves every piece out.
 */
public final class ExhaustFire {

	private ExhaustFire() {
	}

	/**
	 * A lick of flame out along {@code normal}: a short-lived glow at the mouth, {@code count} tongues strung out at
	 * different speeds around {@code speed}, and once in a while a stray ember.
	 */
	public static void flames(Level level, Vec3 at, Vec3 normal, int count, double speed) {
		if (CEClientConfig.photosensitive())
			return;
		RandomSource random = level.random;
		glow(level, at, normal.scale(0.03), GlowParticleOptions.fire(0xFFB848, 0xE03000, 0.08f, 0.6f, 1.5f, 4));
		for (int i = 0; i < count; i++)
			tongue(level, random, at, normal.scale(speed * (0.55 + 0.9 * (i + random.nextDouble()) / count)), speed * 0.3);
		if (random.nextInt(12) == 0)
			sparks(level, at, normal.scale(speed * 1.3), 1, 0.06);
	}

	/** Sparks thrown from {@code at}, {@code push} their common velocity and {@code scatter} how far each strays from it. */
	public static void sparks(Level level, Vec3 at, Vec3 push, int count, double scatter) {
		if (CEClientConfig.photosensitive())
			return;
		RandomSource random = level.random;
		for (int i = 0; i < count; i++) {
			// Some barely leave and some shoot far past the rest, as grinding sparks do
			Vec3 velocity = push.scale(0.5 + random.nextDouble() * 1.1)
				.add(spread(random, scatter * 2));
			level.addParticle(CEParticles.SPARK.get(), at.x, at.y, at.z, velocity.x, velocity.y + scatter * 0.5, velocity.z);
		}
	}

	/**
	 * The fireball of unburned fuel going off in a pipe: a white flash at the mouth, a cone of flame, a few sparks
	 * and the dirty puff it leaves behind.
	 */
	public static void backfire(Level level, Vec3 at, Vec3 normal) {
		if (CEClientConfig.photosensitive())
			return;
		RandomSource random = level.random;
		glow(level, at, Vec3.ZERO, GlowParticleOptions.fire(0xFFE0A0, 0xFF6000, 0.3f, 0.85f, 1.6f, 3));
		glow(level, at.add(normal.scale(0.25)), normal.scale(0.09),
			GlowParticleOptions.fire(0xFF9828, 0xB01800, 0.2f, 0.55f, 1.8f, 5));
		for (int i = 0; i < 8; i++)
			tongue(level, random, at, normal.scale(0.14 + random.nextDouble() * 0.28), 0.18);
		sparks(level, at, normal.scale(0.25), 3, 0.1);
		GasParticleOptions soot = new GasParticleOptions(0.16f, 0.14f, 0.13f, 0.9f, 0.6f, 0.4f, 26);
		for (int i = 0; i < 3; i++) {
			Vec3 velocity = normal.scale(0.07 + 0.04 * i)
				.add(spread(random, 0.05));
			level.addParticle(soot, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private static void tongue(Level level, RandomSource random, Vec3 at, Vec3 push, double cone) {
		Vec3 velocity = push.add(spread(random, cone));
		Vec3 from = at.add(spread(random, 0.08));
		level.addParticle(CEParticles.EXHAUST_FLAME.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
	}

	private static void glow(Level level, Vec3 at, Vec3 velocity, GlowParticleOptions options) {
		level.addParticle(options, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
	}

	private static Vec3 spread(RandomSource random, double size) {
		return new Vec3((random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size,
			(random.nextDouble() - 0.5) * size);
	}
}
