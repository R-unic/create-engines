package com.createengines.engine.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RotorPlannerTest {

	private static final double[] NONE = new double[0];

	/** The smallest angle between two phases, mod 360. */
	private static double apart(double a, double b) {
		double gap = Math.abs(a - b) % 360;
		return Math.min(gap, 360 - gap);
	}

	@Test
	void twoRotorsFireHalfATurnApart() {
		RotorPlanner.Plan plan = RotorPlanner.solve(1, NONE, new double[2]);
		assertTrue(plan.even(), "deviation " + plan.deviation());
		assertEquals(180, apart(plan.phases()[0], plan.phases()[1]), 1e-9);
	}

	@Test
	void threeRotorsFireAThirdOfATurnApart() {
		RotorPlanner.Plan plan = RotorPlanner.solve(1, NONE, new double[3]);
		assertTrue(plan.even(), "deviation " + plan.deviation());
		double[] sorted = plan.phases().clone();
		Arrays.sort(sorted);
		assertEquals(120, sorted[1] - sorted[0], 1e-9);
		assertEquals(120, sorted[2] - sorted[1], 1e-9);
	}

	@Test
	void fourAndFiveRotorsAreAsEvenAsFifteenDegreeStepsAllow() {
		assertTrue(RotorPlanner.solve(1, NONE, new double[4]).even());
		assertTrue(RotorPlanner.solve(1, NONE, new double[5]).deviation() <= 12.001, "72 degrees is not a multiple of 15, so one gap is 60 and the rest 75");
	}

	@Test
	void quarterTurnStylesStillPairTwoRotorsButCannotSpaceThree() {
		assertTrue(RotorPlanner.solve(6, NONE, new double[2]).even());
		RotorPlanner.Plan three = RotorPlanner.solve(6, NONE, new double[3]);
		assertFalse(three.even());
		for (double phase : three.phases())
			assertEquals(0, phase % 90, 1e-9, "phases stay on the style's steps");
	}

	@Test
	void camsOnlyLeavesTheRotorsWhereTheyAre() {
		double[] current = {30, 45};
		assertEquals(Arrays.toString(current), Arrays.toString(RotorPlanner.solve(0, NONE, current).phases()));
		assertFalse(RotorPlanner.solve(0, NONE, current).even());
	}

	@Test
	void aRotorFitsAroundPistonsThatAreAlreadyTimed() {
		// Two cylinders a turn apart leave room for a rotor's two firings exactly halfway between them
		RotorPlanner.Plan plan = RotorPlanner.solve(1, new double[] {0, 360}, new double[1]);
		assertTrue(plan.even(), "deviation " + plan.deviation());
		assertEquals(180, plan.phases()[0], 1e-9);
	}

	@Test
	void theEccentricAngleAndThePhaseAreInverses() {
		for (int sense : new int[] {1, -1})
			for (int direction : new int[] {1, -1})
				for (double phase = 0; phase < 360; phase += 15) {
					double eccentric = RotorPlanner.eccentricFor(phase, sense, direction);
					assertEquals(phase, RotorPlanner.phaseOf(eccentric, sense, direction), 1e-9);
					assertEquals(0, eccentric % 15, 1e-9, "a whole step of the value box");
				}
	}
}
