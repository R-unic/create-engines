package com.createengines.content;

/**
 * One exhaust run as a waveguide: a pressure pulse travels down a delay line as long as the run, turns over at the open end,
 * losing some of its edge, and comes back, so the run rings at a note of its own and that note's odd harmonics, higher the
 * shorter it is. What is heard is what leaves the mouth, which passes the highs and loses some of the lows. A muffler is a
 * filter inside the loop: it takes the edge off every pass and lets less come round again. Pure Java, so it is tested.
 */
final class ExhaustPipe {

	/** Room for the longest run's delay; a power of two, so wrapping is a mask. */
	private static final int LINE = 1024;
	/**
	 * Seconds in which what goes round the pipe falls to about a third. Set as a time and not a share per pass, so a short
	 * run, which goes round more often, does not ring on any longer: a long ring was heard as reverb.
	 */
	private static final double RING = 0.016;
	/** The most that ever comes back from the open end. */
	private static final double MOST_BACK = 0.88;
	/** Where the loss at the open end rolls off, open and fully muffled. */
	private static final double OPEN_HZ = 2400;
	private static final double MUFFLED_HZ = 700;
	/** How much less comes round again in a fully muffled pipe. */
	private static final float MUFFLED_LOSS = 0.35f;
	/** The mouth radiates less of what is below this, and of that, this share is lost. */
	private static final float RADIATE_POLE = (float) Math.exp(-2 * Math.PI * 120 / EngineNoteSynth.SAMPLE_RATE);
	private static final float RADIATE_LOSS = 0.5f;

	private final float[] line = new float[LINE];
	private int at;
	private float delay = 100;
	/** The share of the wave a muffler lets come round again. */
	private float kept = 1;
	private float damping = pole(OPEN_HZ);
	/** How muffled the damping was last worked out for. */
	private float muffling = Float.NaN;
	private float feedback = 0.6f;
	private float low;
	private float lows;
	private float quiet;
	private double hz;

	private static float pole(double cutoff) {
		return (float) (1 - Math.exp(-2 * Math.PI * cutoff / EngineNoteSynth.SAMPLE_RATE));
	}

	/** Makes the run ring at {@code note} hertz. */
	void tune(double note) {
		hz = Math.clamp(note, EngineNoteSynth.SAMPLE_RATE / (2.0 * (LINE - 4)), 2000);
		// The end turns the wave over, so a full period is two passes. The filter in the loop delays it too, by about a
		// sample for each of its time constants, which comes off the line
		double pass = EngineNoteSynth.SAMPLE_RATE / (2 * hz);
		double filter = (1 - pole(OPEN_HZ)) / pole(OPEN_HZ);
		delay = (float) Math.clamp(pass - filter, 2, LINE - 2);
		feedback = (float) Math.min(MOST_BACK, Math.exp(-pass / (RING * EngineNoteSynth.SAMPLE_RATE)));
	}

	/** The note it is tuned to, in hertz. */
	double note() {
		return hz;
	}

	/** How much of this run's gas goes through a muffler, 0 to 1. */
	void muffle(float share) {
		// A note asks this of every pipe over a thousand times a second, and the share seldom moves
		if (share == muffling)
			return;
		muffling = share;
		damping = pole(OPEN_HZ + (MUFFLED_HZ - OPEN_HZ) * share);
		kept = 1 - MUFFLED_LOSS * share;
	}

	/** Puts {@code in} into the valve end and returns what comes out of the mouth. */
	float next(float in) {
		int whole = (int) delay;
		float part = delay - whole;
		float near = line[(at - whole) & (LINE - 1)];
		float far = line[(at - whole - 1) & (LINE - 1)];
		low += damping * (near + (far - near) * part - low);
		float wave = in - feedback * kept * low;
		// A tail too small to hear would run into subnormal numbers, which cost many times more to add up
		if (Math.abs(wave) < Denormals.FLOOR)
			wave = low = 0;
		line[at] = wave;
		at = (at + 1) & (LINE - 1);
		lows = Denormals.flush(RADIATE_POLE * lows + (1 - RADIATE_POLE) * wave);
		float out = wave - RADIATE_LOSS * lows;
		quiet = Denormals.flush(Math.max(Math.abs(out), quiet * 0.999f));
		return out;
	}

	/** Whether it has stopped ringing. */
	boolean isSilent() {
		return quiet < 0.001f;
	}

	void clear() {
		java.util.Arrays.fill(line, 0);
		low = lows = quiet = 0;
	}
}
