package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The wear total a Black Box and a Display Link show for a whole build: a plain mean, and nothing at all when there is nothing to show. */
class WearTotalTest {

	@Test
	void theTotalIsThePlainMeanOfEveryPart() {
		assertEquals(0.3f, WearTotal.mean(0.6, 0.3, 0.0), 1e-6);
		assertEquals(0.5f, WearTotal.mean(0.5), 1e-6);
		assertEquals(1f, WearTotal.mean(1, 1, 1), 0);
		assertEquals(0f, WearTotal.mean(0, 0), 0);
	}

	@Test
	void everyPartCountsTheSameWhateverItIs() {
		// Not weighted toward the worst part: one spent part among four new ones is a quarter, not a failure
		assertEquals(0.25f, WearTotal.mean(1, 0, 0, 0), 1e-6);
	}

	@Test
	void noPartsMeansNoFigureAtAll() {
		assertEquals(WearTotal.NONE, WearTotal.mean());
		assertEquals(-1, WearTotal.percent(WearTotal.NONE));
	}

	@Test
	void nonsenseIsNewAndTooMuchIsSpent() {
		assertEquals(0f, WearTotal.mean(Double.NaN), 0);
		assertEquals(0f, WearTotal.mean(-4), 0);
		assertEquals(1f, WearTotal.mean(9, Double.POSITIVE_INFINITY), 0);
		assertEquals(0.5f, WearTotal.mean(Double.NaN, 1), 1e-6);
	}

	@Test
	void thePercentIsWholeAndWithinZeroToAHundred() {
		assertEquals(0, WearTotal.percent(0f));
		assertEquals(38, WearTotal.percent(0.375f));
		assertEquals(100, WearTotal.percent(1f));
		assertEquals(100, WearTotal.percent(7f));
		assertEquals(-1, WearTotal.percent(Float.NaN));
		assertEquals(-1, WearTotal.percent(-0.5f));
	}
}
