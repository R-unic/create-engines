package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Worked figures and exact edges for {@link RunComparison}. */
class RunComparisonExactTest {

	@Test
	void everyDeltaIsNowMinusBefore() {
		float[] rpm = {100, 200, 300, 400};
		RunComparison c = RunComparison.between(rpm, new float[] {10, 12, 11, 8}, rpm, new float[] {10, 13, 12, 10});
		// Before: peak torque 12, peak power 3300 at 300 RPM, band: only 400 RPM (3200) is within 90% of it, so 100 RPM wide
		// Now: peak torque 13, peak power 4000 at 400 RPM, band: 300 RPM (3600) is at 90% of it
		assertEquals(1, c.torqueDelta(), 1e-4);
		assertEquals(700, c.powerDelta(), 1e-2);
		assertEquals(100, c.peakShift(), 1e-4);
		assertEquals((13f - 12f) / 12f * 100, c.torquePercent(), 1e-3);
		assertEquals((4000f - 3300f) / 3300f * 100, c.powerPercent(), 1e-3);
	}

	@Test
	void aPercentIsOfTheOldPeakAndIsZeroWhenThereWasNone() {
		float[] rpm = {100};
		RunComparison grew = RunComparison.between(rpm, new float[] {10}, rpm, new float[] {12});
		assertEquals(20, grew.torquePercent(), 1e-4);
		assertEquals(20, grew.powerPercent(), 1e-4);
		RunComparison shrank = RunComparison.between(rpm, new float[] {10}, rpm, new float[] {5});
		assertEquals(-50, shrank.torquePercent(), 1e-4);
		assertEquals(-5, shrank.torqueDelta(), 1e-6);
		RunComparison fromNothing = RunComparison.between(rpm, new float[] {0}, rpm, new float[] {5});
		assertEquals(0, fromNothing.torquePercent(), 0);
		assertEquals(0, fromNothing.powerPercent(), 0);
		assertEquals(5, fromNothing.torqueDelta(), 1e-6);
		RunComparison empty = RunComparison.between(new float[0], new float[0], rpm, new float[] {5});
		assertEquals(5, empty.torqueDelta(), 1e-6);
		assertEquals(0, empty.torquePercent(), 0);
		// Arrays of different lengths use the shorter
		RunComparison ragged = RunComparison.between(new float[] {100, 200}, new float[] {10}, new float[] {100}, new float[] {10, 99});
		assertEquals(0, ragged.torqueDelta(), 1e-6);
	}

	@Test
	void thePowerbandIncludesAPointExactlyAtNinetyPercentOfThePeakPower() {
		// Peak power 100 at the last speed; the point before it makes exactly POWERBAND of that
		float edge = RunComparison.POWERBAND * 100f;
		float[] rpm = {1, 1, 2};
		RunComparison below = RunComparison.between(rpm, new float[] {0, edge, 50}, rpm, new float[] {0, edge, 50});
		assertEquals(0, below.bandDelta(), 0);
		RunComparison widerNow = RunComparison.between(new float[] {1, 2}, new float[] {0, 50}, rpm, new float[] {0, edge, 50});
		// Before: only the peak is in the band (width 0); now the point at exactly 90% counts too (2 - 1)
		assertEquals(1, widerNow.bandDelta(), 1e-6);
		// And on the high side
		float[] rpmHigh = {2, 1};
		RunComparison high = RunComparison.between(new float[] {2}, new float[] {50}, rpmHigh, new float[] {50, edge});
		assertEquals(-1, high.bandDelta(), 1e-6);
	}

	@Test
	void theBandStopsAtTheFirstPointBelowNinetyPercent() {
		float[] rpm = {100, 200, 300, 400, 500};
		// Powers (torque times rpm): 500, 4000, 9000, 4000, 2500
		RunComparison c = RunComparison.between(rpm, new float[] {5, 20, 30, 10, 5}, rpm, new float[] {5, 20, 30, 10, 5});
		assertEquals(0, c.bandDelta(), 0);
		RunComparison wide = RunComparison.between(rpm, new float[] {5, 20, 30, 10, 5}, rpm, new float[] {5, 25, 30, 22.5f, 5});
		// Now: powers 500, 5000, 9000, 9000... peak is the first of equal powers (index 2); index 1 (5000) and 3 (9000) -> only 3 is in
		assertEquals(100, wide.bandDelta(), 1e-4);
	}

	@Test
	void twoRunsAreTheSameOnlyJustInsideEveryLimit() {
		assertTrue(new RunComparison(0, 0, 0, 0, 0, 0).same());
		assertTrue(new RunComparison(0, 0, 0.99f, 0.99f, 0.49f, 0.49f).same());
		assertTrue(new RunComparison(0, 0, -0.99f, -0.99f, -0.49f, -0.49f).same());
		assertFalse(new RunComparison(0, 0, 0, 0, RunComparison.SAME, 0).same());
		assertFalse(new RunComparison(0, 0, 0, 0, -RunComparison.SAME, 0).same());
		assertFalse(new RunComparison(0, 0, 0, 0, 0, RunComparison.SAME).same());
		assertFalse(new RunComparison(0, 0, 0, 0, 0, -RunComparison.SAME).same());
		assertFalse(new RunComparison(0, 0, 1, 0, 0, 0).same());
		assertFalse(new RunComparison(0, 0, -1, 0, 0, 0).same());
		assertFalse(new RunComparison(0, 0, 0, 1, 0, 0).same());
		assertFalse(new RunComparison(0, 0, 0, -1, 0, 0).same());
	}
}
