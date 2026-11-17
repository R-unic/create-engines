package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamStallWatchTest {

	private static final long TICK = 50_000_000L;

	@Test
	void aStreamReadTenTimesASecondNeverStalls() {
		StreamStallWatch watch = new StreamStallWatch();
		int reads = 0;
		for (int tick = 0; tick < 1000; tick++) {
			if (tick % 2 == 0)
				reads++;
			assertFalse(watch.tick(reads, tick * TICK));
		}
	}

	@Test
	void aStreamThatStopsBeingReadStalls() {
		StreamStallWatch watch = new StreamStallWatch();
		int reads = 5;
		watch.tick(reads, 0);
		boolean stalled = false;
		int ticks = 0;
		while (!stalled && ticks < 100) {
			ticks++;
			stalled = watch.tick(reads, ticks * TICK);
		}
		assertTrue(stalled);
		assertTrue(ticks <= StreamStallWatch.STALLED_TICKS + 1);
	}

	@Test
	void aHitchInTheGameIsNotAStall() {
		StreamStallWatch watch = new StreamStallWatch();
		watch.tick(3, 0);
		for (int tick = 1; tick < 10; tick++)
			watch.tick(3, tick * TICK);
		// The game froze for ten seconds, e.g. loading shaders: the first tick back says nothing
		long back = 10_000_000_000L;
		for (int tick = 0; tick < StreamStallWatch.STALLED_TICKS - 1; tick++)
			assertFalse(watch.tick(3, back + tick * TICK));
	}

	@Test
	void aReadClearsTheRun() {
		StreamStallWatch watch = new StreamStallWatch();
		watch.tick(1, 0);
		for (int tick = 1; tick < StreamStallWatch.STALLED_TICKS - 1; tick++)
			assertFalse(watch.tick(1, tick * TICK));
		assertFalse(watch.tick(2, 20 * TICK));
		for (int tick = 21; tick < 21 + StreamStallWatch.STALLED_TICKS - 1; tick++)
			assertFalse(watch.tick(2, tick * TICK));
	}
}
