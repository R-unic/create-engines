package com.createengines.content;

/**
 * Where an engine's speed or load will be when what is being rendered now is heard. The note waits in the sound engine's
 * queue, so a throttle blip followed as it comes in is heard after it is seen. While a figure keeps moving one way this
 * carries it on along its trend. A single step says nothing about what comes next, so a trend takes at least two moves the
 * same way, close together, and it is dropped as soon as the figure stops moving or turns back. How far ahead of the figure
 * it will go is bounded, so a trend that ends suddenly is overshot only a little. Pure Java so it is tested; times are
 * passed in.
 */
final class NoteLookahead {

	/** Moves older than this, in seconds, say nothing about the trend now. */
	static final double WINDOW = 0.4;
	/** A figure that has not moved for this long, in seconds, has stopped. */
	static final double STALE = 0.2;
	private static final int KEPT = 8;

	/** The figure each time it moved, oldest first, since it last turned round. */
	private final float[] values = new float[KEPT];
	private final long[] times = new long[KEPT];
	private int count;

	/**
	 * Takes the figure as it is at {@code nowNanos} and returns what it will be {@code ahead} seconds later.
	 *
	 * @param most the furthest from {@code value} the answer may be
	 */
	float ahead(float value, long nowNanos, double ahead, float most) {
		if (!Float.isFinite(value)) {
			count = 0;
			return value;
		}
		if (count == 0 || value != values[count - 1])
			note(value, nowNanos);
		// What is too old to count, though the latest is always kept to compare the next move with
		int from = 0;
		while (from < count - 1 && (nowNanos - times[from]) * 1e-9 > WINDOW)
			from++;
		if (count - from < 3 || (nowNanos - times[count - 1]) * 1e-9 > STALE)
			return value;
		double seconds = (times[count - 1] - times[from]) * 1e-9;
		if (seconds <= 0)
			return value;
		float move = (float) ((values[count - 1] - values[from]) / seconds * ahead);
		float bound = Math.max(0, most);
		return value + Math.clamp(move, -bound, bound);
	}

	private void note(float value, long nowNanos) {
		// A turn back starts the trend over from the last figure before it
		if (count >= 2 && (value - values[count - 1] > 0) != (values[count - 1] - values[count - 2] > 0)) {
			values[0] = values[count - 1];
			times[0] = times[count - 1];
			count = 1;
		} else if (count == KEPT) {
			System.arraycopy(values, 1, values, 0, KEPT - 1);
			System.arraycopy(times, 1, times, 0, KEPT - 1);
			count--;
		}
		values[count] = value;
		times[count] = nowNanos;
		count++;
	}
}
