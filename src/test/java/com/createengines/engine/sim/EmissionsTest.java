package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmissionsTest {

	@Test
	void carbonFollowsTheFuelBurned() {
		Emissions emissions = new Emissions();
		emissions.add(1000, 0, 1, 1);
		assertEquals((int) (1000 * Emissions.CARBON_PER_MB), emissions.takeCarbon());
	}

	@Test
	void sulfurFollowsHowDirtyTheFuelIs() {
		Emissions crude = new Emissions();
		Emissions clean = new Emissions();
		crude.add(1000, 0, 1, 1);
		clean.add(1000, 0, 0, 1);
		assertTrue(crude.takeSulfur() > 0);
		assertEquals(0, clean.takeSulfur(), "ethanol is not sulfurous");
		assertTrue(clean.takeCarbon() > 0, "but it still makes carbon");
	}

	@Test
	void richRunsThatLetSootEscapeMakeMoreCarbon() {
		Emissions tidy = new Emissions();
		Emissions sooty = new Emissions();
		tidy.add(500, 0, 1, 1);
		sooty.add(500, 100, 1, 1);
		assertTrue(sooty.takeCarbon() > tidy.takeCarbon());
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
		burned.add(150, 0, 1, 1);
		assertEquals(3, burned.takeCarbon());
		assertEquals(1, burned.takeSulfur());
		Emissions soot = new Emissions();
		soot.add(0, 55, 1, 1);
		assertEquals(4, soot.takeCarbon(), "unburned fuel counts SOOT_CARBON times as much carbon, 55 x 4 x 0.02 = 4.4");
		assertEquals(0, soot.takeSulfur(), "and no sulfur: only burned fuel carries it");
		Emissions dirty = new Emissions();
		dirty.add(300, 0, 2, 1);
		assertEquals(6, dirty.takeSulfur(), "sulfur scales with the soot factor");
	}

	@Test
	void theRateScalesBothGases() {
		Emissions half = new Emissions();
		half.add(300, 0, 1, 0.5);
		assertEquals(3, half.takeCarbon());
		assertEquals(1, half.takeSulfur());
		Emissions double_ = new Emissions();
		double_.add(300, 0, 1, 2);
		assertEquals(12, double_.takeCarbon());
		assertEquals(6, double_.takeSulfur());
	}

	@Test
	void whatIsNotWholeStaysHeldAndIsNeverReleasedTwice() {
		Emissions emissions = new Emissions();
		emissions.add(150, 0, 1, 1);
		assertEquals(1, emissions.takeSulfur());
		assertEquals(0, emissions.takeSulfur(), "taking it again gives nothing");
		emissions.add(60, 0, 1, 1);
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
}
