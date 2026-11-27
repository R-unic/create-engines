package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What dirty air costs an engine, and what a filter gives back. */
class IntakeAirTest {

	@Test
	void cleanAirCostsExactlyNothing() {
		assertEquals(0.0, IntakeAir.dirt(0, IntakeAir.DEFAULT_FULL));
		for (IntakeAir.Filter filter : IntakeAir.Filter.values())
			assertEquals(0.0, IntakeAir.loss(0, filter));
	}

	@Test
	void dirtGrowsWithTheGasAndStopsAtAsDirtyAsItCounts() {
		assertEquals(0.5, IntakeAir.dirt(75, 150), 1e-12);
		assertEquals(1.0, IntakeAir.dirt(150, 150));
		assertEquals(1.0, IntakeAir.dirt(100000, 150));
	}

	@Test
	void withNothingCountingAsDirtyNoAirIs() {
		assertEquals(0.0, IntakeAir.dirt(1, 0));
		assertEquals(0.0, IntakeAir.dirt(5, -1));
		assertEquals(0.0, IntakeAir.dirt(-5, 150));
	}

	@Test
	void theDirtiestAirTakesTheMostAndNoMore() {
		assertEquals(IntakeAir.MAX_LOSS, IntakeAir.loss(1, IntakeAir.Filter.NONE), 1e-12);
		assertEquals(IntakeAir.MAX_LOSS, IntakeAir.loss(7, IntakeAir.Filter.NONE), 1e-12);
		assertEquals(IntakeAir.MAX_LOSS / 2, IntakeAir.loss(0.5, IntakeAir.Filter.NONE), 1e-12);
	}

	@Test
	void aFilterTakesMostBackAndLessWhenTheAirCanGoRoundIt() {
		double none = IntakeAir.loss(1, IntakeAir.Filter.NONE);
		double bypassed = IntakeAir.loss(1, IntakeAir.Filter.BYPASSED);
		double inline = IntakeAir.loss(1, IntakeAir.Filter.INLINE);
		assertEquals(none * IntakeAir.FILTERED_SHARE, inline, 1e-12);
		assertEquals(none * IntakeAir.BYPASSED_SHARE, bypassed, 1e-12);
		assertTrue(inline > 0 && inline < bypassed && bypassed < none, "a filter helps, and one in the way helps most");
	}

	@Test
	void nonsenseReadingsNeverMakeAirOrANumberThatIsNotOne() {
		for (long held : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE})
			for (long full : new long[] {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}) {
				double dirt = IntakeAir.dirt(held, full);
				assertTrue(dirt >= 0 && dirt <= 1, held + " of " + full + ": " + dirt);
			}
		for (double dirt : new double[] {Double.NaN, -1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.MAX_VALUE})
			for (IntakeAir.Filter filter : IntakeAir.Filter.values()) {
				double loss = IntakeAir.loss(dirt, filter);
				assertTrue(loss >= 0 && loss <= IntakeAir.MAX_LOSS, dirt + " " + filter + ": " + loss);
			}
	}
}
