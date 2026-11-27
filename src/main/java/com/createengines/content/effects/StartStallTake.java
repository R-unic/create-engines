package com.createengines.content.effects;

import com.createengines.engine.Diagnosis;
import com.createengines.engine.sim.Thermal;

/**
 * How one catch or stall is to sound: which set of recordings, and how high and loud, from the engine and from how it got
 * there. Kept apart from the sound engine so it can be tested. Only the sound: nothing here changes how the engine runs.
 *
 * @param kind     which recordings
 * @param pitch    playback pitch: lower for more cylinders, for compression ignition and for a cold block
 * @param loudness before the player's own volume setting: louder for more cylinders, and for a stall from speed
 */
public record StartStallTake(Kind kind, float pitch, float loudness) {

	public enum Kind {
		/** Takes hold readily. */
		CATCH,
		/** Cold, or long on the starter: labours through many coughs before it takes hold. */
		HARD_CATCH,
		/** Winds down and rocks to a stop. */
		STALL,
		/** The load dragged it to a halt: heavy, slowing thuds into a hard stop. */
		LUGGED_STALL,
		/** It ran out of fuel, or got too little: a long, thin sputter. */
		STARVED_STALL
	}

	/** Ticks on the starter after which even a warm engine's catch is a hard one. */
	static final int LONG_CRANK_TICKS = 60;
	/** The speed from which a dying engine is at its loudest. */
	static final float FAST_RPM = 128;
	/** As many cylinders as still make a difference to the sound. */
	private static final int BIGGEST = 12;
	private static final float PITCH_JITTER = Pitches.SPREAD;
	private static final float LOUDNESS_JITTER = 0.08f;

	/**
	 * @param temperature  the block's as it catches, as in the engine's summary
	 * @param crankedTicks how long the starter had been turning it; 0 if it caught without one
	 * @param pitchRoll    a random number from 0 to 1, so no two takes are quite alike
	 * @param loudnessRoll another
	 */
	public static StartStallTake catching(int cylinders, boolean compression, float temperature, long crankedTicks,
		float pitchRoll, float loudnessRoll) {
		float cold = coldness(temperature);
		Kind kind = cold > 0 || crankedTicks >= LONG_CRANK_TICKS ? Kind.HARD_CATCH : Kind.CATCH;
		return new StartStallTake(kind, pitch(cylinders, compression, cold, pitchRoll),
			jitter(0.68f + 0.24f * size(cylinders), LOUDNESS_JITTER, loudnessRoll));
	}

	/**
	 * @param cue        {@code STALL}, {@code FLOOD} or {@code OVERLOAD}
	 * @param cause      what the engine says killed it
	 * @param runningRpm how fast it last turned while it was still burning
	 */
	public static StartStallTake stalling(EngineCues cue, Diagnosis.Cause cause, int cylinders, boolean compression,
		float temperature, float runningRpm, float pitchRoll, float loudnessRoll) {
		Kind kind = cue == EngineCues.OVERLOAD ? Kind.LUGGED_STALL
			: cue == EngineCues.STALL && (cause == Diagnosis.Cause.NO_FUEL || cause == Diagnosis.Cause.TOO_LEAN)
				? Kind.STARVED_STALL
				: Kind.STALL;
		float speed = Math.clamp(Math.abs(runningRpm) / FAST_RPM, 0, 1);
		return new StartStallTake(kind, pitch(cylinders, compression, coldness(temperature), pitchRoll),
			jitter(0.5f + 0.2f * size(cylinders) + 0.2f * speed, LOUDNESS_JITTER, loudnessRoll));
	}

	/** 0 for a single, 1 for a twelve and up. */
	private static float size(int cylinders) {
		return (Math.clamp(cylinders, 1, BIGGEST) - 1) / (float) (BIGGEST - 1);
	}

	/** 0 for a block warm enough not to count as cold, 1 for one at ambient. */
	private static float coldness(float temperature) {
		return (float) Math.clamp((Thermal.COLD_BELOW - temperature) / Thermal.COLD_BELOW, 0, 1);
	}

	private static float pitch(int cylinders, boolean compression, float cold, float roll) {
		float pitch = 1.14f - 0.28f * (float) Math.sqrt(size(cylinders));
		if (compression)
			pitch *= 0.93f;
		return jitter(pitch * (1 - 0.04f * cold), PITCH_JITTER, roll);
	}

	private static float jitter(float value, float share, float roll) {
		return value * (1 + share * (2 * roll - 1));
	}
}
