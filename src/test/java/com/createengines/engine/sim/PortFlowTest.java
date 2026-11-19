package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Port count in 2.0: every opening helps, each less than the last, and a tidy layout beats a crowd. */
class PortFlowTest {

	@Test
	void noOpeningsOrNoHeadsMeansNoBreathing() {
		assertEquals(0, PortFlow.factor(0, 4));
		assertEquals(0, PortFlow.factor(3, 0));
		assertEquals(0, PortFlow.factor(-1, 4));
		assertEquals(0, PortFlow.factor(Double.NaN, 4));
	}

	@Test
	void oneOpeningPerHeadIsCloseToFullAndTwoIsNearlyAll() {
		assertTrue(PortFlow.factor(6, 6) > 0.89 && PortFlow.factor(6, 6) < 0.92);
		assertTrue(PortFlow.factor(12, 6) > 0.98);
	}

	@Test
	void everyExtraOpeningHelpsButLessThanTheLast() {
		double previous = 0;
		double previousGain = Double.MAX_VALUE;
		for (int openings = 1; openings <= 24; openings++) {
			double factor = PortFlow.factor(openings, 6);
			double gain = factor - previous;
			assertTrue(factor <= 1, "never more than full: " + factor);
			if (factor < 1) {
				assertTrue(gain > 0, "opening " + openings + " still adds something");
				assertTrue(gain < previousGain, "opening " + openings + " adds less than the one before");
			}
			previous = factor;
			previousGain = gain;
		}
	}

	@Test
	void oneOpeningForTwoHeadsIsNoLongerFullBreathing() {
		assertTrue(PortFlow.factor(1, 2) < 0.7, "1.x counted this as 1.0");
		assertTrue(PortFlow.factor(1, 2) > 0.6);
	}

	@Test
	void aForestOfOpeningsIsFullAndNeverAboveIt() {
		assertEquals(1, PortFlow.factor(1000, 2));
		assertEquals(1, PortFlow.factor(Double.POSITIVE_INFINITY, 2));
	}

	@Test
	void crowdedOpeningsCountForHalf() {
		assertEquals(4, PortFlow.effective(4, 4, 0));
		assertEquals(2, PortFlow.effective(4, 4, 4));
		assertEquals(3, PortFlow.effective(4, 4, 2));
		assertTrue(PortFlow.factor(PortFlow.effective(4, 4, 4), 4) < PortFlow.factor(4, 4));
	}

	@Test
	void crowdingNeverGoesNegativeOrPastTheOpeningsThereAre() {
		assertEquals(0, PortFlow.effective(0, 4, 4));
		assertEquals(0, PortFlow.effective(3, 0, 0));
		assertEquals(2, PortFlow.effective(4, 4, 99));
		assertEquals(4, PortFlow.effective(4, 4, -5));
	}
}
