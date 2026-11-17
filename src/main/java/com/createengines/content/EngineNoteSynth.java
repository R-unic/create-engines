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
		 * @param size         how big each cylinder sounds, 0 to 1: a big one thumps lower and its crack hangs on longer
		 * @param sharpness    how hard each firing cracks, 0 to 1: boost, a hot engine and a hard-worked one crack harder
	 */
	public record Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion, float size,
							 float sharpness) {

			/** An engine of middling size and sharpness. */
			public Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion) {
				this(rpm, firingAngles, firingShare, load, combustion, 0.5f, 0.5f);
			}


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
	/**
	 * The bang itself, so it lands on the firing it belongs to instead of beside it: a sine that drops from about 150 Hz to
	 * 46 Hz in a few hundredths of a second, which is the thump of the pressure, and a low rumble of noise under it. Both
	 * die away in about a tenth of a second.
	 */
	private static final double THUMP_LOW = 46;
	private static final double THUMP_DROP = 110;
	private static final float THUMP_SWEEP_FALL = (float) Math.exp(-1 / (0.02 * SAMPLE_RATE));
	private static final float THUMP_FALL = (float) Math.exp(-1 / (0.05 * SAMPLE_RATE));
	private static final float RUMBLE_FALL = (float) Math.exp(-1 / (0.045 * SAMPLE_RATE));
	private static final float RUMBLE_POLE = (float) Math.exp(-2 * Math.PI * 700 / SAMPLE_RATE);
	/**
	 * The crack of the flame front, the part of a firing that makes it a bang and not a thud: a burst of noise around a few
	 * kilohertz that rises in about a fifth of a millisecond (never a single-sample edge) and is gone in a few
	 * milliseconds. Each cylinder cracks at its own pitch. Every firing is heard on its own at the few a second a slow
	 * engine makes, so it is strongest there and fades out as firings run together into a note, where a crack on each would
	 * only buzz.
	 */
	private static final float CRACK_RISE = (float) Math.exp(-1 / (0.0003 * SAMPLE_RATE));
	private static final double CRACK_PULSE_GAIN = 1 / (1 - CRACK_RISE);
	private static final float CRACK_WEIGHT = 2f;
	private static final double CRACK_FALL_SMALL = 0.0035;
	private static final double CRACK_FALL_BIG = 0.0085;
	/** Firings a second at which the crack has faded to half. */
	private static final float CRACK_FADE_AT = 20;
	private static final float THUMP_WEIGHT = 0.45f;
	private static final float RUMBLE_WEIGHT = 0.8f;
	/** Firings a second at which one thump has not quite died before the next starts. */
	private static final float THUMPS_BEFORE_PILE_UP = 16;
	/**
	 * Above a hundred firings a second the pulses come faster than the pipe's resonances ring, and which of them a firing
	 * rate happens to land on decides the loudness: the note would drop away in steps as the engine sped up. So the firings
	 * themselves, softened to below a few hundred hertz, are heard as a tone at the firing rate that grows in as the rate
	 * climbs, and a slow gain follows the level so a fast engine stays as loud as a slow one.
	 */
	private static final float TONE_POLE = (float) Math.exp(-2 * Math.PI * 600 / SAMPLE_RATE);
	private static final float TONE_DC_POLE = (float) Math.exp(-2 * Math.PI * 15 / SAMPLE_RATE);
	private static final float TONE_WEIGHT = 3f;
	private static final float TONE_FROM = 40;
	private static final float LEVEL_FOLLOW = (float) (1 / (0.3 * SAMPLE_RATE));
	/** The mean square of the note before it is clipped, as it sits across the middle of the range. */
	private static final float TARGET_POWER = 0.16f;
	private static final float MOST_BOOST = 4;
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
	private float thumpEnvelope;
	private float thumpSweep;
	private float thumpPitch = 1;
	private double thumpPhase;
	private float rumbleEnvelope;
	private float rumbleNoise;
	private double crackImpulse;
	private double crackPulse;
	private float crackEnvelope;
	private float crackFall = (float) Math.exp(-1 / (CRACK_FALL_SMALL * SAMPLE_RATE));
	private float crackStrength;
	private float crackTune = 0.3f;
	private float crackLow;
	private float crackBand;
	private float toneA;
	private float toneB;
	private float toneDc;
	private float power = TARGET_POWER;
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

	/**
	 * About how long after a sample is rendered it is heard: four tenth-of-a-second chunks are queued with the sound
	 * engine. The game thread aims the cycle this far ahead of the engine, so a firing is heard as the engine fires.
	 */
	public static final double OUTPUT_LATENCY = 0.35;
	/** Per-sample share of a phase error closed: about a fifth of a second, so the correction is a glide and never a click. */
	private static final double LOCK_FOLLOW = 1 / (0.2 * SAMPLE_RATE);
	/** The most the cycle may be sped up or slowed by catching up, as a share of its own speed; it never runs backwards. */
	private static final double LOCK_SLEW = 0.35;
	/** An error this large is a different place in the cycle, not drift, and the cycle jumps there. */
	private static final double LOCK_SNAP = 180;

	private volatile double lockTarget = Double.NaN;
	private boolean locked;
	private double correction;

	/**
	 * Called by the game thread with where the engine's cycle will be, in degrees, at the moment the sound now being
	 * rendered is heard. The synthesizer keeps its own clock, which would otherwise wander against the engine's real
	 * crank (a speed it follows a tenth of a second late, the stream's own delay, a start at any angle) so firings were
	 * heard off from the flames and puffs that follow the cam. It closes the gap smoothly and snaps only on the first
	 * call or a large error.
	 */
	public void lockTo(double cycleDegrees) {
		if (Double.isFinite(cycleDegrees))
			lockTarget = ((cycleDegrees % CYCLE) + CYCLE) % CYCLE;
	}

	/** Takes up a new aim from the game thread; on the audio thread, once per block of samples. */
	private void applyLock() {
		double target = lockTarget;
		if (Double.isNaN(target))
			return;
		lockTarget = Double.NaN;
		double error = ((target - phase) % CYCLE + CYCLE * 1.5) % CYCLE - CYCLE / 2;
		if (!locked || Math.abs(error) > LOCK_SNAP) {
			locked = true;
			phase = target;
			correction = 0;
			nextEvent = firstAfter(angles, phase);
		} else
			correction = error;
	}

	/** Called by the game thread; takes effect over the next tenth of a second. */
	public void set(Params next) {
		params = clean(next);
	}

	/**
	 * What the synthesizer is willing to follow. A NaN or infinity from the engine would poison the filters and the phase
	 * for good, leaving that engine mute until the world reloads, so anything unusable becomes silence or a sane limit.
	 */
	private static Params clean(Params p) {
		float[] angles = p.firingAngles == null ? new float[0] : p.firingAngles;
		for (float angle : angles)
			if (!Float.isFinite(angle) || angle < 0 || angle >= CYCLE)
				return Params.SILENT;
		return new Params(finite(p.rpm, 0, 20000), angles, finite(p.firingShare, 0, 1), finite(p.load, 0, 1.3f),
			finite(p.combustion, 0, 1), finite(p.size, 0, 1), finite(p.sharpness, 0, 1));
	}

	private static float finite(float value, float min, float max) {
		return Float.isFinite(value) ? Math.clamp(value, min, max) : 0;
	}

	/** Whether there is nothing left to hear: the engine is quiet and the pipe has stopped ringing. */
	public boolean isSilent() {
		return params.combustion <= 0 && combustion < 0.001f && Math.abs(y1[0]) + Math.abs(y1[1]) < 0.001 && pulse < 0.001
			&& thumpEnvelope < 0.001f;
	}

	/** Fills {@code out[offset, offset + count)} with 16-bit samples. */
	public void render(short[] out, int offset, int count) {
		applyLock();
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

		double advance = rpm * 6.0 / SAMPLE_RATE;
		double trim = Math.clamp(correction * LOCK_FOLLOW, -LOCK_SLEW * advance, LOCK_SLEW * advance);
		correction -= trim;
		phase += advance + trim;
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

		// Every firing restarts the thump's drop; its phase carries on, so the pitch jumps but the wave never does
		thumpSweep *= THUMP_SWEEP_FALL;
		thumpPhase += 2 * Math.PI * (THUMP_LOW + THUMP_DROP * thumpSweep) * thumpPitch / SAMPLE_RATE;
		if (thumpPhase > 2 * Math.PI)
			thumpPhase -= 2 * Math.PI;
		thumpEnvelope = thumpEnvelope * THUMP_FALL + (float) (pulse * PULSE_GAIN);
		// Thumps that overlap pile up, so a fast engine's are thinned until it hums instead of roaring
		float eventsPerSecond = angles.length * rpm / 120f;
		float pileUp = 1 / (float) Math.sqrt(1 + (eventsPerSecond / THUMPS_BEFORE_PILE_UP) * (eventsPerSecond / THUMPS_BEFORE_PILE_UP));
		float thump = (float) Math.sin(thumpPhase) * thumpEnvelope * THUMP_WEIGHT * pileUp;
		rumbleEnvelope = rumbleEnvelope * RUMBLE_FALL + (float) (pulse * PULSE_GAIN);
		rumbleNoise = RUMBLE_POLE * rumbleNoise + (1 - RUMBLE_POLE) * noise();
		float rumble = rumbleNoise * rumbleEnvelope * RUMBLE_WEIGHT * pileUp;

		// The crack: noise through a resonance tuned to the firing cylinder, struck by a fast pulse and gone in milliseconds
		crackPulse = CRACK_RISE * crackPulse + (1 - CRACK_RISE) * crackImpulse;
		crackImpulse = 0;
		crackEnvelope = crackEnvelope * crackFall + (float) (crackPulse * CRACK_PULSE_GAIN);
		float crackHigh = noise() - crackLow - 0.8f * crackBand;
		crackBand += crackTune * crackHigh;
		crackLow += crackTune * crackBand;
		float crackShare = 1 / (1 + (eventsPerSecond / CRACK_FADE_AT) * (eventsPerSecond / CRACK_FADE_AT));
		float crack = crackBand * crackEnvelope * CRACK_WEIGHT * crackShare;

		toneA = TONE_POLE * toneA + (1 - TONE_POLE) * (float) (pulse * PULSE_GAIN);
		toneB = TONE_POLE * toneB + (1 - TONE_POLE) * toneA;
		toneDc = TONE_DC_POLE * toneDc + (1 - TONE_DC_POLE) * toneB;
		float toneShare = eventsPerSecond * eventsPerSecond / (eventsPerSecond * eventsPerSecond + TONE_FROM * TONE_FROM);
		float tone = (toneB - toneDc) * TONE_WEIGHT * toneShare;

		float scale = LEVEL / (float) Math.pow(1 + 0.03f * eventsPerSecond, 0.7);
		float mix = (float) (body * 0.5 + chuff + thump + rumble + crack + tone) * scale * 2;
		power += (mix * mix - power) * LEVEL_FOLLOW;
		// Only a fast engine is levelled, so the lope of a slow one is left alone, and a stopping engine is not turned up, and a resonance the firing rate happens to hit is turned down
		float follow = toneShare * Math.min(1f, combustion * 2);
		float boost = Math.clamp(TARGET_POWER / (power + 1e-6f), 0.25f, MOST_BOOST * MOST_BOOST);
		mix *= 1 + follow * ((float) Math.sqrt(boost) - 1);
		float out = (float) Math.tanh(mix + mechanical);
		if (!Float.isFinite(out)) {
			reset();
			return 0;
		}
		return out;
	}

	/** Back to a quiet, stopped engine, if the arithmetic ever goes wrong. */
	private void reset() {
		java.util.Arrays.fill(x1, 0);
		java.util.Arrays.fill(x2, 0);
		java.util.Arrays.fill(y1, 0);
		java.util.Arrays.fill(y2, 0);
		phase = 0;
		nextEvent = 0;
		locked = false;
		correction = 0;
		rpm = load = combustion = 0;
		impulse = pulse = 0;
		noiseLow = thumpEnvelope = thumpSweep = rumbleEnvelope = rumbleNoise = toneA = toneB = toneDc = 0;
		crackImpulse = crackPulse = 0;
		crackEnvelope = crackLow = crackBand = 0;
		thumpPhase = 0;
		power = TARGET_POWER;
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
		if (lit) {
			thumpSweep = 1;
			// A bigger cylinder thumps lower, and each has its own pitch within that
			thumpPitch = (0.92f + 0.16f * (cylinderGain(index) - 0.88f) / 0.24f) * (1.22f - 0.44f * p.size);
			// Its crack is tuned to it too: a small, hot cylinder cracks higher and harder than a big, cool one
			float voice = cylinderVoice(index);
			float hz = (1700 + 1500 * voice) * (1.25f - 0.5f * p.size) * (0.9f + 0.25f * p.sharpness);
			crackTune = (float) (2 * Math.sin(Math.PI * Math.min(hz, 3600) / SAMPLE_RATE));
			crackFall = (float) Math.exp(-1 / ((CRACK_FALL_SMALL + (CRACK_FALL_BIG - CRACK_FALL_SMALL) * p.size) * SAMPLE_RATE));
			crackStrength = (0.45f + 0.55f * p.sharpness) * (0.7f + 0.3f * Math.min(1f, p.load)) * burn;
			crackImpulse += strength * crackStrength;
		}
	}

	/** Which of a few tones a cylinder cracks at, 0 to 1, the same each time for the same one. */
	private static float cylinderVoice(int index) {
		int hash = (index + 5) * 0x85EBCA6B;
		hash ^= hash >>> 13;
		hash *= 0xC2B2AE35;
		hash ^= hash >>> 16;
		return (hash & 0xFFFF) / 65535f;
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
