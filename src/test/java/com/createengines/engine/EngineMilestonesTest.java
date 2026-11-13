package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.advancement.Milestone;
import com.createengines.engine.EngineMilestones.Snapshot;
import com.createengines.engine.sim.HeatRecovery;
import com.createengines.engine.topology.PrecisionParts;

class EngineMilestonesTest {

	private static Snapshot running(float rpm, int firing) {
		return new Snapshot(EngineState.RUNNING, EngineState.RUNNING, rpm, false, firing, 100, false, 0.5f, false, 0,
			false);
	}

	@Test
	void aRunningEngineIsAliveAndNothingMore() {
		Set<Milestone> reached = EngineMilestones.reached(running(64, 1));
		assertEquals(Set.of(Milestone.ENGINE_RUNNING), reached);
	}

	@Test
	void aSharedLobeCountsOnlyWhileTheEngineRuns() {
		EngineMilestones.Mechanics shared = new EngineMilestones.Mechanics(true, 100, 0, 0, false, false, 2, false, false,
			false, true);
		assertEquals(Set.of(Milestone.VALLEY_CAM), EngineMilestones.reachedMechanics(shared));
		EngineMilestones.Mechanics stopped = new EngineMilestones.Mechanics(false, 0, 0, 0, false, false, 2, false, false,
			false, true);
		assertTrue(EngineMilestones.reachedMechanics(stopped)
			.isEmpty(), "a stopped engine is not a working cam");
	}

	@Test
	void aStoppedEngineReachesNothing() {
		for (EngineState state : new EngineState[] {EngineState.STALLED, EngineState.COASTING, EngineState.INCOMPLETE})
			assertTrue(EngineMilestones.reached(new Snapshot(state, state, 100, false, 8, 99999, true, 1, true, 0, false))
				.isEmpty(), state.name());
	}

	@Test
	void cylinderCountsStack() {
		assertFalse(EngineMilestones.reached(running(64, 3))
			.contains(Milestone.FOUR_CYLINDERS));
		assertTrue(EngineMilestones.reached(running(64, 4))
			.contains(Milestone.FOUR_CYLINDERS));
		Set<Milestone> twelve = EngineMilestones.reached(running(64, 12));
		assertTrue(twelve.containsAll(Set.of(Milestone.FOUR_CYLINDERS, Milestone.EIGHT_CYLINDERS, Milestone.TWELVE_CYLINDERS)));
	}

