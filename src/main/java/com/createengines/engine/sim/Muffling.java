package com.createengines.engine.sim;

/**
 * How much quieter an engine's exhaust is for the mufflers in its exhaust runs. Only the sound changes: a muffler costs
 * the engine nothing, in power or back-pressure, so the engine the simulation runs is the one it would be without it.
 * Pure arithmetic, so it is unit tested without a world; the client turns the result into volume.
 */
public final class Muffling {

	/** Share of its exhaust noise an engine loses when every exhaust run it has goes through a muffler. */
	public static final float QUIET = 0.8f;
	/** A muffler the exhaust can go round only works on the share that goes through it: half of the run's noise. */
	public static final float BYPASSED = 0.5f;

	private Muffling() {
	}

	/**
	 * How muffled one exhaust run is, 0 to 1.
	 *
	 * @param series   a muffler that every path through the run goes through
	 * @param bypassed a muffler the run can go round (and no muffler that it cannot)
	 */
	public static float run(boolean series, boolean bypassed) {
		return series ? 1 : bypassed ? BYPASSED : 0;
	}

	/**
	 * How muffled the engine is, 0 to 1: the average of its exhaust runs, each weighted by the cylinder heads it serves,
	 * so a V with one bank muffled is half muffled.
	 */
	public static float engine(int[] heads, float[] runs) {
		int total = 0;
		float weighted = 0;
		for (int i = 0; i < heads.length; i++) {
			total += heads[i];
			weighted += heads[i] * runs[i];
		}
		return total == 0 ? 0 : weighted / total;
	}

	/** The volume an exhaust sound is played at, as a share of what it would be with no muffler: 1 at none, 0.2 at all. */
	public static float volume(float muffling) {
		return 1 - QUIET * Math.clamp(muffling, 0f, 1f);
	}
}
