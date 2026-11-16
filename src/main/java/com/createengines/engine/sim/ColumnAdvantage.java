package com.createengines.engine.sim;

/** How much better a refining column does than the basin at the same job, for the line on a basin's goggles. */
public final class ColumnAdvantage {

	/** The least gain worth saying: below this a line on the goggles only adds noise. */
	public static final int MIN_PERCENT = 1;
	/** Most a line will claim, so a pack's odd recipe cannot print a silly figure. */
	public static final int MAX_PERCENT = 999;

	private ColumnAdvantage() {
	}

	/**
	 * Whole percent of extra product a column gives for the same feed, or 0 when it gives no more (or an input is
	 * not a usable number). Yields are product per mB of feed.
	 */
	public static int percentMore(double columnYield, double basinYield) {
		if (!(columnYield > 0) || !(basinYield > 0) || Double.isInfinite(columnYield) || Double.isInfinite(basinYield))
			return 0;
		long percent = Math.round((columnYield / basinYield - 1) * 100);
		if (percent < MIN_PERCENT)
			return 0;
		return (int) Math.min(percent, MAX_PERCENT);
	}
}
