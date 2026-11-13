package com.createengines.content.transmission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Thirteen forward gears from twelve to one, neutral, reverse, and a panel setting past them for auto. */
class GearsTest {

	@Test
	void firstIsTwelveToOneAndThirteenthIsDirect() {
		assertEquals(12, Gears.ratio(Gears.FIRST), 1e-9);
		assertEquals(1, Gears.ratio(Gears.TOP), 1e-9);
	}

	@Test
	void everyGearIsTallerThanTheLastByAboutAQuarter() {
		for (int gear = Gears.FIRST + 1; gear <= Gears.TOP; gear++) {
			double step = Gears.ratio(gear - 1) / Gears.ratio(gear);
			assertTrue(step > 1.18 && step < 1.28, "Gear " + gear + " steps by " + step);
		}
	}

	@Test
	void neutralPassesNothingAndReverseTurnsBack() {
		assertEquals(0, Gears.speedModifier(Gears.NEUTRAL));
		assertTrue(Gears.speedModifier(Gears.REVERSE) < 0);
		assertEquals(1f, Gears.speedModifier(Gears.TOP), 1e-6);
		assertEquals(1 / 12f, Gears.speedModifier(Gears.FIRST), 1e-4);
	}

	@Test
	void autoIsASettingAndNotAPosition() {
		assertEquals("A", Gears.label(Gears.AUTO));
		assertEquals(Gears.POSITIONS - 1, Gears.clamp(Gears.AUTO));
		assertEquals(Gears.AUTO, Gears.clampSetting(99));
	}

	@Test
	void labelsAreNumbersExceptNeutralAndReverse() {
		assertEquals("N", Gears.label(Gears.NEUTRAL));
		assertEquals("R", Gears.label(Gears.REVERSE));
		assertEquals("7", Gears.label(7));
	}

	@Test
	void redstoneStrengthOneIsNeutralAndFifteenIsReverse() {
		assertEquals(-1, Gears.fromSignal(0));
		assertEquals(Gears.NEUTRAL, Gears.fromSignal(1));
		assertEquals(Gears.FIRST, Gears.fromSignal(2));
		assertEquals(Gears.TOP, Gears.fromSignal(14));
		assertEquals(Gears.REVERSE, Gears.fromSignal(15));
	}

	@Test
	void anOldBlocksGearsLandOnTheSameRatios() {
		// Old first to fourth were 4, 2.5, 1.6 and 1 to one
		assertEquals(4, Gears.ratio(Gears.fromLegacy(1)), 0.3);
		assertEquals(2.5, Gears.ratio(Gears.fromLegacy(2)), 0.3);
		assertEquals(1.6, Gears.ratio(Gears.fromLegacy(3)), 0.15);
		assertEquals(1, Gears.ratio(Gears.fromLegacy(4)), 1e-9);
		assertEquals(Gears.REVERSE, Gears.fromLegacy(5));
		assertEquals(Gears.NEUTRAL, Gears.fromLegacy(0));
	}

	@Test
	void onlyTheThirteenAreForward() {
		assertTrue(Gears.isForward(Gears.FIRST) && Gears.isForward(Gears.TOP));
		assertTrue(!Gears.isForward(Gears.NEUTRAL) && !Gears.isForward(Gears.REVERSE));
	}
}
