package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoteMathTest {

	/** A tenth of the smallest step of the note's 16 bit samples, which run to 30000. */
	private static final double CLOSE = 0.1 / 30000;

	@Test
	void theSoftClipTableIsCloseToTheCurve() {
		for (int i = -1_000_000; i <= 1_000_000; i++) {
			float x = i / 83_333f;
			assertEquals(Math.tanh(x), NoteMath.tanh(x), CLOSE, "at " + x);
		}
	}

	@Test
	void theSoftClipHoldsAtTheEndsAndKeepsWhatIsNotANumber() {
		assertEquals(1f, NoteMath.tanh(1e30f));
		assertEquals(-1f, NoteMath.tanh(Float.NEGATIVE_INFINITY));
		assertEquals(0f, NoteMath.tanh(0f));
		assertTrue(Float.isNaN(NoteMath.tanh(Float.NaN)), "the note resets itself on a NaN, so it must get through");
	}

	@Test
	void theSineTableIsCloseToTheSine() {
		for (int i = 0; i <= 1_000_000; i++) {
			double radians = i * (6 * Math.PI / 1_000_000);
			assertEquals(Math.sin(radians), NoteMath.sin(radians), CLOSE, "at " + radians);
		}
		assertTrue(Float.isNaN(NoteMath.sin(Double.NaN)));
	}
}
