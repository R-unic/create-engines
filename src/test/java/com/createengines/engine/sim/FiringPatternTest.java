package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FiringPatternTest {

	@Test
	void noIntervalsSayNothing() {
		assertEquals("", FiringPattern.describe(new float[0]));
	}

	@Test
	void aShortUnevenListIsGivenInFull() {
		assertEquals("90° / 270° / 180° / 180°", FiringPattern.describe(new float[] { 90, 270, 180, 180 }));
		assertEquals("180° / 540°", FiringPattern.describe(new float[] { 180, 540 }));
	}

	@Test
	void anEvenOrderIsOneStatementWhateverTheCount() {
		assertEquals("120° apart, all even", FiringPattern.describe(new float[] { 120, 120, 120 }));
		float[] many = new float[48];
		java.util.Arrays.fill(many, 15f);
		assertEquals("15° apart, all even", FiringPattern.describe(many));
	}

	@Test
	void aLongUnevenListGivesItsRange() {
		// The first intervals of the 48-cylinder rotary engine that overflowed the screen
		float[] intervals = { 15, 15, 15, 30, 15, 30, 15, 30, 720, 15, 30, 15, 30, 30, 30, 45 };
		assertEquals("uneven, 15° to 720°", FiringPattern.describe(intervals));
	}

	@Test
	void theListLimitIsWhereTheListStops() {
		float[] atLimit = new float[FiringPattern.MAX_LISTED];
		float[] overLimit = new float[FiringPattern.MAX_LISTED + 1];
		for (int i = 0; i < atLimit.length; i++)
			atLimit[i] = 10 + i * 10;
		for (int i = 0; i < overLimit.length; i++)
			overLimit[i] = 10 + i * 10;
		assertTrue(FiringPattern.describe(atLimit).contains(" / "));
		assertEquals("uneven, 10° to 70°", FiringPattern.describe(overLimit));
	}

	@Test
	void theLineStaysShortForAnyNumberOfCylinders() {
		java.util.Random random = new java.util.Random(7);
		for (int count : new int[] { 1, 2, 7, 48, 500, 5000 }) {
			float[] intervals = new float[count];
			for (int i = 0; i < count; i++)
				intervals[i] = 1 + random.nextInt(720);
			assertTrue(FiringPattern.describe(intervals).length() <= 60, "length for " + count + " cylinders");
		}
	}

	@Test
	void evenToleratesAWholeDegreeOfDrift() {
		assertTrue(FiringPattern.even(new float[] { 90f, 90.9f, 89.2f }));
		assertFalse(FiringPattern.even(new float[] { 90f, 91f }));
	}
}
