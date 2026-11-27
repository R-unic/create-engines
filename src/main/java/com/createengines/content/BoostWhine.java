package com.createengines.content;

/**
 * A turbo's whistle or a supercharger's whine made as it is heard, to the same recipe as the {@code turbo_whistle} and
 * {@code supercharger_whine} recordings (tools/boost_sounds.py), so an engine with a note carries its blowers in the same
 * stream: the pitch glides with no stairs, no loop seam and no limit of one octave either way, and no channel is used.
 * Every tone of a recording is a whole number of hertz and the recording a second long, so one clock that runs as fast as
 * the pitch drives them all. The air noise is made fresh instead of looped. Pure Java, so it is tested.
 */
final class BoostWhine {

	enum Kind {
		TURBO(20), SUPERCHARGER(16);

		/** The blocks the recording is heard over: its attenuation distance in sounds.json, which a carried voice keeps. */
		final float range;

		Kind(float range) {
			this.range = range;
		}
	}

	/** The pitch may go this far either way; 1 is the recording as it is. */
	static final float LOWEST_PITCH = 0.25f;
	static final float HIGHEST_PITCH = 4f;

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	/** Tones above this would fold back down as other tones. */
	private static final double HIGHEST_HZ = 0.45 * RATE;
	/** The noise filters follow the pitch once every this many samples, about 3 ms. */
	private static final int RETUNE = 64;
	/** Each voice is as loud as its recording at full scale, an RMS of about 0.318; the blower needs this to be. */
	private static final float BLOWER_LEVEL = 0.9f;

	private static final float[] SINE = new float[4097];
	static {
		for (int i = 0; i < SINE.length; i++)
			SINE[i] = (float) Math.sin(2 * Math.PI * i / 4096);
	}

	final Kind kind;
	/** The recording's clock, in seconds, kept within the one second it repeats in. */
	private double clock;
	/** The recording's bands of noise, each its centre and width in hertz and how many times its filter is run. */
	private final int[][] spread;
	private final Band[] bands;
	private int since;
	private int random;

	BoostWhine(Kind kind, int seed) {
		this.kind = kind;
		random = seed == 0 ? 0x1F123BB5 : seed;
		spread = kind == Kind.TURBO ? TURBO_BANDS : BLOWER_BANDS;
		bands = new Band[spread.length];
		for (int i = 0; i < bands.length; i++)
			bands[i] = new Band();
	}

	/** The next sample at {@code pitch}, about -0.8 to 0.8, as the recording is. */
	float next(float pitch) {
		if (since == 0)
			for (int i = 0; i < bands.length; i++)
				bands[i].tune(spread[i][0], spread[i][1], spread[i][2], pitch);
		since = (since + 1) % RETUNE;
		clock += pitch / (double) RATE;
		if (clock >= 1)
			clock -= Math.floor(clock);
		return kind == Kind.TURBO ? turbo(pitch) : blower(pitch);
	}

	/** The turbo's breath at its whistle, the hollow note under it, its rushing air and its low whoosh. */
	private static final int[][] TURBO_BANDS = {{2000, 80, 2}, {1000, 110, 2}, {4873, 7000, 1}, {520, 420, 2}};

	/** A breathy whistle, never quite still, with a hollow note an octave down, bright overtones and a wide rush of air. */
	private float turbo(float pitch) {
		float drift = 0.11f * sine(3, 0.07f) + 0.05f * sine(7, 0.41f) + 0.025f * sine(13, 0.83f);
		float core = tone(2000, drift, pitch) + 0.35f * tone(4000, 2 * drift + 0.2f, pitch)
			+ 0.11f * tone(6000, 0.3f, pitch) + 0.11f * tone(1000, 0.5f * drift + 0.6f, pitch);
		float air = band(2) * (0.8f + 0.2f * sine(38, 0.25f));
		float mix = 0.5f * core + 0.21f * band(0) + 0.075f * band(1) + 0.168f * air + 0.085f * band(3);
		return (float) Math.tanh(TURBO_DRIVE * mix);
	}

