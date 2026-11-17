package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exact numbers and exact edges, added after mutation testing (PIT) showed assertions that never bit: a flipped
 * comparison or a swapped operator left every earlier test green. Each test names the code it pins. Where a mutant is
 * equivalent (the changed comparison gives the same answer at the edge), there is nothing to pin and it is not tested.
 */
class ExactValuesTest {

	// ---- Mixture ----------------------------------------------------------------------------------------------

	@Test
	void mixtureEfficiencyAndFlameSpeedOutsideTheBandFollowTheirSlopes() {
		assertEquals(0.95, Mixture.efficiency(0.7), 1e-12);
		assertEquals(0.925, Mixture.efficiency(1.5), 1e-12);
		assertEquals(0.92, Mixture.flameSpeed(0.7), 1e-12);
		assertEquals(0.9, Mixture.flameSpeed(1.5), 1e-12);
		// Far outside, the floors hold
		assertEquals(0.85, Mixture.efficiency(0.0), 1e-12);
		assertEquals(0.85, Mixture.efficiency(5.0), 1e-12);
		assertEquals(0.7, Mixture.flameSpeed(0.0), 1e-12);
		assertEquals(0.8, Mixture.flameSpeed(5.0), 1e-12);
	}

	@Test
	void mixtureBalanceHasItsPeakAtOneAndZeroAtTheEdges() {
		assertEquals(1, Mixture.balance(1), 1e-12);
		assertEquals(0.5, Mixture.balance(0.9), 1e-12);
		assertEquals(0.5, Mixture.balance(1.125), 1e-12);
		assertEquals(0, Mixture.balance(Mixture.LEAN_BAND), 1e-12);
		assertEquals(0, Mixture.balance(Mixture.RICH_BAND), 1e-12);
		assertEquals(0, Mixture.balance(0.79), 0);
		assertEquals(0, Mixture.balance(1.26), 0);
	}

	@Test
	void theEdgesOfTheBalancedBandAreInsideIt() {
		assertTrue(Mixture.balanced(Mixture.LEAN_BAND));
		assertTrue(Mixture.balanced(Mixture.RICH_BAND));
		assertTrue(Mixture.balanced(1));
		assertFalse(Mixture.balanced(Math.nextDown(Mixture.LEAN_BAND)));
		assertFalse(Mixture.balanced(Math.nextUp(Mixture.RICH_BAND)));
		assertFalse(Mixture.balanced(0.1));
		assertFalse(Mixture.balanced(3));
	}

	// ---- SimMath ----------------------------------------------------------------------------------------------

	@Test
	void anAngleOnTheEdgeOfAPeriodWrapsToZero() {
		assertEquals(0, SimMath.wrap(720, 720), 0);
		assertEquals(0, SimMath.wrap(1440, 720), 0);
		assertEquals(0, SimMath.wrap(0, 720), 0);
		assertEquals(360, SimMath.wrap(1080, 720), 1e-9);
		assertEquals(10, SimMath.wrap(730, 720), 1e-9);
		assertEquals(5, SimMath.wrap(-715, 720), 1e-9);
		assertEquals(719.5, SimMath.wrap(719.5, 720), 0);
		// More than two periods round goes through the remainder
		assertEquals(30, SimMath.wrap(2190, 720), 1e-9);
		assertEquals(690, SimMath.wrap(-1470, 720), 1e-9);
	}

	@Test
	void relaxationApproachesItsTargetOnBothSidesOfTheSeriesCutOff() {
		for (double x : new double[] {0.001, 0.1, 0.24, 0.25, 0.26, 1, 3})
			assertEquals(1 - Math.exp(-x), SimMath.approach(x), 1e-9, "x = " + x);
		assertEquals(0, SimMath.approach(0), 0);
	}

	@Test
	void aMarkIsPassedByExactlyOneOfTwoNeighbouringSteps() {
		// The mark at 100 lies on the boundary between the step 90..100 and the step 100..110
		assertTrue(SimMath.crosses(90, 10, 100));
		assertFalse(SimMath.crosses(100, 10, 100));
		assertFalse(SimMath.crosses(100, 10, 110 + 1e-6));
		assertTrue(SimMath.crosses(100, 10, 110));
		assertFalse(SimMath.crosses(100, 10, 95));
		assertTrue(SimMath.crosses(100, 10, 101));
	}

	// ---- HeatRecovery -----------------------------------------------------------------------------------------

