package com.createengines.content;

/**
 * The last stage of an engine note, between what leaves its pipes and the ear: a fast engine is held as loud as a slow one,
 * what goes through mufflers is filtered and turned down, the whole is held under full scale, and casing and distance dull
 * it. Pure Java; the sound thread alone uses it.
 */
final class NoteExit {

	private static final float LEVEL_FOLLOW = (float) (1 / (0.3 * EngineNoteSynth.SAMPLE_RATE));
	/** The mean square of the note before it is clipped, as it sits across the middle of the range. */
	private static final float TARGET_POWER = 0.16f;
	private static final float MOST_BOOST = 4;
	/**
	 * How loud the note's muffled part is, before its filter. Higher than {@link com.createengines.engine.sim.Muffling#volume},
	 * which the recorded sounds use: the filter takes the crack away as well, and at that volume a slow, fully muffled single
	 * could not be heard beside it.
	 */
	private static final float MUFFLED_LEVEL = 0.43f;
	/** Where a muffler's filter sits. */
	private static final float MUFFLED_HZ = 500;
	private static final float MUFFLE_AMOUNT = amount(MUFFLED_HZ);
	/** Where the dulling filter sits: open above this, and how low casing and distance take it. */
	private static final float OPEN_HZ = 9000;
	private static final float CLAD_HZ = 640;
	private static final float FAR_HZ = 1200;

	private float power = TARGET_POWER;
	private float muffledOne;
	private float muffledTwo;
	/** The share of the way the dulling filter moves each sample (1: open). */
	private float dullAmount = 1;
	private float dull;
	/** The casing and distance the filter was last placed for. */
	private float cladFor = Float.NaN;
	private float farFor = Float.NaN;

	private static float amount(float hz) {
		return (float) (1 - Math.exp(-2 * Math.PI * hz / EngineNoteSynth.SAMPLE_RATE));
	}

	/** How much of the engine is behind casing and how far off it is heard, each 0 to 1. */
	void dulledBy(float clad, float far) {
		if (clad == cladFor && far == farFor)
			return;
		cladFor = clad;
		farFor = far;
		float hz = OPEN_HZ;
		hz += (CLAD_HZ - hz) * clad;
		hz += (FAR_HZ - hz) * far;
		dullAmount = hz >= OPEN_HZ - 1 ? 1 : amount(hz);
	}

	/**
	 * One sample out, from what leaves the open outlets and what goes through mufflers.
	 *
	 * @param follow  how far the level is held steady, 0 (a slow engine's lope is left alone) to 1
	 * @param muffled whether any outlet has a muffler
	 */
	float next(float open, float shut, float follow, boolean muffled) {
		float mix = open + shut;
		power += (mix * mix - power) * LEVEL_FOLLOW;
		float boost = Math.clamp(TARGET_POWER / (power + 1e-6f), 0.25f, MOST_BOOST * MOST_BOOST);
		float levelled = 1 + follow * ((float) Math.sqrt(boost) - 1);
		float out;
		if (muffled) {
			// Two gentle poles in a row, so what is above the pipe's boom falls away; after the levelling, which would undo it
			float piped = NoteMath.tanh(shut * levelled);
			muffledOne = Denormals.flush(muffledOne + MUFFLE_AMOUNT * (piped - muffledOne));
			muffledTwo = Denormals.flush(muffledTwo + MUFFLE_AMOUNT * (muffledOne - muffledTwo));
			float clear = NoteMath.tanh(open * levelled);
			out = EngineRoomMix.limit(clear + muffledTwo * MUFFLED_LEVEL);
		} else {
			out = NoteMath.tanh(mix * levelled);
			muffledOne = muffledTwo = 0;
		}
		if (dullAmount < 1) {
			dull = Denormals.flush(dull + dullAmount * (out - dull));
			out = dull;
		} else
			dull = out;
		return out;
	}

	void clear() {
		power = TARGET_POWER;
		muffledOne = muffledTwo = dull = 0;
	}
}
