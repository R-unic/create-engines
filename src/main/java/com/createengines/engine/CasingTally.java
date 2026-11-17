package com.createengines.engine;

/**
 * Counts distinct blocks, by their packed position, up to a limit. The engine's cladding milestone only asks whether
 * there are {@link EngineMilestones#CLAD_BLOCKS} casing blocks round the cylinders, so counting stops at the limit and
 * the tally is a few longs in a row instead of a set built (and thrown away) on every check.
 *
 * <p>Pure Java, so it is unit tested.
 */
final class CasingTally {

	private final long[] seen;
	private int count;

	CasingTally(int limit) {
		seen = new long[limit];
	}

	/** Counts {@code key} unless it is already counted or the tally is full. */
	void add(long key) {
		if (count == seen.length)
			return;
		for (int i = 0; i < count; i++)
			if (seen[i] == key)
				return;
		seen[count++] = key;
	}

	/** Distinct keys counted, never more than the limit. */
	int count() {
		return count;
	}

	/** Nothing more can change the count. */
	boolean full() {
		return count == seen.length;
	}
}