	private static Snapshot twoCranks(int firing, boolean coupled) {
		return new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, firing, 100, false, 0.5f, false, 0, false,
			0, false, 0, false, false, false, false, false, false, coupled);
	}

	@Test
	void coupledCranksNeedBothAnEngineWithSeveralCylindersAndMoreThanOneCrank() {
		assertTrue(EngineMilestones.reached(twoCranks(4, true))
			.contains(Milestone.COUPLED_CRANKS));
		assertFalse(EngineMilestones.reached(twoCranks(4, false))
			.contains(Milestone.COUPLED_CRANKS), "one crankshaft is not coupled");
		assertFalse(EngineMilestones.reached(twoCranks(2, true))
			.contains(Milestone.COUPLED_CRANKS), "two cylinders is not enough to count");
	}

	@Test
	void sixteenFiringCylindersAreAMilestoneOfTheirOwn() {
		assertFalse(EngineMilestones.reached(running(64, 15))
			.contains(Milestone.SIXTEEN_CYLINDERS));
		assertTrue(EngineMilestones.reached(running(64, 16))
			.containsAll(Set.of(Milestone.TWELVE_CYLINDERS, Milestone.SIXTEEN_CYLINDERS)));
	}

	@Test
	void speedMilestonesNeedTheEngineRunning() {
		assertTrue(EngineMilestones.reached(running(205, 1))
			.contains(Milestone.HIGH_REVS));
		assertFalse(EngineMilestones.reached(running(205, 1))
			.contains(Milestone.REDLINE));
		assertTrue(EngineMilestones.reached(running(250, 1))
			.contains(Milestone.REDLINE));
		assertFalse(EngineMilestones.reached(new Snapshot(EngineState.CRANKING, EngineState.CRANKING, 250, true, 0, 0,
			false, 1, false, 0, false)).contains(Milestone.HIGH_REVS));
	}

	@Test
	void stallingNeedsTheEngineToHaveBeenTurning() {
		assertTrue(EngineMilestones.reached(new Snapshot(EngineState.STALLED, EngineState.RUNNING, 0, false, 0, 0, false,
			0, false, 0, false)).contains(Milestone.STALLED));
		assertFalse(EngineMilestones.reached(new Snapshot(EngineState.STALLED, EngineState.STALLED, 0, false, 0, 0,
			false, 0, false, 0, false)).contains(Milestone.STALLED));
		assertFalse(EngineMilestones.reached(new Snapshot(EngineState.STALLED, EngineState.CRANKING, 0, false, 0, 0,
			false, 0, false, 0, false)).contains(Milestone.STALLED));
	}

	@Test
	void oilAndMixtureMilestones() {
		Snapshot dry = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 0, false, 0.02f, true, 0, false);
		Set<Milestone> reached = EngineMilestones.reached(dry);
		assertTrue(reached.contains(Milestone.SMOKE_SIGNALS));
		Snapshot oiled = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 0, true, 0.95f, false, 0, false);
		reached = EngineMilestones.reached(oiled);
		assertTrue(reached.contains(Milestone.SPARK_ENGINE));
		assertFalse(reached.contains(Milestone.SMOKE_SIGNALS));
	}

	@Test
	void boostMilestonesFollowThePressureAndTheDump() {
		Snapshot weak = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 0, false, 0.5f, false, 0.1f, false);
		assertFalse(EngineMilestones.reached(weak)
			.contains(Milestone.BOOST));
		Snapshot boosted = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 0, false, 0.5f, false, 0.5f, false);
		assertTrue(EngineMilestones.reached(boosted)
			.contains(Milestone.BOOST));
		assertFalse(EngineMilestones.reached(boosted)
			.contains(Milestone.BIG_BOOST));
		assertFalse(EngineMilestones.reached(boosted)
			.contains(Milestone.BLOW_OFF));
		Snapshot dumping = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 1, 0, false, 0.5f, false, 1.1f, true);
		assertTrue(EngineMilestones.reached(dumping)
			.containsAll(Set.of(Milestone.BIG_BOOST, Milestone.BLOW_OFF)));
	}

	private static Snapshot mixture(EngineState state, float rpm, float fouling, boolean balanced, float dilution) {
		return new Snapshot(state, state, rpm, false, 1, 100, false, 0.5f, false, 0, false, fouling, balanced, dilution);
	}

	@Test
	void aBalancedMixtureOnlyCountsOnceTheEngineIsUpToSpeed() {
		assertTrue(EngineMilestones.reached(mixture(EngineState.RUNNING, 100, 0, true, 0))
			.contains(Milestone.DIALED_IN));
		assertFalse(EngineMilestones.reached(mixture(EngineState.RUNNING, 60, 0, true, 0))
			.contains(Milestone.DIALED_IN));
		assertFalse(EngineMilestones.reached(mixture(EngineState.RUNNING, 150, 0, false, 0))
			.contains(Milestone.DIALED_IN));
		assertFalse(EngineMilestones.reached(mixture(EngineState.COASTING, 150, 0, true, 0))
			.contains(Milestone.DIALED_IN));
	}

	@Test
	void aFouledPlugAndThinnedOilAreReportedOnceTheyAreBadEnough() {
		assertFalse(EngineMilestones.reached(mixture(EngineState.RUNNING, 64, 0.4f, false, 0.2f))
			.containsAll(Set.of(Milestone.FOULED_PLUG, Milestone.THINNED_OIL)));
		Set<Milestone> reached = EngineMilestones.reached(mixture(EngineState.STALLED, 0, 0.6f, false, 0.35f));
		assertTrue(reached.containsAll(Set.of(Milestone.FOULED_PLUG, Milestone.THINNED_OIL)), reached.toString());
	}

	@Test
	void firingIsEvenOnlyWhenFourOrMoreCylindersAreSpacedEqually() {
		assertTrue(EngineMilestones.evenFiring(new float[] {450, 90, 630, 270}));
		assertTrue(EngineMilestones.evenFiring(new float[] {0, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660}));
		assertFalse(EngineMilestones.evenFiring(new float[] {90, 270}), "two cylinders prove nothing");
		assertFalse(EngineMilestones.evenFiring(new float[] {90, 90, 270, 270}), "paired firing is not even");
		assertFalse(EngineMilestones.evenFiring(new float[] {0, 100, 360, 460}));
	}

	@Test
	void pushrodAndEvenFiringCountOnlyWhileTheEngineRuns() {
		Snapshot running = new Snapshot(EngineState.RUNNING, EngineState.RUNNING, 64, false, 4, 100, false, 0.5f, false,
			0, false, 0, false, 0, true, true);
		assertTrue(EngineMilestones.reached(running)
			.containsAll(Set.of(Milestone.PUSHROD_CAM, Milestone.EVEN_FIRING)));
		Snapshot coasting = new Snapshot(EngineState.COASTING, EngineState.RUNNING, 64, false, 4, 100, false, 0.5f, false,
			0, false, 0, false, 0, true, true);
		assertFalse(EngineMilestones.reached(coasting)
			.contains(Milestone.PUSHROD_CAM));
	}

	@Test
	void rotaryMilestonesNeedARunningRotor() {
		assertTrue(EngineMilestones.reachedRotary(new EngineMilestones.Rotary(false, 300, 2))
			.isEmpty(), "a stopped engine");
		assertTrue(EngineMilestones.reachedRotary(new EngineMilestones.Rotary(true, 300, 0))
			.isEmpty(), "a piston engine has no rotors");
		assertEquals(Set.of(Milestone.ROTARY_ENGINE), EngineMilestones.reachedRotary(new EngineMilestones.Rotary(true, 90, 1)));
		assertEquals(Set.of(Milestone.ROTARY_ENGINE, Milestone.ROTARY_REVS),
			EngineMilestones.reachedRotary(new EngineMilestones.Rotary(true, 210, 1)));
		assertEquals(Set.of(Milestone.ROTARY_ENGINE, Milestone.TWIN_ROTORS),
			EngineMilestones.reachedRotary(new EngineMilestones.Rotary(true, 90, 2)));
	}

	@Test
	void partsAreRewardedForWorkingInARunningEngine() {
		EngineMilestones.Parts idle = new EngineMilestones.Parts(false, true, true, true, true, true, 1, true, true,
			true, true, PrecisionParts.ALL, false);
		assertTrue(EngineMilestones.reachedParts(idle)
			.isEmpty(), "fitting parts to an engine that is not running earns nothing");

		EngineMilestones.Parts everything = new EngineMilestones.Parts(true, true, true, true, true, true, 1, true, true,
			true, true, PrecisionParts.ALL, true);
		assertEquals(Set.of(Milestone.CAMSHAFT_TIMED, Milestone.PIPED_ENGINE, Milestone.THROTTLED,
			Milestone.DIESEL_BURNED, Milestone.GASOLINE_BURNED, Milestone.SUMP_OILING,
			Milestone.MAF_READING, Milestone.GOVERNOR_ACTIVE, Milestone.GOVERNOR_COMMANDED, Milestone.PRECISION_BORE,
			Milestone.PRECISION_HEAD, Milestone.PRECISION_PISTON, Milestone.PRECISION_INJECTOR,
			Milestone.ROTOR_HOUSING_TURNED), EngineMilestones.reachedParts(everything));

		EngineMilestones.Parts dry = new EngineMilestones.Parts(true, false, false, false, false, false, 0.2f, true,
			false, false, false, PrecisionParts.PISTON, false);
		assertEquals(Set.of(Milestone.PRECISION_PISTON), EngineMilestones.reachedParts(dry),
			"a sump on a dry engine has not oiled anything");

		EngineMilestones.Parts housing = new EngineMilestones.Parts(false, false, false, false, false, false, 0, false,
			false, false, false, 0, true);
		assertEquals(Set.of(Milestone.ROTOR_HOUSING_TURNED), EngineMilestones.reachedParts(housing),
			"a rotor housing counts as soon as the shaft turns");
	}

	@Test
	void ethanolAndRecoveredHeatCountOnlyWhileTheEngineRuns() {
		assertTrue(EngineMilestones.reachedRefining(false, true, HeatRecovery.SEETHE)
			.isEmpty(), "a stopped engine");
		assertEquals(Set.of(Milestone.ETHANOL_BURNED), EngineMilestones.reachedRefining(true, true, HeatRecovery.SMOULDER));
		assertEquals(Set.of(Milestone.ETHANOL_BURNED, Milestone.ETHANOL_BOOSTED),
			EngineMilestones.reachedRefining(true, true, HeatRecovery.NONE, true), "boosted ethanol with a hot charge");
		assertTrue(EngineMilestones.reachedRefining(true, false, HeatRecovery.NONE, true)
			.isEmpty(), "a hot charge on any other fuel is not ethanol's doing");
		assertEquals(Set.of(Milestone.HEAT_RECOVERED), EngineMilestones.reachedRefining(true, false, HeatRecovery.KINDLE),
			"enough heat to cook heated recipes");
		assertEquals(Set.of(Milestone.HEAT_RECOVERED, Milestone.SUPERHEAT_RECOVERED),
			EngineMilestones.reachedRefining(true, false, HeatRecovery.SEETHE), "enough to superheat as well");
	}

	@Test
	void crankingAnEngineUnderwaterCounts() {
		assertTrue(EngineMilestones.reachedWater(false, true)
			.isEmpty(), "an engine underwater that nobody is trying to start");
		assertEquals(Set.of(Milestone.FLOODED), EngineMilestones.reachedWater(true, true));
		assertTrue(EngineMilestones.reachedWater(true, false)
			.isEmpty(), "cranking a dry engine");
	}

	@Test
	void everyMilestoneHasADistinctLowerCaseId() {
		Set<String> ids = new java.util.HashSet<>();
		for (Milestone milestone : Milestone.values()) {
			assertEquals(milestone.id(), milestone.id()
				.toLowerCase(java.util.Locale.ROOT));
			assertTrue(ids.add(milestone.id()));
		}
	}
}
