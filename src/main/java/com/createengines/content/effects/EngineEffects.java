package com.createengines.content.effects;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.client.RainCaps;
import com.createengines.config.CEClientConfig;
import com.createengines.content.raincap.RainCapBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Muffling;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.Thermal;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEParticles;
import com.createengines.registry.CESounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a running engine throws into the world around it: exhaust at its outlets, the draught at its intakes, steam and
 * heat off its heads, sparks. Called from the blocks' client ticks, at the moments their moving parts say, so everything
 * here only reads the level and adds particles and sounds to it.
 */
public final class EngineEffects {

	/** How far in front of an opening's centre its gas appears: just clear of its mouth. */
	private static final double MOUTH = 0.55;
	/** Block temperature above which rain steams off a head, and above which a running one shimmers. */
	private static final float STEAMING = 0.35f;
	private static final float SHIMMERING = 0.62f;
	/** Cylinders past which each firing throws fewer puffs. */
	private static final int SPARING_FROM = 4;
	/** Create's usual speed limit, against which an engine's speed is reckoned for effects. */
	private static final float TOP_RPM = 256;

	private EngineEffects() {
	}

	/**
	 * One cylinder's exhaust stroke, out of one of the outlets its exhaust reaches: smoke as {@link ExhaustLook} says
	 * the charge burned, now and then with flame, or the crack of an afterfire. An outlet underwater bubbles instead.
	 *
	 * @param crank a crankshaft block of the cylinder's engine, for what the engine is doing; without one the smoke
	 *              is that of a warm engine on a middling load
	 */
	public static void exhaust(Level level, CylinderStatus status, @Nullable CrankshaftBlockEntity crank) {
		Opening outlet = pick(level, status.exhaustOutlets());
		// A rain cap on the pipe's end flaps with the pulse, whether or not there is smoke to see
		if (outlet != null && !outlet.flooded && outlet.open && !CylinderFault.exhaustShut(status.faults())
			&& level.getBlockState(outlet.cap)
			.getBlock() instanceof RainCapBlock)
			RainCaps.pulse(level, outlet.cap);
		if (!CEClientConfig.particles())
			return;
		// Past 48 blocks nothing below can be seen (particles) or heard (the knock and afterfire are quieter than the
		// bang, which already stops at 48), so none of it is built
		if (outlet == null || !nearViewer(level, outlet.at.x, outlet.at.y, outlet.at.z, 48))
			return;
		if (outlet.flooded) {
			bubble(level, outlet);
			return;
		}
		if (CylinderFault.exhaustShut(status.faults()) || !outlet.open)
			return;

		EngineSummary engine = crank == null ? null : crank.engineSummary();
		ExhaustLook look = ExhaustLook.of(describe(status, engine, crank != null && crank.catching()));
		if (look.puffs() == 0)
			return;
		RandomSource random = level.random;
		GasParticleOptions gas = new GasParticleOptions(look.red(), look.green(), look.blue(), look.scale(), look.alpha(),
			look.lift(), look.lifetime());
		// A big engine fires so often that its puffs run together: each firing throws fewer, so sixteen cylinders make
		// a thicker plume than four without making four times the particles
		if (engine != null && SmokeRing.rings(engine.cylinders(), engine.rpm(), status.rich(),
			engine.availableTorque() > 0 ? engine.loadTorque() / engine.availableTorque() : 0)) {
			ring(level, outlet, look);
			return;
		}
		int puffs = look.puffs();
		if (engine != null && engine.cylinders() > SPARING_FROM)
			puffs = Math.max(1, Math.round(puffs * (float) SPARING_FROM / engine.cylinders()));
		for (int i = 0; i < puffs; i++) {
			// Each puff of a firing leaves a little slower than the one before, so they string out along the way they go
			Vec3 velocity = outlet.normal.scale(look.speed() * (1.3 - 0.75 * i / puffs + random.nextDouble() * 0.2))
				.add(jitter(random, 0.02));
			Vec3 at = outlet.at.add(jitter(random, 0.2));
			level.addParticle(gas, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}

		// Boost lit the charge early: a rattle in the pipe and orange flashes
		if (random.nextFloat() < ExhaustLook.preignitionRattle(status.preignition())) {
			flames(level, outlet.at, outlet.normal, 2, 0.18);
			float volume = CEClientConfig.mechanicalVolume();
			if (volume > 0)
				level.playLocalSound(outlet.at.x, outlet.at.y, outlet.at.z, CESounds.PREIGNITION_KNOCK.get(),
					SoundSource.BLOCKS, 0.3f * volume, 0.9f + random.nextFloat() * 0.3f, false);
		}

		float roll = random.nextFloat();
		if (roll < look.pop())
			afterfire(level, outlet.at, outlet.normal, 0.7f * Muffling.volume(engine == null ? 0 : engine.muffling()), 1);
		else if (roll < look.pop() + look.flame())
			flames(level, outlet.at, outlet.normal, look.flame() > 0.4f ? 3 : 2, 0.14);
	}

	/** A slow single's firing, as a ring of puffs that travels out of the pipe and widens as it goes. */
	private static void ring(Level level, Opening outlet, ExhaustLook look) {
		double[][] ring = SmokeRing.velocities(new double[] {outlet.normal.x, outlet.normal.y, outlet.normal.z}, 0.06, 0.018);
		// Small, dense puffs, so the ring reads as a ring and not a cloud, and hanging on for longer than the usual smoke
		GasParticleOptions gas = new GasParticleOptions(look.red(), look.green(), look.blue(), 0.45f,
			Math.min(0.9f, look.alpha() + 0.15f), look.lift() * 0.4f, look.lifetime() + 14);
		for (double[] v : ring)
			level.addParticle(gas, outlet.at.x, outlet.at.y, outlet.at.z, v[0], v[1], v[2]);
	}

	private static ExhaustLook.Engine describe(CylinderStatus status, @Nullable EngineSummary engine, boolean catching) {
		CycleResult result = status.result();
		if (engine == null)
			return new ExhaustLook.Engine(result, status.rich(), status.lean(), status.mixture(), 0.5f, 0.3f,
				(float) Thermal.NORMAL, 0, result.fired(), false);
		float load = engine.availableTorque() > 0 ? Mth.clamp(engine.loadTorque() / engine.availableTorque(), 0, 1.3f) : 0;
		boolean burning = engine.state() == EngineState.RUNNING || engine.state() == EngineState.OVERLOADED;
		return new ExhaustLook.Engine(result, status.rich(), status.lean(), status.mixture(), load,
			Math.abs(engine.rpm()) / TOP_RPM, engine.temperature(), engine.thinning(), burning && engine.firing() > 0,
			catching);
	}

	/**
	 * Unburned fuel going off in a hot pipe: a crack, a burst of flame and a scatter of sparks, out along {@code normal}.
	 * The crack is one of the engine's bangs, so it follows that volume setting.
	 */
	public static void afterfire(Level level, Vec3 at, Vec3 normal, float loudness, float pitch) {
		RandomSource random = level.random;
		float volume = CEClientConfig.bangVolume();
		if (volume > 0)
			level.playLocalSound(at.x, at.y, at.z, CESounds.EXHAUST_POP.get(), SoundSource.BLOCKS, loudness * volume,
				pitch * (0.9f + random.nextFloat() * 0.25f), false);
		if (!CEClientConfig.particles())
			return;
		flames(level, at, normal, 4, 0.24);
		sparks(level, at, normal.scale(0.22), 3, 0.1);
	}

	/** An engine drowning: a burst of white steam from the block at {@code at} and a hard clunk as it seizes up on water. */
	public static void floodedStall(Level level, BlockPos at) {
		RandomSource random = level.random;
		float volume = CEClientConfig.mechanicalVolume();
		if (volume > 0) {
			level.playLocalSound(at, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.8f * volume, 0.6f, false);
			level.playLocalSound(at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f * volume, 0.8f, false);
			level.playLocalSound(at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f * volume, 0.5f, false);
		}
		if (!CEClientConfig.particles())
			return;
		for (int i = 0; i < 14; i++) {
			Vec3 from = Vec3.atCenterOf(at)
				.add(jitter(random, 1.6));
			level.addParticle(new GasParticleOptions(0.95f, 0.95f, 0.96f, 1.2f, 0.7f, 0.4f, 30), from.x, from.y, from.z,
				(random.nextDouble() - 0.5) * 0.08, 0.05 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.08);
		}
	}

	/** Oil smoke breathing out of the crankcase of an engine whose bearings are running dry, around the crank at {@code at}. */
	public static void crankcaseSmoke(Level level, Vec3 at) {
		if (!CEClientConfig.particles())
			return;
		RandomSource random = level.random;
		Vec3 from = at.add(jitter(random, 0.9));
		level.addParticle(new GasParticleOptions(0.35f, 0.36f, 0.4f, 0.8f, 0.5f, 0.3f, 30), from.x, from.y, from.z, 0, 0.04, 0);
	}

	/** Tongues of flame out along {@code normal}. */
	public static void flames(Level level, Vec3 at, Vec3 normal, int count, double speed) {
		if (CEClientConfig.photosensitive())
			return;
		RandomSource random = level.random;
		for (int i = 0; i < count; i++) {
			Vec3 velocity = normal.scale(speed * (0.7 + random.nextDouble() * 0.6))
				.add(jitter(random, 0.05));
			Vec3 from = at.add(jitter(random, 0.1));
			level.addParticle(CEParticles.EXHAUST_FLAME.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
		}
	}

	/** Sparks thrown from {@code at}, {@code push} their common velocity and {@code scatter} how far each strays from it. */
	public static void sparks(Level level, Vec3 at, Vec3 push, int count, double scatter) {
		if (CEClientConfig.photosensitive())
			return;
		RandomSource random = level.random;
		for (int i = 0; i < count; i++) {
			Vec3 velocity = push.add(jitter(random, scatter * 2));
			level.addParticle(CEParticles.SPARK.get(), at.x, at.y, at.z, velocity.x, velocity.y + scatter * 0.5, velocity.z);
		}
	}

	/**
	 * One cylinder's intake stroke, at one of the intakes its air comes through: a wisp of the air in front of the mouth
	 * drawn into it, faster as the engine turns faster. An intake underwater bubbles instead, and a covered one is still.
	 */
	public static void draught(Level level, CylinderStatus status, @Nullable EngineSummary engine) {
		if (!CEClientConfig.particles())
			return;
		Opening inlet = pick(level, status.intakeInlets());
		// Everything below is particles, which the game drops beyond 32 blocks of the camera
		if (inlet == null || !nearViewer(level, inlet.at.x, inlet.at.y, inlet.at.z, 40))
			return;
		if (inlet.flooded) {
			bubble(level, inlet);
			return;
		}
		if (CylinderFault.intakeShut(status.faults()) || !inlet.open)
			return;
		RandomSource random = level.random;
		float rev = engine == null ? 0.3f : Mth.clamp(Math.abs(engine.rpm()) / TOP_RPM, 0, 1);
		// A charge too lean to light is still burning when the valve opens, and a lick of it spits back out of the mouth
		float spit = ExhaustLook.intakeSpit(status.result(), engine != null && engine.firing() > 0,
			engine == null ? 0 : engine.temperature());
		if (spit > 0 && random.nextFloat() < spit) {
			flames(level, inlet.at, inlet.normal, 2, 0.1);
			sparks(level, inlet.at, inlet.normal.scale(0.12), 2, 0.08);
		}
		// Burned gas with nowhere to go comes back out the way the air went in
		if (ExhaustLook.chuffsBack(CylinderFault.exhaustShut(status.faults()), false) && random.nextFloat() < 0.7f) {
			for (int i = 0; i < 2; i++) {
				Vec3 velocity = inlet.normal.scale(0.05 + random.nextDouble() * 0.03)
					.add(jitter(random, 0.02));
				Vec3 at = inlet.at.add(jitter(random, 0.2));
				level.addParticle(new GasParticleOptions(0.3f, 0.3f, 0.3f, 0.9f, 0.55f, 0.15f, 28), at.x, at.y, at.z, velocity.x,
					velocity.y, velocity.z);
			}
			return;
		}
		int wisps = rev > 0.5f ? 5 : 3;
		for (int i = 0; i < wisps; i++) {
			Vec3 at = inlet.at.add(inlet.normal.scale(0.3 + random.nextDouble() * 0.5))
				.add(jitter(random, 0.6));
			// Aimed at the middle of the mouth, so wisps from all round it converge as they are drawn in, and curling
			// round the mouth's axis as a vortex does, tighter the nearer they get
			Vec3 toMouth = inlet.at.subtract(at);
			Vec3 spin = inlet.normal.cross(toMouth.subtract(inlet.normal.scale(toMouth.dot(inlet.normal))));
			Vec3 velocity = toMouth.normalize()
				.scale(0.06 + 0.07 * rev)
				.add(spin.scale(0.18));
			// Cool and pale, brightening to white as it is drawn in and shrinking as the stream tightens
			level.addParticle(GlowParticleOptions.mist(0xCFE8FF, 0xF4FAFF, 0.1f, 0.38f, 0.35f, 11), at.x, at.y, at.z,
				velocity.x, velocity.y, velocity.z);
		}
	}

	/**
	 * Where the air of the cylinder with {@code status} is drawn in: the front of one of its intake mouths that is open to
	 * the air, or null if none is (every one capped, covered or underwater), in which case there is nothing to roar.
	 */
	public static @Nullable Vec3 openMouth(Level level, CylinderStatus status) {
		if (CylinderFault.intakeShut(status.faults()))
			return null;
		for (BlockPos pos : status.intakeInlets()) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof PortOpeningBlock && !PortOpeningBlock.isFlooded(level, pos, state)
				&& PortOpeningBlock.isOpen(level, pos, state))
				return Vec3.atCenterOf(pos)
					.add(Vec3.atLowerCornerOf(state.getValue(PortOpeningBlock.FACING)
						.getNormal())
						.scale(MOUTH));
		}
		return null;
	}

