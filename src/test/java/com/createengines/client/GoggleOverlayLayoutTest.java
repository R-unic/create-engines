package com.createengines.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoggleOverlayLayoutTest {

	@Test
	void shortReadoutAtScreenCentreIsUntouched() {
		int h = 240, th = 40;
		int y = h / 2;
		assertEquals(Math.min(y, h - th - 20), GoggleOverlayLayout.clampTop(y, h - th - 20));
	}

	@Test
	void tallReadoutEndsAboveTheHungerAndAirBubbleRows() {
		int h = 240, th = 150;
		int top = GoggleOverlayLayout.clampTop(h / 2, h - th - 20);
		assertEquals(h - th - 56, top);
		// readout bottom must sit above the air bubbles, 49 pixels up
		org.junit.jupiter.api.Assertions.assertTrue(top + th <= h - 49);
	}

	@Test
	void neverLowerThanCreatesOwnClampWouldGive() {
		for (int h = 100; h <= 540; h += 20)
			for (int th = 10; th <= 300; th += 10)
				for (int y = 0; y <= h; y += 20) {
					int create = Math.min(y, h - th - 20);
					assertEquals(true, GoggleOverlayLayout.clampTop(y, h - th - 20) <= create);
				}
	}

	@Test
	void readoutTooTallForTheRaisedMarginFallsBackToCreate() {
		int h = 240, th = 215; // raised limit would be -31: off the top
		assertEquals(Math.min(h / 2, h - th - 20), GoggleOverlayLayout.clampTop(h / 2, h - th - 20));
	}
}
