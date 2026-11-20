package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The size cap on the tables kept for cylinder shapes and cam profiles. Before it, every distinct shape a reloaded pack or config
 * made left a table of about 90 KB behind for good. Nothing about what the tables hold changed, only how many are kept.
 */
class TableCacheTest {

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> staticMap(Class<?> owner, String field) throws Exception {
		Field f = owner.getDeclaredField(field);
		f.setAccessible(true);
		return (Map<Object, Object>) f.get(null);
	}

	@Test
	void theCapIsOneHundredAndTwentyEightTablesForEachKind() {
		assertEquals(128, TableCache.LIMIT);
		Map<Integer, String> cache = TableCache.create();
		for (int i = 0; i < 128; i++)
			cache.put(i, "v" + i);
		assertEquals(128, cache.size(), "the whole limit fits");
		cache.put(128, "v128");
		assertEquals(128, cache.size(), "one more pushes the oldest out");
		assertFalse(cache.containsKey(0));
		assertTrue(cache.containsKey(1) && cache.containsKey(128));
	}

	@Test
	void exactlyTheLimitIsKeptAndTheOldestBeyondItIsDropped() {
		Map<Integer, String> cache = TableCache.create(3);
		cache.put(1, "a");
		cache.put(2, "b");
		cache.put(3, "c");
		assertEquals(3, cache.size(), "three fit in a cache of three");
		assertTrue(cache.containsKey(1) && cache.containsKey(2) && cache.containsKey(3));
		cache.put(4, "d");
		assertEquals(3, cache.size());
		assertFalse(cache.containsKey(1), "the one asked for longest ago goes");
		assertTrue(cache.containsKey(2) && cache.containsKey(3) && cache.containsKey(4));
	}

	@Test
	void aShapeAskedForRecentlyOutlivesOlderOnes() {
		Map<Integer, String> cache = TableCache.create(3);
		cache.computeIfAbsent(1, k -> "a");
		cache.computeIfAbsent(2, k -> "b");
		cache.computeIfAbsent(3, k -> "c");
		// Asking for 1 again makes 2 the oldest
		assertEquals("a", cache.computeIfAbsent(1, k -> "not built again"));
		cache.computeIfAbsent(4, k -> "d");
		assertTrue(cache.containsKey(1), "asked for again, so kept");
		assertFalse(cache.containsKey(2), "the oldest now, so dropped");
		assertTrue(cache.containsKey(3) && cache.containsKey(4));
	}

	@Test
	void aTableIsBuiltOnceWhileItIsKept() {
		Map<Integer, Object> cache = TableCache.create(2);
		AtomicInteger built = new AtomicInteger();
		Object first = cache.computeIfAbsent(7, k -> {
			built.incrementAndGet();
			return new Object();
		});
		assertSame(first, cache.computeIfAbsent(7, k -> {
			built.incrementAndGet();
			return new Object();
		}));
		assertEquals(1, built.get());
	}

	/** A flood of distinct shapes, as a reloaded pack or config makes, no longer grows the table of cylinder shapes without end. */
	@Test
	void aFloodOfDistinctCylinderShapesKeepsOnlyTheLimit() throws Exception {
		Map<Object, Object> cache = staticMap(CylinderSpec.class, "GEOMETRIES");
		cache.clear();
		try {
			List<CylinderSpec> specs = new ArrayList<>();
			for (int i = 0; i < 300; i++) {
				CylinderSpec spec = new CylinderSpec(5 + i * 0.01, 0.2857, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.2);
				specs.add(spec);
				spec.geometry();
			}
			assertEquals(TableCache.LIMIT, cache.size(), "no more than the cap, and the cap is reached");
			// The newest are kept, the oldest are not
			assertTrue(cache.containsKey(specs.get(299)));
			assertFalse(cache.containsKey(specs.get(0)));
			// An evicted shape asked for again gets a new table that holds the same figures to the last digit
			CylinderSpec evicted = specs.get(0);
			CylinderSpec.Geometry rebuilt = evicted.geometry();
			assertEquals(TableCache.LIMIT, cache.size());
			for (int k = 0; k <= 360 * 8; k += 17) {
				double angle = k / 8d;
				assertEquals(evicted.volumeAt(angle), rebuilt.volume(angle), 1e-12, "volume at " + angle);
			}
			assertSame(rebuilt, evicted.geometry(), "and it is kept again, shared");
		} finally {
			cache.clear();
		}
	}

	@Test
	void aFloodOfDistinctCamProfilesKeepsOnlyTheLimit() throws Exception {
		Map<Object, Object> cache = staticMap(CamProfile.class, "CURVES");
		cache.clear();
		try {
			List<CamProfile> profiles = new ArrayList<>();
			for (int i = 0; i < 300; i++) {
				CamProfile profile = new CamProfile(700 + i * 0.01, 220, 500, 10, 345, 350);
				profiles.add(profile);
				profile.intakeCurve();
			}
			assertEquals(TableCache.LIMIT, cache.size());
			assertTrue(cache.containsKey(profiles.get(299)));
			assertFalse(cache.containsKey(profiles.get(0)));
			CamProfile evicted = profiles.get(0);
			CamProfile.Curve rebuilt = evicted.intakeCurve();
			for (int k = 0; k <= 720 * 8; k += 29) {
				double angle = k / 8d;
				assertEquals(evicted.intakeLift(angle), rebuilt.at(angle), 1e-12, "lift at " + angle);
			}
			assertSame(rebuilt, evicted.intakeCurve());
			assertNotSame(profiles.get(1).intakeCurve(), rebuilt);
		} finally {
			cache.clear();
		}
	}

	/** Many threads asking for many shapes at once, as the server and client threads do: no error, never past the cap, every table right. */
	@Test
	void manyThreadsFloodingTheCacheNeverBreakItOrPassTheLimit() throws Exception {
		Map<Object, Object> cache = staticMap(CylinderSpec.class, "GEOMETRIES");
		cache.clear();
		try {
			int threads = 4;
			CountDownLatch go = new CountDownLatch(1);
			ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
			List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final int id = t;
				Thread worker = new Thread(() -> {
					try {
						go.await();
						for (int i = 0; i < 150; i++) {
							CylinderSpec spec = new CylinderSpec(5 + ((i + id * 37) % 250) * 0.01, 0.2857, 20, 20, 0.5, 0.02, 0.06,
								0.08, 0.01, 0.2);
							CylinderSpec.Geometry geometry = spec.geometry();
							double angle = (i * 7) % 360;
							if (Math.abs(geometry.volume(angle) - spec.volumeAt(angle)) > 1e-5 * spec.volumeAt(angle))
								throw new AssertionError("a wrong table for " + spec + " at " + angle);
							if (cache.size() > TableCache.LIMIT)
								throw new AssertionError("past the cap: " + cache.size());
						}
					} catch (Throwable e) {
						thrown.add(e);
					}
				}, "flood-" + t);
				workers.add(worker);
				worker.start();
			}
			go.countDown();
			for (Thread worker : workers)
				worker.join(30_000);
			for (Thread worker : workers)
				assertFalse(worker.isAlive(), worker.getName() + " did not finish");
			assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
			assertTrue(cache.size() <= TableCache.LIMIT);
		} finally {
			cache.clear();
		}
	}
}
