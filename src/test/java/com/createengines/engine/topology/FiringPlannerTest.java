package com.createengines.engine.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.engine.topology.FiringPlanner.Cylinder;
import com.createengines.engine.topology.FiringPlanner.Plan;
import com.createengines.engine.topology.FiringPlanner.Style;

class FiringPlannerTest {

	/** One cylinder per throw, all bored on the same side, each on its own cam lobe. */
	private static List<Cylinder> inline(int count) {
		List<Cylinder> cylinders = new ArrayList<>();
		for (int i = 0; i < count; i++)
			cylinders.add(new Cylinder(i, 90, i));
		return cylinders;
	}

	/** Two banks a right angle apart, one cylinder from each on every throw. */
	private static List<Cylinder> vee(int throwCount) {
		List<Cylinder> cylinders = new ArrayList<>();
		for (int i = 0; i < throwCount; i++) {
			cylinders.add(new Cylinder(i, 90, 2 * i));
			cylinders.add(new Cylinder(i, 180, 2 * i + 1));
		}
		return cylinders;
	}

	private static Plan solve(Style style, int throwCount, int lobeCount, List<Cylinder> cylinders) {
		return FiringPlanner.solve(style, 1, new double[throwCount], new double[lobeCount], cylinders);
	}

	private static void assertEvenlySpaced(Plan plan, int count) {
		for (double gap : FiringPlanner.intervals(plan.fireAngles()))
			assertEquals(720.0 / count, gap, 1e-6, "every firing should be " + 720.0 / count + " degrees apart");
		assertTrue(plan.even());
	}

	@Test
	void aStraightTwelveFiresEveryHalfSixtyDegrees() {
		Plan plan = solve(Style.SMOOTH, 12, 12, inline(12));
		assertEvenlySpaced(plan, 12);
	}

	@Test
	void everyAngleItChoosesCanBeSetOnTheBlocks() {
		Plan plan = solve(Style.SMOOTH, 12, 12, inline(12));
		for (double angle : plan.throwAngles())
			assertEquals(0, angle % FiringPlanner.THROW_STEP, 1e-9, "throw " + angle);
		for (double angle : plan.lobeAngles()) {
			assertEquals(0, angle % FiringPlanner.LOBE_STEP, 1e-9, "lobe " + angle);
			assertTrue(angle >= 0 && angle < 360);
		}
	}

	@Test
	void anInlineSixFiresEvenlyAndNotInSequence() {
		Plan plan = solve(Style.SMOOTH, 6, 6, inline(6));
		assertEvenlySpaced(plan, 6);
		int straight = 0;
		for (int i = 0; i + 1 < plan.order().length; i++)
			if (Math.abs(plan.order()[i] - plan.order()[i + 1]) == 1)
				straight++;
		assertTrue(straight <= 1, "neighbours should not fire one after the other: " + java.util.Arrays.toString(plan.order()));
	}

	@Test
	void aFlatPlaneFourUsesOnlyTwoThrowAngles() {
		Plan plan = solve(Style.FLAT_PLANE, 4, 4, inline(4));
		assertEvenlySpaced(plan, 4);
		for (double angle : plan.throwAngles())
			assertTrue(angle == 0 || angle == 180, "flat plane throw " + angle);
	}

	@Test
	void aCrossPlaneEightUsesQuarterTurns() {
		Plan plan = solve(Style.CROSS_PLANE, 4, 8, vee(4));
		assertEvenlySpaced(plan, 8);
		for (double angle : plan.throwAngles())
			assertEquals(0, angle % 90, 1e-9, "cross plane throw " + angle);
	}

	@Test
	void aSmoothVeeEightIsEvenToo() {
		assertEvenlySpaced(solve(Style.SMOOTH, 4, 8, vee(4)), 8);
	}

	/** Six throws with two cylinders each a right angle apart cannot be evenly spaced; the planner still does its best. */
	@Test
	void aNinetyDegreeTwelveCannotBeEvenButIsAsCloseAsItGets() {
		Plan plan = solve(Style.SMOOTH, 6, 12, vee(6));
		assertTrue(!plan.even(), "a 90 degree V12 can't fire evenly");
		assertTrue(plan.deviation() <= 30, "but it should be close: " + plan.deviation());
	}

