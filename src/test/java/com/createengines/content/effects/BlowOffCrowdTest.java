package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlowOffCrowdTest {

	@Test
	void eightTurbosLiftingTogetherComeToLittleMoreThanOne() {
		BlowOffCrowd crowd = new BlowOffCrowd();
		double power = 0;
		int heard = 0;
		for (int i = 0; i < 8; i++) {
			float share = crowd.share(100, i, 0, 0);
			power += share * share;
			if (share > 0)
				heard++;
		}
		assertEquals(3, heard);
		// Under 2 dB over one on its own, where eight in full would be 9 dB over at the least
		assertTrue(10 * Math.log10(power) < 2, "Together " + 10 * Math.log10(power) + " dB over one");
	}

	@Test
	void theFirstIsHeardInFullAndTheNextOnesMoreSoftly() {
		BlowOffCrowd crowd = new BlowOffCrowd();
		float first = crowd.share(5, 0, 0, 0);
		float second = crowd.share(6, 0, 0, 0);
		float third = crowd.share(7, 0, 0, 0);
		assertEquals(1, first, 0);
		assertTrue(second < first && third < second && third > 0);
	}

	@Test
	void oneFarOffOrAMomentLaterIsHeardInFull() {
		BlowOffCrowd crowd = new BlowOffCrowd();
		for (int i = 0; i < 5; i++)
			crowd.share(100, 0, 0, 0);
		assertEquals(1, crowd.share(100, BlowOffCrowd.NEAR, 0, 0), 0);
		assertEquals(0, crowd.share(100 + BlowOffCrowd.WINDOW - 1, 0, 0, 0), 0);
		assertEquals(1, crowd.share(100 + BlowOffCrowd.WINDOW, 0, 0, 0), 0);
	}

	@Test
	void aClockThatGoesBackStartsAfresh() {
		BlowOffCrowd crowd = new BlowOffCrowd();
		for (int i = 0; i < 5; i++)
			crowd.share(10_000, 0, 0, 0);
		assertEquals(1, crowd.share(3, 0, 0, 0), 0);
	}
}
