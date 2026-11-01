package com.createengines.engine.sim;

/**
 * How much of an engine's exhaust heat an exhaust heat exchanger turns into a heat source for Create's basins.
 * <p>
 * Heat is measured as {@link CylinderModel} measures it, exhaust heat per second, which is about 3 for each cylinder
 * running at speed on a full shot and is shared between every exchanger on the same exhaust network. The levels match
 * Create's blaze burner: a basin runs "heated" recipes at {@link #KINDLED} and "superheated" ones at {@link #SEETHING}.
 * A small engine is enough to cook diesel, a big engine at speed is what it takes to crack gasoline.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class HeatRecovery {

	/** Heat per second below which an exchanger is cold. */
	public static final double SMOULDERING = 1.5;
	/** Heat per second a basin needs to run heated recipes (about two cylinders at speed). */
	public static final double KINDLED = 6;
	/** Heat per second a basin needs to run superheated recipes (about seven cylinders at speed). */
	public static final double SEETHING = 20;
	/** A level is held until the heat falls this share below the heat that earned it, so a ticking engine doesn't flicker. */
	private static final double HOLD = 0.85;

	public static final int NONE = 0;
	public static final int SMOULDER = 1;
	public static final int KINDLE = 2;
	public static final int SEETHE = 3;

	private HeatRecovery() {
	}

	/** The heat level an exchanger should show, given the heat it is being fed and the level it shows now. */
	public static int level(double heat, int current) {
		int earned = heat >= SEETHING ? SEETHE : heat >= KINDLED ? KINDLE : heat >= SMOULDERING ? SMOULDER : NONE;
		if (earned >= current)
			return earned;
		// Falling: drop a level only once the heat is clearly below what that level needs
		return heat >= threshold(current) * HOLD ? current : earned;
	}

	private static double threshold(int level) {
		return switch (level) {
			case SEETHE -> SEETHING;
			case KINDLE -> KINDLED;
			case SMOULDER -> SMOULDERING;
			default -> 0;
		};
	}

	/** Redstone strength for an exchanger's heat: 15 at twice the heat superheating takes, 1 as soon as it is warm. */
	public static int signal(double heat) {
		if (heat < SMOULDERING)
			return 0;
		return (int) Math.clamp(Math.round(15 * heat / (2 * SEETHING)), 1, 15);
	}
}
