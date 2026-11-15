package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A rain cap's flap is kicked open by each exhaust pulse and falls shut when they stop. */
class RainCapFlapTest {

	@Test
	void aCapThatHasNeverBeenPulsedIsShut() {
		assertEquals(0, new RainCapFlap().degrees(100));
	}

	@Test
	void aPulseKicksTheFlapOpenAndItFallsShutAgain() {
		RainCapFlap flap = new RainCapFlap();
		flap.pulse(10);
		double kicked = flap.degrees(10);
		assertEquals(RainCapFlap.MAX_DEGREES * RainCapFlap.KICK, kicked, 1e-9, "a single pulse lifts it by the kick");
		assertTrue(flap.degrees(12) < kicked && flap.degrees(12) > 0, "it is falling");
		assertTrue(flap.degrees(40) < 0.5, "and a stopped engine leaves it shut within a second or two");
	}

	@Test
	void aSlowEngineClacksOncePerFiringAndAFastOneHoldsTheFlapUp() {
		RainCapFlap slow = new RainCapFlap();
		RainCapFlap fast = new RainCapFlap();
		for (int pulse = 0; pulse < 20; pulse++) {
			slow.pulse(pulse * 10);
			fast.pulse(pulse * 1.5);
		}
		assertTrue(slow.degrees(199) < 0.5 * RainCapFlap.MAX_DEGREES * RainCapFlap.KICK + 1e-6,
			"between slow firings the flap falls back almost shut");
		assertTrue(fast.degrees(28.5) > 0.8 * RainCapFlap.MAX_DEGREES, "rapid pulses keep it fluttering high");
	}

	@Test
	void theFlapNeverOpensPastItsStopOrAtAnImpossibleTime() {
		RainCapFlap flap = new RainCapFlap();
		for (int i = 0; i < 50; i++)
			flap.pulse(i * 0.2);
		assertTrue(flap.degrees(10) <= RainCapFlap.MAX_DEGREES + 1e-9);
		assertTrue(flap.degrees(0) <= RainCapFlap.MAX_DEGREES + 1e-9, "asked before the last pulse it is still in range");
	}
}
