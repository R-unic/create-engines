package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Hand-worked governor traces, added after mutation testing: the existing tests checked that a governor holds a speed,
 * not the arithmetic of the proportional band, the reset and its two saturation edges. Every expected number below is
 * worked out from the formulas in the class comment.
 */
class GovernorExactTest {

	@Test
	void seedingContinuesFromTheHeldPositionAndTheSpeedError() {
		Governor governor = new Governor();
		// error = (1000 - 900) / 1000 = 0.1, so the reset is 0.5 - 0.1 / 0.1 = -0.5 and the rack stays at 0.5
		governor.seed(0.5, 900, 1000);
		assertEquals(0.5, governor.position(), 1e-12);
		assertEquals(0.5, governor.update(900, 1000, 0), 1e-12);
		// A little later, with the speed still 10% low and no time passing, it neither jumps nor drifts
		assertEquals(0.5, governor.update(900, 1000, 0), 1e-12);
	}

	@Test
	void seedingNeedsASpeedToHoldAndFiniteInputs() {
		for (double[] bad : new double[][] {{0.5, 100, 0}, {0.5, 100, -5}, {Double.NaN, 100, 1000}, {0.5, Double.NaN, 1000},
			{Double.POSITIVE_INFINITY, 100, 1000}, {0.5, Double.POSITIVE_INFINITY, 1000}}) {
			Governor governor = new Governor();
			governor.seed(bad[0], bad[1], bad[2]);
			assertEquals(1, governor.position(), 0, "seed(" + bad[0] + ", " + bad[1] + ", " + bad[2] + ") must change nothing");
		}
		Governor clamped = new Governor();
		clamped.seed(7, 1000, 1000);
		assertEquals(1, clamped.position(), 0);
		clamped.seed(-3, 1000, 1000);
		assertEquals(0, clamped.position(), 0);
	}

	@Test
	void withNothingToHoldOrNoSpeedTheRackStaysWhereItIs() {
		Governor governor = new Governor();
		assertEquals(1, governor.update(500, 0, 0.05), 0);
		assertEquals(1, governor.update(500, -10, 0.05), 0);
		assertEquals(1, governor.update(Double.NaN, 1000, 0.05), 0);
		assertEquals(1, governor.update(Double.POSITIVE_INFINITY, 1000, 0.05), 0);
		governor.seed(0.25, 1000, 1000);
		assertEquals(0.25, governor.update(500, 0, 0.05), 0);
	}

	@Test
	void theSpeedIsSmoothedButANearlyTenPercentOverspeedIsActedOnAtOnce() {
		Governor governor = new Governor();
		governor.seed(0.8, 1000, 1000);
		// Exactly 10% over is still "smoothed": half the way (dt 0.25 of 0.25 + 0.25) to 1100 from 1000
		double edge = 1000 * (1 + Governor.BAND);
		double position = governor.update(edge, 1000, 0.25);
		double smoothed = 1000 + (edge - 1000) * 0.5;
		double error = (1000 - smoothed) / 1000;
		double reset = 0.8 + 4 * error * 0.25;
		assertEquals(reset + error / Governor.BAND, position, 1e-9);
		assertEquals(0.25, position, 1e-9);

		// Above the edge the raw speed is used: 1200 against 1000 is error -0.2 whatever the smoothing says
		Governor over = new Governor();
		over.seed(0.8, 1000, 1000);
		double fast = over.update(1200, 1000, 0.25);
		assertEquals(0, fast, 1e-12);
	}

	@Test
	void theResetStopsIntegratingWhileTheRackIsPinnedWideOpen() {
		Governor governor = new Governor();
		// 10% slow: the proportional part alone is exactly fully open (raw = 1.0), which is saturated
		governor.seed(1.0, 900, 1000);
		assertEquals(1, governor.update(900, 1000, 0.5), 1e-12);
		// The load goes away. The reset never wound up, so the rack comes back at once: reset 0.0667, proportional 0.3333
		assertEquals(0.4, governor.update(1000, 1000, 0.5), 1e-9);
	}

	@Test
	void theResetStopsIntegratingWhileTheRackIsPinnedShut() {
		Governor governor = new Governor();
		// 10% fast: raw = 1 - 1 = 0 exactly, saturated low, so the reset stays at 1
		governor.seed(0.0, 1100, 1000);
		assertEquals(0, governor.update(1100, 1000, 0.5), 1e-12);
		// Back to speed: smoothed 1033.33, error -0.0333, reset 1 + 4 * -0.0333 * 0.5 = 0.9333, rack 0.9333 - 0.3333
		assertEquals(0.6, governor.update(1000, 1000, 0.5), 1e-9);
	}

	@Test
	void resettingForgetsEverything() {
		Governor governor = new Governor();
		governor.seed(0.3, 900, 1000);
		governor.update(900, 1000, 0.5);
		governor.reset();
		assertEquals(1, governor.position(), 0);
		// With no smoothing history and a zero reset, 5% low is a rack of 0.5
		assertEquals(0.5, governor.update(950, 1000, 0), 1e-12);
	}

	@Test
	void aRedstoneSignalAsksForItsShareOfTheSettingButNeverLessThanTheFloor() {
		assertEquals(1000, Governor.commanded(1000, 0), 0);
		assertEquals(1000, Governor.commanded(1000, -4), 0);
		assertEquals(1000, Governor.commanded(1000, 15), 1e-9);
		assertEquals(1000, Governor.commanded(1000, 100), 1e-9);
		assertEquals(1000 * 8 / 15.0, Governor.commanded(1000, 8), 1e-9);
		assertEquals(1000 / 15.0, Governor.commanded(1000, 1), 1e-9);
		assertEquals(Governor.MIN_COMMANDED, Governor.commanded(30, 1), 0);
		// A setting below the floor is never raised to it
		assertEquals(10, Governor.commanded(10, 1), 0);
	}

	@Test
	void theOpeningAndSignalFollowTheRackPosition() {
		assertEquals(Governor.MIN_OPENING, Governor.opening(0), 1e-12);
		assertEquals(1, Governor.opening(1), 1e-12);
		assertEquals(Governor.MIN_OPENING + (1 - Governor.MIN_OPENING) * 0.5, Governor.opening(0.5), 1e-12);
		assertEquals(Governor.MIN_OPENING, Governor.opening(-4), 1e-12);
		assertEquals(1, Governor.opening(4), 1e-12);
		assertEquals(0, Governor.signal(0));
		assertEquals(15, Governor.signal(1));
		assertEquals(8, Governor.signal(0.5));
		assertEquals(0, Governor.signal(-1));
		assertEquals(15, Governor.signal(9));
	}
}
