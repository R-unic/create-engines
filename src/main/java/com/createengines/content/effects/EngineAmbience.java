package com.createengines.content.effects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.createengines.config.CEClientConfig;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.LoopSource;
import com.createengines.content.MachineLoopSound;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Thermal;
import com.createengines.registry.CESounds;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a whole engine sounds like besides its exhaust note, kept by its primary crankshaft on the client: the cough as it
 * catches and the knock and sigh as it dies, its bearings squealing when they run dry, and its hot metal ticking as it cools after it stops. Only ever made and ticked on a client.
 */
public final class EngineAmbience {

	private static final Logger LOGGER = LogUtils.getLogger();
	/** Ticks after catching for which an engine is clearing its throat: its exhaust is thick and dark for this long. */
	private static final int CATCH_TICKS = 30;
	/** Ticks before the catch or the stall is played again, for an engine that hunts between starting and dying. */
	private static final int REPEAT_TICKS = 60;
	/** How far away the one-off sounds are worth playing. */
	private static final double HEARING_RANGE = 48;
	private static final double TICKING_RANGE = 16;
	private static final float START_LEVEL = 0.05f;
	private static final float SQUEAL_LOUDEST = 0.12f;
	private static final float ROAR_LOUDEST = 0.35f;
	/** Ticks after a cylinder last reported an open intake mouth before the roar is taken to have lost it. */
	private static final int MOUTH_TICKS = 40;

	private final CrankshaftBlockEntity crank;
	private final Squeal squeal = new Squeal();
	private final Roar roar = new Roar();
	private @Nullable EngineState last;
	private long caughtAt = Long.MIN_VALUE / 2;
	private long stalledAt = Long.MIN_VALUE / 2;
	private long crankingSince;
	private float runningRpm;
	/** When the crank of a stall the note is playing comes to rest, and how that is to sound; null with none due. */
	private @Nullable StartStallTake settling;
	private long settleAt;

	public EngineAmbience(CrankshaftBlockEntity crank) {
		this.crank = crank;
	}

	/**
	 * A cylinder of this engine reports an intake mouth open to the air at {@code mouth}. The roar is heard from the most
	 * recent one, and goes quiet once none has been reported for a moment.
	 */
	public void offerIntake(Vec3 mouth) {
		Level level = crank.getLevel();
		if (level == null)
			return;
		roar.mouth = mouth;
		roar.seenAt = level.getGameTime();
	}

	/** Whether the engine caught a moment ago, so its cylinders cough out what pooled in them while it cranked. */
	public boolean catching() {
		Level level = crank.getLevel();
		return level != null && level.getGameTime() - caughtAt < CATCH_TICKS;
	}

	public void tick(EngineSummary summary) {
		Level level = crank.getLevel();
		if (level == null)
			return;
		EngineState now = summary.state();
		boolean turning = Math.abs(summary.rpm()) > 1 && now != EngineState.STALLED && now != EngineState.INCOMPLETE
			&& now != EngineState.BROKEN;
		long time = level.getGameTime();
		RandomSource random = level.random;

		EngineCues cue = EngineCues.forChange(last, now, summary.cause());
		if (now == EngineState.CRANKING && last != EngineState.CRANKING)
			crankingSince = time;
		if (now == EngineState.RUNNING || now == EngineState.OVERLOADED)
			runningRpm = summary.rpm();
		if (now != EngineState.STALLED) {
			settling = null;
			EngineNoteSound.endStall(crank.getBlockPos());
		} else if (settling != null && time >= settleAt) {
			// The note rocks its own crank to rest; the recording is only for a note lost part way through the stall
			if (!EngineNoteSound.isPlayingFor(crank.getBlockPos()))
				play(level, CESounds.ENGINE_SETTLE.get(), settling.loudness(), settling.pitch());
			settling = null;
		}
		if (cue != EngineCues.NONE) {
			if (cue == EngineCues.CATCH) {
				if (time - caughtAt >= REPEAT_TICKS) {
					StartStallTake take = StartStallTake.catching(summary.cylinders(), summary.sound().compression(),
						summary.temperature(), last == EngineState.CRANKING ? time - crankingSince : 0,
						random.nextFloat(), random.nextFloat());
					if (EngineNoteSound.perform(crank.getBlockPos(), take.kind()) < 0)
						play(level, take);
				}
				caughtAt = time;
			} else {
				if (time - stalledAt >= REPEAT_TICKS) {
					StartStallTake take = StartStallTake.stalling(cue, summary.cause(), summary.cylinders(),
						summary.sound().compression(), summary.temperature(), runningRpm,
						random.nextFloat(), random.nextFloat());
					int rest = EngineNoteSound.perform(crank.getBlockPos(), take.kind());
					if (rest >= 0) {
						settling = take;
						settleAt = time + rest;
					} else
						play(level, take);
				}
				if (cue == EngineCues.FLOOD && time - stalledAt >= REPEAT_TICKS)
						EngineEffects.floodedStall(level, at());
					// The load beat it: what was left in the cylinders and the pipe comes out in one dark belch
					if (cue == EngineCues.OVERLOAD)
						caughtAt = time;
					stalledAt = time;
			}
		}
		last = now;

		BlockPos at = crank.getBlockPos();
		if (turning) {
			if (squeal.loopLevel() > START_LEVEL)
				MachineLoopSound.ensurePlaying(squeal, at, CESounds.BEARING_SQUEAL.get(), SQUEAL_LOUDEST, squeal);
			if (roar.mouth != null && roar.loopLevel() > START_LEVEL)
				MachineLoopSound.ensurePlaying(roar, BlockPos.containing(roar.mouth), CESounds.INTAKE_ROAR.get(), ROAR_LOUDEST,
					roar);
		} else if (summary.cooling())
			cool(level, at, random, summary.temperature());
	}

