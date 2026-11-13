package com.createengines.content.effects;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
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
		if (!CEClientConfig.particles())
			return;
		Opening outlet = pick(level, status.exhaustOutlets());
		if (outlet == null)
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

		float roll = random.nextFloat();
		if (roll < look.pop())
			afterfire(level, outlet.at, outlet.normal, 0.7f * Muffling.volume(engine == null ? 0 : engine.muffling()), 1);
		else if (roll < look.pop() + look.flame())
			flames(level, outlet.at, outlet.normal, 2, 0.14);
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

	/** Tongues of flame out along {@code normal}. */
	public static void flames(Level level, Vec3 at, Vec3 normal, int count, double speed) {
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
		if (inlet == null)
			return;
		if (inlet.flooded) {
			bubble(level, inlet);
			return;
		}
		if (CylinderFault.intakeShut(status.faults()) || !inlet.open)
			return;
		RandomSource random = level.random;
		float rev = engine == null ? 0.3f : Mth.clamp(Math.abs(engine.rpm()) / TOP_RPM, 0, 1);
		GasParticleOptions air = new GasParticleOptions(0.9f, 0.92f, 0.95f, 0.32f, 0.45f, 0, 10);
		int wisps = rev > 0.5f ? 3 : 2;
		for (int i = 0; i < wisps; i++) {
			Vec3 at = inlet.at.add(inlet.normal.scale(0.4 + random.nextDouble() * 0.3))
				.add(jitter(random, 0.45));
			// Aimed at the middle of the mouth, so wisps from all round it converge as they are drawn in
			Vec3 velocity = inlet.at.subtract(at)
				.normalize()
				.scale(0.07 + 0.07 * rev);
			level.addParticle(air, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
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
			normal, true, false));
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
	 * The heat coming off a head or a rotor housing, shown above the block: rain boils off it with a hiss while it is
	 * hot, and a running engine with no coolant to keep it down shimmers. Meant to be called a few times a second.
	 */
	public static void heat(Level level, BlockPos block, @Nullable EngineSummary engine) {
		if (engine == null || engine.temperature() < STEAMING || !CEClientConfig.particles())
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
		if (level.isRainingAt(above)) {
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
		// A block still giving up its heat shimmers faintly, and a little longer than it takes to stop being hot
		boolean shimmers = turning ? engine.temperature() >= SHIMMERING : engine.cooling() && engine.temperature() >= SHIMMERING - 0.12f;
		if (shimmers && random.nextFloat() < (turning ? 0.12f : 0.05f))
			level.addParticle(new GasParticleOptions(0.9f, 0.9f, 0.9f, 0.5f, 0.24f, 0.7f, 26), x, y, z, 0, 0.02, 0);
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
			PortOpeningBlock.isOpen(level, pos, state));
	}

	/** An opening's mouth: the point just in front of it, the way it faces, and whether anything can pass. */
	private record Opening(Vec3 at, Vec3 normal, boolean flooded, boolean open) {
	}

	private static Vec3 jitter(RandomSource random, double size) {
		return new Vec3((random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size,
			(random.nextDouble() - 0.5) * size);
	}
}
