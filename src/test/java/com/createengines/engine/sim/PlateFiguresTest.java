package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a Maker's Plate takes from a curve, and how it keeps it. */
class PlateFiguresTest {

	private static final float[] RPM = {32, 64, 96, 128, 160};
	private static final float[] TORQUE = {10, 18, 20, 16, 9};

	@Test
	void peaksAreFoundWithTheirSpeeds() {
		PlateFigures figures = PlateFigures.of(RPM, TORQUE);
		assertEquals(20, figures.peakTorque());
		assertEquals(96, figures.torqueRpm());
		// Power is torque times speed: 10*32, 18*64, 20*96, 16*128, 9*160 = 320, 1152, 1920, 2048, 1440
		assertEquals(2048, figures.peakPower());
		assertEquals(128, figures.powerRpm());
		assertTrue(figures.stamped());
	}

	@Test
	void curvesNotWorthStampingAreTurnedAway() {
		assertNull(PlateFigures.of(null, TORQUE));
		assertNull(PlateFigures.of(new float[0], new float[0]));
		assertNull(PlateFigures.of(RPM, new float[] {1, 2}), "ragged");
		assertNull(PlateFigures.of(RPM, new float[5]), "no torque at all");
		assertNull(PlateFigures.of(new float[] {0, 0}, new float[] {5, 5}), "no speed, so no power");
		assertNull(PlateFigures.of(new float[] {1, Float.NaN}, new float[] {5, 5}));
	}

	@Test
	void aBlankPlateIsNotStamped() {
		assertFalse(PlateFigures.NONE.stamped());
	}

	@Test
	void figuresReadBackAsTheyWerePacked() {
		PlateFigures figures = PlateFigures.of(RPM, TORQUE);
		assertEquals(figures, PlateFigures.unpack(figures.pack()));
	}

	@Test
	void damagedStorageReadsAsBlankRatherThanFailing() {
		assertEquals(PlateFigures.NONE, PlateFigures.unpack(null));
		assertEquals(PlateFigures.NONE, PlateFigures.unpack(new int[2]));
		assertEquals(0, PlateFigures.unpack(new int[] {-5, -5, -5, -5}).peakPower(), "negative figures are clamped");
	}

	@Test
	void namesAreCleanedAndCut() {
		assertEquals("Old Faithful", PlateFigures.cleanName("  Old \n\t Faithful  ", 24));
		assertEquals("", PlateFigures.cleanName(null, 24));
		assertEquals("", PlateFigures.cleanName("   ", 24));
		assertEquals("abcde", PlateFigures.cleanName("abcdefgh", 5));
		assertEquals("ab", PlateFigures.cleanName("ab   ", 5), "no trailing blank is left behind by the cut");
	}
}
