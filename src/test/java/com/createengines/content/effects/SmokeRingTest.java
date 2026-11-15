package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Only a slow, clean single puffs rings, and a ring is a circle of puffs round the way the pipe points. */
class SmokeRingTest {

	@Test
	void onlyASlowCleanLightlyLoadedSingleMakesRings() {
		assertTrue(SmokeRing.rings(1, 20, false, 0.2f));
		assertFalse(SmokeRing.rings(2, 20, false, 0.2f), "a twin runs together into a plume");
		assertFalse(SmokeRing.rings(1, 120, false, 0.2f), "too fast");
		assertFalse(SmokeRing.rings(1, 0, false, 0.2f), "stopped");
		assertFalse(SmokeRing.rings(1, 20, true, 0.2f), "rich smoke is a plume");
		assertFalse(SmokeRing.rings(1, 20, false, 0.9f), "so is a heavy load");
		assertTrue(SmokeRing.rings(1, -20, false, 0.2f), "either way round");
	}

	@Test
	void everyPuffOfARingTravelsForwardAndStraysTheSameDistanceAcrossIt() {
		for (double[] normal : new double[][] {{0, 1, 0}, {1, 0, 0}, {0, 0, -1}, {0, -1, 0}}) {
			double[][] ring = SmokeRing.velocities(normal, 0.05, 0.02);
			assertEquals(SmokeRing.POINTS, ring.length);
			double[] sum = new double[3];
			for (double[] v : ring) {
				double along = v[0] * normal[0] + v[1] * normal[1] + v[2] * normal[2];
				assertEquals(0.05, along, 1e-9, "forward speed");
				double across = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2] - along * along);
				assertEquals(0.02, across, 1e-9, "spread");
				for (int i = 0; i < 3; i++)
					sum[i] += v[i];
			}
			for (int i = 0; i < 3; i++)
				assertEquals(0.05 * SmokeRing.POINTS * normal[i], sum[i], 1e-9, "the ring is centred on the pipe's axis");
		}
	}
}
