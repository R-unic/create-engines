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

	@Test
	void neutralOrReverseStartsFromFirst() {
		assertEquals(Gears.FIRST, AutoGearbox.next(Gears.NEUTRAL, 0.3, LONG));
		assertEquals(Gears.FIRST, AutoGearbox.next(Gears.REVERSE, 0.3, LONG));
	}
}
