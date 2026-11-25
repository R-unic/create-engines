package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Battery wear (opt-in, 2.3): the shape of the curves, what makes each tier different, and that a worn battery is weak and never dead.
 * The block entity's own behaviour (nothing runs with the mode off, saves, the kit) is covered by the GameTests.
 */
class BatteryWearTest {

	private static final double EPS = 1e-9;

	@Test
	void aBatteryWithNoWearHoldsExactlyWhatItsTierSays() {
		for (BatteryTier tier : BatteryTier.values()) {
			assertEquals(1, BatteryWear.capacityShare(0), 0);
			assertEquals(tier.capacity(), BatteryWear.capacity(tier, 0), 0, tier + " new");
		}
	}

	@Test
	void aWornBatteryHoldsLessAndNeverStopsWorking() {
		for (BatteryTier tier : BatteryTier.values()) {
			float previous = Float.MAX_VALUE;
			for (double wear = 0; wear <= 1.0001; wear += 0.05) {
				float held = BatteryWear.capacity(tier, wear);
				assertTrue(held <= previous, tier + " never holds more as it wears");
				previous = held;
			}
			assertEquals(tier.capacity() * (1 - BatteryWear.MAX_FADE), BatteryWear.capacity(tier, 1), 1e-4, tier + " spent");
			assertTrue(BatteryWear.capacity(tier, 1) > 0, tier + " is weak, not dead");
		}
	}

	@Test
	void wearOutsideZeroToOneOrNotANumberReadsAsTheNearestSound() {
		assertEquals(1, BatteryWear.capacityShare(-5), 0);
		assertEquals(1, BatteryWear.capacityShare(Double.NaN), 0);
		assertEquals(1 - BatteryWear.MAX_FADE, BatteryWear.capacityShare(7), 0);
		assertEquals(1 - BatteryWear.MAX_FADE, BatteryWear.capacityShare(Double.POSITIVE_INFINITY), 0);
	}

	@Test
	void aFullWarmDischargeFromAGoodChargeTakesOneOverTheTiersCycles() {
		for (BatteryTier tier : BatteryTier.values())
			assertEquals(1 / BatteryWear.profile(tier).cycles(), BatteryWear.drawWear(tier, tier.capacity(), 1, 0), EPS, tier.name());
	}

	@Test
	void aDeepDischargeWearsMoreThanAShallowOne() {
		for (BatteryTier tier : BatteryTier.values()) {
			double good = BatteryWear.drawWear(tier, 10, 0.8, 0);
			double edge = BatteryWear.drawWear(tier, 10, BatteryWear.DEEP_BELOW, 0);
			double deep = BatteryWear.drawWear(tier, 10, 0.05, 0);
			double empty = BatteryWear.drawWear(tier, 10, 0, 0);
			assertEquals(good, edge, EPS, tier + ": nothing extra at the edge of the deep zone");
			assertTrue(deep > edge && empty > deep, tier + ": " + good + " < " + deep + " < " + empty);
			assertEquals(BatteryWear.profile(tier).deepStress(), empty / good, 1e-6, tier + ": an empty battery's stress is the tier's");
		}
	}

	@Test
	void theColdWearsEveryTierButTheDryCell() {
		for (BatteryTier tier : BatteryTier.values()) {
			double warm = BatteryWear.drawWear(tier, 10, 0.8, 0);
			double cold = BatteryWear.drawWear(tier, 10, 0.8, 1);
			if (tier == BatteryTier.DRY_CELL)
				assertEquals(warm, cold, EPS, "the dry cell is made for the cold");
			else
				assertTrue(cold > warm, tier + ": " + cold + " > " + warm);
		}
	}

	@Test
	void eachTierWearsForItsOwnReasons() {
		BatteryWear.Profile plain = BatteryWear.profile(BatteryTier.PLAIN);
		BatteryWear.Profile dry = BatteryWear.profile(BatteryTier.DRY_CELL);
		BatteryWear.Profile large = BatteryWear.profile(BatteryTier.LARGE);
		// The cheap cell has the shortest life and a deep discharge hurts it most; the large battery lasts longest and the cold hurts it most
		assertTrue(dry.cycles() < plain.cycles() && plain.cycles() < large.cycles());
		assertTrue(dry.deepStress() > plain.deepStress() && plain.deepStress() > large.deepStress());
		assertTrue(large.coldStress() > plain.coldStress() && plain.coldStress() > dry.coldStress());
		assertEquals(0, dry.coldStress(), 0);
	}

	@Test
	void theSameWorkWearsALargeBatteryLessThanAPlainOneOnAGoodDayAndMoreInTheCold() {
		double plainWarm = BatteryWear.drawWear(BatteryTier.PLAIN, 200, 0.8, 0) / BatteryTier.PLAIN.capacity();
		double largeWarm = BatteryWear.drawWear(BatteryTier.LARGE, 200, 0.8, 0) / BatteryTier.LARGE.capacity();
		assertTrue(largeWarm < plainWarm);
		// Per spark, a battery in the deepest cold: the large one's life shrinks by the larger factor
		double plainShrink = BatteryWear.drawWear(BatteryTier.PLAIN, 100, 0.8, 1) / BatteryWear.drawWear(BatteryTier.PLAIN, 100, 0.8, 0);
		double largeShrink = BatteryWear.drawWear(BatteryTier.LARGE, 100, 0.8, 1) / BatteryWear.drawWear(BatteryTier.LARGE, 100, 0.8, 0);
		assertTrue(largeShrink > plainShrink);
	}

	@Test
	void drawWearIsFiniteAndBoundedForAnyInput() {
		double[] odd = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 1e300, 1e-300};
		for (BatteryTier tier : BatteryTier.values())
			for (double drawn : odd)
				for (double share : odd)
					for (double cold : odd) {
						double w = BatteryWear.drawWear(tier, drawn, share, cold);
						assertTrue(w >= 0 && w <= 1, tier + " " + drawn + " " + share + " " + cold + " -> " + w);
					}
		assertEquals(0, BatteryWear.drawWear(BatteryTier.PLAIN, 0, 0, 0), 0);
		assertEquals(0, BatteryWear.drawWear(BatteryTier.PLAIN, -5, 0, 0), 0);
		assertEquals(0, BatteryWear.drawWear(BatteryTier.PLAIN, Double.NaN, 0, 0), 0);
	}

	@Test
	void aBatteryTakesAboutTheStatedNumberOfFullDischargesToWearOut() {
		BatteryTier tier = BatteryTier.PLAIN;
		double wear = 0;
		int cycles = 0;
		while (wear < 1 && cycles < 100_000) {
			wear += BatteryWear.drawWear(tier, tier.capacity(), 1, 0);
			cycles++;
		}
		assertEquals(BatteryWear.profile(tier).cycles(), cycles, 2);
	}

	@Test
	void aLightWornBatteryCanBeSetRightAndAHeavyOneCannot() {
		assertTrue(BatteryWear.repairable(0.3));
		assertEquals(Wear.REPAIRED_TO, BatteryWear.repaired(0.3), 0);
		assertFalse(BatteryWear.repairable(0.6));
		assertEquals(0.6, BatteryWear.repaired(0.6), 0, "heavy wear is cured by replacing the battery");
		assertFalse(BatteryWear.repairable(0), "a new battery has nothing to set right");
	}
}
