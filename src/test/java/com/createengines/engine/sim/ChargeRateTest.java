package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The alternator's charge-rate dial ({@link ChargeRate}): four steps, the top one being what every alternator did before the dial. */
class ChargeRateTest {

	@Test
	void theTopSettingIsFullOutputAndIsTheDefault() {
		assertEquals(ChargeRate.MAX_SETTING, ChargeRate.DEFAULT_SETTING);
		assertEquals(1.0, ChargeRate.share(ChargeRate.DEFAULT_SETTING), 0);
		assertEquals(100, ChargeRate.percent(ChargeRate.DEFAULT_SETTING));
	}

	@Test
	void theSettingsStepUpAFourthAtATime() {
		assertEquals(0.25, ChargeRate.share(0), 0);
		assertEquals(0.5, ChargeRate.share(1), 0);
		assertEquals(0.75, ChargeRate.share(2), 0);
		assertEquals(1.0, ChargeRate.share(3), 0);
		assertEquals(25, ChargeRate.percent(0));
		assertEquals(50, ChargeRate.percent(1));
		assertEquals(75, ChargeRate.percent(2));
	}

	@Test
	void everySettingIsLowerThanTheNextAndNeverZero() {
		for (int setting = ChargeRate.MIN_SETTING; setting < ChargeRate.MAX_SETTING; setting++)
			assertTrue(ChargeRate.share(setting) < ChargeRate.share(setting + 1));
		assertTrue(ChargeRate.share(ChargeRate.MIN_SETTING) > 0, "the lowest setting still charges");
	}

	@Test
	void aSettingOutsideTheDialIsHeldToItsEnds() {
		assertEquals(ChargeRate.MIN_SETTING, ChargeRate.clamp(-5));
		assertEquals(ChargeRate.MAX_SETTING, ChargeRate.clamp(99));
		assertEquals(1.0, ChargeRate.share(Integer.MAX_VALUE), 0);
		assertEquals(0.25, ChargeRate.share(Integer.MIN_VALUE), 0);
	}
}
