package com.createengines.content;

/**
 * How an engine passes between its own note and the shared room voice: both cross-fade over the same time at equal power,
 * so their powers always add to the engine's own and the change is in neither loudness nor timing. Pure Java, so it is
 * tested.
 */
final class NoteHandover {

	/** Ticks a note takes to come in or go out. */
	static final int CROSSFADE_TICKS = 16;
	/** Ticks a note is held before a nearer engine may take it, so walking between two engines does not swap them to and fro. */
	static final int HOLD_TICKS = 60;

	private NoteHandover() {
	}

	/** A note's place in its fade, 0 (silent) to 1, one tick on. */
	static float step(float fade, boolean in) {
		return Math.clamp(fade + (in ? 1f : -1f) / CROSSFADE_TICKS, 0, 1);
	}

	/** The share of its level the note is played at, {@code fade} of the way in. */
	static float noteGain(float fade) {
		return (float) Math.sin(Math.clamp(fade, 0, 1) * Math.PI / 2);
	}

	/** The share of its level the room voice plays the same engine at, so the two add to its whole power. */
	static float roomGain(float fade) {
		return (float) Math.cos(Math.clamp(fade, 0, 1) * Math.PI / 2);
	}

	/** Whether a note {@code age} ticks old, held by an engine {@code holding} blocks away, gives way to one {@code waiting} blocks away. */
	static boolean mayYield(long age, double holding, double waiting) {
		return age >= HOLD_TICKS && EngineChorus.shouldYield(holding, waiting);
	}
}
