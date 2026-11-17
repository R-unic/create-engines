package com.createengines.content;

/**
 * Notices a streamed voice the sound engine still lists as playing but has stopped reading from, which is silence that
 * nothing else reports (the instance is "active", so the engine's bangs keep giving way to it). A hitch such as a world
 * load can starve the audio source, and a starved source stays stopped. A playing stream is read about ten times a
 * second, so many ticks in a row with no read means it is dead. Ticks that arrive after a long gap are not counted: a
 * frozen game says nothing about the stream. Pure Java so it can be tested.
 */
public final class StreamStallWatch {

	/** Ticks in a row without a read before the stream is called stalled: three quarters of a second. */
	static final int STALLED_TICKS = 15;
	/** A tick this long after the last one means the game hitched, so it neither counts nor clears the run. */
	static final long HITCH_NANOS = 150_000_000L;

	private int lastReads = -1;
	private long lastTick;
	private int quiet;

	/** One game tick, given how many reads the stream has had so far. Returns whether the stream has stalled. */
	public boolean tick(int reads, long nowNanos) {
		long gap = nowNanos - lastTick;
		lastTick = nowNanos;
		if (reads != lastReads) {
			lastReads = reads;
			quiet = 0;
			return false;
		}
		if (gap > HITCH_NANOS || gap < 0) {
			quiet = 0;
			return false;
		}
		return ++quiet >= STALLED_TICKS;
	}
}
