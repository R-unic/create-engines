package com.createengines.compat.pollution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

class AreaCapCacheTest {

	private final AreaCapCache<String> cache = new AreaCapCache<>();
	private final AtomicInteger readings = new AtomicInteger();

	private LongSupplier air(long held) {
		return () -> {
			readings.incrementAndGet();
			return held;
		};
	}

	@Test
	void theAirIsReadOnceForAsLongAsAReadingIsKept() {
		for (int tick = 0; tick < AreaCapCache.KEEP; tick += 10)
			assertFalse(cache.full("outlet", tick, 100, air(5)));
		assertEquals(1, readings.get());
		assertFalse(cache.full("outlet", AreaCapCache.KEEP, 100, air(5)));
		assertEquals(2, readings.get(), "an old reading is taken again");
	}

	@Test
	void eachOutletHasItsOwnReading() {
		assertTrue(cache.full("hazy", 0, 10, air(10)), "at the cap is full");
		assertFalse(cache.full("clear", 0, 10, air(9)));
		assertEquals(2, readings.get());
	}

	@Test
	void aChangedCapIsFeltAtOnceOnTheSameReading() {
		assertTrue(cache.full("outlet", 0, 12, air(12)));
		assertFalse(cache.full("outlet", 0, 100_000, air(0)));
		assertTrue(cache.full("outlet", 0, 5, air(0)));
		assertEquals(1, readings.get());
	}

	@Test
	void whatAnOutletReleasesCountsTowardsTheCapBeforeTheAirIsReadAgain() {
		assertFalse(cache.full("outlet", 0, 20, air(5)));
		cache.added("outlet", 14);
		assertFalse(cache.full("outlet", 10, 20, air(0)), "19 of 20");
		cache.added("outlet", 1);
		assertTrue(cache.full("outlet", 20, 20, air(0)));
		assertFalse(cache.full("outlet", AreaCapCache.KEEP, 20, air(3)), "the next reading says what is really there");
	}

	@Test
	void releaseFromAnOutletWithNoReadingIsNotKept() {
		cache.added("never read", 50);
		assertEquals(0, cache.size());
	}

	@Test
	void aClockThatWentBackReadsAgain() {
		cache.full("outlet", 1000, 10, air(0));
		cache.full("outlet", 999, 10, air(0));
		assertEquals(2, readings.get());
	}

	@Test
	void oldReadingsAreDroppedOnceThereAreMany() {
		for (int i = 0; i < AreaCapCache.PRUNE_FROM; i++)
			cache.full("old " + i, 0, 10, air(0));
		assertEquals(AreaCapCache.PRUNE_FROM, cache.size());
		cache.full("new", AreaCapCache.KEEP, 10, air(0));
		assertEquals(1, cache.size());
	}
}
