package com.createengines.compat.pollution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * What the area cap last read in the air at each outlet. Reading it looks at a whole cube of blocks, and an outlet asks twice a
 * second, so a reading is kept for {@link #KEEP} ticks. The cap is a ceiling and not a precise meter: what an outlet releases is added
 * to its reading, so it cannot run on past the cap on an old one, and gas that has thinned since, or that another outlet put in the
 * same air, shows at the next reading.
 * <p>
 * It is the gas that is kept and not the answer, so a changed cap is felt at once. Plain Java, so it is unit tested.
 */
final class AreaCapCache<K> {

	/** Ticks a reading of the air is used before it is taken again. */
	static final int KEEP = 60;
	/** Readings held before the out of date ones are dropped, so that outlets long broken do not pile up. */
	static final int PRUNE_FROM = 1024;

	private static final class Reading {
		final long at;
		long held;

		Reading(long at, long held) {
			this.at = at;
			this.held = held;
		}
	}

	private final Map<K, Reading> readings = new HashMap<>();

	/** Whether the air at {@code outlet} holds {@code cap} steps of gas or more, reading it with {@code measure} if the last reading is old. */
	boolean full(K outlet, long now, long cap, LongSupplier measure) {
		return held(outlet, now, measure) >= cap;
	}

	/** The steps of gas in the air at {@code outlet}, read with {@code measure} if the last reading is old. */
	long held(K outlet, long now, LongSupplier measure) {
		Reading reading = readings.get(outlet);
		if (reading == null || !current(reading, now)) {
			if (readings.size() >= PRUNE_FROM)
				readings.values()
					.removeIf(old -> !current(old, now));
			reading = new Reading(now, measure.getAsLong());
			readings.put(outlet, reading);
		}
		return reading.held;
	}

	/** {@code steps} of gas went out of {@code outlet}, into the air its reading is of. */
	void added(K outlet, long steps) {
		Reading reading = readings.get(outlet);
		if (reading != null)
			reading.held += steps;
	}

	int size() {
		return readings.size();
	}

	private static boolean current(Reading reading, long now) {
		return now >= reading.at && now - reading.at < KEEP;
	}
}
