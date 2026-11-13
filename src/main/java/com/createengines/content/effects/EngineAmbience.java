package com.createengines.content.effects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.createengines.config.CEClientConfig;
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

/**
 * What a whole engine sounds like besides its exhaust note, kept by its primary crankshaft on the client: the cough as it
 * catches and the knock and sigh as it dies, its valve gear ticking for as long as the crank turns, its bearings squealing
 * when they run dry, and its hot metal ticking as it cools after it stops. Only ever made and ticked on a client.
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
	private static final float CLATTER_LOUDEST = 0.2f;
	private static final float SQUEAL_LOUDEST = 0.12f;

	private final CrankshaftBlockEntity crank;
	private final Loop clatter = new Loop(false);
	private final Loop squeal = new Loop(true);
	private @Nullable EngineState last;
	private long caughtAt = Long.MIN_VALUE / 2;
	private long stalledAt = Long.MIN_VALUE / 2;

	public EngineAmbience(CrankshaftBlockEntity crank) {
		this.crank = crank;
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

		EngineCues cue = EngineCues.forChange(last, now);
		if (cue != EngineCues.NONE) {
			if (cue == EngineCues.CATCH) {
				if (time - caughtAt >= REPEAT_TICKS)
					// A big engine catches with a deeper cough than a single
					play(level, CESounds.ENGINE_CATCH.get(), 0.8f,
						1.12f - 0.02f * Math.min(12, summary.cylinders()) + random.nextFloat() * 0.06f);
				caughtAt = time;
			} else {
				if (time - stalledAt >= REPEAT_TICKS)
					play(level, CESounds.ENGINE_STALL.get(), 0.7f, 0.95f + random.nextFloat() * 0.1f);
				stalledAt = time;
			}
		}
		last = now;

		BlockPos at = crank.getBlockPos();
		if (turning) {
			if (clatter.loopLevel() > 0.02f)
				MachineLoopSound.ensurePlaying(clatter, at, CESounds.ENGINE_CLATTER.get(), CLATTER_LOUDEST, clatter);
			if (squeal.loopLevel() > 0.02f)
				MachineLoopSound.ensurePlaying(squeal, at, CESounds.BEARING_SQUEAL.get(), SQUEAL_LOUDEST, squeal);
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
			0.75f + random.nextFloat() * 0.6f, false);
	}

	private void play(Level level, SoundEvent sound, float loudness, float pitch) {
		float volume = CEClientConfig.mechanicalVolume();
		BlockPos at = crank.getBlockPos();
		if (volume <= 0 || level.getNearestPlayer(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, HEARING_RANGE, false) == null)
			return;
		level.playLocalSound(at, sound, SoundSource.BLOCKS, loudness * volume, pitch, false);
		LOGGER.debug("Engine at {} plays {}", at, sound.getLocation());
	}

	/** The valve gear's tick or the bearings' squeal, as loud and as high as the engine's figures say right now. */
	private final class Loop implements LoopSource {

		private final boolean squealing;

		private Loop(boolean squealing) {
			this.squealing = squealing;
		}

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
			return squealing ? MechanicalNoise.squealLevel(summary.rpm(), summary.lubrication())
				: MechanicalNoise.clatterLevel(summary.rpm(), summary.roughness(), summary.lubrication());
		}

		@Override
		public float loopPitch() {
			EngineSummary summary = crank.summary();
			float rpm = summary == null ? 0 : summary.rpm();
			return squealing ? MechanicalNoise.squealPitch(rpm) : MechanicalNoise.clatterPitch(rpm);
		}

		@Override
		public float loopVolume() {
			return CEClientConfig.mechanicalVolume();
		}
	}
}
