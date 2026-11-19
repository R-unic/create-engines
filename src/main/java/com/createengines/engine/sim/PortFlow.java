package com.createengines.engine.sim;

/**
 * How well a port network breathes, from how many openings it has for its heads. 2.0: each opening adds less than the one
 * before it, and openings crowded against each other count for half.
 *
 * <p>1.x was {@code min(1, 2 * openings / heads)}, so one opening served two heads completely and anything past that was
 * wasted. Now {@code 1 - e^(-K * r)} for {@code r} openings per head: one opening per head is about 90 percent, two is
 * about 99 percent, and one opening for two heads is about two thirds. A six cylinder engine at 0.5 breathing makes about 4
 * percent less peak torque and loses much more at the top of the rev range (measured on the dyno, 6 cylinders).
 */
public final class PortFlow {

	/** Steepness of the curve. */
	public static final double K = 2.3;

	/** What an opening is worth when another opening is right beside it. */
	public static final double CROWDED = 0.5;

	private PortFlow() {
	}

	/** Share of full breathing for {@code openings} (a count that crowding may have made fractional) over {@code heads}. */
	public static double factor(double openings, int heads) {
		if (Double.isNaN(openings) || heads <= 0)
			return 0;
		return Math.max(0, Math.min(1, 1 - Math.exp(-K * openings / heads)));
	}

	/**
	 * The worth of a set of openings given how many of the {@code total} are crowded beside another one.
	 * @return {@code open} openings, each of the crowded ones counting {@link #CROWDED}
	 */
	public static double effective(int open, int total, int crowded) {
		if (total <= 0)
			return 0;
		double crowdedShare = Math.min(1, Math.max(0, (double) crowded / total));
		return Math.max(0, open) * (1 - crowdedShare * (1 - CROWDED));
	}
}
