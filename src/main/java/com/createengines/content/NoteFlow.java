package com.createengines.content;

/**
 * The rush of hot gas down each outlet's pipe: noise that opens with each firing and dies away before long, fed in at
 * the valve so the pipe colours it. A real exhaust is as much this breath as the pulse, and without it the note was a
 * struck tube. Where firings crowd, the breaths run into one roar that still swells with them. Its noise is its own, so
 * it leaves every firing as it was. Pure Java; the sound thread alone uses it.
 */
final class NoteFlow {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final float RISE = (float) (1 - Math.exp(-1 / (0.0025 * RATE)));
	/** Seconds in which a breath falls to about a third: a big cylinder takes longer to empty. */
	private static final double SMALL_EMPTIES = 0.009;
	private static final double BIG_EMPTIES = 0.022;
	/** The band of the noise: above a rumble, and well under a hiss. */
	private static final float TOP_POLE = (float) (1 - Math.exp(-2 * Math.PI * 2600 / RATE));
	private static final float FLOOR_POLE = (float) (1 - Math.exp(-2 * Math.PI * 350 / RATE));
	private static final float WEIGHT = 0.085f;

	private final float[] drive = new float[EngineNoteSynth.MOST_OUTLETS];
	private final float[] open = new float[EngineNoteSynth.MOST_OUTLETS];
	private float fall = (float) Math.exp(-1 / (BIG_EMPTIES * RATE));
	private float top;
	private float floor;
	private float noise;
	private int random = 0x1F123BB5;

	/** How long a breath lasts, for cylinders of {@code size}, 0 to 1. */
	void sized(float size) {
		fall = (float) Math.exp(-1 / ((SMALL_EMPTIES + (BIG_EMPTIES - SMALL_EMPTIES) * size) * RATE));
	}

	/** A firing of this strength out of {@code outlet}. */
	void breathe(int outlet, float strength) {
		drive[outlet] += strength * WEIGHT;
	}

	/** Makes the sample's noise, which every outlet's breath is a share of; once a sample, before {@link #next}. */
	void advance() {
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		top += TOP_POLE * (random / (float) Integer.MAX_VALUE - top);
		floor += FLOOR_POLE * (top - floor);
		noise = top - floor;
	}

	/** What goes into {@code outlet}'s pipe this sample. */
	float next(int outlet) {
		if (drive[outlet] == 0 && open[outlet] == 0)
			return 0;
		drive[outlet] *= fall;
		open[outlet] += RISE * (drive[outlet] - open[outlet]);
		if (drive[outlet] < 1e-6f && open[outlet] < 1e-6f)
			drive[outlet] = open[outlet] = 0;
		return noise * open[outlet];
	}

	boolean isStill() {
		for (int o = 0; o < drive.length; o++)
			if (drive[o] != 0 || open[o] != 0)
				return false;
		return true;
	}

	void clear() {
		java.util.Arrays.fill(drive, 0);
		java.util.Arrays.fill(open, 0);
		top = floor = noise = 0;
	}
}
