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
	void onlyThePartsFilledInAreCounted() {
		double[] room = {0.6, 0.2, 9, 9};
		assertEquals(0.4f, WearTotal.meanOfFirst(room, 2), 1e-6);
		assertEquals(WearTotal.NONE, WearTotal.meanOfFirst(room, 0));
		assertEquals(WearTotal.NONE, WearTotal.meanOfFirst(new double[0], 0));
	}

	/** How many times a running engine reads its parts over {@code ticks} ticks, wearing every 20th, with every reading {@code total}. */
	private static int readingsOver(WearTotal.Held held, int ticks, float total) {
		int readings = 0;
		for (int tick = 1; tick <= ticks; tick++)
			if (held.wanted(tick % 20 == 0)) {
				held.read(total);
				readings++;
			}
		return readings;
	}

	@Test
	void theHeldTotalIsReadOnTheFirstTickAndThenOnceAStep() {
		WearTotal.Held held = new WearTotal.Held();
		assertEquals(WearTotal.NONE, held.total());
		assertEquals(1 + 5, readingsOver(held, 100, 0.25f));
		assertEquals(0.25f, held.total(), 0);
	}

	@Test
	void anEngineWithNothingLoadedIsNotReadAgainEveryTick() {
		WearTotal.Held held = new WearTotal.Held();
		assertEquals(1 + 5, readingsOver(held, 100, WearTotal.NONE));
		assertEquals(WearTotal.NONE, held.total());
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