	/**
	 * An engine turning with an intake or an exhaust underwater: a stream of bubbles from one of the drowned mouths.
	 * Meant to be called a few times a second for as long as the crank turns.
	 */
	public static void flooded(Level level, CylinderStatus status) {
		if (!CEClientConfig.particles())
			return;
		Opening opening = pick(level, level.random.nextBoolean() ? status.intakeInlets() : status.exhaustOutlets());
		if (opening != null && opening.flooded)
			bubble(level, opening);
	}

	/**
	 * An engine turning underwater: bubbles off one of the faces of the cylinder, head or housing at {@code block} that
	 * has water against it. Meant to be called a few times a second for as long as the crank turns.
	 */
	public static void submerged(Level level, BlockPos block) {
		if (!CEClientConfig.particles())
			return;
		RandomSource random = level.random;
		Direction side = Direction.getRandom(random);
		if (level.getFluidState(block.relative(side))
			.isEmpty())
			return;
		Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
		bubble(level, new Opening(Vec3.atCenterOf(block)
			.add(normal.scale(MOUTH))
			.add(jitter(random, 0.6)
				.multiply(1 - Math.abs(normal.x), 1 - Math.abs(normal.y), 1 - Math.abs(normal.z))),
			normal, true, false, block.relative(side)));
	}

