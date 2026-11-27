package com.createengines.content;

/**
 * Lets whichever of several callers comes first in a game tick do that tick's work. Pure Java, so it is tested.
 */
final class TickOnce {

	private long done = Long.MIN_VALUE;

	/** Whether this tick's work is still to do; the caller told so does it. */
	boolean due(long now) {
		if (now == done)
			return false;
		done = now;
		return true;
	}

	void reset() {
		done = Long.MIN_VALUE;
	}
}
