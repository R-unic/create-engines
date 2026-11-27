package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrankSurgeTest {

	@Test
	void aSmoothOrStoppedCrankHasNone() {
		for (double degrees = 0; degrees < 720; degrees += 7) {
			assertEquals(0, CrankSurge.offset(degrees, 100, 0, 1));
			assertEquals(0, CrankSurge.offset(degrees, 0, 0.5, 1));
			assertEquals(0, CrankSurge.offset(degrees, 100, 0.5, 0), "nothing firing, nothing to surge with");
		}
	}

	@Test
	void itSurgesOnceForEachFiring() {
		for (int firing : new int[] {1, 2, 4, 8}) {
			int peaks = 0;
			double step = 0.25;
			for (double degrees = 0; degrees < 720; degrees += step) {
				double here = CrankSurge.offset(degrees, 60, 0.3, firing);
				if (here > CrankSurge.offset(degrees - step, 60, 0.3, firing)
					&& here >= CrankSurge.offset(degrees + step, 60, 0.3, firing))
					peaks++;
			}
			assertEquals(firing, peaks);
		}
	}

	@Test
	void itGrowsWithTheRippleToItsCap() {
		assertTrue(most(0.05, 4) > 0);
		assertTrue(most(0.1, 4) > most(0.05, 4));
		assertEquals(CrankSurge.CAP, most(1, 1), 0.01);
		for (double ripple : new double[] {0.01, 0.2, 1, 40, Double.POSITIVE_INFINITY})
			for (int firing = 1; firing <= 64; firing++)
				assertTrue(most(ripple, firing) <= CrankSurge.CAP + 1e-9);
	}

	@Test
	void theDrawnCrankNeverTurnsBackwards() {
		for (int firing : new int[] {1, 3, 16, 128}) {
			double last = CrankSurge.offset(0, 60, 5, firing);
			for (double degrees = 0.1; degrees < 720; degrees += 0.1) {
				double drawn = degrees + CrankSurge.offset(degrees, 60, 5, firing);
				assertTrue(drawn > last, firing + " firing at " + degrees);
				last = drawn;
			}
		}
	}

	@Test
	void aCrankTurningTheOtherWayMirrorsIt() {
		for (double degrees = 0; degrees < 720; degrees += 11)
			assertEquals(-CrankSurge.offset(degrees, 60, 0.2, 3), CrankSurge.offset(-degrees, -60, 0.2, 3), 1e-9);
	}

	@Test
	void nothingThatIsNotANumberGetsOut() {
		double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -1};
		for (double degrees : bad)
			for (double rpm : bad)
				for (double ripple : bad)
					assertTrue(Double.isFinite(CrankSurge.offset(degrees, rpm, ripple, 2)), degrees + " " + rpm + " " + ripple);
	}

	private static double most(double ripple, int firing) {
		double most = 0;
		for (double degrees = 0; degrees < 720; degrees += 0.5)
			most = Math.max(most, Math.abs(CrankSurge.offset(degrees, 60, ripple, firing)));
		return most;
	}
}
