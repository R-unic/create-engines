package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunComparisonTest {

	private static final float[] RPM = {100, 200, 300, 400, 500};

	@Test
	void identicalRunsAreTheSame() {
		float[] torque = {10, 12, 14, 13, 9};
		RunComparison comparison = RunComparison.between(RPM, torque, RPM, torque.clone());
		assertTrue(comparison.same());
		assertEquals(0, comparison.torqueDelta());
		assertEquals(0, comparison.powerDelta());
	}

	@Test
	void moreTorqueEverywhereShowsAsAGain() {
		float[] before = {10, 12, 14, 13, 9};
		float[] after = {11, 13.2f, 15.4f, 14.3f, 9.9f};
		RunComparison comparison = RunComparison.between(RPM, before, RPM, after);
		assertFalse(comparison.same());
		assertEquals(1.4f, comparison.torqueDelta(), 0.01f);
		assertEquals(10, comparison.torquePercent(), 0.1f);
		assertEquals(10, comparison.powerPercent(), 0.1f);
		assertEquals(0, comparison.peakShift());
	}

	@Test
	void thePeakMovingUpTheRangeIsReportedInRpm() {
		float[] before = {10, 14, 12, 8, 5};
		float[] after = {6, 8, 12, 15, 13};
		RunComparison comparison = RunComparison.between(RPM, before, RPM, after);
		assertTrue(comparison.peakShift() > 0, "the power peak should have moved up");
	}

	@Test
	void aWiderBandShowsAsPositive() {
		float[] narrow = {2, 4, 14, 4, 2};
		float[] wide = {2, 6.3f, 14, 10.5f, 8.4f};
		assertTrue(RunComparison.between(RPM, narrow, RPM, wide).bandDelta() > 0);
	}

	@Test
	void anEmptyCurveComparesAsZeros() {
		RunComparison comparison = RunComparison.between(new float[0], new float[0], RPM, new float[] {1, 2, 3, 2, 1});
		assertEquals(0, comparison.torquePercent());
		assertEquals(3, comparison.torqueDelta());
	}
}
