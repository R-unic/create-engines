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
		return meanOfFirst(wears, wears.length);
	}

	/** As {@link #mean}, of the first {@code count} of these only, for a caller that fills an array sized for the most parts it could have. */
	public static float meanOfFirst(double[] wears, int count) {
		if (count <= 0)
			return NONE;
		double sum = 0;
		for (int i = 0; i < count; i++)
			sum += Wear.clamp(wears[i]);
		return (float) (sum / count);
	}

	/**
	 * The total as a running engine holds it from one wearing step to the next. It is read on the first look and again with each step,
	 * whatever it reads: an engine with nothing loaded reads {@link #NONE}, which is an answer and no reason to read again next tick.
	 */
	public static final class Held {

		private float total = NONE;
		private boolean read;

		public float total() {
			return total;
		}

		/** Whether the parts should be read now: on a wearing step, and until they have been read once. */
		public boolean wanted(boolean step) {
			return step || !read;
		}

		public void read(float total) {
			this.total = total;
			read = true;
		}
	}

	/** The figure as the readouts show it: a whole percent from 0 to 100, or -1 when there is none. */
	public static int percent(float total) {
		return total < 0 || Float.isNaN(total) ? -1 : Math.min(100, Math.round(total * 100));
	}
}
