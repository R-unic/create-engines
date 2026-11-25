package com.createengines.engine.sim;

/**
 * The single wear figure a Black Box and a Display Link show for a whole build: the plain mean of how worn every part that wears is
 * (the author, 2026-11-24). Pure arithmetic, so it is unit tested; {@code WearKeeper.total} gathers the parts.
 */
public final class WearTotal {

	/** What the figure is while there is nothing to show: no wear mode, or an engine with no part that wears. */
	public static final float NONE = -1f;

	private WearTotal() {
	}

	/**
	 * The mean of these wears, each taken as 0 to 1 (nonsense is new, more than spent is spent), or {@link #NONE} if there are none. A part
	 * that has gone altogether (a rod that was thrown, a gasket that is missing) is counted as spent by whoever gathers them, not skipped:
	 * a build that has lost a part is not made to look healthier for it.
	 */
	public static float mean(double... wears) {
		if (wears.length == 0)
			return NONE;
		double sum = 0;
		for (double wear : wears)
			sum += Wear.clamp(wear);
		return (float) (sum / wears.length);
	}

	/** The figure as the readouts show it: a whole percent from 0 to 100, or -1 when there is none. */
	public static int percent(float total) {
		return total < 0 || Float.isNaN(total) ? -1 : Math.min(100, Math.round(total * 100));
	}
}
