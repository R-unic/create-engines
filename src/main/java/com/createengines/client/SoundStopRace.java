package com.createengines.client;

/**
 * Counts the times {@code SoundStopRaceMixin} has run, so the client audit can tell that it still applies to the game it
 * is running in. Kept apart from the mixin because a mixin class cannot be named from ordinary code.
 */
public final class SoundStopRace {

	private static volatile int closed;

	private SoundStopRace() {
	}

	public static void closed() {
		closed++;
	}

	public static int count() {
		return closed;
	}
}