	/** The air singing along with the blower's first two tones, and its soft whir. */
	private static final int[][] BLOWER_BANDS = {{520, 90, 2}, {1040, 170, 2}, {1500, 1500, 2}};
	/**
	 * The blower's tones, each its hertz, level and starting turn: the lobes' whine and its soft overtones, the growl
	 * 48 Hz either side of the first two, and a trace of the drive gears' mesh.
	 */
	private static final float[][] BLOWER_TONES = {
		{520, 1f, 0f}, {1040, 0.28f, 0.31f}, {1560, 0.08f, 0.67f},
		{472, 0.14f, 0.55f}, {568, 0.11f, 0.23f}, {992, 0.05f, 0.79f}, {1088, 0.04f, 0.45f},
		{1361, 0.05f, 0.9f}};

	/** A rounded tone tied to the shaft over a low thrum, in a soft whir of air: made to be lived with. */
	private float blower(float pitch) {
		float wander = 0.035f * sine(2, 0.19f) + 0.018f * sine(5, 0.62f);
		float whine = 0;
		for (float[] partial : BLOWER_TONES) {
			int hz = (int) partial[0];
			whine += partial[1] * tone(hz, hz / 520f * wander + partial[2], pitch);
		}
		float thrum = tone(87, 0.11f, pitch) + 0.6f * tone(174, 0.62f, pitch) + 0.3f * tone(261, 0.35f, pitch);
		float mix = 0.25f * whine + 0.17f * thrum + 0.07f * band(0) + 0.04f * band(1) + 0.1f * band(2);
		return (float) Math.tanh(BLOWER_DRIVE * mix) * BLOWER_LEVEL;
	}

	/** The next sample of the {@code index}th band, from noise of its own. */
	private float band(int index) {
		return bands[index].through(noise());
	}

	/** How hard each mix is eased into the soft clip. */
	private static final float TURBO_DRIVE = 0.78f;
	private static final float BLOWER_DRIVE = 1.35f;

	/** A tone of the recording, {@code hz} there, dropped where the pitch takes it past what the rate can hold. */
	private float tone(int hz, float turns, float pitch) {
		return hz * pitch > HIGHEST_HZ ? 0 : sine(hz, turns);
	}

	private float sine(int hz, float turns) {
		double at = hz * clock + turns;
		at -= Math.floor(at);
		double index = at * 4096;
		int low = (int) index;
		return SINE[low] + (SINE[low + 1] - SINE[low]) * (float) (index - low);
	}

	private float noise() {
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		return random / (float) Integer.MAX_VALUE;
	}

	/**
	 * A band of noise with the spread of the recording's, scaled to an RMS of about 1: a two-pole filter, run twice where
	 * its hiss is to stay close round it, or once where it is to reach far.
	 */
	private static final class Band {
		private double b0;
		private double a1;
		private double a2;
		private final Stage first = new Stage();
		private final Stage second = new Stage();
		private float scale = 1;
		private boolean twice;

		void tune(double centre, double width, int passes, float pitch) {
			twice = passes > 1;
			double w = 2 * Math.PI * Math.min(centre * pitch, 0.4 * RATE) / RATE;
			double alpha = Math.sin(w) * width / (2 * centre);
			b0 = alpha / (1 + alpha);
			a1 = -2 * Math.cos(w) / (1 + alpha);
			a2 = (1 - alpha) / (1 + alpha);
			// White noise at -1 to 1 comes out of one pass at an RMS of 0.577 times the root of b0, of two at about half b0's
			scale = (float) (1 / (0.577 * Math.sqrt(twice ? b0 / 2 : b0)));
		}

		float through(float in) {
			double out = first.through(in, b0, a1, a2);
			return (float) (twice ? second.through(out, b0, a1, a2) : out) * scale;
		}
	}

	/** What one pass of a band's filter remembers. */
	private static final class Stage {
		private double x1;
		private double x2;
		private double y1;
		private double y2;

		double through(double in, double b0, double a1, double a2) {
			double out = b0 * (in - x2) - a1 * y1 - a2 * y2;
			x2 = x1;
			x1 = in;
			y2 = y1;
			y1 = out;
			return out;
		}
	}
}
