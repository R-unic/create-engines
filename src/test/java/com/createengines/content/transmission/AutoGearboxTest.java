package com.createengines.content.transmission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Auto holds the tallest gear that leaves the engine room, and settles instead of hunting. */
class AutoGearboxTest {

	private static final int LONG = 1000;

	@Test
	void anEngineWithRoomClimbsOneGearAtATime() {
		assertEquals(5, AutoGearbox.next(4, 0.1, LONG));
	}

	@Test
	void aLightLoadClimbsToTheTopEventually() {
		int gear = Gears.FIRST;
		for (int i = 0; i < 40; i++)
			gear = AutoGearbox.next(gear, 0.02 * Gears.ratio(Gears.FIRST) / Gears.ratio(gear), LONG);
		assertEquals(Gears.TOP, gear);
	}

	@Test
	void anEngineStrugglingDropsToTheTallestGearThatFits() {
		int gear = Gears.TOP;
		int next = AutoGearbox.next(gear, 1.4, LONG);
		assertTrue(next < gear);
		assertTrue(AutoGearbox.predicted(1.4, gear, next) <= AutoGearbox.FIT);
		assertTrue(AutoGearbox.predicted(1.4, gear, next + 1) > AutoGearbox.FIT, "A taller gear would also have fitted");
	}

	@Test
	void anOverstressedNetworkIsJudgedByTheSameRule() {
		int next = AutoGearbox.next(Gears.TOP, 3, LONG);
		assertTrue(AutoGearbox.predicted(3, Gears.TOP, next) <= AutoGearbox.FIT);
	}

	@Test
	void aLoadNoGearCarriesLeavesItInFirst() {
		assertEquals(Gears.FIRST, AutoGearbox.next(8, 50, LONG));
	}

	@Test
	void aShiftIsLeftAloneForAMoment() {
		assertEquals(5, AutoGearbox.next(5, 1.5, AutoGearbox.DOWN_DWELL - 1));
		assertEquals(5, AutoGearbox.next(5, 0.1, AutoGearbox.UP_DWELL - 1));
		assertTrue(AutoGearbox.next(5, 1.5, AutoGearbox.DOWN_DWELL) < 5);
	}

	@Test
	void aSteadyLoadSettlesInsteadOfHunting() {
		// A load that asks for 0.5 of the engine in first gear, left to settle for a long time
		double demandInFirst = 0.05;
		int gear = Gears.FIRST;
		int shifts = 0;
		int last = gear;
		for (int i = 0; i < 400; i++) {
			double utilization = demandInFirst * Gears.ratio(Gears.FIRST) / Gears.ratio(gear);
			gear = AutoGearbox.next(gear, utilization, LONG);
			if (gear != last) {
				shifts++;
				last = gear;
			}
		}
		assertTrue(shifts <= Gears.TOP, "Shifted " + shifts + " times");
		double settled = demandInFirst * Gears.ratio(Gears.FIRST) / Gears.ratio(gear);
		assertTrue(settled <= AutoGearbox.DOWNSHIFT_ABOVE, "Settled in an overloaded gear: " + settled);
	}

	@Test
	void theClimbThresholdLeavesRoomForThePredictionToBeRight() {
		// Climbing never lands in a gear that the box would immediately drop out of
		for (double utilization = 0.01; utilization < 1.2; utilization += 0.01)
			for (int gear = Gears.FIRST; gear < Gears.TOP; gear++) {
				int up = AutoGearbox.next(gear, utilization, LONG);
				if (up != gear + 1)
					continue;
				double there = AutoGearbox.predicted(utilization, gear, up);
				assertTrue(there <= AutoGearbox.DOWNSHIFT_ABOVE, "Climbed into a gear it would drop out of at " + utilization);
				assertTrue(AutoGearbox.next(up, there, LONG) >= up, "Hunts at " + utilization + " in gear " + gear);
			}
	}

	// Shift profiles

	@Test
	void theNormalProfileIsTheOriginalBehaviourExactly() {
		AutoGearbox.Profile normal = AutoGearbox.Profile.NORMAL;
		assertEquals(AutoGearbox.DOWNSHIFT_ABOVE, normal.downshiftAbove);
		assertEquals(AutoGearbox.FIT, normal.fit);
		assertEquals(AutoGearbox.CLIMB_TO, normal.climbTo);
		assertEquals(AutoGearbox.DOWN_DWELL, normal.downDwell);
		assertEquals(AutoGearbox.UP_DWELL, normal.upDwell);
		for (int gear = Gears.FIRST; gear <= Gears.TOP; gear++)
			for (double u = 0; u < 3; u += 0.013)
				for (int ticks : new int[] {0, 9, 10, 29, 30, LONG})
					assertEquals(AutoGearbox.next(gear, u, ticks), AutoGearbox.next(normal, gear, u, ticks),
						"gear " + gear + " load " + u + " ticks " + ticks);
	}

	@Test
	void everyProfileKeepsItsThresholdsInOrderSoItSettles() {
		for (AutoGearbox.Profile p : AutoGearbox.Profile.values()) {
			assertTrue(p.climbTo <= p.fit, p + ": a downshift must not land where it would climb straight back");
			assertTrue(p.fit < p.downshiftAbove, p + ": a downshift must land under where it downshifts");
			assertTrue(p.downDwell > 0 && p.upDwell > 0, p + ": waits are real");
		}
	}

