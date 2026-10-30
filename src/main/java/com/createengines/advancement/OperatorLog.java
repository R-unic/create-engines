package com.createengines.advancement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Remembers which player last placed or used each block, by packed position. Kept free of the world so the choice of
 * who gets the credit for an engine can be tested; {@link Operators} stores it with the level.
 */
public final class OperatorLog {

	public record Entry(UUID player, long time) {
	}

	private final Map<Long, Entry> entries = new HashMap<>();

	public void record(long pos, UUID player, long time) {
		entries.put(pos, new Entry(player, time));
	}

	public void forget(long pos) {
		entries.remove(pos);
	}

	public Map<Long, Entry> entries() {
		return entries;
	}

	/** The player who most recently placed or used any of {@code positions}, or null if none of them has been. */
	public @Nullable UUID latest(Iterable<Long> positions) {
		Entry best = null;
		for (long pos : positions) {
			Entry entry = entries.get(pos);
			if (entry != null && (best == null || entry.time() > best.time()))
				best = entry;
		}
		return best == null ? null : best.player();
	}
}
