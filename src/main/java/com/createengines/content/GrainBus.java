package com.createengines.content;

/**
 * Grains struck and still sounding, overlapped into one ring: a strike adds a whole grain ahead of the play point, and the
 * ring is read a sample at a time. However many firings overlap, a sample costs one read, and nothing is allocated. Pure Java.
 */
final class GrainBus {

	/** The longest grain it holds; a power of two, so wrapping is a mask. */
	static final int LONGEST = 4096;

	private final float[] ring = new float[LONGEST];
	private int at;
	/** Samples until the last grain struck has played out. */
	private int pending;

	/** Starts {@code grain}, {@code gain} times as strong, at the sample {@link #take} gives next. */
	void strike(float[] grain, float gain) {
		if (gain == 0)
			return;
		for (int i = 0; i < grain.length; i++)
			ring[(at + i) & (LONGEST - 1)] += grain[i] * gain;
		pending = Math.max(pending, grain.length);
	}

	/** The next sample. */
	float take() {
		if (pending > 0)
			pending--;
		float sample = ring[at];
		ring[at] = 0;
		at = (at + 1) & (LONGEST - 1);
		return sample;
	}

	boolean idle() {
		return pending == 0;
	}

	void clear() {
		java.util.Arrays.fill(ring, 0);
		pending = 0;
	}
}