	@Test
	void theProfilesAreOrderedFromTallToLowInTheirEagernessToClimb() {
		AutoGearbox.Profile eco = AutoGearbox.Profile.ECONOMY;
		AutoGearbox.Profile normal = AutoGearbox.Profile.NORMAL;
		AutoGearbox.Profile sport = AutoGearbox.Profile.SPORT;
		assertTrue(eco.climbTo > normal.climbTo && normal.climbTo > sport.climbTo);
		assertTrue(eco.downshiftAbove > normal.downshiftAbove && normal.downshiftAbove > sport.downshiftAbove);
		assertTrue(eco.upDwell < normal.upDwell && normal.upDwell < sport.upDwell);
	}

	@Test
	void everyProfileSettlesInsteadOfHuntingAtEveryLoad() {
		for (AutoGearbox.Profile profile : AutoGearbox.Profile.values())
			for (double demandInFirst = 0.01; demandInFirst < 0.9; demandInFirst += 0.01) {
				int gear = Gears.FIRST;
				int last = gear;
				int shifts = 0;
				for (int i = 0; i < 600; i++) {
					double utilization = demandInFirst * Gears.ratio(Gears.FIRST) / Gears.ratio(gear);
					gear = AutoGearbox.next(profile, gear, utilization, LONG);
					if (gear != last) {
						shifts++;
						last = gear;
					}
				}
				assertTrue(shifts <= Gears.TOP, profile + " shifted " + shifts + " times at " + demandInFirst);
				// and at rest it is not about to shift again
				double settled = demandInFirst * Gears.ratio(Gears.FIRST) / Gears.ratio(gear);
				assertEquals(gear, AutoGearbox.next(profile, gear, settled, LONG), profile + " not at rest at " + demandInFirst);
			}
	}

	@Test
	void aClimbNeverLandsWhereTheProfileWouldDropOutAgain() {
		for (AutoGearbox.Profile profile : AutoGearbox.Profile.values())
			for (double u = 0.01; u < 1.2; u += 0.01)
				for (int gear = Gears.FIRST; gear < Gears.TOP; gear++) {
					if (AutoGearbox.next(profile, gear, u, LONG) != gear + 1)
						continue;
					double there = AutoGearbox.predicted(u, gear, gear + 1);
					assertTrue(there <= profile.climbTo, profile + " climbed into " + there);
					assertTrue(AutoGearbox.next(profile, gear + 1, there, LONG) >= gear + 1, profile + " hunts at " + u);
				}
	}

	@Test
	void sportDropsAtALoadNormalHoldsAndEconomyClimbsWhereNormalWaits() {
		// 0.8 in gear 7: normal holds (under 0.9) but sport (over 0.75) drops
		assertEquals(7, AutoGearbox.next(AutoGearbox.Profile.NORMAL, 7, 0.8, LONG));
		assertTrue(AutoGearbox.next(AutoGearbox.Profile.SPORT, 7, 0.8, LONG) < 7);
		// A load so that gear 8 would sit at 0.75: normal will not climb (over 0.7), economy will (under 0.8)
		double u = 0.75 * Gears.ratio(8) / Gears.ratio(7);
		assertEquals(7, AutoGearbox.next(AutoGearbox.Profile.NORMAL, 7, u, LONG));
		assertEquals(8, AutoGearbox.next(AutoGearbox.Profile.ECONOMY, 7, u, LONG));
	}

	@Test
	void eachProfileKeepsItsOwnWaits() {
		for (AutoGearbox.Profile p : AutoGearbox.Profile.values()) {
			assertEquals(5, AutoGearbox.next(p, 5, 1.5, p.downDwell - 1), p + " downshifts too soon");
			assertTrue(AutoGearbox.next(p, 5, 1.5, p.downDwell) < 5, p + " will not downshift after its wait");
			assertEquals(5, AutoGearbox.next(p, 5, 0.01, p.upDwell - 1), p + " climbs too soon");
			assertEquals(6, AutoGearbox.next(p, 5, 0.01, p.upDwell), p + " will not climb after its wait");
		}
	}

	@Test
	void theDialValueMapsToAProfileAndAnythingElseIsNormal() {
		for (AutoGearbox.Profile p : AutoGearbox.Profile.values())
			assertEquals(p, AutoGearbox.Profile.fromSetting(p.setting()));
		assertEquals(AutoGearbox.Profile.NORMAL, AutoGearbox.Profile.fromSetting(-1));
		assertEquals(AutoGearbox.Profile.NORMAL, AutoGearbox.Profile.fromSetting(3));
		assertEquals(AutoGearbox.Profile.NORMAL, AutoGearbox.Profile.fromSetting(Integer.MAX_VALUE));
		assertEquals(1, AutoGearbox.Profile.NORMAL.setting(), "The default dial value of 1 must be the normal profile");
	}

	@Test
	void neutralOrReverseStartsFromFirst() {
		assertEquals(Gears.FIRST, AutoGearbox.next(Gears.NEUTRAL, 0.3, LONG));
		assertEquals(Gears.FIRST, AutoGearbox.next(Gears.REVERSE, 0.3, LONG));
	}
}
