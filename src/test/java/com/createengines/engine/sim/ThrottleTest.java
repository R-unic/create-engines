package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ThrottleTest {

	private static CycleStats runThrottled(double rpm, double opening) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = MixtureTest.UNLIMITED;
		cylinder.intakeFactor = opening;
		cylinder.fuelTrim = Throttle.fuelTrim(opening);
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder.lastCycle();
	}

	/** The further the throttle closes, the less air each cycle draws and the less work it makes. */
	@Test
	void closingTheThrottleDrawsLessAirAndMakesLessWork() {
		for (double rpm : new double[] {32, 64, 128}) {
			CycleStats previous = runThrottled(rpm, 1);
			for (double opening : new double[] {0.5, 0.25, 0.1}) {
				CycleStats cycle = runThrottled(rpm, opening);
				assertTrue(cycle.freshAirAtInjection < previous.freshAirAtInjection,
					rpm + " RPM at " + opening + " drew " + cycle.freshAirAtInjection + " air");
				assertTrue(cycle.work < previous.work, rpm + " RPM at " + opening + " made " + cycle.work + " work");
				previous = cycle;
			}
		}
	}

	/** Shut far enough, the piston works harder dragging air through the valve than the fuel gives back. */
	@Test
	void aNearlyClosedThrottleCostsMoreThanItMakes() {
		assertTrue(runThrottled(128, 0.05).work < 0, "work " + runThrottled(128, 0.05).work);
	}

	/** The throttle's linkage trims the shot with the air, so closing it does not flood a tuned engine. */
	@Test
	void theLinkageKeepsAThrottledEngineOutOfTheRich() {
		for (double opening : new double[] {1, 0.5, 0.25, 0.15, 0.1}) {
			CycleStats cycle = runThrottled(64, opening);
			assertTrue(!cycle.rich(), "opening " + opening + " ran rich at " + cycle.equivalence);
			assertTrue(cycle.equivalence > 0.6 && cycle.equivalence < 1.1,
				"opening " + opening + " mixture " + cycle.equivalence);
		}
	}

	/** It is a linkage, not a mixture computer: breathing still falls with speed, so a fast engine is rich anyway. */
	@Test
	void theLinkageDoesNotRetuneForSpeed() {
		assertTrue(runThrottled(256, 0.5).rich());
	}

	@Test
	void fuelTrimRisesWithTheOpeningFromNothingToTheFullShot() {
		assertEquals(0, Throttle.fuelTrim(0), 1e-9);
		assertEquals(1, Throttle.fuelTrim(1), 1e-9);
		double previous = 0;
		for (double opening = 0.05; opening <= 1.001; opening += 0.05) {
			double trim = Throttle.fuelTrim(opening);
			assertTrue(trim > previous, "opening " + opening);
			previous = trim;
		}
	}

	@Test
	void theOpeningRisesFromTheIdleStopToWideOpenWithTheSignal() {
		assertEquals(0.2, Throttle.opening(0.2, 0), 1e-9);
		assertEquals(1, Throttle.opening(0.2, 15), 1e-9);
		assertEquals(0.2 + 0.8 / 3, Throttle.opening(0.2, 5), 1e-9);
		assertEquals(1, Throttle.opening(1, 0), 1e-9);
		double previous = 0;
		for (int signal = 0; signal <= 15; signal++) {
			double opening = Throttle.opening(0.05, signal);
			assertTrue(opening > previous || signal == 0, "signal " + signal);
			previous = opening;
		}
	}
}
