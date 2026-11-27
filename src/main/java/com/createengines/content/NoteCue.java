package com.createengines.content;

/**
 * When a bang asked of an engine's note will be heard. The note is made a little ahead of what is heard and a bang waits
 * for a firing to go out with, so whatever is seen of it (flame, sparks) is held until the sound thread has said when
 * that is. The sound thread writes it; the game thread reads it. Pure Java, so it is tested.
 */
public final class NoteCue {

	private static final long UNSAID = Long.MIN_VALUE;

	private volatile long heardAt = UNSAID;

	/** From the sound thread: the bang's first sample is heard at {@code nanos}, as {@link System#nanoTime} counts. */
	void heardAt(long nanos) {
		heardAt = nanos == UNSAID ? UNSAID + 1 : nanos;
	}

	/** Whether the bang is heard by {@code nanos}; false while the sound thread has not got to it. */
	public boolean heardBy(long nanos) {
		long at = heardAt;
		return at != UNSAID && nanos - at >= 0;
	}
}
