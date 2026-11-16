package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class SootCollectorTest {

	@Test
	void everyFiftyMbOfUnburnedFuelMakesALump() {
		SootCollector trap = new SootCollector();
		assertEquals(0, trap.add(49.9));
		assertEquals(1, trap.add(0.1));
		assertEquals(3, trap.add(150));
		assertEquals(4, trap.stored());
		assertEquals(0, trap.progress(), 1e-9);
	}

	@Test
	void aFullTrapLetsTheRestThroughAndNeverOverfills() {
		SootCollector trap = new SootCollector();
		assertEquals(SootCollector.CAPACITY, trap.add(1e9));
		assertTrue(trap.full());
		assertEquals(0, trap.add(500));
		assertEquals(SootCollector.CAPACITY, trap.stored());
		assertEquals(0, trap.progress(), 1e-9);
		assertEquals(10, trap.take(10));
		assertFalse(trap.full());
		assertEquals(54, trap.stored());
	}

	@Test
	void takingMoreThanIsThereOrNegativeGivesWhatIsThere() {
		SootCollector trap = new SootCollector();
		trap.add(125);
		assertEquals(0, trap.take(-5));
		assertEquals(2, trap.take(1000));
		assertEquals(0, trap.stored());
		assertEquals(25, trap.progress(), 1e-9);
	}

	@Test
	void hostileAmountsCountAsNothing() {
		SootCollector trap = new SootCollector();
		assertEquals(0, trap.add(Double.NaN));
		assertEquals(0, trap.add(Double.NEGATIVE_INFINITY));
		assertEquals(0, trap.add(Double.POSITIVE_INFINITY));
		assertEquals(0, trap.add(-10));
		assertEquals(0, trap.stored());
		assertEquals(0, trap.progress(), 0);
		assertEquals(0, SootCollector.sootFrom(Double.NaN, 0.5, 1), 0);
		assertEquals(0, SootCollector.sootFrom(1, Double.POSITIVE_INFINITY, 0), 0);
		assertEquals(0, SootCollector.sootFrom(1, 0.5, -3), 0);
		assertEquals(0, SootCollector.share(Double.NaN, 1, 1, 1, 1), 0);
		assertEquals(0, SootCollector.share(100, 0, 1, 1, 1), 0);
		assertEquals(0, SootCollector.share(100, 1, 0, 1, 1), 0);
		assertEquals(0, SootCollector.share(100, 1, 1, 1, 0), 0);
	}

	@Test
	void aDamagedSaveIsClamped() {
		SootCollector trap = new SootCollector();
		trap.restore(Integer.MAX_VALUE, 1e12);
		assertEquals(SootCollector.CAPACITY, trap.stored());
		assertEquals(0, trap.progress(), 0);
		trap.restore(-4, Double.NaN);
		assertEquals(0, trap.stored());
		assertEquals(0, trap.progress(), 0);
		trap.restore(3, 1e12);
		assertEquals(SootCollector.MB_PER_SOOT, trap.progress(), 1e-9);
		trap.add(0.5);
		assertTrue(trap.stored() <= SootCollector.CAPACITY && trap.progress() >= 0);
	}

	@Test
	void soakWithRandomInputKeepsTheTrapInBounds() {
		Random random = new Random(7);
		SootCollector trap = new SootCollector();
		for (int i = 0; i < 100_000; i++) {
			switch (random.nextInt(4)) {
				case 0 -> trap.add(random.nextDouble() * 200 - 20);
				case 1 -> trap.take(random.nextInt(80) - 5);
				case 2 -> trap.add(random.nextBoolean() ? Double.NaN : Double.MAX_VALUE);
				default -> trap.restore(random.nextInt(200) - 50, random.nextDouble() * 120 - 10);
			}
			assertTrue(trap.stored() >= 0 && trap.stored() <= SootCollector.CAPACITY, "stored " + trap.stored());
			assertTrue(trap.progress() >= 0 && trap.progress() <= SootCollector.MB_PER_SOOT, "progress " + trap.progress());
		}
	}

	@Test
	void aTrapInSeriesCatchesEverythingAndABypassedOneHalf() {
		assertEquals(1f, SootCollector.catchShare(true, false), 0);
		assertEquals(0.5f, SootCollector.catchShare(false, true), 0);
		assertEquals(0f, SootCollector.catchShare(false, false), 0);
	}

	@Test
	void runsAndTrapsSplitTheSootBetweenThem() {
		// A V12 with a trap in each bank's run: each run is half the heads and one trap
		assertEquals(50, SootCollector.share(100, 6, 12, 1f, 1), 1e-9);
		// Two traps in one run share what the run catches
		assertEquals(25, SootCollector.share(100, 6, 12, 1f, 2), 1e-9);
		// A bypassed trap gets half of that
		assertEquals(25, SootCollector.share(100, 6, 12, 0.5f, 1), 1e-9);
		// More heads than the engine total (a shared run) cannot catch more than there is
		assertEquals(100, SootCollector.share(100, 20, 12, 1f, 1), 1e-9);
	}

	@Test
	void sootFromARichCycleScalesWithFuelAndTheFuelsOwnFactor() {
		assertEquals(0.3 * 0.4, SootCollector.sootFrom(0.3, 0.4, 1), 1e-12);
		assertEquals(0, SootCollector.sootFrom(0.3, 0.4, 0), 0);
		assertTrue(SootCollector.sootFrom(0.3, 0.4, 2) > SootCollector.sootFrom(0.3, 0.4, 1));
	}
}
