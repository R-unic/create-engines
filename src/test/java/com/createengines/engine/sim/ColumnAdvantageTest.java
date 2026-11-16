package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColumnAdvantageTest {

	@Test
	void dieselColumnGivesAboutTenPercentMore() {
		assertEquals(10, ColumnAdvantage.percentMore(1.1, 1.0));
	}

	@Test
	void ethanolColumnAgainstTheWeakenedBasinIsAboutAFifthMore() {
		assertEquals(21, ColumnAdvantage.percentMore(170.0 / 1000, 35.0 / 250));
		assertEquals(21, ColumnAdvantage.percentMore(340.0 / 1000, 70.0 / 250));
	}

	@Test
	void noGainOrALossSaysNothing() {
		assertEquals(0, ColumnAdvantage.percentMore(1.0, 1.0));
		assertEquals(0, ColumnAdvantage.percentMore(0.9, 1.0));
		assertEquals(0, ColumnAdvantage.percentMore(1.004, 1.0));
	}

	@Test
	void hostileNumbersSayNothingAndNeverThrow() {
		assertEquals(0, ColumnAdvantage.percentMore(Double.NaN, 1));
		assertEquals(0, ColumnAdvantage.percentMore(1, Double.NaN));
		assertEquals(0, ColumnAdvantage.percentMore(1, 0));
		assertEquals(0, ColumnAdvantage.percentMore(0, 1));
		assertEquals(0, ColumnAdvantage.percentMore(-1, -1));
		assertEquals(0, ColumnAdvantage.percentMore(Double.POSITIVE_INFINITY, 1));
		assertEquals(0, ColumnAdvantage.percentMore(1, Double.POSITIVE_INFINITY));
	}

	@Test
	void aHugeGainIsCapped() {
		assertEquals(ColumnAdvantage.MAX_PERCENT, ColumnAdvantage.percentMore(1e9, 1e-9));
	}
}
