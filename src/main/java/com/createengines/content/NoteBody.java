package com.createengines.content;

/**
 * The exhaust system after the pipes: two resonances that stay where they are whatever the engine's speed, which is
 * what gives an engine a voice of its own to be told by ({@link NoteTimbre}). Each stream through it (the open outlets,
 * the muffled ones) has state of its own. Pure Java; the sound thread alone uses it.
 */
final class NoteBody {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	/** How narrow each resonance is. */
	private static final double LOW_Q = 1.8;
	private static final double HIGH_Q = 2.2;
	private static final float HIGHEST_HZ = 5000;
	private static final float LOWEST_HZ = 40;

	/** One resonance: a band-pass that passes its own frequency at 1. */
	private static final class Band {
		private float in;
		private float feedbackOne;
		private float feedbackTwo;
		private float inputOne;
		private float inputTwo;
		private float outOne;
		private float outTwo;

		void tune(float hz, double q) {
			double turn = 2 * Math.PI * Math.clamp(hz, LOWEST_HZ, HIGHEST_HZ) / RATE;
			double width = Math.sin(turn) / (2 * q);
			double whole = 1 + width;
			in = (float) (width / whole);
			feedbackOne = (float) (-2 * Math.cos(turn) / whole);
			feedbackTwo = (float) ((1 - width) / whole);
		}

		float next(float sample) {
			float out = Denormals.flush(in * (sample - inputTwo) - feedbackOne * outOne - feedbackTwo * outTwo);
			inputTwo = inputOne;
			inputOne = sample;
			outTwo = outOne;
			outOne = out;
			return out;
		}

		void clear() {
			inputOne = inputTwo = outOne = outTwo = 0;
		}
	}

	private final Band low = new Band();
	private final Band high = new Band();
	private NoteTimbre tuned;

	/** What has gone in, as heard through this engine's exhaust system. */
	float next(float sample, NoteTimbre timbre) {
		if (timbre != tuned) {
			tuned = timbre;
			low.tune(timbre.lowHz(), LOW_Q);
			high.tune(timbre.highHz(), HIGH_Q);
		}
		return (sample * timbre.dry() + low.next(sample) * timbre.lowGain() + high.next(sample) * timbre.highGain())
			* timbre.level();
	}

	void clear() {
		low.clear();
		high.clear();
	}
}
