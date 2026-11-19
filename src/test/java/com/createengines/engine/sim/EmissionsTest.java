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
}
