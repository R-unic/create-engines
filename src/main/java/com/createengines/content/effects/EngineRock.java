package com.createengines.content.effects;

/**
 * How much a rough engine bucks, drawn only on the parts that already move: its pistons and rods slap about in their
 * bores by an amount that follows the engine's roughness rating, and a little dust shakes off the block. Nothing here
 * shakes a block or changes a figure. Kept free of the game so it can be tested.
 */
public final class EngineRock {

	/** Roughness, the share of the rating lost to uneven running, below which an engine sits perfectly still. */
	public static final float STILL_BELOW = 0.03f;
	/** The roughest an engine can be rated: see {@link com.createengines.engine.sim.Roughness#MAX_LOSS}. */
	public static final float ROUGHEST = 0.4f;
	/** The furthest, in blocks, a piston strays from its bore's middle: under a pixel, so it never leaves the block. */
	public static final float MOST_STRAY = 0.04f;
	/** Chance per check, for the roughest engine, that a pinch of dust shakes loose. */
	public static final float MOST_DUST = 0.6f;

	private EngineRock() {
	}

	/** How strongly an engine bucks, 0 (still) to 1 (as rough as engines get). */
	public static float strength(float roughness) {
		if (roughness < STILL_BELOW)
			return 0;
		return Math.min(1, (roughness - STILL_BELOW) / (ROUGHEST - STILL_BELOW));
	}

	/**
	 * How far a piston is thrown aside, in blocks, along each of the two directions across its bore. It follows the crank
	 * angle, so it stops when the crank does and keeps time with the firing, and each cylinder is a little out of step with
	 * its neighbours, as a rough engine's are.
	 *
	 * @param crankAngle degrees the crank has turned
	 * @param phase      a number that tells this cylinder from its neighbours, any value
	 */
	public static float[] stray(float roughness, float crankAngle, int phase) {
		float reach = strength(roughness) * MOST_STRAY;
		if (reach == 0)
			return new float[] {0, 0};
		double angle = Math.toRadians(crankAngle);
		double lag = (phase & 7) * 0.7;
		return new float[] {(float) (reach * Math.sin(2 * angle + lag)), (float) (reach * Math.cos(3 * angle + 1.3 * lag))};
	}

	/** Chance, per check, that a pinch of dust shakes off the block: none for a smooth engine. */
	public static float dustChance(float roughness) {
		return strength(roughness) * MOST_DUST;
	}
}
