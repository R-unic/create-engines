package com.createengines.engine.sim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A map of the lookup tables built for a cylinder shape or a cam profile, kept to a fixed size.
 * <p>
 * Each table is about 90 KB and depends only on the shape that asked for it, so they are built once and shared. But a shape
 * is made of the part effects and the tuning in force, and every value either takes makes a different shape: a pack or a
 * config that is reloaded with new values would add a table for each, and nothing took them out. This keeps the
 * {@link #LIMIT} most recently asked for and lets the rest go. A table that is let go and asked for again is built again and is
 * the same to the last digit, so only the memory changes: an engine that already holds a table keeps it.
 * <p>
 * Asking is only done when a cylinder is built, never while one runs, so the lock costs nothing that matters. Pure Java, like
 * the rest of this package.
 */
final class TableCache {

	/** Tables kept for each kind: a dozen shapes are in use at once at most, so this is room for every pack state seen in a long session. */
	static final int LIMIT = 128;

	private TableCache() {
	}

	/** An empty cache that keeps the {@link #LIMIT} shapes asked for most recently. */
	static <K, V> Map<K, V> create() {
		return create(LIMIT);
	}

	/** An empty cache that keeps the {@code limit} shapes asked for most recently; one in step with the others may be asked at once from many threads. */
	static <K, V> Map<K, V> create(int limit) {
		return Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > limit;
			}
		});
	}
}
