package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Edges of the dynamometer's small curve functions that the other suites let the mutation run change without a failure: a speed
 * exactly at the limit counts, a curve shorter than the speed table is read to its end and no further, a ripple list shorter than
 * the curve reads as smooth, and the crawl floor is judged at exactly its own speed. Every expected value is worked out by hand from the
 * documented rule: roughness takes {@code Roughness.loss(ripple)} off a point, and 0.5 ripple is the 0.4 maximum.
 */
class DynamometerEdgesTest {

	private static final double EPS = 1e-12;

	@Test
	void theSpeedTableIsWhatTheseTestsAssume() {
		assertEquals(16, Dynamometer.speed(0), 0);
		assertEquals(32, Dynamometer.speed(1), 0);
		assertEquals(Dynamometer.CRAWL_RPM, Dynamometer.speed(1), 0, "the crawl floor is the second point");
		assertEquals(48, Dynamometer.speed(2), 0);
		assertEquals(64, Dynamometer.speed(3), 0);
	}

	@Test
	void thePeakAndTheRatingAtASpeedIncludeThePointMeasuredAtExactlyThatSpeed() {
		double[] curve = {1, 2, 9, 3};
		double[] smooth = new double[4];
		assertEquals(9, Dynamometer.peakTorque(curve, Dynamometer.speed(2)), 0, "the point at the limit counts");
		assertEquals(2, Dynamometer.peakTorque(curve, Dynamometer.speed(2) - 0.5), 0, "one just below the limit does not");
		assertEquals(9, Dynamometer.ratedTorque(curve, smooth, Dynamometer.speed(2)), EPS);
		assertEquals(2, Dynamometer.ratedTorque(curve, smooth, Dynamometer.speed(2) - 0.5), EPS);
	}

	@Test
	void aCurveShorterThanTheSpeedTableIsReadToItsEndAtAnySpeed() {
		double[] curve = {1, 2, 9};
		double[] smooth = new double[3];
		assertEquals(9, Dynamometer.peakTorque(curve, 1e9), 0);
		assertEquals(9, Dynamometer.ratedTorque(curve, smooth, 1e9), EPS);
		assertEquals(0, Dynamometer.roughnessLoss(curve, smooth, 1e9), EPS);
	}

	@Test
	void aRippleListShorterThanTheCurveReadsTheRestAsSmooth() {
		double[] curve = {1, 2, 9};
		assertEquals(9, Dynamometer.ratedTorque(curve, new double[] {0}, Dynamometer.speed(2)), EPS);
		assertEquals(0, Dynamometer.roughnessLoss(curve, new double[] {0, 0}, Dynamometer.speed(2)), EPS);
		assertEquals(0, Dynamometer.roughnessLoss(curve, new double[0], Dynamometer.speed(2)), EPS);
	}

	@Test
	void roughnessTakesTheRatingOffByTheShareTheRoughestPointLoses() {
		double[] curve = {10, 20, 40, 30};
		double[] ripples = {0, 0, 0.5, 0};
		// Up to 64 RPM the 40 point is rated at 40 * 0.6 = 24, and the 30 point at 30: the rating is 30 of a peak of 40
		assertEquals(0.25, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.speed(3)), EPS);
		// Up to exactly 48 RPM the rough 40 point is the last one counted, so the rating is 24 of 40
		assertEquals(0.4, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.speed(2)), EPS);
		// A hair below it the rough point is out and nothing is rough
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.speed(2) - 0.5), EPS);
	}

	@Test
	void theCrawlPointIsLeftOutFromExactlyTheCrawlSpeedUp() {
		// The slowest point is the roughest and biggest: from the crawl floor up it is not allowed to set the peak
		double[] curve = {50, 20};
		double[] ripples = {0.5, 0};
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.CRAWL_RPM), EPS, "at the crawl floor the point is left out");
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.CRAWL_RPM + 10), EPS);
		// An engine that is itself below the floor is judged by it: 50 * 0.6 = 30 of a peak of 50
		assertEquals(0.4, Dynamometer.roughnessLoss(new double[] {50}, new double[] {0.5}, Dynamometer.CRAWL_RPM - 1), EPS);
	}
}
