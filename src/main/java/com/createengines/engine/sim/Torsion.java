package com.createengines.engine.sim;

/**
 * What a long crankshaft costs. A crank twists under load, and the longer the unbroken line of it, the more it twists and
 * the less of the cylinders' torque reaches the output cleanly. Up to {@link #FREE_THROWS} throws on one line it costs
 * nothing; past that the stress an engine can be rated for falls by {@link #LOSS_PER_THROW} for each throw over, to at
 * most {@link #MAX_LOSS}. Like {@link Roughness} it takes from the rating, and does not stop or harm the engine.
 * <p>
 * What counts is throws on one line, not cylinders. A vee or a flat puts two cylinders on each throw, so a V12 or a flat
 * twelve has six and loses nothing, where a straight twelve has twelve and loses a tenth. A vee thirty is fifteen throws and
	 * loses under a fifth, so the biggest builds still come out ahead of a smaller engine. Crank lines geared
 * together one to one are separate lines (each flexes on its own), so two straight sixes or eights coupled lose
 * nothing either. That is the point: it is how the mod rewards the layouts real big engines use.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class Torsion {

	/** Throws on one crank line that cost nothing. */
	public static final int FREE_THROWS = 8;
	/** Share of the rating lost for each throw on a line beyond {@link #FREE_THROWS}. */
	public static final double LOSS_PER_THROW = 0.025;
	/** The most an engine loses to torsional flex at the shipped balance. */
	public static final double MAX_LOSS = 0.35;
	/** The most the server's scaling can take off, however it is set. */
	private static final double ABSOLUTE_MAX_LOSS = 0.8;

	private Torsion() {
	}

	/**
	 * How many throws sit on each crank line, in the order the lines are met.
	 *
	 * @param isThrow    for each crank block in order, whether it is a throw (or a rotor's eccentric) rather than a plain journal
	 * @param startsLine for each block, whether it begins a new line rather than continuing the one before it
	 */
	public static int[] throwsPerLine(boolean[] isThrow, boolean[] startsLine) {
		int lines = 0;
		for (int i = 0; i < isThrow.length; i++)
			if (i == 0 || startsLine[i])
				lines++;
		int[] counts = new int[lines];
		int line = -1;
		for (int i = 0; i < isThrow.length; i++) {
			if (i == 0 || startsLine[i])
				line++;
			if (isThrow[i])
				counts[line]++;
		}
		return counts;
	}

	/** The share of the rating one line of {@code throwsOnLine} throws loses at the shipped balance. */
	public static double lineLoss(int throwsOnLine) {
		return Math.min(MAX_LOSS, Math.max(0, throwsOnLine - FREE_THROWS) * LOSS_PER_THROW);
	}

	/**
	 * The share of an engine's rating its crank lines lose at the shipped balance: each line's loss, weighted by how many
	 * of the engine's throws it carries, since a short line does little of the work of a long one.
	 */
	public static double loss(int[] throwsPerLine) {
		int total = 0;
		double weighted = 0;
		for (int count : throwsPerLine) {
			total += count;
			weighted += count * lineLoss(count);
		}
		return total == 0 ? 0 : weighted / total;
	}

	/** What the server's scaling makes of a loss: 0 turns flex off, 2 doubles it. */
	public static double scaled(double loss) {
		return Math.clamp(loss * Tuning.torsionalFlex, 0, ABSOLUTE_MAX_LOSS);
	}

	/** The share of the rating left once a crank's flex has taken its share, 1 for a stiff one. */
	public static double stiffness(double loss) {
		return 1 - scaled(loss);
	}
}
