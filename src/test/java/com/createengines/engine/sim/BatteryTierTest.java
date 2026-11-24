package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The battery tiers ({@link BatteryTier}): the plain battery is what it always was, and the others differ in size and in how the cold treats them. */
class BatteryTierTest {

	private static final double EPS = 1e-9;

	@Test
	void theCheapestTierIsExactlyTheBatteryThatAlwaysExisted() {
		assertEquals(200, BatteryTier.PLAIN.capacity(), 0);
		assertEquals(BatteryChemistry.CAPACITY, BatteryTier.PLAIN.capacity(), 0);
		assertEquals(BatteryChemistry.MAX_COLD_LOSS, BatteryTier.PLAIN.coldLoss(), 0);
		for (double cold : new double[] {0, 0.3, 1})
			assertEquals(BatteryChemistry.deliverableLimit(200, cold, true),
				BatteryChemistry.deliverableLimit(200, cold, true, BatteryTier.PLAIN.coldLoss()), EPS);
	}

	@Test
	void aLossOfNothingGivesAllItHoldsInTheDeepestCold() {
		assertEquals(100, BatteryChemistry.deliverableLimit(100, 1, true, 0), EPS);
		assertEquals(60, BatteryChemistry.deliverable(60, 100, 1, true, 0), EPS);
	}

	@Test
	void aHighColdLossKeepsThatShareBackInTheDeepestCold() {
		assertEquals(200, BatteryChemistry.deliverableLimit(800, 1, true, 0.75), EPS);
		assertEquals(500, BatteryChemistry.deliverableLimit(800, 0.5, true, 0.75), EPS);
		assertEquals(800, BatteryChemistry.deliverableLimit(800, 0, true, 0.75), EPS);
		assertEquals(200, BatteryChemistry.deliverable(700, 800, 1, true, 0.75), EPS, "the charge is still there; the cold holds it back");
	}

	@Test
	void theColdSwitchAndOddValuesStillApply() {
		assertEquals(800, BatteryChemistry.deliverableLimit(800, 1, false, 0.75), EPS);
		assertEquals(0, BatteryChemistry.deliverableLimit(100, 1, true, 5), EPS, "a loss past 1 is held to all of it");
		assertEquals(100, BatteryChemistry.deliverableLimit(100, 1, true, -5), EPS, "a negative loss is none");
		assertEquals(0, BatteryChemistry.deliverable(-3, 100, 0, true, 0), EPS);
	}

	@Test
	void theDryCellIsSmallerThanThePlainBatteryAndColdProof() {
		assertEquals(100, BatteryTier.DRY_CELL.capacity(), 0);
		assertTrue(BatteryTier.DRY_CELL.capacity() < BatteryTier.PLAIN.capacity());
		assertEquals(0, BatteryTier.DRY_CELL.coldLoss(), 0);
		assertEquals(100, BatteryChemistry.deliverableLimit(100, 1, true, BatteryTier.DRY_CELL.coldLoss()), EPS);
	}

	@Test
	void theLargeBatteryIsFourPlainOnesAndTheColdHoldsBackThreeQuarters() {
		assertEquals(800, BatteryTier.LARGE.capacity(), 0);
		assertEquals(4 * BatteryTier.PLAIN.capacity(), BatteryTier.LARGE.capacity(), 0);
		assertEquals(0.75, BatteryTier.LARGE.coldLoss(), 0);
		assertEquals(200, BatteryChemistry.deliverableLimit(800, 1, true, BatteryTier.LARGE.coldLoss()), EPS);
	}

	@Test
	void everyTierHasASaneSizeAndLoss() {
		for (BatteryTier tier : BatteryTier.values()) {
			assertTrue(tier.capacity() > 0 && Float.isFinite(tier.capacity()), tier + " has a capacity");
			assertTrue(tier.coldLoss() >= 0 && tier.coldLoss() <= 1, tier + " loses a share of it, 0 to 1");
		}
	}
}
