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

	/** A bore opened past the plain intake gives a fast engine more air, and so more work, where it was starved. */
	@Test
	void aBoreOpenedPastTheIntakeMakesMoreWorkAtSpeed() {
		for (double rpm : new double[] {192, 256}) {
			CycleStats plain = runThrottled(rpm, 1);
			CycleStats wide = runThrottled(rpm, 1.4);
			assertTrue(wide.freshAirAtInjection > plain.freshAirAtInjection,
				rpm + " RPM drew " + wide.freshAirAtInjection + " air against " + plain.freshAirAtInjection);
			assertTrue(wide.work > plain.work, rpm + " RPM made " + wide.work + " work against " + plain.work);
		}
	}

	/** Where the engine already fills, a bigger bore has nothing to add and costs almost nothing (a slightly leaner charge). */
	@Test
	void aBoreOpenedPastTheIntakeBarelyChangesLowSpeed() {
		for (double rpm : new double[] {32 * CylinderSpec.SPEED_GAIN, 64 * CylinderSpec.SPEED_GAIN, 128 * CylinderSpec.SPEED_GAIN}) {
			double plain = runThrottled(rpm, 1).work;
			double wide = runThrottled(rpm, 1.4).work;
			assertTrue(wide >= plain * 0.97, rpm + " RPM made " + wide + " work against " + plain);
		}
	}

	/** The most closed body governs a line, and a bore past the intake counts: starting the comparison from 1 once hid it. */
	@Test
	void throttleBodiesInSeriesAreGovernedByTheMostClosed() {
		assertEquals(1, Throttle.inSeries(List.of()), 1e-9);
		assertEquals(1.4, Throttle.inSeries(List.of(1.4)), 1e-9);
		assertEquals(0.3, Throttle.inSeries(List.of(1.4, 0.3)), 1e-9);
		assertEquals(1.2, Throttle.inSeries(List.of(1.4, 1.2)), 1e-9);
	}

	@Test
	void theLinkageGivesNoMoreFuelPastTheIntake() {
		assertEquals(1, Throttle.fuelTrim(1), 1e-9);
		assertEquals(1, Throttle.fuelTrim(1.4), 1e-9);
	}

	@Test
	void aWiderBoreOpensFurtherAtTheSameSignalAndTheIdleStopIsUntouched() {
		assertEquals(0.2, Throttle.opening(0.2, 1.4, 0), 1e-9);
		assertEquals(1.4, Throttle.opening(0.2, 1.4, 15), 1e-9);
		assertEquals(0.2 + 1.2 / 3, Throttle.opening(0.2, 1.4, 5), 1e-9);
		assertEquals(Throttle.opening(0.2, 7), Throttle.opening(0.2, 1, 7), 1e-9);
		assertEquals(Throttle.MAX_WIDE_OPEN, Throttle.opening(0.2, 9, 15), 1e-9);
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
