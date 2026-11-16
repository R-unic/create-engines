package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When the sting plays: goals and challenges that show a toast, once a minute, a challenge allowed to follow a goal. */
class StingGateTest {

	@Test
	void onlyGoalsAndChallengesWithAToastSting() {
		assertTrue(StingGate.worthIt(true, true));
		assertTrue(StingGate.worthIt(true, true));
		assertFalse(StingGate.worthIt(false, true), "a stepping stone is quiet");
		assertFalse(StingGate.worthIt(true, false), "no toast, no sting");
	}

	@Test
	void theFirstStingPlaysAndTheNextWaitsAMinute() {
		StingGate gate = new StingGate();
		assertTrue(gate.allow(1000, false));
		assertFalse(gate.allow(1001, false));
		assertFalse(gate.allow(1000 + StingGate.QUIET_MILLIS - 1, false));
		assertTrue(gate.allow(1000 + StingGate.QUIET_MILLIS, false));
	}

	@Test
	void aChallengeMayFollowAGoalButNotAnotherChallenge() {
		StingGate gate = new StingGate();
		assertTrue(gate.allow(0, false));
		assertTrue(gate.allow(10, true));
		assertFalse(gate.allow(20, true));
		assertFalse(gate.allow(30, false));
	}

	@Test
	void aClockThatGoesBackwardsDoesNotSilenceItForGood() {
		StingGate gate = new StingGate();
		assertTrue(gate.allow(1_000_000, false));
		assertTrue(gate.allow(5, false), "time went backwards: play, and start counting again");
		assertFalse(gate.allow(6, false));
	}
}
