package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.WearFailures.Kind;

/** The failure counts a summary carries so the Black Box can tell one more has failed. */
class WearFailuresTest {

	@Test
	void eachKindKeepsItsOwnCountAndNoneMeansZero() {
		long packed = WearFailures.pack(1, 2, 3, 4);
		assertEquals(1, WearFailures.count(packed, Kind.ROD_THROWN));
		assertEquals(2, WearFailures.count(packed, Kind.GASKET_BLOWN));
		assertEquals(3, WearFailures.count(packed, Kind.PISTON_SEIZED));
		assertEquals(4, WearFailures.count(packed, Kind.APEX_FAILED));
		assertEquals(0, WearFailures.pack(0, 0, 0, 0));
		for (Kind kind : Kind.values())
			assertEquals(0, WearFailures.count(0, kind));
	}

	@Test
	void aPackedFigureIsNeverNegativeSoNoReadingCannotBeMistakenForOne() {
		assertTrue(WearFailures.pack(500, 500, 500, 500) >= 0);
		assertEquals(127, WearFailures.count(WearFailures.pack(500, 0, 0, 0), Kind.ROD_THROWN));
		assertEquals(127, WearFailures.count(WearFailures.pack(0, 0, 0, 500), Kind.APEX_FAILED));
		assertEquals(0, WearFailures.count(WearFailures.pack(-5, -5, -5, -5), Kind.GASKET_BLOWN));
	}

	@Test
	void aRiseIsOneMoreOfThatKindAndNothingElseIsOne() {
		long before = WearFailures.pack(1, 0, 0, 0);
		assertTrue(WearFailures.rose(before, WearFailures.pack(2, 0, 0, 0), Kind.ROD_THROWN));
		assertFalse(WearFailures.rose(before, WearFailures.pack(2, 0, 0, 0), Kind.GASKET_BLOWN), "another kind did not rise");
		assertFalse(WearFailures.rose(before, before, Kind.ROD_THROWN), "the same count is not an event");
		assertFalse(WearFailures.rose(WearFailures.pack(2, 0, 0, 0), before, Kind.ROD_THROWN), "a falling count (a part replaced) is not an event");
		assertTrue(WearFailures.rose(0, WearFailures.pack(0, 0, 1, 0), Kind.PISTON_SEIZED));
	}

	@Test
	void noReadingIsNeverARise() {
		assertFalse(WearFailures.rose(-1, WearFailures.pack(5, 5, 5, 5), Kind.ROD_THROWN));
		assertFalse(WearFailures.rose(WearFailures.pack(0, 0, 0, 0), -1, Kind.ROD_THROWN));
	}

	@Test
	void aRiseFromNothingCountsAndTheNoReadingFigureIsNotAZero() {
		assertTrue(WearFailures.rose(0, WearFailures.pack(1, 0, 0, 0), Kind.ROD_THROWN), "from none to one is a rise");
		assertEquals(-1, WearFailures.NO_READING);
		assertFalse(WearFailures.rose(WearFailures.NO_READING, WearFailures.NO_READING, Kind.ROD_THROWN));
		assertFalse(WearFailures.rose(0, 0, Kind.APEX_FAILED), "none to none is not");
		assertFalse(WearFailures.rose(0, WearFailures.NO_READING, Kind.ROD_THROWN), "nor is anything after no reading");
	}
}
