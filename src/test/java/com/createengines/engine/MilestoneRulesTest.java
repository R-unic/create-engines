package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.advancement.Milestone;
import com.createengines.engine.EngineMilestones.Mechanics;
import com.createengines.engine.EngineMilestones.Snapshot;

/**
 * The milestone rules that read a few figures from a running engine: cold starts, flywheels, vee and flat engines, a dyno
 * read, variable vanes, anti-lag, and the engine-wide flags (governor, precision parts, MAF trim). Each rule is tested at
 * its edge, and a stopped engine earns only what it is allowed to without running.
 */
class MilestoneRulesTest {

	private static Snapshot running(boolean governed, boolean precision, boolean trimming, boolean enriching) {
		return new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 100, false, 0.5f, false, 0, false, 0,
			false, 0, false, false, trimming, enriching, governed, precision, false);
	}

	@Test
	void aColdStartNeedsAHeavyStarterAssistingAColdEngine() {
		assertEquals(Set.of(Milestone.COLD_START), EngineMilestones.reachedColdStart(true));
		assertTrue(EngineMilestones.reachedColdStart(false)
			.isEmpty());
	}

	@Test
	void governorPrecisionAndMafFlagsEachEarnTheirOwnMilestoneOnlyWhileRunning() {
		assertEquals(Set.of(Milestone.ENGINE_RUNNING, Milestone.GOVERNED),
			EngineMilestones.reached(running(true, false, false, false)));
		assertEquals(Set.of(Milestone.ENGINE_RUNNING, Milestone.PRECISION_ENGINE),
			EngineMilestones.reached(running(false, true, false, false)));
		assertEquals(Set.of(Milestone.ENGINE_RUNNING, Milestone.MAF_TRIM),
			EngineMilestones.reached(running(false, false, true, false)));
		assertEquals(Set.of(Milestone.ENGINE_RUNNING, Milestone.MAF_ENRICH),
			EngineMilestones.reached(running(false, false, false, true)));
		Snapshot stalled = new Snapshot(EngineState.STALLED, EngineState.STALLED, 0, false, 1, 100, false, 0.5f, false, 0,
			false, 0, false, 0, false, false, true, true, true, true, false);
		assertTrue(EngineMilestones.reached(stalled)
			.isEmpty(), "none of these count for a stopped engine");
	}

	private static Mechanics mechanics(boolean running, float rpm, double inertia, float roughness, boolean vanes,
		boolean antiLag, int firing, boolean vee, boolean flat, boolean dyno) {
		return new Mechanics(running, rpm, inertia, roughness, vanes, antiLag, firing, vee, flat, dyno, false);
	}

	@Test
	void aFlywheelCountsOnceItCarriesASmoothEngineThroughItsStrokes() {
		assertEquals(Set.of(Milestone.FLYWHEEL_SMOOTH), EngineMilestones.reachedMechanics(
			mechanics(true, EngineMilestones.FLYWHEEL_RPM, 5, EngineMilestones.SMOOTH_LOSS, false, false, 1, false, false, false)));
		assertTrue(EngineMilestones.reachedMechanics(mechanics(true, EngineMilestones.FLYWHEEL_RPM, 0, 0, false, false, 1, false,
			false, false))
			.isEmpty(), "no flywheel mass");
		assertTrue(EngineMilestones.reachedMechanics(mechanics(true, EngineMilestones.FLYWHEEL_RPM - 1, 5, 0, false, false, 1,
			false, false, false))
			.isEmpty(), "too slow");
		assertTrue(EngineMilestones.reachedMechanics(mechanics(true, 100, 5, EngineMilestones.SMOOTH_LOSS + 0.01f, false,
			false, 1, false, false, false))
			.isEmpty(), "too rough");
		assertTrue(EngineMilestones.reachedMechanics(mechanics(false, 100, 5, 0, false, false, 1, false, false, false))
			.isEmpty(), "not running");
	}

	@Test
	void veeAndFlatEnginesNeedEnoughFiringCylindersOnASharedThrow() {
		int four = EngineMilestones.EVEN_FIRING_CYLINDERS;
		assertEquals(Set.of(Milestone.VEE_ENGINE),
			EngineMilestones.reachedMechanics(mechanics(true, 0, 0, 1, false, false, four, true, false, false)));
		assertEquals(Set.of(Milestone.FLAT_ENGINE),
			EngineMilestones.reachedMechanics(mechanics(true, 0, 0, 1, false, false, four, false, true, false)));
		assertTrue(EngineMilestones.reachedMechanics(mechanics(true, 0, 0, 1, false, false, four - 1, true, true, false))
			.isEmpty(), "too few firing");
		assertTrue(EngineMilestones.reachedMechanics(mechanics(false, 0, 0, 1, false, false, four, true, true, false))
			.isEmpty(), "not running");
	}

	@Test
	void aDynoReadNeedsARunningEngine() {
		assertEquals(Set.of(Milestone.DYNO_RUN),
			EngineMilestones.reachedMechanics(mechanics(true, 0, 0, 1, false, false, 1, false, false, true)));
		assertFalse(EngineMilestones.reachedMechanics(mechanics(false, 0, 0, 1, false, false, 1, false, false, true))
			.contains(Milestone.DYNO_RUN));
	}

	@Test
	void vanesAndAntiLagCountEvenWhenTheEngineIsNotRunningOnItsOwnPower() {
		assertEquals(Set.of(Milestone.VARIABLE_VANES),
			EngineMilestones.reachedMechanics(mechanics(false, 0, 0, 1, true, false, 0, false, false, false)));
		assertEquals(Set.of(Milestone.ANTI_LAG),
			EngineMilestones.reachedMechanics(mechanics(false, 0, 0, 1, false, true, 0, false, false, false)));
	}
}
