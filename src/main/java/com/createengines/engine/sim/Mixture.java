package com.createengines.engine.sim;

/**
 * How well a charge burns at a given fuel-air equivalence ratio (1 is exactly enough air for the fuel, above is rich).
 * <p>
 * Tuning is rewarded more than slop is punished. At the edges of the balanced band a charge burns as an ordinary one
 * does, and the closer it gets to exactly balanced the faster the flame travels and the more of the fuel's energy
 * becomes heat, so the same fuel makes more work. Outside the band the flame slows a little and a little less of the
 * fuel's energy is useful (lean quenches, rich makes soot and carbon monoxide), but only down to a modest floor: an
 * engine that is off is merely ordinary. Fuel the air can't burn at all is wasted separately, and it also fouls spark
 * plugs and thins the oil (see {@link CylinderModel} and {@link Lubrication}).
 */
public final class Mixture {

	/** Leaner than this and the charge stops getting a balanced mixture's bonus; it matches what goggles call balanced. */
	public static final double LEAN_BAND = 0.8;
	/** Richer than this and the charge stops getting the bonus. */
	public static final double RICH_BAND = 1.25;
	/** Share of the fuel left unburned beyond which a cycle counts as running rich. */
	public static final double RICH_UNBURNED = 0.15;

	/** Extra heat from the fuel at exactly balanced, over an ordinary charge. */
	public static final double BALANCED_HEAT_BONUS = 0.2;
	/** Extra flame speed at exactly balanced, over an ordinary charge. */
	public static final double BALANCED_FLAME_BONUS = 0.15;

	private static final double LEAN_EFFICIENCY_LOSS = 0.5;
	private static final double RICH_EFFICIENCY_LOSS = 0.3;
	private static final double MIN_EFFICIENCY = 0.85;
	private static final double LEAN_FLAME_LOSS = 0.8;
	private static final double RICH_FLAME_LOSS = 0.4;
	private static final double MIN_LEAN_FLAME_SPEED = 0.7;
	private static final double MIN_RICH_FLAME_SPEED = 0.8;

	private Mixture() {
	}

	/** How far into the balanced band a mixture is: 1 at exactly balanced, 0 at either edge and outside it. */
	public static double balance(double equivalence) {
		if (equivalence < LEAN_BAND || equivalence > RICH_BAND)
			return 0;
		return equivalence < 1 ? (equivalence - LEAN_BAND) / (1 - LEAN_BAND) : (RICH_BAND - equivalence) / (RICH_BAND - 1);
	}

	/**
	 * Share of the burned fuel's energy that becomes heat in the cylinder: above 1 inside the balanced band, peaking at
	 * exactly balanced, and a little under 1 outside it.
	 */
	public static double efficiency(double equivalence) {
		if (equivalence < LEAN_BAND)
			return Math.max(MIN_EFFICIENCY, 1 - (LEAN_BAND - equivalence) * LEAN_EFFICIENCY_LOSS);
		if (equivalence > RICH_BAND)
			return Math.max(MIN_EFFICIENCY, 1 - (equivalence - RICH_BAND) * RICH_EFFICIENCY_LOSS);
		return 1 + BALANCED_HEAT_BONUS * balance(equivalence);
	}

	/** How fast the flame burns, as a multiple of the cylinder's normal burn rate: faster the more balanced the charge. */
	public static double flameSpeed(double equivalence) {
		if (equivalence < LEAN_BAND)
			return Math.max(MIN_LEAN_FLAME_SPEED, 1 - (LEAN_BAND - equivalence) * LEAN_FLAME_LOSS);
		if (equivalence > RICH_BAND)
			return Math.max(MIN_RICH_FLAME_SPEED, 1 - (equivalence - RICH_BAND) * RICH_FLAME_LOSS);
		return 1 + BALANCED_FLAME_BONUS * balance(equivalence);
	}

	public static boolean balanced(double equivalence) {
		return equivalence >= LEAN_BAND && equivalence <= RICH_BAND;
	}
}
