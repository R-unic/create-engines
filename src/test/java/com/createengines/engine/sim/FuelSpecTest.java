package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FuelSpecTest {

	@Test
	void theShortConstructorsFillInTheUsualDefaults() {
		FuelSpec plain = new FuelSpec(10, 2, 1.2);
		assertEquals(1, plain.coldSensitivity(), 0);
		assertEquals(0, plain.chargeCooling(), 0);
		assertEquals(1, plain.sootFactor(), 0);

		FuelSpec cold = new FuelSpec(10, 2, 1.2, 1.7);
		assertEquals(1.7, cold.coldSensitivity(), 0);
		assertEquals(0, cold.chargeCooling(), 0);
		assertEquals(1, cold.sootFactor(), 0);
	}

	@Test
	void sulfurDefaultsToTheSootFactorSoEveryOldFuelReleasesWhatItDid() {
		assertEquals(1, new FuelSpec(10, 2, 1.2).sulfur(), 0);
		assertEquals(0.4, new FuelSpec(10, 2, 1.2, 1, 0, 0.4, 2).sulfur(), 0, "the seven-argument form is how 1.x fuels were built");
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL})
			assertEquals(fuel.sootFactor(), fuel.sulfur(), 0, "built-in fuels keep their sulfur at their soot factor");
		assertEquals(0, FuelSpec.ETHANOL.sulfur(), 0);
	}

	@Test
	void sulfurCanBeSetApartFromTheSootFactor() {
		FuelSpec lowSulfurSooty = new FuelSpec(10, 2, 1.2, 1, 0, 1, 2, 0.1);
		assertEquals(1, lowSulfurSooty.sootFactor(), 0);
		assertEquals(0.1, lowSulfurSooty.sulfur(), 0);
		FuelSpec cleanSulfurous = new FuelSpec(10, 2, 1.2, 1, 0, 0, 2, 1.5);
		assertEquals(0, cleanSulfurous.sootFactor(), 0);
		assertEquals(1.5, cleanSulfurous.sulfur(), 0);
	}

	@Test
	void gasolineAndDieselAreTheReferenceForInjectorSizing() {
		assertEquals(1, FuelSpec.GASOLINE.injectorFlow(), 1e-12);
		assertEquals(1, FuelSpec.DIESEL.injectorFlow(), 1e-12);
		assertEquals(1, FuelSpec.PLANT_OIL.injectorFlow(), 1e-12);
	}

	@Test
	void aThinFuelNeedsABiggerShotForTheSameAir() {
		assertEquals(1.5, FuelSpec.ETHANOL.injectorFlow(), 1e-12);
		assertEquals(3, new FuelSpec(10, 2, 0.5).injectorFlow(), 1e-12);
	}

	@Test
	void injectorFlowIsNeverBelowTheUsualShot() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL,
			new FuelSpec(10, 2, 9)})
			assertTrue(fuel.injectorFlow() >= 1);
	}

	@Test
	void aChargeBelowTheAutoignitionPointLosesNothingToPreignition() {
		assertEquals(0, FuelSpec.GASOLINE.preignitionLoss(1), 0);
		assertEquals(0, FuelSpec.GASOLINE.preignitionLoss(FuelSpec.GASOLINE.autoignitionTemp()), 0);
	}

	@Test
	void preignitionCostsMoreTheHotterTheChargeIsPastTheLimit() {
		double autoignition = FuelSpec.GASOLINE.autoignitionTemp();
		double little = FuelSpec.GASOLINE.preignitionLoss(autoignition + 0.1);
		double more = FuelSpec.GASOLINE.preignitionLoss(autoignition + 0.2);
		assertEquals(0.1 * FuelSpec.PREIGNITION_LOSS_PER_DEGREE, little, 1e-12);
		assertEquals(2 * little, more, 1e-12);
	}

	@Test
	void preignitionLossIsCapped() {
		assertEquals(FuelSpec.PREIGNITION_MAX_LOSS, FuelSpec.GASOLINE.preignitionLoss(100), 0);
		assertEquals(FuelSpec.PREIGNITION_MAX_LOSS, FuelSpec.ETHANOL.preignitionLoss(100), 0);
	}

	@Test
	void ethanolHoldsOutAgainstTheChargeThatSetsGasolineOff() {
		double charge = 3.0;
		assertTrue(FuelSpec.GASOLINE.preignitionLoss(charge) > 0);
		assertEquals(0, FuelSpec.ETHANOL.preignitionLoss(charge), 0);
	}

	@Test
	void onlyEthanolCoolsTheChargeAndBurnsCleanOfSoot() {
		assertTrue(FuelSpec.ETHANOL.chargeCooling() > 0);
		assertEquals(0, FuelSpec.ETHANOL.sootFactor(), 0);
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE}) {
			assertEquals(0, fuel.chargeCooling(), 0);
			// 2.1 (author-approved, Exception 2.9): plant oil is the dirty fuel, 1.3; diesel and gasoline are still 1
			assertEquals(fuel == FuelSpec.PLANT_OIL ? 1.3 : 1, fuel.sootFactor(), 0);
		}
	}

	@Test
	void gasolineNeedsASparkWhileDieselAndOilLightByCompression() {
		assertTrue(FuelSpec.GASOLINE.autoignitionTemp() > FuelSpec.DIESEL.autoignitionTemp());
		assertTrue(FuelSpec.GASOLINE.autoignitionTemp() > FuelSpec.PLANT_OIL.autoignitionTemp());
		assertTrue(FuelSpec.ETHANOL.autoignitionTemp() > FuelSpec.GASOLINE.autoignitionTemp());
	}

	@Test
	void dieselCarriesMoreEnergyThanOilAndEthanolLeast() {
		assertTrue(FuelSpec.GASOLINE.energyPerMb() > FuelSpec.DIESEL.energyPerMb());
		assertTrue(FuelSpec.DIESEL.energyPerMb() > FuelSpec.PLANT_OIL.energyPerMb());
		assertTrue(FuelSpec.PLANT_OIL.energyPerMb() > FuelSpec.ETHANOL.energyPerMb());
	}
}
