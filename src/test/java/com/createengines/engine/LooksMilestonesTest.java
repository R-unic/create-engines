package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.advancement.Milestone;

class LooksMilestonesTest {

	@Test
	void aStoppedEngineEarnsNothingFromItsLooks() {
		assertTrue(EngineMilestones.reachedLooks(false, 1f, 12)
			.isEmpty());
	}

	@Test
	void aRunningEngineMostlyThroughAMufflerIsMuffled() {
		assertEquals(Set.of(Milestone.MUFFLED), EngineMilestones.reachedLooks(true, 1f, 0));
		assertEquals(Set.of(Milestone.MUFFLED), EngineMilestones.reachedLooks(true, EngineMilestones.MUFFLED_AT, 0));
		assertTrue(EngineMilestones.reachedLooks(true, 0.4f, 0)
			.isEmpty(), "a muffler on a small part of the exhaust is not enough");
	}

	@Test
	void anEngineWithEnoughCasingRoundItIsClad() {
		assertEquals(Set.of(Milestone.CLAD), EngineMilestones.reachedLooks(true, 0f, EngineMilestones.CLAD_BLOCKS));
		assertTrue(EngineMilestones.reachedLooks(true, 0f, EngineMilestones.CLAD_BLOCKS - 1)
			.isEmpty());
	}

	@Test
	void bothAtOnceEarnBoth() {
		assertEquals(Set.of(Milestone.MUFFLED, Milestone.CLAD), EngineMilestones.reachedLooks(true, 1f, 8));
	}
}
