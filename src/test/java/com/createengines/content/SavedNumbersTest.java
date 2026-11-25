package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A number a block entity reads back from its save is always finite, and a sound one is read exactly as it was. Reading from a real
 * save is covered by the hostile-number GameTests in {@code SmokeAuditGameTests}; the unit tests cannot load Minecraft's classes.
 */
class SavedNumbersTest {

	@Test
	void aSoundNumberIsKeptExactly() {
		assertEquals(12.5f, SavedNumbers.finite(12.5f, 0));
		assertEquals(Float.MAX_VALUE, SavedNumbers.finite(Float.MAX_VALUE, 0), "large but finite is not this helper's to judge");
		assertEquals(-Float.MAX_VALUE, SavedNumbers.finite(-Float.MAX_VALUE, 0));
		assertEquals(0f, SavedNumbers.finite(0f, 1));
	}

	@Test
	void notANumberAndInfinitiesBecomeTheFallback() {
		assertEquals(1f, SavedNumbers.finite(Float.NaN, 1));
		assertEquals(0f, SavedNumbers.finite(Float.POSITIVE_INFINITY, 0));
		assertEquals(0f, SavedNumbers.finite(Float.NEGATIVE_INFINITY, 0));
	}
}