	/** With the throws left alone, only the revolution each cylinder fires on is chosen. */
	@Test
	void camsOnlyLeavesTheThrowsAndSpreadsTheFiring() {
		double[] throwAngles = {0, 180, 180, 0};
		Plan plan = FiringPlanner.solve(Style.CAMS_ONLY, 1, throwAngles, new double[4], inline(4));
		assertEvenlySpaced(plan, 4);
		assertTrue(java.util.Arrays.equals(throwAngles, plan.throwAngles()));
	}

	@Test
	void camsOnlyCanBeUnevenWhenTheThrowsForbidEven() {
		double[] throwAngles = {0, 0, 0, 0};
		Plan plan = FiringPlanner.solve(Style.CAMS_ONLY, 1, throwAngles, new double[4], inline(4));
		assertTrue(!plan.even(), "four pistons rising together can fire only twice per cycle");
	}

	/** One lobe in the valley of a vee: the lobe's angle times the first cylinder and the second follows its piston. */
	@Test
	void aLobeSharedByTheTwoBanksOfAVeeFiresTheSecondAfterTheFirstByTheirCrankAngles() {
		List<Cylinder> cylinders = List.of(new Cylinder(0, 90, 0), new Cylinder(0, 180, 0));
		Plan plan = solve(Style.SMOOTH, 1, 1, cylinders);
		assertEquals(90, plan.fireAngles()[1] - plan.fireAngles()[0] + (plan.fireAngles()[1] < plan.fireAngles()[0] ? 720 : 0),
			1e-9, "a right-angle vee fires its second bank a quarter turn on");
		assertEquals(plan.fireAngles()[0] / 2, plan.lobeAngles()[0], 1e-9, "the lobe is set for the first cylinder");

		Plan reversed = FiringPlanner.solve(Style.SMOOTH, -1, new double[1], new double[1], cylinders);
		double gap = reversed.fireAngles()[1] - reversed.fireAngles()[0];
		assertEquals(270, (gap + 720) % 720, 1e-9, "turning the other way mirrors the offset");
	}

	/** Four throws of a vee, a lobe in the valley of each: every pair is tied, and the planner still spaces the firing evenly. */
	@Test
	void aVeeEightWithOneLobePerValleyIsStillEven() {
		List<Cylinder> cylinders = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			cylinders.add(new Cylinder(i, 90, i));
			cylinders.add(new Cylinder(i, 180, i));
		}
		Plan plan = solve(Style.SMOOTH, 4, 4, cylinders);
		assertEvenlySpaced(plan, 8);
		for (int i = 0; i < 4; i++)
			assertEquals(0, plan.lobeAngles()[i] % FiringPlanner.LOBE_STEP, 1e-9, "lobe " + i + " can be set on its block");
	}

	/** A lobe times everything within one crank turn after the first cylinder, so one lobe for a whole four cannot spread them. */
	@Test
	void oneLobeForTheWholeEngineCannotSpreadTheFiring() {
		List<Cylinder> cylinders = new ArrayList<>();
		for (int i = 0; i < 4; i++)
			cylinders.add(new Cylinder(i, 90, 0));
		Plan plan = solve(Style.SMOOTH, 4, 1, cylinders);
		assertTrue(!plan.even(), "four firings inside one turn leave a long gap: " + plan.deviation());
		assertEquals(plan.fireAngles()[0] / 2, plan.lobeAngles()[0], 1e-9, "the lobe is still set for the first cylinder");
	}

	@Test
	void aReversedEngineFiresInTheSameEvenSpacing() {
		Plan plan = FiringPlanner.solve(Style.SMOOTH, -1, new double[8], new double[8], inline(8));
		assertEvenlySpaced(plan, 8);
	}

	@Test
	void noCylindersPlansNothing() {
		Plan plan = solve(Style.SMOOTH, 3, 0, List.of());
		assertEquals(0, plan.order().length);
		assertEquals(3, plan.throwAngles().length);
	}
}
