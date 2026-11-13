package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CycleStatsTest {

	private static CycleStats filled() {
		CycleStats stats = new CycleStats();
		stats.work = 1;
		stats.fuelInjected = 2;
		stats.fuelBurned = 3;
		stats.compressionTemp = 4;
		stats.peakPressure = 5;
		stats.freshAirAtInjection = 6;
		stats.equivalence = 7;
		stats.sparks = 8;
		stats.deadSparks = 9;
		stats.flameOut = -1;
		stats.mafCut = 0.1;
		stats.mafAdded = 0.2;
		stats.pilotShot = 0.3;
		stats.preignition = 0.4;
		stats.result = CycleResult.TOO_RICH;
		return stats;
	}

	@Test
	void aNewCycleHasNothingInIt() {
		CycleStats stats = new CycleStats();
		assertEquals(0, stats.work, 0);
		assertEquals(0, stats.unburnedFraction(), 0);
		assertEquals(CycleResult.NONE, stats.result);
		assertFalse(stats.rich());
		assertFalse(stats.lean());
	}

	@Test
	void unburnedFractionIsTheShareOfTheShotThatDidNotBurn() {
		CycleStats stats = new CycleStats();
		stats.fuelInjected = 10;
		stats.fuelBurned = 7;
		assertEquals(0.3, stats.unburnedFraction(), 1e-12);
		stats.fuelBurned = 0;
		assertEquals(1, stats.unburnedFraction(), 1e-12);
	}

	@Test
	void unburnedFractionIsZeroWithNoShotOrAnOverBurn() {
		CycleStats stats = new CycleStats();
		stats.fuelBurned = 5;
		assertEquals(0, stats.unburnedFraction(), 0);
		stats.fuelInjected = 4;
		assertEquals(0, stats.unburnedFraction(), 0);
	}

	@Test
	void aBurnIsRichOnceMoreThanTheLimitGoesUnburned() {
		CycleStats stats = new CycleStats();
		stats.result = CycleResult.FIRED;
		stats.fuelInjected = 100;
		stats.fuelBurned = 100 * (1 - Mixture.RICH_UNBURNED) - 1;
		assertTrue(stats.rich());
		stats.fuelBurned = 100 * (1 - Mixture.RICH_UNBURNED) + 1;
		assertFalse(stats.rich());
	}

	@Test
	void onlyACycleThatFiredCanBeRichOrLean() {
		CycleStats stats = new CycleStats();
		stats.fuelInjected = 100;
		stats.fuelBurned = 0;
		stats.equivalence = 0.3;
		for (CycleResult result : CycleResult.values()) {
			stats.result = result;
			assertEquals(result == CycleResult.FIRED, stats.rich(), result.name());
			assertEquals(result == CycleResult.FIRED, stats.lean(), result.name());
		}
	}

	@Test
	void aFiredCycleIsLeanBelowTwoThirdsOfTheAir() {
		CycleStats stats = new CycleStats();
		stats.result = CycleResult.FIRED;
		stats.equivalence = 0.5;
		assertTrue(stats.lean());
		stats.equivalence = 0.65;
		assertFalse(stats.lean());
		stats.equivalence = 1.2;
		assertFalse(stats.lean());
		stats.equivalence = 0;
		assertFalse(stats.lean(), "no recorded equivalence is not lean");
	}

	@Test
	void copyingTakesEveryFigure() {
		CycleStats source = filled();
		CycleStats copy = new CycleStats();
		copy.copyFrom(source);
		assertEquals(source.work, copy.work, 0);
		assertEquals(source.fuelInjected, copy.fuelInjected, 0);
		assertEquals(source.fuelBurned, copy.fuelBurned, 0);
		assertEquals(source.compressionTemp, copy.compressionTemp, 0);
		assertEquals(source.peakPressure, copy.peakPressure, 0);
		assertEquals(source.freshAirAtInjection, copy.freshAirAtInjection, 0);
		assertEquals(source.equivalence, copy.equivalence, 0);
		assertEquals(source.sparks, copy.sparks);
		assertEquals(source.deadSparks, copy.deadSparks);
		assertEquals(source.flameOut, copy.flameOut);
		assertEquals(source.mafCut, copy.mafCut, 0);
		assertEquals(source.mafAdded, copy.mafAdded, 0);
		assertEquals(source.pilotShot, copy.pilotShot, 0);
		assertEquals(source.preignition, copy.preignition, 0);
		assertEquals(source.result, copy.result);
	}

	@Test
	void aCopyIsIndependentOfItsSource() {
		CycleStats source = filled();
		CycleStats copy = new CycleStats();
		copy.copyFrom(source);
		source.work = 99;
		source.result = CycleResult.FIRED;
		assertEquals(1, copy.work, 0);
		assertEquals(CycleResult.TOO_RICH, copy.result);
	}

	@Test
	void resettingClearsEveryFigure() {
		CycleStats stats = filled();
		stats.reset();
		CycleStats fresh = new CycleStats();
		assertEquals(fresh.work, stats.work, 0);
		assertEquals(fresh.fuelInjected, stats.fuelInjected, 0);
		assertEquals(fresh.fuelBurned, stats.fuelBurned, 0);
		assertEquals(fresh.compressionTemp, stats.compressionTemp, 0);
		assertEquals(fresh.peakPressure, stats.peakPressure, 0);
		assertEquals(fresh.freshAirAtInjection, stats.freshAirAtInjection, 0);
		assertEquals(fresh.equivalence, stats.equivalence, 0);
		assertEquals(fresh.sparks, stats.sparks);
		assertEquals(fresh.deadSparks, stats.deadSparks);
		assertEquals(fresh.flameOut, stats.flameOut);
		assertEquals(fresh.mafCut, stats.mafCut, 0);
		assertEquals(fresh.mafAdded, stats.mafAdded, 0);
		assertEquals(fresh.pilotShot, stats.pilotShot, 0);
		assertEquals(fresh.preignition, stats.preignition, 0);
		assertEquals(CycleResult.NONE, stats.result);
	}

	@Test
	void onlyTheFiredResultCountsAsFired() {
		for (CycleResult result : CycleResult.values())
			assertEquals(result == CycleResult.FIRED, result.fired(), result.name());
	}
}
