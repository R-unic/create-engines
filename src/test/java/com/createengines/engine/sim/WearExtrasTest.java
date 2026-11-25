package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spark plug wear, turbo wear and knock: sound parts change nothing, wear is monotonic and bounded, and nonsense inputs stay sane. */
class WearExtrasTest {

	private static final double[] ODD = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 1e300};

	@Test
	void aPlugThatIsNotWornIsExactlyAPlugThatNeverWore() {
		assertEquals(0, PlugWear.misfireChance(0), 0);
		assertEquals(1, PlugWear.leanScale(0), 0);
		assertEquals(0, PlugWear.misfireChance(PlugWear.MISFIRE_FROM), 0);
	}

	@Test
	void plugWearGrowsWithSpeedCoilAndSoot() {
		double base = PlugWear.wear(128, false, 0, 1000);
		assertTrue(PlugWear.wear(256, false, 0, 1000) > base);
		assertTrue(PlugWear.wear(128, true, 0, 1000) > base);
		assertTrue(PlugWear.wear(128, false, 1, 1000) > base);
		assertEquals(1, PlugWear.wear(128, false, 0, PlugWear.LIFE), 1e-9);
		assertEquals(0, PlugWear.wear(0, false, 0, 1000), 0);
	}

	@Test
	void aWornPlugMissesMoreAndLightsLeanerLessButIsNeverDead() {
		double missBefore = 0, leanBefore = 1;
		for (int i = 0; i <= 100; i++) {
			double w = i / 100d;
			double miss = PlugWear.misfireChance(w);
			double lean = PlugWear.leanScale(w);
			assertTrue(miss >= missBefore && miss <= PlugWear.MISFIRE_MAX + 1e-12);
			assertTrue(lean >= leanBefore && lean <= PlugWear.LEAN_LOSS + 1e-12);
			missBefore = miss;
			leanBefore = lean;
		}
		assertTrue(PlugWear.misfireChance(1) < 1, "a spent plug still lights most sparks");
	}

	@Test
	void turboWearGrowsWithBoostHeatAndNoOilAndIdlingStillWears() {
		double base = TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL, 1, 1000);
		assertEquals(1000 / TurboWear.LIFE, base, 1e-12);
		assertTrue(TurboWear.wear(1, Thermal.NORMAL, 1, 1000) > base);
		assertTrue(TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL + 0.3, 1, 1000) > base);
		assertTrue(TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL, 0, 1000) > base);
		double idle = TurboWear.wear(0, Thermal.NORMAL, 1, 1000);
		assertTrue(idle > 0 && idle < base);
	}

	@Test
	void aWornTurboMakesLessBoostAndNeverNone() {
		assertEquals(1, TurboWear.boostShare(0), 0);
		double before = 1;
		for (int i = 0; i <= 100; i++) {
			double share = TurboWear.boostShare(i / 100d);
			assertTrue(share <= before && share >= 1 - TurboWear.MAX_BOOST_LOSS - 1e-12);
			before = share;
		}
	}

	@Test
	void aTurboOnlyFailsWhenWellWornAndOverspeedMakesItWorse() {
		for (int i = 0; i <= 10; i++)
			assertEquals(0, TurboWear.failureChance(i * 0.05, 3, 100), 0);
		double normal = TurboWear.failureChance(0.9, TurboWear.REFERENCE_BOOST, 1);
		assertTrue(normal > 0 && normal < 1);
		assertTrue(TurboWear.failureChance(0.9, TurboWear.OVERSPEED + 1, 1) > normal * 4);
		assertTrue(TurboWear.failureChance(0.95, 1, 1) > TurboWear.failureChance(0.8, 1, 1));
	}

	@Test
	void noKnockBelowTheThresholdAndItRisesToOne() {
		assertEquals(0, Knock.intensity(0), 0);
		assertEquals(0, Knock.intensity(Knock.FROM), 0);
		assertEquals(0, Knock.pistonWear(Knock.FROM, 256, 1000), 0);
		assertFalse(Knock.audible(Knock.FROM));
		assertEquals(1, Knock.intensity(FuelSpec.PREIGNITION_MAX_LOSS), 1e-12);
		assertEquals(1, Knock.intensity(5), 0);
		double before = 0;
		for (int i = 0; i <= 35; i++) {
			double hard = Knock.intensity(i / 100d);
			assertTrue(hard >= before);
			before = hard;
		}
		assertTrue(Knock.audible(FuelSpec.PREIGNITION_MAX_LOSS));
	}

	@Test
	void knockWearsThePistonFasterTheHarderAndTheFasterItRuns() {
		double mild = Knock.pistonWear(0.1, 128, 60);
		assertTrue(mild > 0);
		assertTrue(Knock.pistonWear(0.3, 128, 60) > mild);
		assertTrue(Knock.pistonWear(0.1, 256, 60) > mild);
		assertEquals(1, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, 128, Knock.LIFE), 1e-9);
	}

	@Test
	void nonsenseInputsStayFiniteAndNeverNegative() {
		for (double x : ODD)
			for (double y : ODD) {
				double[] results = {PlugWear.wear(x, true, y, x), PlugWear.wear(x, false, y, y), PlugWear.misfireChance(x),
					PlugWear.leanScale(x), TurboWear.wear(x, y, x, y), TurboWear.boostShare(x), TurboWear.failureChance(x, y, x),
					TurboWear.failureChance(1, x, y), Knock.intensity(x), Knock.pistonWear(x, y, x)};
				for (double r : results)
					assertTrue(!Double.isNaN(r) && r >= 0, "no NaN or negative: " + r + " for " + x + ", " + y);
			}
	}
}