	@Test
	void anExchangerReachesEachLevelAtExactlyItsThreshold() {
		assertEquals(HeatRecovery.NONE, HeatRecovery.level(Math.nextDown(HeatRecovery.SMOULDERING), 0));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(HeatRecovery.SMOULDERING, 0));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(Math.nextDown(HeatRecovery.KINDLED), 0));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(HeatRecovery.KINDLED, 0));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(Math.nextDown(HeatRecovery.SEETHING), 0));
		assertEquals(HeatRecovery.SEETHE, HeatRecovery.level(HeatRecovery.SEETHING, 0));
	}

	@Test
	void aFallingExchangerKeepsItsLevelUntilTheHeatIsClearlyBelow() {
		double hold = HeatRecovery.KINDLED * 0.85;
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(hold, HeatRecovery.KINDLE));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(Math.nextDown(hold), HeatRecovery.KINDLE));
		double holdTop = HeatRecovery.SEETHING * 0.85;
		assertEquals(HeatRecovery.SEETHE, HeatRecovery.level(holdTop, HeatRecovery.SEETHE));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(Math.nextDown(holdTop), HeatRecovery.SEETHE));
	}

	// ---- WindowTint -------------------------------------------------------------------------------------------

	@Test
	void aTintIsFoundByItsFluidPathOrFallsBackToAmber() {
		assertSame(WindowTint.DIESEL, WindowTint.ofPath("diesel"));
		assertSame(WindowTint.ENGINE_OIL, WindowTint.ofPath("engine_oil"));
		assertSame(WindowTint.AMBER, WindowTint.ofPath("water"));
		assertSame(WindowTint.AMBER, WindowTint.ofPath("amber"));
		assertSame(WindowTint.AMBER, WindowTint.ofPath(""));
	}

	@Test
	void aTintByOrdinalIsExactAtBothEnds() {
		WindowTint[] all = WindowTint.values();
		assertSame(all[0], WindowTint.byOrdinal(0));
		assertSame(all[1], WindowTint.byOrdinal(1));
		assertSame(all[all.length - 1], WindowTint.byOrdinal(all.length - 1));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(all.length));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(-1));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(Integer.MAX_VALUE));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(Integer.MIN_VALUE));
	}

	// ---- IgnitionSource ---------------------------------------------------------------------------------------

	@Test
	void noIgnitionSourceIsNotInstalledPoweredOrSparking() {
		assertFalse(IgnitionSource.NONE.installed());
		assertFalse(IgnitionSource.NONE.powered());
		assertFalse(IgnitionSource.NONE.spark());
		assertTrue(IgnitionSource.UNLIMITED.installed());
		assertTrue(IgnitionSource.UNLIMITED.powered());
		assertTrue(IgnitionSource.UNLIMITED.spark());
	}

	// ---- SootCollector ----------------------------------------------------------------------------------------

	@Test
	void aTrapIsFullAtExactlyItsCapacityAndLosesAnythingMore() {
		SootCollector trap = new SootCollector();
		assertEquals(SootCollector.CAPACITY - 1, trap.add((SootCollector.CAPACITY - 1) * SootCollector.MB_PER_SOOT));
		assertFalse(trap.full());
		assertEquals(1, trap.add(SootCollector.MB_PER_SOOT));
		assertEquals(SootCollector.CAPACITY, trap.stored());
		assertTrue(trap.full());
		assertEquals(0, trap.add(1000));
		assertEquals(0, trap.progress(), 0);
	}

	@Test
	void aTrapKeepsTheRemainderTowardsTheNextLump() {
		SootCollector trap = new SootCollector();
		assertEquals(0, trap.add(49.9));
		assertEquals(49.9, trap.progress(), 1e-12);
		assertEquals(1, trap.add(0.1));
		assertEquals(0, trap.progress(), 1e-9);
		assertEquals(2, trap.add(120));
		assertEquals(20, trap.progress(), 1e-9);
		assertEquals(0, trap.add(0));
		assertEquals(0, trap.add(-5));
		assertEquals(0, trap.add(Double.NaN));
	}

	// ---- PlateFigures -----------------------------------------------------------------------------------------

	@Test
	void whenTwoSpeedsTieTheLowerOneIsTheFigure() {
		PlateFigures tieTorque = PlateFigures.of(new float[] {1000, 2000}, new float[] {10, 10});
		assertNotNull(tieTorque);
		assertEquals(1000, tieTorque.torqueRpm());
		// Power ties as well: 10 * 1000 == 5 * 2000
		PlateFigures tiePower = PlateFigures.of(new float[] {1000, 2000}, new float[] {10, 5});
		assertNotNull(tiePower);
		assertEquals(1000, tiePower.powerRpm());
	}

	@Test
	void aRunWithNoPowerHasNoFigures() {
		assertNull(PlateFigures.of(new float[] {0}, new float[] {5}));
		assertNull(PlateFigures.of(new float[] {1000}, new float[] {0}));
		assertNotNull(PlateFigures.of(new float[] {1}, new float[] {1}));
	}
}