	/**
	 * A stopped engine's metal shrinking as it cools, following the engine's real temperature: a tick now and then, softer
	 * and further apart as the block gives up its heat, and none once it has.
	 */
	private void cool(Level level, BlockPos at, RandomSource random, float temperature) {
		float volume = CEClientConfig.mechanicalVolume();
		if (volume <= 0 || random.nextDouble() >= Thermal.tickChance(temperature))
			return;
		float warmth = Mth.clamp((temperature - (float) Thermal.COOLING_FROM) / (float) (Thermal.NORMAL - Thermal.COOLING_FROM), 0, 1);
		volume *= 0.35f + 0.65f * warmth;
		double x = at.getX() + 0.5 + (random.nextDouble() - 0.5) * 3;
		double y = at.getY() + 0.5 + random.nextDouble() * 2;
		double z = at.getZ() + 0.5 + (random.nextDouble() - 0.5) * 3;
		if (level.getNearestPlayer(x, y, z, TICKING_RANGE, false) == null)
			return;
		level.playLocalSound(x, y, z, CESounds.METAL_TICK.get(), SoundSource.BLOCKS, (0.2f + random.nextFloat() * 0.3f) * volume,
			Pitches.near(1.25f, random.nextFloat()), false);
	}

	private BlockPos at() {
		return crank.getBlockPos();
	}

	private void play(Level level, StartStallTake take) {
		SoundEvent sound = (switch (take.kind()) {
			case CATCH -> CESounds.ENGINE_CATCH;
			case HARD_CATCH -> CESounds.ENGINE_CATCH_HARD;
			case STALL -> CESounds.ENGINE_STALL;
			case LUGGED_STALL -> CESounds.ENGINE_STALL_LUGGED;
			case STARVED_STALL -> CESounds.ENGINE_STALL_STARVED;
		}).get();
		play(level, sound, take.loudness(), take.pitch());
	}

	private void play(Level level, SoundEvent sound, float loudness, float pitch) {
		float volume = CEClientConfig.mechanicalVolume();
		BlockPos at = crank.getBlockPos();
		if (volume <= 0 || level.getNearestPlayer(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, HEARING_RANGE, false) == null)
			return;
		level.playLocalSound(at, sound, SoundSource.BLOCKS, loudness * volume, pitch, false);
		LOGGER.debug("Engine at {} plays {}", at, sound.getLocation());
	}

	/**
	 * The gulp of air at the intake mouth: one voice for the engine, swelling with speed, load and boost, and quickening
	 * with the pulses of its cylinders. Heard from the mouth, not the block, and unaffected by a muffler.
	 */
	private final class Roar implements LoopSource {

		private @Nullable Vec3 mouth;
		private long seenAt = Long.MIN_VALUE / 2;

		@Override
		public boolean isRemoved() {
			return crank.isRemoved();
		}

		@Override
		public float loopLevel() {
			EngineSummary summary = crank.summary();
			Level level = crank.getLevel();
			if (summary == null || level == null || mouth == null || level.getGameTime() - seenAt > MOUTH_TICKS
				|| summary.state() == EngineState.STALLED || summary.state() == EngineState.INCOMPLETE
				|| summary.state() == EngineState.BROKEN)
				return 0;
			float load = summary.availableTorque() > 0 ? Mth.clamp(summary.loadTorque() / summary.availableTorque(), 0, 1.3f) : 0;
			float roar = MechanicalNoise.intakeRoarLevel(summary.rpm(), load, summary.boost());
			// An engine that is only turned over, or coasts, pumps air without a pull on it
			return summary.state() == EngineState.COASTING || summary.state() == EngineState.CRANKING ? roar * 0.4f : roar;
		}

		@Override
		public float loopPitch() {
			EngineSummary summary = crank.summary();
			return summary == null ? 1 : MechanicalNoise.intakeRoarPitch(summary.rpm(), summary.cylinders());
		}

		@Override
		public @Nullable Vec3 loopAt() {
			return mouth;
		}

		@Override
		public float loopSpinUp() {
			return 0.15f;
		}

		@Override
		public float loopSpinDown() {
			return 0.15f;
		}

		@Override
		public float loopVolume() {
			return CEClientConfig.mechanicalVolume();
		}
	}

	/** The bearings' squeal, as loud and as high as the engine's figures say right now. */
	private final class Squeal implements LoopSource {

		@Override
		public boolean isRemoved() {
			return crank.isRemoved();
		}

		@Override
		public float loopLevel() {
			EngineSummary summary = crank.summary();
			if (summary == null || summary.state() == EngineState.STALLED || summary.state() == EngineState.INCOMPLETE
				|| summary.state() == EngineState.BROKEN)
				return 0;
			return MechanicalNoise.squealLevel(summary.rpm(), summary.lubrication());
		}

		@Override
		public float loopPitch() {
			EngineSummary summary = crank.summary();
			return MechanicalNoise.squealPitch(summary == null ? 0 : summary.rpm());
		}

		@Override
		public float loopVolume() {
			return CEClientConfig.mechanicalVolume();
		}
	}
}
