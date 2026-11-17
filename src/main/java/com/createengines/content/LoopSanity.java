package com.createengines.content;

/**
 * Keeps what a looping sound is told (level, pitch, speed) usable. A NaN from a machine would otherwise sit in a loop's
 * smoothed state for as long as it lives and reach OpenAL as a NaN volume or pitch. Pure Java so it can be tested.
 */
public final class LoopSanity {

	/** Beyond this distance, in blocks, a machine's loop is not started and a playing one fades out. */
	public static final double RANGE = 48;

	private LoopSanity() {
	}

	/** {@code value} limited to {@code [min, max]}; NaN becomes {@code fallback} (itself limited), infinities the nearer limit. */
	public static float finite(float value, float min, float max, float fallback) {
		if (Float.isNaN(value))
			value = fallback;
		return Math.max(min, Math.min(max, value));
	}

	/** Whether a point {@code distanceSquared} from the player is close enough to bother with a loop. */
	public static boolean inRange(double distanceSquared) {
		return distanceSquared <= RANGE * RANGE;
	}
}
