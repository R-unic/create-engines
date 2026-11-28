package com.createengines.content;

/**
 * How the listener hears an engine note, apart from what the engine is doing: how far off they are and how muffled each
 * outlet is. The game thread asks; the sound thread glides there. Pure Java.
 */
final class NoteListening {

	/** Per-slow-step shares of the way distance (about a fifth of a second) and muffling (about a fourteenth) move. */
	private static final float FAR_GLIDE = (float) (EngineNoteSynth.SLOW_EVERY / (0.18 * EngineNoteSynth.SAMPLE_RATE));
	private static final float MUFFLE_GLIDE =
		(float) (EngineNoteSynth.SLOW_EVERY / (0.073 * EngineNoteSynth.SAMPLE_RATE));

	volatile float wantedFar;
	volatile float wantedMuffling;
	/** 0 (beside it) to 1 (at the edge of hearing). */
	float far;
	/** How muffled each outlet is. */
	final float[] muffled = new float[EngineNoteSynth.MOST_OUTLETS];
	/** The most muffled of the outlets that ring. */
	float most;

	/** One slow step, every {@link EngineNoteSynth#SLOW_EVERY} samples. */
	void follow(float[] asked, int outlets) {
		far += (wantedFar - far) * FAR_GLIDE;
		float highest = 0;
		for (int o = 0; o < muffled.length; o++) {
			muffled[o] += ((asked.length > o ? asked[o] : wantedMuffling) - muffled[o]) * MUFFLE_GLIDE;
			if (o < outlets)
				highest = Math.max(highest, muffled[o]);
		}
		most = highest;
	}

	static float share(float wanted) {
		return Float.isFinite(wanted) ? Math.clamp(wanted, 0f, 1f) : 0f;
	}
}