	/** What an opening underwater does when the engine turns: nothing gets through but a few bubbles. */
	private static void bubble(Level level, Opening opening) {
		RandomSource random = level.random;
		Vec3 from = opening.at.add(opening.normal.scale(0.12));
		for (int i = 0; i < 3; i++) {
			Vec3 at = from.add(jitter(random, 0.3));
			level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, opening.normal.x * 0.1, 0.06 + opening.normal.y * 0.1,
				opening.normal.z * 0.1);
		}
		if (random.nextInt(6) == 0)
			level.playLocalSound(from.x, from.y, from.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
				0.3f * CEClientConfig.mechanicalVolume(), 0.8f + random.nextFloat() * 0.4f, false);
	}

	/**
	 * A pinch of dust shaken loose from the top of a rough engine's block at {@code block}, falling off its edge. Meant to be
	 * called a few times a second; a smooth engine sheds none.
	 */
	public static void rockDust(Level level, BlockPos block, @Nullable EngineSummary engine) {
		if (engine == null || !CEClientConfig.particles() || !(engine.state() == EngineState.RUNNING
			|| engine.state() == EngineState.OVERLOADED) || !nearViewer(level, block)
			|| level.random.nextFloat() >= EngineRock.dustChance(engine.roughness()))
			return;
		RandomSource random = level.random;
		// Off a face with air against it, picked from a random start: dust inside or behind other blocks is never seen
		int start = random.nextInt(6);
		for (int i = 0; i < 6; i++) {
			Direction face = Direction.values()[(start + i) % 6];
			if (!level.getBlockState(block.relative(face))
				.isAir())
				continue;
			Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
			Vec3 at = Vec3.atCenterOf(block)
				.add(normal.scale(0.52))
				.add(jitter(random, 0.8)
					.multiply(1 - Math.abs(normal.x), 1 - Math.abs(normal.y), 1 - Math.abs(normal.z)));
			// Grey-brown flecks that fall away from the block and thin out
			level.addParticle(new GasParticleOptions(0.5f, 0.45f, 0.38f, 0.4f, 0.75f, -0.03f, 26), at.x, at.y, at.z,
				normal.x * 0.015, normal.y * 0.015 - 0.01, normal.z * 0.015);
			return;
		}
	}

	/**
	 * The heat coming off a head or a rotor housing, shown above the block: rain boils off it with a hiss while it is
	 * hot, and a running engine with no coolant to keep it down shimmers. Meant to be called a few times a second.
	 */
	public static void heat(Level level, BlockPos block, @Nullable EngineSummary engine) {
		if (engine == null || engine.temperature() < STEAMING || !CEClientConfig.particles() || !nearViewer(level, block))
			return;
		BlockPos above = block.above();
		if (!level.getBlockState(above)
			.isAir())
			return;
		RandomSource random = level.random;
		float heat = Math.min(1, engine.temperature() / (float) Thermal.NORMAL);
		double x = block.getX() + 0.15 + random.nextDouble() * 0.7;
		double y = block.getY() + 1.03;
		double z = block.getZ() + 0.15 + random.nextDouble() * 0.7;
		// Rain falls where the engine is in the world, which on a moving structure is not where its plot is
		if (level.isRainingAt(com.createengines.compat.sable.SableCompat.worldBlockPos(level, above))) {
			if (random.nextFloat() >= 0.9f * heat)
				return;
			level.addParticle(new GasParticleOptions(0.95f, 0.95f, 0.96f, 0.6f, 0.6f, 0.22f, 18), x, y, z,
				(random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
			if (random.nextFloat() < 0.08f)
				level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
					0.1f * CEClientConfig.mechanicalVolume(), 1.8f + random.nextFloat() * 0.4f, false);
			return;
		}
		boolean turning = engine.state() == EngineState.RUNNING || engine.state() == EngineState.OVERLOADED;
		// An overheating block steams, harder the hotter it is, and a critical one is heard boiling
		if (engine.temperature() >= Thermal.OVERHEATING) {
			boolean critical = engine.temperature() >= Thermal.CRITICAL;
			if (random.nextFloat() < (critical ? 0.5f : 0.25f))
				level.addParticle(new GasParticleOptions(0.95f, 0.95f, 0.96f, 0.7f, 0.4f, 0.5f, 24), x, y, z,
					(random.nextDouble() - 0.5) * 0.02, 0.05, (random.nextDouble() - 0.5) * 0.02);
			if (critical && random.nextFloat() < 0.03f)
				level.playLocalSound(x, y, z, com.createengines.registry.CESounds.COOLANT_BOIL.get(), SoundSource.BLOCKS,
					0.5f * CEClientConfig.mechanicalVolume(), 0.9f + random.nextFloat() * 0.2f, false);
		}
		// A block still giving up its heat shimmers faintly, and a little longer than it takes to stop being hot
		boolean shimmers = turning ? engine.temperature() >= SHIMMERING : engine.cooling() && engine.temperature() >= SHIMMERING - 0.12f;
		if (shimmers && random.nextFloat() < (turning ? 0.12f : 0.05f))
			level.addParticle(new GasParticleOptions(0.9f, 0.9f, 0.9f, 0.5f, 0.24f, 0.7f, 26), x, y, z, 0, 0.02, 0);
	}

	/**
	 * Whether anyone could see a particle at {@code at}. Ordinary particles more than 32 blocks from the camera are
	 * dropped by the game on spawn, so a cylinder farther than that from every player (40, to cover a third-person
	 * camera) skips building them. It only ever lets more work through, never less than the game would show.
	 */
	/**
	 * Every player counts as a viewer, a spectator included: {@code getNearestPlayer(.., false)} skips spectators, so someone
	 * flying about in spectator mode (or a camera such as the trailer's) saw no exhaust, steam or sparks at all.
	 */
	public static final java.util.function.Predicate<net.minecraft.world.entity.Entity> SPECTATORS_TOO = e -> true;

	private static boolean nearViewer(Level level, BlockPos at) {
		return nearViewer(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 40);
	}

	private static boolean nearViewer(Level level, double x, double y, double z, double range) {
		return level.getNearestPlayer(x, y, z, range, SPECTATORS_TOO) != null;
	}

	/** One of {@code openings} at random, as it stands in the world; null if it is no longer an opening. */
	private static @Nullable Opening pick(Level level, List<BlockPos> openings) {
		if (openings.isEmpty())
			return null;
		BlockPos pos = openings.get(level.random.nextInt(openings.size()));
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof PortOpeningBlock))
			return null;
		Direction mouth = state.getValue(PortOpeningBlock.FACING);
		Vec3 normal = Vec3.atLowerCornerOf(mouth.getNormal());
		return new Opening(Vec3.atCenterOf(pos)
			.add(normal.scale(MOUTH)), normal, PortOpeningBlock.isFlooded(level, pos, state),
			PortOpeningBlock.isOpen(level, pos, state), pos.relative(mouth));
	}

	/** An opening's mouth: the point just in front of it, the way it faces, and whether anything can pass. */
	private record Opening(Vec3 at, Vec3 normal, boolean flooded, boolean open, BlockPos cap) {
	}

	private static Vec3 jitter(RandomSource random, double size) {
		return new Vec3((random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size,
			(random.nextDouble() - 0.5) * size);
	}
}
