package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepSizeTest {

	private static double limitAt(double rpm) {
		return EngineSimulation.stepLimit(EngineSimulation.rpmToOmega(rpm));
	}

	@Test
	void stepsAreAsFineAsEverAtCreatesUsualSpeeds() {
		for (double rpm : new double[] {1, 64, 256, 512, 640})
			assertEquals(EngineSimulation.MIN_STEP_DEGREES, limitAt(rpm), 1e-9, rpm + " RPM");
	}

	@Test
	void stepsGrowWithSpeedUntilTheyHitTheirCeiling() {
		double previous = 0;
		for (double rpm : new double[] {640, 1024, 2048, 3072, 4096, 8192, 65535}) {
			double step = limitAt(rpm);
			assertTrue(step >= previous, rpm + " RPM steps back from " + previous + " to " + step);
			assertTrue(step <= EngineSimulation.MAX_STEP_DEGREES, rpm + " RPM step " + step);
			previous = step;
		}
		assertEquals(EngineSimulation.MAX_STEP_DEGREES, limitAt(8192), 1e-9);
	}

	@Test
	void aTickNeverTakesMoreThanTheStepBudgetBeforeTheCeiling() {
		for (double rpm : new double[] {640, 1024, 2048, 3072}) {
			double degreesPerTick = Math.toDegrees(EngineSimulation.rpmToOmega(rpm) * EngineSimulation.TICK);
			assertTrue(degreesPerTick / limitAt(rpm) <= EngineSimulation.STEPS_PER_TICK + 1e-9, rpm + " RPM");
		}
	}
}
