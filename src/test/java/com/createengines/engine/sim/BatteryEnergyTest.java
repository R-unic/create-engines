package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BatteryEnergyTest {

	@Test
	void sparksConvertToWholeEnergyAndBack() {
		assertEquals(200, BatteryEnergy.toEnergy(1));
		assertEquals(100, BatteryEnergy.toEnergy(0.5));
		assertEquals(1.0, BatteryEnergy.toSparks(200), 1e-9);
	}

	@Test
	void hostileValuesGiveNothingAndNeverOverflow() {
		assertEquals(0, BatteryEnergy.toEnergy(-5));
		assertEquals(0, BatteryEnergy.toEnergy(Double.NaN));
		assertEquals(Integer.MAX_VALUE, BatteryEnergy.toEnergy(Double.POSITIVE_INFINITY));
		assertEquals(Integer.MAX_VALUE, BatteryEnergy.toEnergy(1e30));
		assertEquals(0, BatteryEnergy.toSparks(-1));
		assertEquals(0, BatteryEnergy.toSparks(0));
	}

	@Test
	void anAlternatorsDefaultOutputBuysBackExactlyWhatItCharged() {
		// 2 FE per RPM a tick (default alternatorEnergy) against 0.01 sparks per RPM a tick
		assertEquals(BatteryEnergy.FE_PER_SPARK, Math.round(2.0 / 0.01));
	}
}
