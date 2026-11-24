package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimMathTest {

	@Test
	void anAngleAlreadyInTheCycleIsLeftAlone() {
		assertEquals(0, SimMath.wrap(0), 1e-12);
		assertEquals(123.5, SimMath.wrap(123.5), 1e-12);
		assertEquals(719.999, SimMath.wrap(719.999), 1e-12);
	}

	@Test
	void anAngleOnTheEndOfTheCycleWrapsToTheStart() {
		assertEquals(0, SimMath.wrap(720), 1e-12);
		assertEquals(0, SimMath.wrap(1440), 1e-12);
	}

	@Test
	void anglesPastOrBeforeTheCycleComeBackInside() {
		assertEquals(5, SimMath.wrap(725), 1e-12);
		assertEquals(10, SimMath.wrap(1450), 1e-12);
		assertEquals(710, SimMath.wrap(-10), 1e-12);
		assertEquals(710, SimMath.wrap(-730), 1e-12);
		assertEquals(20, SimMath.wrap(720 * 50 + 20), 1e-6);
	}

	@Test
	void wrappingToAnotherPeriodUsesThatPeriod() {
		assertEquals(10, SimMath.wrap(370, 360), 1e-12);
		assertEquals(350, SimMath.wrap(-10, 360), 1e-12);
		assertEquals(0, SimMath.wrap(360, 360), 1e-12);
	}

	@Test
	void theFastPathsAgreeWithAPlainRemainder() {
		for (double degrees = -3000; degrees < 3000; degrees += 13.7) {
			double expected = ((degrees % 720) + 720) % 720;
			assertEquals(expected, SimMath.wrap(degrees), 1e-9, "at " + degrees);
		}
	}

	@Test
	void approachIsNothingForNoTimeAndNearlyAllForALongOne() {
		assertEquals(0, SimMath.approach(0), 1e-15);
		assertTrue(SimMath.approach(20) > 0.999999);
		assertTrue(SimMath.approach(20) < 1);
	}

	@Test
	void approachMatchesTheExponentialOnBothSidesOfTheSeriesCutoff() {
		for (double x : new double[] {1e-6, 0.001, 0.05, 0.2, 0.25, 0.2501, 0.5, 1, 3}) {
			assertEquals(1 - Math.exp(-x), SimMath.approach(x), 1e-9, "at " + x);
		}
	}

	@Test
	void approachOnlyEverGrows() {
		double previous = 0;
		for (double x = 0.01; x < 4; x += 0.01) {
			double now = SimMath.approach(x);
			assertTrue(now > previous, "at " + x);
			previous = now;
		}
	}

	@Test
	void aMarkInsideTheStepIsCrossed() {
		assertTrue(SimMath.crosses(0, 10, 5));
		assertFalse(SimMath.crosses(0, 10, 11));
		assertFalse(SimMath.crosses(0, 10, 400));
	}

	@Test
	void theStartOfAStepIsNotCrossedButTheEndIs() {
		assertFalse(SimMath.crosses(100, 10, 100));
		assertTrue(SimMath.crosses(100, 10, 110));
	}

	@Test
	void aStepThatWrapsRoundTheCycleStillSeesMarksAcrossTheSeam() {
		assertTrue(SimMath.crosses(715, 10, 2));
		assertTrue(SimMath.crosses(715, 10, 0));
		assertFalse(SimMath.crosses(715, 10, 20));
	}

	@Test
	void aMarkOnTheBoundaryBetweenStepsIsCrossedByExactlyOneOfThem() {
		double step = 10 / 3d;
		for (double mark : new double[] {10, 20, 360, 0}) {
			double angle = SimMath.wrap(mark - 3 * step);
			int crossings = 0;
			for (int i = 0; i < 6; i++) {
				if (SimMath.crosses(angle, step, mark))
					crossings++;
				angle = SimMath.wrap(angle + step);
			}
			assertEquals(1, crossings, "mark " + mark);
		}
	}

	@Test
	void clampMatchesTheJdkBitForBit() {
		double[] ds = {Double.NaN, Double.NEGATIVE_INFINITY, -2.5, -0.0, 0.0, 0.3, 1, 7.25, Double.POSITIVE_INFINITY};
		for (double v : ds)
			for (double lo : new double[] {-1, -0.0, 0.0, 0.1})
				for (double hi : new double[] {0.0, 0.1, 1, 10}) {
					if (lo > hi)
						continue;
					assertEquals(Double.doubleToRawLongBits(Math.clamp(v, lo, hi)),
							Double.doubleToRawLongBits(SimMath.clamp(v, lo, hi)));
					assertEquals(Float.floatToRawIntBits(Math.clamp((float) v, (float) lo, (float) hi)),
							Float.floatToRawIntBits(SimMath.clamp((float) v, (float) lo, (float) hi)));
				}
		for (long v : new long[] {Long.MIN_VALUE, -5, 0, 3, 99, Long.MAX_VALUE})
			for (int lo : new int[] {-4, 0, 2})
				for (int hi : new int[] {2, 15, Integer.MAX_VALUE}) {
					assertEquals(Math.clamp(v, lo, hi), SimMath.clamp(v, lo, hi));
					assertEquals(Math.clamp(v, (long) lo, (long) hi), SimMath.clamp(v, (long) lo, (long) hi));
				}
	}

	@Test
	void clampRefusesBackwardsAndNaNBounds() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimMath.clamp(1.0, 2.0, 1.0));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimMath.clamp(1.0, Double.NaN, 1.0));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimMath.clamp(1, 3, 2));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimMath.clamp(1L, 3L, 2L));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimMath.clamp(1f, 2f, 1f));
	}
}
