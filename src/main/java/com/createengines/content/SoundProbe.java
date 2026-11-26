package com.createengines.content;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Where the sound bench listens in on the game's engine sound: each firing the game draws, each sighting of a cam, the level
 * and pitch each tickable sound is set to, and each stretch of audio the sound engine reads from a stream. Nobody listens in
 * a player's game: with no {@link #listener} every call returns at once. Only the development harness sets one (the sound
 * bench, see docs/SMOKE.md). Client only.
 */
public final class SoundProbe {

	public interface Listener {
		void firing(BlockPos engine);

		void sighting(BlockPos engine, double cycleDegrees);

		void level(String voice, Object source, float volume, float pitch);

		void streamRead(Object stream, int samples);
	}

	/** Volatile: streams are read on the sound thread. */
	private static volatile @Nullable Listener listener;

	private SoundProbe() {
	}

	public static void listen(@Nullable Listener to) {
		listener = to;
	}

	/** Whether anyone is listening, for a caller that would have to look something up first. */
	public static boolean active() {
		return listener != null;
	}

	/** A firing is being drawn now by the engine whose primary crankshaft is at {@code engine}. */
	public static void firing(@Nullable BlockPos engine) {
		Listener to = listener;
		if (to != null && engine != null)
			to.firing(engine);
	}

	static void sighting(BlockPos engine, double cycleDegrees) {
		Listener to = listener;
		if (to != null)
			to.sighting(engine, cycleDegrees);
	}

	static void level(String voice, Object source, float volume, float pitch) {
		Listener to = listener;
		if (to != null)
			to.level(voice, source, volume, pitch);
	}

	static void streamRead(Object stream, int samples) {
		Listener to = listener;
		if (to != null)
			to.streamRead(stream, samples);
	}
}
