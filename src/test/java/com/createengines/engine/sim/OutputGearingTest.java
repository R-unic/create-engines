package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutputGearingTest {

	@Test
	void oneChangesNothing() {
		for (double max : new double[] {64, 256, 512, 1024}) {
			assertEquals(1, OutputGearing.scale(1, max));
			assertEquals(max, OutputGearing.engineLimit(1, max));
		}
	}

	@Test
	void doubledWithTheLimitDoubledKeepsTheEngineAsItWas() {
		double scale = OutputGearing.scale(2, 512);
		assertEquals(2, scale);
		assertEquals(256, OutputGearing.engineLimit(scale, 512));
	}

	@Test
	void aScaleWithNoRoomUnderCreatesLimitTakesNoPowerAway() {
		assertEquals(1, OutputGearing.scale(2, 256));
		assertEquals(1, OutputGearing.scale(2, 128));
		assertEquals(1.5, OutputGearing.scale(2, 384));
		for (double max = 64; max <= 2048; max += 16)
			for (double setting = 1; setting <= 8; setting += 0.25) {
				double scale = OutputGearing.scale(setting, max);
				double limit = OutputGearing.engineLimit(scale, max);
				assertTrue(limit >= Math.min(max, OutputGearing.KEPT_RPM), setting + " at " + max);
				assertTrue(limit * scale <= max + 1e-9, setting + " at " + max);
			}
	}

	@Test
	void slowedTheEngineKeepsItsOwnLimit() {
		double scale = OutputGearing.scale(0.5, 256);
		assertEquals(0.5, scale);
		assertEquals(256, OutputGearing.engineLimit(scale, 256));
	}
}
