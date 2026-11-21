package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmissionsTest {

	@Test
	void carbonFollowsTheFuelBurned() {
		Emissions emissions = new Emissions();
		emissions.add(100, 0, 1, 1);
		assertEquals((int) (100 * Emissions.CARBON_PER_MB), emissions.takeCarbon());
	}

	@Test
	void sulfurFollowsHowDirtyTheFuelIs() {
		Emissions crude = new Emissions();
		Emissions clean = new Emissions();
		crude.add(100, 0, 1, 1);
		clean.add(100, 0, 0, 1);
		assertTrue(crude.takeSulfur() > 0);
		assertEquals(0, clean.takeSulfur(), "ethanol is not sulfurous");
		assertTrue(clean.takeCarbon() > 0, "but it still makes carbon");
	}

	@Test
	void richRunsThatLetSootEscapeMakeMoreCarbon() {
		Emissions tidy = new Emissions();
		Emissions sooty = new Emissions();
		tidy.add(20, 0, 1, 1);
		sooty.add(20, 5, 1, 1);
		assertTrue(sooty.takeCarbon() > tidy.takeCarbon());
	}

	@Test
	void aTrapThatCatchesTheSootTakesTheExtraCarbonOffButNotTheRest() {
		double burned = 20;
		double made = 5;
		Emissions uncaught = new Emissions();
		Emissions trapped = new Emissions();
		Emissions bare = new Emissions();
		uncaught.add(burned, SootCollector.escaped(made, 0), 1, 1);
		trapped.add(burned, SootCollector.escaped(made, made), 1, 1);
		bare.add(burned, 0, 1, 1);
		assertEquals((int) ((burned + made * Emissions.SOOT_CARBON) * Emissions.CARBON_PER_MB), uncaught.takeCarbon());
		assertEquals(bare.takeCarbon(), trapped.takeCarbon(), "a trap that catches it all leaves only the carbon of burning");
		assertEquals(uncaught.takeSulfur(), trapped.takeSulfur(), "the trap does not touch sulfur");
	}

	@Test
	void sulfurFollowsTheFuelsOwnValueNotItsSoot() {
		Emissions lowSulfur = new Emissions();
		Emissions highSulfur = new Emissions();
		lowSulfur.add(40, 0, new FuelSpec(10, 2, 1.2, 1, 0, 1, 2, 0.2).sulfur(), 1);
		highSulfur.add(40, 0, new FuelSpec(10, 2, 1.2, 1, 0, 0, 2, 2).sulfur(), 1);
		assertEquals(2, lowSulfur.takeSulfur());
		assertEquals(20, highSulfur.takeSulfur(), "a clean-burning fuel can still be sulfurous");
	}

	@Test
	void aSlowEngineStillReleasesAStepNowAndThen() {
		Emissions emissions = new Emissions();
		int steps = 0;
		for (int round = 0; round < 1000; round++) {
			emissions.add(1, 0, 1, 1);
			steps += emissions.takeCarbon();
		}
		assertEquals(1000 * Emissions.CARBON_PER_MB, steps, 1.0);
	}

	@Test
	void noRateNoGasAndHostileNumbersAreHarmless() {
		Emissions emissions = new Emissions();
		emissions.add(1e9, 1e9, 1, 0);
		assertEquals(0, emissions.takeCarbon());
		emissions.add(Double.NaN, -5, Double.NaN, 1);
		emissions.add(-1, -1, -1, 1);
		assertEquals(0, emissions.takeCarbon());
		emissions.add(Double.POSITIVE_INFINITY, 0, 1, 1);
		assertTrue(emissions.takeCarbon() <= Emissions.MAX_HELD);
	}

	@Test
	void whatIsHeldIsCappedAndCanBeDropped() {
		Emissions emissions = new Emissions();
		for (int i = 0; i < 100; i++)
			emissions.add(1e6, 0, 1, 10);
		assertEquals((int) Emissions.MAX_HELD, emissions.takeCarbon());
		emissions.add(1e6, 0, 1, 1);
		emissions.clear();
		assertEquals(0, emissions.takeCarbon());
		assertEquals(0, emissions.takeSulfur());
	}

	@Test
	void theAmountsAreExactlyTheConstantsTimesTheFuel() {
		Emissions burned = new Emissions();
		burned.add(6, 0, 1, 1);
		assertEquals(3, burned.takeCarbon());
		assertEquals(1, burned.takeSulfur(), "6 x 0.25 is 1.5, and the half stays held");
		Emissions soot = new Emissions();
		soot.add(0, 2.2, 1, 1);
		assertEquals(4, soot.takeCarbon(), "unburned fuel counts SOOT_CARBON times as much carbon, 2.2 x 4 x 0.5 = 4.4");
		assertEquals(0, soot.takeSulfur(), "and no sulfur: only burned fuel carries it");
		Emissions dirty = new Emissions();
		dirty.add(12, 0, 2, 1);
		assertEquals(6, dirty.takeSulfur(), "sulfur scales with the soot factor");
	}

	@Test
	void theRateScalesBothGases() {
		Emissions half = new Emissions();
		half.add(12, 0, 1, 0.5);
		assertEquals(3, half.takeCarbon());
		assertEquals(1, half.takeSulfur());
		Emissions double_ = new Emissions();
		double_.add(12, 0, 1, 2);
		assertEquals(12, double_.takeCarbon());
		assertEquals(6, double_.takeSulfur());
	}

	@Test
	void eachGasHasItsOwnRateOnTopOfTheCommonOne() {
		Emissions noCarbon = new Emissions();
		noCarbon.add(12, 0, 1, 1, 0, 1);
		assertEquals(0, noCarbon.takeCarbon());
		assertEquals(3, noCarbon.takeSulfur());
		Emissions noSulfur = new Emissions();
		noSulfur.add(12, 0, 1, 1, 1, 0);
		assertEquals(6, noSulfur.takeCarbon());
		assertEquals(0, noSulfur.takeSulfur());
		Emissions stacked = new Emissions();
		stacked.add(12, 0, 1, 2, 2, 0.5);
		assertEquals(24, stacked.takeCarbon(), "common 2 times carbon 2");
		assertEquals(3, stacked.takeSulfur(), "common 2 times sulfur 0.5");
		Emissions defaults = new Emissions();
		defaults.add(12, 0, 1, 1, 1, 1);
		assertEquals(6, defaults.takeCarbon(), "rates of 1 leave the old numbers");
		assertEquals(3, defaults.takeSulfur());
	}

	@Test
	void hostileGasRatesAreHeldInRange() {
		Emissions emissions = new Emissions();
		emissions.add(1, 1, 1, 1, Double.NaN, 1);
		emissions.add(0, 0, 1, 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
		assertEquals(0, emissions.takeCarbon());
		emissions.add(1, 0, 1, 1, Double.POSITIVE_INFINITY, -5);
		assertTrue(emissions.takeCarbon() <= Emissions.MAX_HELD);
		assertEquals(0, emissions.takeSulfur());
	}

	@Test
	void whatIsNotWholeStaysHeldAndIsNeverReleasedTwice() {
		Emissions emissions = new Emissions();
		emissions.add(6, 0, 1, 1);
		assertEquals(1, emissions.takeSulfur());
		assertEquals(0, emissions.takeSulfur(), "taking it again gives nothing");
		emissions.add(2.4, 0, 1, 1);
		assertEquals(1, emissions.takeSulfur(), "the half held back plus 0.6 is 1.1");
		assertEquals(0, emissions.takeSulfur());
		assertEquals(3 + 1, emissions.takeCarbon(), "carbon held 3.0 and 1.2 more is 4.2, taken once");
		assertEquals(0, emissions.takeCarbon());
	}

	@Test
	void zeroFuelOrAnInfiniteRateNeverMakesAnythingNonsensical() {
		Emissions emissions = new Emissions();
		emissions.add(0, 0, 1, Double.POSITIVE_INFINITY);
		assertEquals(0, emissions.takeCarbon());
		emissions.add(1, 0, 1, Double.POSITIVE_INFINITY);
		assertTrue(emissions.takeCarbon() <= Emissions.MAX_HELD);
	}

	@Test
	void aCeilingLimitsWhatOneEngineAddsPerMinute() {
		Emissions capped = new Emissions();
		// 60 mB would make 30 carbon and 15 sulfur; a ceiling of 6 a minute over 30 seconds allows 3 of each
		capped.add(60, 0, 1, 1, 1, 1, 6, 30);
		assertEquals(3, capped.takeCarbon());
		assertEquals(3, capped.takeSulfur());
	}

	@Test
	void noCeilingLeavesTheNumbersAlone() {
		Emissions free = new Emissions();
		free.add(12, 0, 1, 1, 1, 1, 0, 30);
		assertEquals(6, free.takeCarbon());
		assertEquals(3, free.takeSulfur());
	}

	@Test
	void aCeilingNeverRaisesWhatAnEngineMakes() {
		Emissions slow = new Emissions();
		slow.add(2, 0, 1, 1, 1, 1, 600, 60);
		assertEquals(1, slow.takeCarbon());
		assertEquals(0, slow.takeSulfur());
	}
}
