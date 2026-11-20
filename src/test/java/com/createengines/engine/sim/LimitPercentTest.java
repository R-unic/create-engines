package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What a compressor's goggles say about where it works against its boost limit ({@link Turbocharger#limitPercent}). */
class LimitPercentTest {

	@Test
	void aCompressorReadsItsBoostAsAShareOfTheLimit() {
		assertEquals(0, Turbocharger.limitPercent(0, 0.6));
		assertEquals(50, Turbocharger.limitPercent(0.3, 0.6));
		assertEquals(75, Turbocharger.limitPercent(0.45, 0.6));
		assertEquals(100, Turbocharger.limitPercent(0.6, 0.6), "at the limit");
	}

	@Test
	void beyondTheLimitStillReadsOneHundred() {
		assertEquals(100, Turbocharger.limitPercent(0.9, 0.6));
		assertEquals(100, Turbocharger.limitPercent(1e9, 0.1));
	}

	@Test
	void aLimitThatIsNotAboveZeroReadsZeroWhateverTheBoost() {
		assertEquals(0, Turbocharger.limitPercent(0.5, 0), "a limit of nothing is not a divide by zero");
		assertEquals(0, Turbocharger.limitPercent(0.5, -1));
		assertEquals(0, Turbocharger.limitPercent(0.5, Double.NaN));
	}

	@Test
	void aBoostThatIsNegativeOrNotANumberReadsZero() {
		assertEquals(0, Turbocharger.limitPercent(-0.3, 0.6));
		assertEquals(0, Turbocharger.limitPercent(Double.NaN, 0.6));
	}

	@Test
	void theReadingRoundsToTheNearestWholePercent() {
		assertEquals(33, Turbocharger.limitPercent(0.2, 0.6));
		assertEquals(67, Turbocharger.limitPercent(0.4, 0.6));
		assertEquals(1, Turbocharger.limitPercent(0.006, 0.6));
		assertEquals(0, Turbocharger.limitPercent(0.0029, 0.6), "under half a percent rounds to none");
	}
}
