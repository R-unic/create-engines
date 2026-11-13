package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LubricationTest {

	private static final OilSupply PLENTY = mb -> mb;

	@Test
	void theFilmAndDilutionAreClampedToTheirRanges() {
		assertEquals(1, new Lubrication(5).film(), 0);
		assertEquals(0, new Lubrication(-1).film(), 0);

		Lubrication oil = new Lubrication(1);
		oil.setFilm(2);
		assertEquals(1, oil.film(), 0);
		oil.setDilution(9);
		assertEquals(Lubrication.MAX_DILUTION, oil.dilution(), 0);
		oil.setDilution(-9);
		assertEquals(0, oil.dilution(), 0);
		oil.setCondition(3);
		assertEquals(1, oil.condition(), 0);
		oil.setCondition(-3);
		assertEquals(0, oil.condition(), 0);
	}

	@Test
	void fuelWashesIntoTheOilAndBoilsBackOut() {
		Lubrication oil = new Lubrication(1);
		oil.dilute(0.2);
		oil.dilute(0.1);
		assertEquals(0.3, oil.dilution(), 1e-12);
		oil.boilOff(0.1);
		assertEquals(0.2, oil.dilution(), 1e-12);
		oil.boilOff(5);
		assertEquals(0, oil.dilution(), 0);
		oil.dilute(5);
		assertEquals(Lubrication.MAX_DILUTION, oil.dilution(), 0);
	}

	@Test
	void thinnedOilStillLubricatesAtHalfStrengthAtWorst() {
		Lubrication oil = new Lubrication(1);
		assertEquals(1, oil.effective(), 1e-12);
		oil.setDilution(Lubrication.MAX_DILUTION);
		assertEquals(0.5, oil.effective(), 1e-12);
		oil.setFilm(0.6);
		assertEquals(0.3, oil.effective(), 1e-12);
	}

	@Test
	void frictionFactorsGrowAsTheFilmThinsAndSpeedDependentGrowsFaster() {
		assertEquals(1, Lubrication.staticFactor(1), 1e-12);
		assertEquals(1, Lubrication.viscousFactor(1), 1e-12);
		assertEquals(2, Lubrication.staticFactor(0), 1e-12);
		assertEquals(4, Lubrication.viscousFactor(0), 1e-12);
		for (double film = 0; film < 1; film += 0.1) {
			assertTrue(Lubrication.staticFactor(film) > Lubrication.staticFactor(film + 0.1));
			assertTrue(Lubrication.viscousFactor(film) > Lubrication.staticFactor(film));
		}
	}

	@Test
	void theFilmBuildsAtAFixedRateWhileTheSupplyKeepsUp() {
		Lubrication oil = new Lubrication(0);
		oil.update(1, 1, 4, PLENTY);
		assertEquals(0.25, oil.film(), 1e-12);
		oil.update(1, 2, 4, PLENTY);
		assertEquals(0.75, oil.film(), 1e-12);
		oil.update(1, 10, 4, PLENTY);
		assertEquals(1, oil.film(), 1e-12);
	}

	@Test
	void withNoOilTheFilmThinsWithEachRevolution() {
		Lubrication oil = new Lubrication(1);
		oil.update(100, 1, 4, OilSupply.NONE);
		assertEquals(0.8, oil.film(), 1e-12);
		oil.update(1000, 1, 4, OilSupply.NONE);
		assertEquals(0, oil.film(), 0);
	}

	@Test
	void aSupplyThatHalfKeepsUpHoldsTheFilmAtHalf() {
		Lubrication oil = new Lubrication(1);
		oil.update(5000, 1, 4, mb -> mb / 2);
		assertEquals(0.5, oil.film(), 1e-12);
		oil.update(1, 100, 4, mb -> mb / 2);
		assertEquals(0.5, oil.film(), 1e-12);
	}

	@Test
	void anEngineThatDidNotTurnOrHasNoPartsUsesNoOilAndChangesNothing() {
		int[] draws = {0};
		OilSupply counting = mb -> {
			draws[0]++;
			return mb;
		};
		Lubrication oil = new Lubrication(0.4);
		oil.update(0, 1, 4, counting);
		oil.update(10, 1, 0, counting);
		assertEquals(0, draws[0]);
		assertEquals(0.4, oil.film(), 0);
	}

	@Test
	void theOilDrawnIsWhatTheTurningNeeds() {
		double[] asked = {0};
		Lubrication oil = new Lubrication(1);
		oil.update(50, 1, 8, mb -> {
			asked[0] = mb;
			return mb;
		});
		assertEquals(8 * Lubrication.OIL_PER_PART_REV * 50, asked[0], 1e-12);
	}

	@Test
	void freshOilIntoADryEngineIsAFreshChargeOfCleanOil() {
		Lubrication oil = new Lubrication(0);
		oil.setCondition(0.1);
		oil.setDilution(0.4);
		oil.update(10, 1, 4, PLENTY);
		assertEquals(1, oil.condition(), 0);
		assertEquals(0, oil.dilution(), 0);
	}

	@Test
	void oilThatKeepsTheFilmUpIsNotMadeFreshByTheTopUp() {
		Lubrication oil = new Lubrication(0.8);
		oil.setCondition(0.4);
		oil.setDilution(0.2);
		oil.update(10, 1, 4, PLENTY);
		assertEquals(0.4, oil.condition(), 0);
		assertEquals(0.2, oil.dilution(), 0);
	}

	@Test
	void ageingSpendsConditionByRevolutionsAndStress() {
		Lubrication oil = new Lubrication(1);
		oil.age(1500, 1);
		assertEquals(0.5, oil.condition(), 1e-12);
		oil.age(500, 2);
		assertEquals(0.5 - 1000 * Lubrication.AGE_PER_REV, oil.condition(), 1e-12);
		oil.age(1e9, 1);
		assertEquals(0, oil.condition(), 0);
	}

	@Test
	void stressRisesWithHeatAndDilutionAndFallsWithAFilter() {
		Lubrication oil = new Lubrication(1);
		assertEquals(1, oil.stress(Thermal.NORMAL, false), 1e-12);
		assertEquals(1, oil.stress(0, false), 1e-12, "cold oil is not stressed");
		assertEquals(2.5, oil.stress(Thermal.NORMAL + 1, false), 1e-12);
		oil.setDilution(0.5);
		assertEquals(2, oil.stress(Thermal.NORMAL, false), 1e-12);
		assertEquals(2 * Lubrication.FILTER_AGEING, oil.stress(Thermal.NORMAL, true), 1e-12);
	}

	@Test
	void refreshingNothingChangesNothing() {
		Lubrication oil = new Lubrication(1);
		oil.setCondition(0.3);
		oil.setDilution(0.3);
		oil.refresh(0);
		oil.refresh(-10);
		assertEquals(0.3, oil.condition(), 0);
		assertEquals(0.3, oil.dilution(), 0);
	}

	@Test
	void refreshingAChargeBringsBackMostOfTheConditionAndTakesFuelOut() {
		Lubrication oil = new Lubrication(1);
		oil.setCondition(0);
		oil.setDilution(0.4);
		oil.refresh(Lubrication.CHARGE_VOLUME);
		double share = 1 - Math.exp(-1);
		assertEquals(share, oil.condition(), 1e-12);
		assertEquals(0.4 * (1 - share), oil.dilution(), 1e-12);
	}

	@Test
	void dragIsNothingForNewOilAndAQuarterForSpentOil() {
		Lubrication oil = new Lubrication(1);
		assertEquals(1, oil.drag(), 0);
		oil.setCondition(0);
		assertEquals(1 + Lubrication.MAX_AGED_FRICTION, oil.drag(), 1e-12);
	}
}
