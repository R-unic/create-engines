package com.createengines.content;

/**
 * An engine's exhaust note, made up as it is heard instead of played from a recording. Every firing the engine's
 * firing order puts in its 720 degree cycle pushes a soft pulse of pressure into a few resonances, the boom of the pipe
 * and the harmonics above it, so an even straight six hums at one pitch, a V-twin potato-potatoes, a four with a dead
 * cylinder limps, and a rotary buzzes, all from the same code and nothing recorded.
 * <p>
 * The pulse is smoothed and the resonances ring for a few hundredths of a second, so a firing is a thump that sinks
 * into the next one, never a click: a sharp edge into a narrow resonance is what clicks sound like.
 * <p>
 * Pure Java with no Minecraft in it, so the numbers can be tested and rendered to a file. The game thread hands over
 * what the engine is doing with {@link #set}; the audio thread asks for samples with {@link #render} and follows the
 * speed and loudness smoothly between the two.
 */
public final class EngineNoteSynth {

	public static final int SAMPLE_RATE = 22050;

	/**
	 * What the engine is doing.
	 *
	 * @param rpm          crankshaft speed
	 * @param firingAngles where in the 720 degree cycle each cylinder fires, sorted
	 * @param firingShare  the share of firings that light, 0 to 1: the rest are misfires and make a faint puff
	 * @param load         how hard it is working against what it could do, 0 to about 1.3
	 * @param combustion   how much of the note is burning: 1 for a running engine, a quarter for a coasting or cranking one
	 *                     (only air being pumped), 0 for silence
	 */
	public record Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion) {

		public static final Params SILENT = new Params(0, new float[0], 1, 0, 0);
	}

	private static final double CYCLE = 720;

	/** Centre frequency and bandwidth, in Hz, and the weight of each of the three resonances. They ring for about 20, 12 and 7 thousandths of a second. */
	private static final double[] FREQUENCY = {86, 172, 345};
	private static final double[] BANDWIDTH = {16, 26, 45};
	private static final float[] WEIGHT = {1f, 0.85f, 0.5f};

	/** A firing's pressure rises over about a thousandth of a second rather than all at once. */
	private static final float RISE = (float) Math.exp(-1 / (0.001 * SAMPLE_RATE));
	/** Undoes the smoothing's loss of height, so a smoothed firing rings the resonances as hard as a sharp one would. */
	private static final double PULSE_GAIN = 1 / (1 - RISE);
	/** The noise in a firing, and in the valve gear, is kept below a few kilohertz. */
	private static final float NOISE_POLE = (float) Math.exp(-2 * Math.PI * 1800 / SAMPLE_RATE);
	/** Per-sample share of the way to the asked-for speed and loudness: about a tenth of a second. */
	private static final float FOLLOW = 0.0005f;
	/** Overall level, set so a running engine sits around a fifth of full scale. */
	private static final float LEVEL = 0.032f;

	private final double[] b0 = new double[3];
	private final double[] a1 = new double[3];
	private final double[] a2 = new double[3];
	private final double[] inputGain = new double[3];
	private final double[] x1 = new double[3];
	private final double[] x2 = new double[3];
	private final double[] y1 = new double[3];
	private final double[] y2 = new double[3];

	private volatile Params params = Params.SILENT;
	private float[] angles = new float[0];

	private double phase;
	private int nextEvent;
	private float rpm;
	private float load;
	private float combustion;
	private double impulse;
	private double pulse;
	private float noiseLow;
	private int random;

	public EngineNoteSynth(int seed) {
		random = seed == 0 ? 0x2545F491 : seed;
		for (int i = 0; i < 3; i++) {
			double w = 2 * Math.PI * FREQUENCY[i] / SAMPLE_RATE;
			double alpha = Math.sin(w) / (2 * (FREQUENCY[i] / BANDWIDTH[i]));
			double a0 = 1 + alpha;
			b0[i] = alpha / a0;
			a1[i] = -2 * Math.cos(w) / a0;
			a2[i] = (1 - alpha) / a0;
			// A bandpass's impulse response is as small as the filter is narrow; this makes a firing ring at about its own weight
			inputGain[i] = 1 / alpha;
		}
	}

	/** Called by the game thread; takes effect over the next tenth of a second. */
	public void set(Params next) {
		params = next;
	}

	/** Whether there is nothing left to hear: the engine is quiet and the pipe has stopped ringing. */
	public boolean isSilent() {
		return params.combustion <= 0 && combustion < 0.001f && Math.abs(y1[0]) + Math.abs(y1[1]) < 0.001 && pulse < 0.001;
	}

	/** Fills {@code out[offset, offset + count)} with 16-bit samples. */
	public void render(short[] out, int offset, int count) {
		for (int i = 0; i < count; i++)
			out[offset + i] = (short) Math.round(Math.clamp(next(), -1f, 1f) * 30000);
	}

	/** One sample, -1 to 1. */
	public float next() {
		Params p = params;
		rpm += (p.rpm - rpm) * FOLLOW;
		load += (p.load - load) * FOLLOW;
		combustion += (p.combustion - combustion) * FOLLOW;
		if (p.firingAngles != angles) {
			angles = p.firingAngles;
			nextEvent = firstAfter(angles, phase);
		}

		phase += rpm * 6.0 / SAMPLE_RATE;
		fireDue(p);
		if (phase >= CYCLE) {
			phase %= CYCLE;
			nextEvent = 0;
			fireDue(p);
		}

		// The firing as a pressure that rises and falls, not a single sample
		pulse = RISE * pulse + (1 - RISE) * impulse;
		impulse = 0;
		float brightness = Math.clamp(0.45f + 0.55f * load, 0.3f, 1.2f);
		double body = 0;
		for (int i = 0; i < 3; i++) {
			double x = pulse * inputGain[i] * PULSE_GAIN;
			double y = b0[i] * x - b0[i] * x2[i] - a1[i] * y1[i] - a2[i] * y2[i];
			x2[i] = x1[i];
			x1[i] = x;
			y2[i] = y1[i];
			y1[i] = y;
			body += y * WEIGHT[i] * (i == 0 ? 1 : brightness);
		}

		noiseLow = NOISE_POLE * noiseLow + (1 - NOISE_POLE) * noise();
		float low = noiseLow * 2.2f;
		// A chuff of rushing gas that swells with each firing, and a whisper of valve gear that rises with speed
		float chuff = (float) (low * pulse * PULSE_GAIN * 0.3 * (0.35 + 0.65 * Math.min(1f, load)));
		float mechanical = low * 0.03f * Math.min(1f, rpm / 1500f) * Math.min(1f, combustion * 2);

		float eventsPerSecond = angles.length * rpm / 120f;
		float scale = LEVEL / (float) Math.pow(1 + 0.03f * eventsPerSecond, 0.7);
		return (float) Math.tanh((body * 0.5 + chuff) * scale * 2 + mechanical);
	}

	/** Strikes every firing whose crank angle the cycle has now reached. */
	private void fireDue(Params p) {
		while (nextEvent < angles.length && angles[nextEvent] <= phase) {
			fire(nextEvent, p);
			nextEvent++;
		}
	}

	private void fire(int index, Params p) {
		float strength = cylinderGain(index) * (0.9f + 0.1f * (noise() + 1));
		boolean lit = (noise() + 1) * 0.5f < p.firingShare;
		float burn = combustion * (lit ? 1f : 0.18f);
		if (burn <= 0)
			return;
		impulse += strength * burn;
	}

	/** A little difference between cylinders, the same each time for the same one. */
	private static float cylinderGain(int index) {
		int hash = index * 0x9E3779B1;
		hash ^= hash >>> 15;
		return 0.88f + 0.24f * ((hash & 0xFFFF) / 65535f);
	}

	private static int firstAfter(float[] angles, double phase) {
		int index = 0;
		while (index < angles.length && angles[index] <= phase)
			index++;
		return index;
	}

	/** White noise, -1 to 1. */
	private float noise() {
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		return random / (float) Integer.MAX_VALUE;
	}
}
