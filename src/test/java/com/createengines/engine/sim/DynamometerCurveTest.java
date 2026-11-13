package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The curve-reading helpers of {@link Dynamometer}, which need no engine to run. */
class DynamometerCurveTest {

	@Test
	void theSpeedsRunUpInStepsAndStartWithTheUsualOnes() {
		assertEquals(16, Dynamometer.speed(0), 0);
		assertEquals(256, Dynamometer.speed(15), 0);
		for (int i = 0; i < Dynamometer.SPEEDS.length; i++)
			assertEquals(Dynamometer.SPEEDS[i], Dynamometer.speed(i), 0);
		for (int i = 1; i < Dynamometer.pointCount(1e9); i++)
			assertTrue(Dynamometer.speed(i) > Dynamometer.speed(i - 1));
	}

	@Test
	void aCurveAlwaysHasAtLeastTheFirstPoint() {
		assertEquals(1, Dynamometer.pointCount(0));
		assertEquals(1, Dynamometer.pointCount(16));
		assertEquals(1, Dynamometer.pointCount(31));
		assertEquals(2, Dynamometer.pointCount(32));
	}

	@Test
	void theUsualLimitGivesTheSixteenPointCurve() {
		assertEquals(Dynamometer.SPEEDS.length, Dynamometer.pointCount(Dynamometer.DEFAULT_MAX_RPM));
		assertTrue(Dynamometer.pointCount(512) > Dynamometer.pointCount(256));
		assertTrue(Dynamometer.pointCount(1e9) > Dynamometer.pointCount(8192) - 1);
	}

	@Test
	void aMeasurementHasRoomForTheUsualCurveEvenWhenAskedForLess() {
		Dynamometer.Measurement slow = new Dynamometer.Measurement(null, 64);
		assertEquals(Dynamometer.SPEEDS.length, slow.curve().length);
		assertEquals(slow.curve().length, slow.ripples().length);

		Dynamometer.Measurement fast = new Dynamometer.Measurement(null, 8192);
		assertEquals(Dynamometer.pointCount(8192), fast.curve().length);
		assertEquals(fast.curve().length, fast.ripples().length);
	}

	@Test
	void peakTorqueLooksOnlyAtSpeedsUpToTheOneAsked() {
		double[] curve = {1, 5, 3, 9};
		assertEquals(1, Dynamometer.peakTorque(curve, 16), 0);
		assertEquals(1, Dynamometer.peakTorque(curve, 0), 0, "the first point always counts");
		assertEquals(5, Dynamometer.peakTorque(curve, 32), 0);
		assertEquals(5, Dynamometer.peakTorque(curve, 47), 0);
		assertEquals(5, Dynamometer.peakTorque(curve, 48), 0);
		assertEquals(9, Dynamometer.peakTorque(curve, 64), 0);
		assertEquals(9, Dynamometer.peakTorque(curve, 1e9), 0);
	}

	@Test
	void peakTorqueIsNeverBelowZero() {
		assertEquals(0, Dynamometer.peakTorque(new double[] {-3, -1, -2}, 256), 0);
		assertEquals(0, Dynamometer.peakTorque(new double[0], 256), 0);
	}

	@Test
	void aSmoothEngineIsRatedAtItsPeak() {
		double[] curve = {1, 5, 3, 9};
		double[] ripples = new double[4];
		assertEquals(9, Dynamometer.ratedTorque(curve, ripples, 64), 1e-12);
		assertEquals(5, Dynamometer.ratedTorque(curve, ripples, 32), 1e-12);
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, 64), 1e-12);
	}

	@Test
	void roughnessHoldsBackTheRatingOfTheSpeedThatCausesIt() {
		double[] curve = {10, 8};
		double[] ripples = {Roughness.FREE_RIPPLE + 1, 0};
		// The first point is as rough as it can be, so it counts at its smoothed value and the second wins
		assertEquals(10 * (1 - Roughness.MAX_LOSS), Dynamometer.ratedTorque(curve, ripples, 16), 1e-12);
		assertEquals(8, Dynamometer.ratedTorque(curve, ripples, 32), 1e-12);
		assertEquals(0.2, Dynamometer.roughnessLoss(curve, ripples, 32), 1e-12);
		assertEquals(Roughness.MAX_LOSS, Dynamometer.roughnessLoss(curve, ripples, 16), 1e-12);
	}

	@Test
	void aRippleWithinTheFreeAllowanceCostsNothing() {
		double[] curve = {10};
		assertEquals(10, Dynamometer.ratedTorque(curve, new double[] {Roughness.FREE_RIPPLE}, 256), 1e-12);
	}

	@Test
	void missingRipplesAreReadAsSmooth() {
		double[] curve = {4, 6, 2};
		assertEquals(6, Dynamometer.ratedTorque(curve, new double[0], 256), 1e-12);
		assertEquals(0, Dynamometer.roughnessLoss(curve, new double[0], 256), 1e-12);
	}

	@Test
	void anEngineThatMakesNoTorqueLosesNothingToRoughness() {
		double[] curve = {0, 0, -1};
		assertEquals(0, Dynamometer.roughnessLoss(curve, new double[] {1, 1, 1}, 256), 0);
		assertEquals(0, Dynamometer.ratedTorque(curve, new double[] {1, 1, 1}, 256), 0);
	}
}
