package com.createengines.content;

/**
 * The short early reflections of the room the listener is in: two taps off a short delay line, dulled as walls dull what
 * they send back, with nothing fed back round, so it cannot ring or run away. The whole is turned down by as much as the
 * reflections add in power, so indoors sounds closer in, not louder. With no room it passes every sample through
 * untouched. The game thread says what it wants; the sound thread glides the level and cross-fades a change of delay, so
 * neither clicks. Pure Java, so it is tested.
 */
final class NoteEcho {

	/** The longest first reflection, in seconds. */
	static final float LONGEST = 0.05f;
	/** The second reflection comes this much later than the first, and this much quieter. */
	private static final float SECOND_LATE = 1.6f;
	private static final float SECOND_LEVEL = 0.6f;
	/** Enough for the later tap of the longest delay, and a power of two. */
	private static final int LENGTH = 4096;
	private static final float GLIDE = 1f / (0.25f * EngineNoteSynth.SAMPLE_RATE);
	private static final int CROSSFADE = EngineNoteSynth.SAMPLE_RATE / 50;
	private static final float DULL = (float) (1 - Math.exp(-2 * Math.PI * 3000 / EngineNoteSynth.SAMPLE_RATE));

	private final float[] line = new float[LENGTH];
	private int at;
	private volatile float wantedGain;
	private volatile int wantedDelay = samples(0.02f);
	private float gain;
	private int delay;
	/** The delay being faded out, and how much of the fade is left in samples. */
	private int old;
	private int fading;
	private float dulled;

	/**
	 * @param newGain the first reflection's level against the engine's own, 0 for none
	 * @param seconds how long after the engine it arrives
	 */
	void set(float newGain, float seconds) {
		wantedGain = Float.isFinite(newGain) ? Math.clamp(newGain, 0f, NoteSurroundings.LOUDEST_ECHO) : 0;
		if (Float.isFinite(seconds))
			wantedDelay = samples(seconds);
	}

	private static int samples(float seconds) {
		return Math.round(Math.clamp(seconds, 0.005f, LONGEST) * EngineNoteSynth.SAMPLE_RATE);
	}

	/** Whether there is nothing of the room in what comes out. */
	boolean isDry() {
		return gain == 0 && wantedGain == 0;
	}

	float next(float in) {
		float sample = Float.isFinite(in) ? in : 0;
		line[at] = sample;
		float to = wantedGain;
		if (gain == 0 && to == 0) {
			at = (at + 1) & (LENGTH - 1);
			dulled = 0;
			return sample;
		}
		// With nothing of the room heard yet there is no earlier delay to fade from
		if (gain == 0 || delay == 0) {
			delay = wantedDelay;
			fading = 0;
		} else if (fading == 0 && wantedDelay != delay) {
			old = delay;
			delay = wantedDelay;
			fading = CROSSFADE;
		}
		gain += (to - gain) * GLIDE;
		// Any closer and a step of the glide is lost in a float's rounding, and it never arrives
		if (Math.abs(to - gain) < 1e-3f)
			gain = to;
		float back = taps(delay);
		if (fading > 0) {
			back += (taps(old) - back) * (fading / (float) CROSSFADE);
			fading--;
		}
		dulled += DULL * (back - dulled);
		at = (at + 1) & (LENGTH - 1);
		float trim = (float) (1 / Math.sqrt(1 + gain * gain * (1 + SECOND_LEVEL * SECOND_LEVEL)));
		return (sample + gain * dulled) * trim;
	}

	private float taps(int first) {
		int second = Math.round(first * SECOND_LATE);
		return line[(at - first) & (LENGTH - 1)] + SECOND_LEVEL * line[(at - second) & (LENGTH - 1)];
	}
}
