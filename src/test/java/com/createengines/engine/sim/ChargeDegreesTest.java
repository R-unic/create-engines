package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The degrees a player reads for a charge or ignition temperature. */
class ChargeDegreesTest {

	@Test
	void ambientReadsTheSameOnBothScales() {
		assertEquals(Thermal.degrees(0), Thermal.chargeDegrees(1));
		assertEquals(20, Thermal.chargeDegrees(1));
	}

	@Test
	void chargeTemperaturesConvertFromAbsolute() {
		assertEquals(181, Thermal.chargeDegrees(1.55));
		assertEquals(518, Thermal.chargeDegrees(2.7));
		assertEquals(-273, Thermal.chargeDegrees(0));
		assertEquals(-273, Thermal.chargeDegrees(-1));
		assertEquals(20, Thermal.chargeDegrees(Double.NaN));
		assertEquals(20, Thermal.chargeDegrees(Double.POSITIVE_INFINITY));
	}
}
