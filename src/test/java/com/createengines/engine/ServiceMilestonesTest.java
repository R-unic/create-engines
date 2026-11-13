package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.advancement.Milestone;

class ServiceMilestonesTest {

	private static EngineMilestones.Service service(boolean running, float rpm, boolean exchanging, boolean filtered,
												   float condition, float temperature, float cool, boolean coolant,
												   boolean pilot, boolean usedOil) {
		return new EngineMilestones.Service(running, rpm, exchanging, filtered, condition, temperature, cool, coolant,
			pilot, usedOil);
	}

	@Test
	void anEngineThatIsNotRunningEarnsNothing() {
		assertTrue(EngineMilestones.reachedService(service(false, 0, true, true, 0, 1, 1, true, true, true)).isEmpty());
	}

	@Test
	void anOilExchangeIsCreditedAndKeepingTheOilFreshNeedsSpeed() {
		Set<Milestone> slow = EngineMilestones.reachedService(service(true, 50, true, false, 1, 0.7f, 0, false, false, false));
		assertTrue(slow.contains(Milestone.OIL_EXCHANGED));
		assertFalse(slow.contains(Milestone.OIL_KEPT_FRESH));
		Set<Milestone> fast = EngineMilestones.reachedService(service(true, 150, true, false, 0.95f, 0.7f, 0, false, false, false));
		assertTrue(fast.contains(Milestone.OIL_KEPT_FRESH));
		Set<Milestone> notExchanging = EngineMilestones.reachedService(service(true, 150, false, false, 0.95f, 0.7f, 0, false, false, false));
		assertFalse(notExchanging.contains(Milestone.OIL_EXCHANGED));
		assertFalse(notExchanging.contains(Milestone.OIL_KEPT_FRESH));
	}

	@Test
	void aFilterIsNoticed() {
		Set<Milestone> reached = EngineMilestones.reachedService(service(true, 120, false, true, 0.2f, 0.7f, 0, false, false, false));
		assertTrue(reached.contains(Milestone.OIL_FILTERED));
		assertFalse(EngineMilestones.reachedService(service(true, 120, false, false, 0.5f, 0.7f, 0, false, false, false))
			.contains(Milestone.OIL_FILTERED));
	}

	@Test
	void warmingUpCoolantAndRunningCoolAreSeparateSteps() {
		assertTrue(EngineMilestones.reachedService(service(true, 80, false, false, 1, 0.65f, 0, false, false, false))
			.contains(Milestone.WARMED_UP));
		assertFalse(EngineMilestones.reachedService(service(true, 80, false, false, 1, 0.3f, 0, false, false, false))
			.contains(Milestone.WARMED_UP));
		Set<Milestone> cooled = EngineMilestones.reachedService(service(true, 80, false, false, 1, 0.45f, 1, true, false, false));
		assertTrue(cooled.contains(Milestone.COOLANT_USED));
		assertFalse(cooled.contains(Milestone.COOL_RUNNING), "too slow to count");
		assertTrue(EngineMilestones.reachedService(service(true, 160, false, false, 1, 0.45f, 1, true, false, false))
			.contains(Milestone.COOL_RUNNING));
	}

	@Test
	void aPilotShotAndWasteOilAreCredited() {
		Set<Milestone> reached = EngineMilestones.reachedService(service(true, 80, false, false, 1, 0.7f, 0, false, true, true));
		assertEquals(Set.of(Milestone.DUAL_FUEL, Milestone.WASTE_OIL_BURNED, Milestone.WARMED_UP), reached);
	}

	@Test
	void aWarmStartIsCreditedAtOnceAndAHardStartOnlyOnceTheBlockHasWarmedThrough() {
		assertEquals(Set.of(Milestone.PREHEATED), EngineMilestones.reachedWarmup(true, true, false, 0.35f));
		assertEquals(Set.of(), EngineMilestones.reachedWarmup(true, false, true, 0.35f), "still cold, so not yet");
		assertEquals(Set.of(Milestone.HARD_START), EngineMilestones.reachedWarmup(true, false, true, EngineMilestones.WARM));
		assertEquals(Set.of(), EngineMilestones.reachedWarmup(false, true, true, 0.7f), "an engine that has stopped earns nothing");
	}

	private static Set<Milestone> atCapacity(float capacity) {
		return EngineMilestones.reached(new EngineMilestones.Snapshot(EngineState.RUNNING, EngineState.RUNNING, 100,
			false, 8, capacity, false, 0.5f, false, 0, false));
	}

	@Test
	void thePowerLadderClimbsInSteamEngines() {
		assertFalse(atCapacity(EngineMilestones.STEAM_BEATER).contains(Milestone.STEAM_HERD));
		assertTrue(atCapacity(EngineMilestones.STEAM_BEATER).contains(Milestone.STEAM_BEATER));
		Set<Milestone> herd = atCapacity(8 * EngineMilestones.STEAM_ENGINE);
		assertTrue(herd.contains(Milestone.STEAM_BEATER) && herd.contains(Milestone.STEAM_HERD));
		assertFalse(herd.contains(Milestone.STEAM_TITAN));
		assertTrue(atCapacity(16 * EngineMilestones.STEAM_ENGINE).contains(Milestone.STEAM_TITAN));
	}

	@Test
	void aBigStiffCrankIsCreditedOnlyWhenRunningWithNoFlex() {
		assertTrue(EngineMilestones.reachedFlex(true, 12, 0).contains(Milestone.STIFF_CRANK));
		assertTrue(EngineMilestones.reachedFlex(true, 16, 0.001f).contains(Milestone.STIFF_CRANK));
		assertTrue(EngineMilestones.reachedFlex(true, 12, 0.18f).isEmpty(), "a straight twelve flexes");
		assertTrue(EngineMilestones.reachedFlex(true, 8, 0).isEmpty(), "eight cylinders is not a big engine");
		assertTrue(EngineMilestones.reachedFlex(false, 12, 0).isEmpty(), "and it has to be running");
	}
}
