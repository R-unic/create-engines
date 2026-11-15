package com.createengines.content;

/**
 * Ages a streamed voice by game time. Game time restarts from a different number in every world, so a voice made in
 * one world can look like it comes from the future in the next; such a voice is stale, never "still settling". Pure
 * Java so it can be tested.
 */
final class VoiceClock {

	/** Ticks to leave a new instance alone before deciding the sound engine declined it. */
	static final int SETTLE_TICKS = 20;

	private VoiceClock() {
	}

	/** Whether a voice started at {@code startedAt} has had its moment to start, or is from another world's clock. */
	static boolean settled(long now, long startedAt) {
		long age = now - startedAt;
		return age < 0 || age >= SETTLE_TICKS;
	}
}
