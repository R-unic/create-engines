package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensorsTest {

	@Test
	void aBoreFullToItsBottomDeadCentreVolumeReadsAsOne() {
		double full = CylinderSpec.CRUDE.volumeAt(180);
		assertEquals(1, Sensors.airCharge(CylinderSpec.CRUDE, full), 1e-12);
		assertEquals(0.5, Sensors.airCharge(CylinderSpec.CRUDE, full / 2), 1e-12);
	}

	@Test
	void pushedAirReadsAboveOneAndNoAirReadsZero() {
		double full = CylinderSpec.CRUDE.volumeAt(180);
		assertTrue(Sensors.airCharge(CylinderSpec.CRUDE, full * 1.4) > 1);
		assertEquals(0, Sensors.airCharge(CylinderSpec.CRUDE, 0), 0);
		assertEquals(0, Sensors.airCharge(CylinderSpec.CRUDE, -3), 0, "a negative reading is clamped");
	}

	@Test
	void theMafSignalIsZeroForNothingAndFullAtTheFullCharge() {
		assertEquals(0, Sensors.mafSignal(0));
		assertEquals(Sensors.FULL_SIGNAL, Sensors.mafSignal(Sensors.MAF_FULL_CHARGE));
	}

	@Test
	void theMafSignalStaysInTheRedstoneRange() {
		assertEquals(Sensors.FULL_SIGNAL, Sensors.mafSignal(10));
		assertEquals(0, Sensors.mafSignal(-1));
		for (double charge = -1; charge < 4; charge += 0.01) {
			int signal = Sensors.mafSignal(charge);
			assertTrue(signal >= 0 && signal <= Sensors.FULL_SIGNAL, "at " + charge);
		}
	}

	@Test
	void theMafSignalRisesWithTheCharge() {
		int previous = 0;
		for (double charge = 0; charge <= Sensors.MAF_FULL_CHARGE; charge += 0.01) {
			int signal = Sensors.mafSignal(charge);
			assertTrue(signal >= previous, "at " + charge);
			previous = signal;
		}
	}

	@Test
	void anOrdinaryFullBreathReadsAboutTwoThirdsOfTheScale() {
		assertEquals(10, Sensors.mafSignal(1));
		assertEquals(8, Sensors.mafSignal(0.75), "half scale rounds up");
	}
}
