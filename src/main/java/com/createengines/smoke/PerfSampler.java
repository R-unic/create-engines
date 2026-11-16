package com.createengines.smoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Collects the numbers that tell a run is healthy over time rather than at one instant: how long frames and ticks take
 * (a stutter shows in the high percentiles, not the mean) and whether a value that ought to stay flat (heap in use,
 * live particles, sound voices) keeps climbing. The 1.2.0 sound bug was a slow leak that ended in a dead sound thread;
 * a screenshot cannot see that, a trend line can.
 *
 * <p>Pure Java, so the statistics are unit tested.
 */
public final class PerfSampler {

	private long[] durations = new long[1024];
	private int size;

	/** Records one duration, in nanoseconds. */
	public synchronized void record(long nanos) {
		if (size == durations.length)
			durations = Arrays.copyOf(durations, size * 2);
		durations[size++] = nanos;
	}

	public synchronized int count() {
		return size;
	}

	/** The {@code p}th percentile (0..1) in milliseconds, or 0 with nothing recorded. */
	public synchronized double percentileMillis(double p) {
		if (size == 0)
			return 0;
		long[] sorted = Arrays.copyOf(durations, size);
		Arrays.sort(sorted);
		int index = (int) Math.min(size - 1, Math.max(0, Math.ceil(p * size) - 1));
		return sorted[index] / 1_000_000.0;
	}

	public synchronized double maxMillis() {
		long max = 0;
		for (int i = 0; i < size; i++)
			max = Math.max(max, durations[i]);
		return max / 1_000_000.0;
	}

	public synchronized double meanMillis() {
		if (size == 0)
			return 0;
		double total = 0;
		for (int i = 0; i < size; i++)
			total += durations[i];
		return total / size / 1_000_000.0;
	}

	/** A series of readings of one quantity taken at intervals, for {@link #slope}. */
	public static final class Series {
		private final List<Double> values = new ArrayList<>();

		public void add(double value) {
			values.add(value);
		}

		public int size() {
			return values.size();
		}

		public List<Double> values() {
			return values;
		}

		public double last() {
			return values.isEmpty() ? 0 : values.get(values.size() - 1);
		}

		public double max() {
			double max = Double.NEGATIVE_INFINITY;
			for (double v : values)
				max = Math.max(max, v);
			return values.isEmpty() ? 0 : max;
		}

		/** Least-squares slope per sample; positive means the quantity is climbing. */
		public double slope() {
			return PerfSampler.slope(values);
		}
	}

	/** Least-squares slope of {@code values} against their index; 0 for fewer than two. */
	public static double slope(List<Double> values) {
		int n = values.size();
		if (n < 2)
			return 0;
		double meanX = (n - 1) / 2.0, meanY = 0;
		for (double v : values)
			meanY += v;
		meanY /= n;
		double num = 0, den = 0;
		for (int i = 0; i < n; i++) {
			num += (i - meanX) * (values.get(i) - meanY);
			den += (i - meanX) * (i - meanX);
		}
		return num / den;
	}

	/**
	 * Whether a series that should be flat is growing: its fitted line gains more than {@code allowedGrowth} over the
	 * whole run <em>and</em> the last reading is above the first by that much (so one early spike does not count).
	 */
	public static boolean climbing(Series series, double allowedGrowth) {
		if (series.size() < 4)
			return false;
		double fitted = series.slope() * (series.size() - 1);
		double actual = series.last() - series.values()
			.get(0);
		return fitted > allowedGrowth && actual > allowedGrowth;
	}

	/** Heap in use in megabytes, after asking for a collection so garbage is not mistaken for a leak. */
	public static double settledHeapMegabytes() {
		Runtime runtime = Runtime.getRuntime();
		System.gc();
		return (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0;
	}
}
