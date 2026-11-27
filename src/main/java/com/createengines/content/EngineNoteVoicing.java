package com.createengines.content;

/**
 * What a build's cylinders and firing order make of its voice: how big each cylinder sounds, how deep the whole engine is,
 * and how a bank that fires unevenly lopes. Pure Java, so it is tested.
 */
final class EngineNoteVoicing {

	private static final double CYCLE = 720;
	/** Doublings of the cylinder count from a single, at 1, down to a size of 0. */
	private static final float DOUBLINGS_TO_SMALLEST = 6;
	/** How strongly a firing's strength follows how long its bank waited for it, and the furthest it is taken. */
	private static final double LOPE = 0.4;
	private static final float WEAKEST = 0.7f;
	private static final float STRONGEST = 1.35f;

	private EngineNoteVoicing() {
	}

	/**
	 * One cylinder is a big slow thumper at 1; each doubling makes each cylinder sound a little smaller. Only a little: an
	 * engine here grows by adding cylinders of the same size, and a V8 made to sound small-bored could not rumble.
	 */
	static float size(int cylinders) {
		return Math.clamp(1 - doublings(cylinders) / DOUBLINGS_TO_SMALLEST, 0, 1);
	}

	/**
	 * How much of a big engine's low boom the exhaust has, 0 to 1: none with one or two cylinders, whose firings are heard one
	 * by one, half with four, and all of it from eight.
	 */
	static float depth(int cylinders) {
		return Math.clamp((doublings(cylinders) - 1) / 2, 0, 1);
	}

	private static float doublings(int cylinders) {
		return (float) (Math.log(Math.max(1, cylinders)) / Math.log(2));
	}

	/**
	 * How hard each firing hits for how long its own outlet had waited for it. Two pulses close together in one exhaust get in
	 * each other's way, and the one after a long wait goes out freely, which is what makes a crossplane V8's banks burble. An
	 * outlet that fires evenly leaves every firing at 1.
	 *
	 * @param angles where in the cycle each firing is, sorted
	 * @param outlet each firing's outlet, in the same order
	 */
	static float[] lope(float[] angles, int[] outlet) {
		float[] strengths = new float[angles.length];
		for (int i = 0; i < angles.length; i++) {
			int mine = at(outlet, i);
			int sharing = 0;
			double waited = CYCLE;
			for (int j = 0; j < angles.length; j++) {
				if (at(outlet, j) != mine)
					continue;
				sharing++;
				double gap = (angles[i] - angles[j] + CYCLE) % CYCLE;
				// Firings at the very same angle are one pulse, and neither waited on the other
				if (gap > 0.5 && gap < waited)
					waited = gap;
			}
			double ratio = waited * Math.max(1, sharing) / CYCLE;
			strengths[i] = Math.clamp((float) Math.pow(ratio, LOPE), WEAKEST, STRONGEST);
		}
		return strengths;
	}

	private static int at(int[] outlet, int index) {
		return index < outlet.length ? outlet[index] : 0;
	}
}
