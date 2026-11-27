package com.createengines.content;

/**
 * Works out when the sound an {@link EngineNoteSynth} is rendering will be heard, so the engine's cycle can be aimed at
 * where its cam will be then. The sound engine reads four chunks ({@link EngineNotePcm#CHUNK}) at once when a stream
 * starts, then one more each time one has played, at the first game tick after. The samples play at an exact rate, so all that has to be
 * found is when the first was heard. Each later read gives that, late by however long the tick took to come round and
 * never early, so the clock keeps to the earliest answer, letting it slide later only slowly, and starts over only when
 * the stream has stalled or been paused. Pure Java so it can be tested; times are passed in.
 */
final class EngineNoteClock {

	/** Chunks the sound engine holds, and reads all at once when a stream starts. */
	static final int PRIMED_CHUNKS = 4;
	/** How long before it is heard a sample is rendered, once the stream is going: the chunks queued ahead of it. */
	static final double LEAD = (PRIMED_CHUNKS - 1) / 20.0;
	/** A clock this far behind is not a late tick: the stream stalled or was paused. */
	static final double RESET = 0.15;
	/** The most the start may slide later at each read, so a sound card running a little slow is still followed. */
	static final double CREEP = 0.00025;

	static final long PRIMED_SAMPLES = EngineNotePcm.samplesBefore(PRIMED_CHUNKS);

	private boolean started;
	/** When the sample numbered 0 was heard. */
	private long startNanos;

	/** Set for samples that join a stream already going, which wait behind its queue from the first. */
	private boolean joining;

	/** For an engine put into a stream already going, before its first sample: that one is not heard as it is rendered. */
	void joinRunningStream() {
		joining = true;
	}

	/** When the sample numbered {@code sample}, being rendered at {@code nowNanos}, is heard, in the same nanoseconds. */
	long heardAt(long sample, long nowNanos) {
		long into = (long) (sample * 1e9 / EngineNoteSynth.SAMPLE_RATE);
		if (!started) {
			started = true;
			startNanos = nowNanos - into + (joining ? (long) (LEAD * 1e9) : 0);
		} else if (joining || sample >= PRIMED_SAMPLES) {
			// The chunks read at the start say nothing about it: they were not waiting on one to finish playing
			long late = nowNanos + (long) (LEAD * 1e9) - into - startNanos;
			startNanos += late > RESET * 1e9 ? late : Math.min(late, (long) (CREEP * 1e9));
		}
		return startNanos + into;
	}
}
