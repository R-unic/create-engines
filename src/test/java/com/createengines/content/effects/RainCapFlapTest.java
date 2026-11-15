package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A rain cap's flap is shoved open by each exhaust pulse and sinks shut, slowly and with a small bounce, when they stop. */
class RainCapFlapTest {

	@Test
	void aCapThatHasNeverBeenPulsedIsShut() {
		assertEquals(0, new RainCapFlap().degrees(100));
	}

	@Test
	void aPulseLiftsTheFlapAndItSinksBackSlowly() {
		RainCapFlap flap = new RainCapFlap();
		flap.pulse(10);
		double peak = 0;
		double peakAt = 10;
		for (double t = 10; t <= 80; t += 0.5) {
			double degrees = flap.degrees(t);
			if (degrees > peak) {
				peak = degrees;
				peakAt = t;
			}
		}
		assertTrue(peak > 25 && peak < RainCapFlap.MAX_DEGREES, "one pulse lifts it well clear but not to the stop: " + peak);
		RainCapFlap again = new RainCapFlap();
		again.pulse(10);
		assertTrue(again.degrees(peakAt + 3) > 0.3 * peak, "it is still well up three ticks after the top");
		assertTrue(again.degrees(peakAt + 40) < 2, "and a stopped engine leaves it shut in the end");
	}

	@Test
	void theFallTakesAboutAHalfSecondOrMoreAndTheRiseIsQuick() {
		RainCapFlap flap = new RainCapFlap();
		flap.pulse(0);
		double risen = -1;
		double peakAt = 0;
		double peak = 0;
		double shutAt = -1;
		for (double t = 0.25; t < 120 && shutAt < 0; t += 0.25) {
			double degrees = flap.degrees(t);
			if (risen < 0 && degrees >= 5)
				risen = t;
			if (degrees > peak) {
				peak = degrees;
				peakAt = t;
			} else if (peak > 0 && degrees < 5)
				shutAt = t;
		}
		assertTrue(risen > 0 && risen < 4, "the rise is quick: " + risen);
		assertTrue(shutAt - peakAt >= 8, "the fall from the top to nearly shut takes at least 8 ticks: " + (shutAt - peakAt));
	}

	@Test
	void aSlowEngineClacksOncePerFiringAndAFastOneHoldsTheFlapUp() {
		RainCapFlap slow = new RainCapFlap();
		RainCapFlap fast = new RainCapFlap();
		double slowLow = 1000;
		double fastLow = 1000;
		for (int pulse = 0; pulse < 30; pulse++) {
			slow.pulse(pulse * 40);
			fast.pulse(pulse * 2);
			if (pulse > 10) {
				slowLow = Math.min(slowLow, slow.degrees(pulse * 40 + 39));
				fastLow = Math.min(fastLow, fast.degrees(pulse * 2 + 1.9));
			}
		}
		assertTrue(slowLow < 2, "between slow firings the flap falls back shut: " + slowLow);
		assertTrue(fastLow > 25, "rapid pulses keep it up and fluttering: " + fastLow);
	}

	@Test
	void theFlapNeverGoesPastItsStopsOrBelowShut() {
		RainCapFlap flap = new RainCapFlap();
		for (int i = 0; i < 500; i++) {
			flap.pulse(i * 0.2);
			double degrees = flap.degrees(i * 0.2 + 0.1);
			assertTrue(degrees >= 0 && degrees <= RainCapFlap.MAX_DEGREES + 1e-9, "in range: " + degrees);
		}
	}

	@Test
	void askingAboutAnEarlierTimeOrAfterALongGapIsSafe() {
		RainCapFlap flap = new RainCapFlap();
		flap.pulse(100);
		double later = flap.degrees(105);
		assertEquals(later, flap.degrees(102), 1e-9, "an earlier time gives the flap as it last stood");
		assertEquals(0, flap.degrees(100000), "a flap long out of sight is shut");
	}
}
