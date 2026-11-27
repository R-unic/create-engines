package com.createengines.engine.sim;

/**
 * How much of an engine's air dirty air costs it. The air round an intake's mouth holds some gas (another mod's pollution);
 * the more it holds the thinner the useful charge, up to {@link #MAX_LOSS} when the air is as full as {@code full}. An air
 * filter in the intake takes most of that back. The loss scales the air density, as height does (see {@link Altitude}).
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class IntakeAir {

	/** The most air density the dirtiest air takes. A running engine feels it about two and a half times over, as with height. */
	public static final double MAX_LOSS = 0.08;
	/** Steps of gas in the cube round a mouth that count as the dirtiest, when the server sets no area cap of its own. */
	public static final long DEFAULT_FULL = 150;
	/** The share of the loss that gets past an air filter every path of the intake goes through. */
	public static final double FILTERED_SHARE = 0.2;
	/** The share that gets past when the air can also go round the filter by another branch. */
	public static final double BYPASSED_SHARE = 0.6;

	/** What filtering an intake line has. */
	public enum Filter {
		NONE(1), BYPASSED(BYPASSED_SHARE), INLINE(FILTERED_SHARE);

		private final double passes;

		Filter(double passes) {
			this.passes = passes;
		}
	}

	private IntakeAir() {
	}

	/** How dirty air holding {@code held} steps of gas is, 0 clean to 1 as dirty as it counts, {@code full} steps. */
	public static double dirt(long held, long full) {
		if (full <= 0)
			return 0;
		return Math.clamp((double) held / full, 0, 1);
	}

	/** The share of air density an intake line loses when the air at its mouths averages this {@code dirt}. */
	public static double loss(double dirt, Filter filter) {
		if (Double.isNaN(dirt))
			return 0;
		return MAX_LOSS * Math.clamp(dirt, 0, 1) * filter.passes;
	}
}
