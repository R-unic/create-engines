package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopSanityTest {

	@Test
	void everyHostileValueComesBackUsable() {
		float[] hostile = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE, 0, -0f, 1e-30f};
		for (float value : hostile) {
			float result = LoopSanity.finite(value, 0.5f, 2f, 1f);
			assertTrue(result >= 0.5f && result <= 2f, value + " gave " + result);
		}
	}

	@Test
	void nanTakesTheFallbackAndOrdinaryValuesPassUnchanged() {
		assertEquals(1f, LoopSanity.finite(Float.NaN, 0.5f, 2f, 1f));
		assertEquals(0.5f, LoopSanity.finite(Float.NaN, 0.5f, 2f, -4f));
		assertEquals(1.25f, LoopSanity.finite(1.25f, 0.5f, 2f, 1f));
		assertEquals(2f, LoopSanity.finite(Float.POSITIVE_INFINITY, 0.5f, 2f, 1f));
	}

	@Test
	void rangeIsInclusiveAndRejectsNanAndInfinity() {
		assertTrue(LoopSanity.inRange(0));
		assertTrue(LoopSanity.inRange(LoopSanity.RANGE * LoopSanity.RANGE));
		assertFalse(LoopSanity.inRange(LoopSanity.RANGE * LoopSanity.RANGE + 1));
		assertFalse(LoopSanity.inRange(Double.NaN));
		assertFalse(LoopSanity.inRange(Double.POSITIVE_INFINITY));
	}
}
