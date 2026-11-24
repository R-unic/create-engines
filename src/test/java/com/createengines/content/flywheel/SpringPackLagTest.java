package com.createengines.content.flywheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.DualMass;

/**
 * The twist a Dual-Mass Flywheel's secondary disc is drawn with. It is only drawn, so what matters is that it is bounded and
 * finite for every input, nothing at rest, behind the shaft whichever way it turns, that it is there across the whole speed
 * range (it was once only a bump below the resonance and nobody saw it), and that each cause does what it says.
 */
class SpringPackLagTest {

	private static final double RES = 48;

	@Test
	void isNothingAtRestWhateverTheEngineSays() {
		for (double load : new double[] {0, 0.5, 1})
			for (double phase : new double[] {0, 0.25, 0.5, 0.75})
				assertEquals(0, SpringPackLag.twist(0, RES, load, 0.3, phase));
	}

	@Test
	void windsUpWithTheLoadAtEverySpeed() {
		for (double rpm : new double[] {20, 48, 100, 200, 256}) {
			double none = SpringPackLag.twist(rpm, RES, 0, 0, 0);
			double half = SpringPackLag.twist(rpm, RES, 0.5, 0, 0);
			double full = SpringPackLag.twist(rpm, RES, 1, 0, 0);
			assertTrue(none <= half && half < full, "more load winds the springs up further at " + rpm);
			assertEquals(SpringPackLag.WINDUP_MAX * (1 - SpringPackLag.PRELOAD), full - none, 1e-9,
				"full load is the full wind-up at " + rpm);
			assertTrue(none >= SpringPackLag.WINDUP_MAX * SpringPackLag.PRELOAD - 1e-9, "a turning engine is always wound up a little");
		}
	}

	@Test
	void theFiringPulsesSwingItEitherSideOfTheWindUpAndMostOnceIsolated() {
		// Below the resonance the two masses move almost as one: a smaller swing than well past it
		assertTrue(swing(30, 0.15) < swing(256, 0.15), "less swing below the resonance");
		assertEquals(SpringPackLag.RIGID_SWING * 2 * SpringPackLag.PULSE_MAX, swing(1e-6, 0.15), 1e-3, "and not none");
		// Well past it, a fully rippling engine swings by the full pulse either way about the steady twist
		double steady = SpringPackLag.twist(256, RES, 0.5, 0.15, 0);
		double most = Double.NEGATIVE_INFINITY, least = Double.POSITIVE_INFINITY;
		for (double phase = 0; phase < 1; phase += 0.01) {
			double t = SpringPackLag.twist(256, RES, 0.5, 0.15, phase);
			most = Math.max(most, t);
			least = Math.min(least, t);
		}
		assertEquals(steady + SpringPackLag.PULSE_MAX, most, 1e-3);
		assertEquals(steady - SpringPackLag.PULSE_MAX, least, 1e-3);
	}

	private static double swing(double rpm, double ripple) {
		double most = Double.NEGATIVE_INFINITY, least = Double.POSITIVE_INFINITY;
		for (double phase = 0; phase < 1; phase += 0.01) {
			double t = SpringPackLag.twist(rpm, RES, 0.5, ripple, phase);
			most = Math.max(most, t);
			least = Math.min(least, t);
		}
		return most - least;
	}

	@Test
	void aSmootherEngineSwingsLess() {
		double[] swing = new double[3];
		double[] ripples = {0.03, 0.08, 0.15};
		for (int i = 0; i < 3; i++) {
			double most = Double.NEGATIVE_INFINITY, least = Double.POSITIVE_INFINITY;
			for (double phase = 0; phase < 1; phase += 0.01) {
				double t = SpringPackLag.twist(256, RES, 0.5, ripples[i], phase);
				most = Math.max(most, t);
				least = Math.min(least, t);
			}
			swing[i] = most - least;
		}
		assertTrue(swing[0] < swing[1] && swing[1] < swing[2], "the swing follows the ripple: " + java.util.Arrays.toString(swing));
	}

	@Test
	void passingThroughTheResonanceAddsALittleExtraWindUp() {
		double at = SpringPackLag.twist(RES, RES, 0.5, 0, 0);
		double windup = SpringPackLag.WINDUP_MAX * (SpringPackLag.PRELOAD + (1 - SpringPackLag.PRELOAD) * 0.5);
		assertEquals(windup + SpringPackLag.BELL_MAX, at, 1e-9);
		assertEquals(windup, SpringPackLag.twist(RES * DualMass.FULL_AT, RES, 0.5, 0, 0), 1e-9,
			"the bump is gone once the secondary is fully isolated");
	}

	@Test
	void isTheSameWhicheverWayTheShaftTurns() {
		for (double rpm : new double[] {1, 20, 48, 77, 130, 900})
			assertEquals(SpringPackLag.twist(rpm, RES, 0.6, 0.1, 0.3), SpringPackLag.twist(-rpm, RES, 0.6, 0.1, 0.3), 0.0);
	}

	@Test
	void isFiniteAndWithinItsBoundsForEveryInput() {
		double[] values = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e300, -1, -0.0, 0, 1e-300, 0.5, 48,
			1e300, Double.MAX_VALUE, Double.MIN_VALUE};
		for (double a : values)
			for (double b : values)
				for (double c : values) {
					double t = SpringPackLag.twist(a, b, c, b, c);
					assertTrue(Double.isFinite(t) && t >= -SpringPackLag.MAX_AHEAD && t <= SpringPackLag.MAX_BEHIND,
						a + ", " + b + ", " + c + " gave " + t);
					assertTrue(Double.isFinite(SpringPackLag.secondaryAngle(30, a, t)));
					assertTrue(Double.isFinite(SpringPackLag.secondaryAngle(a, b, c)));
				}
	}

	@Test
	void drawsTheSecondaryBehindTheShaftAgainstItsTurning() {
		assertEquals(100 - 4, SpringPackLag.secondaryAngle(100, 50, 4), 1e-9);
		assertEquals(-100 + 4, SpringPackLag.secondaryAngle(-100, -50, 4), 1e-9);
		assertEquals(100, SpringPackLag.secondaryAngle(100, 0, 4), "a shaft at rest leaves the discs together");
		assertEquals(0, SpringPackLag.secondaryAngle(Double.NaN, 50, 4), "a corrupt angle is drawn as zero");
		assertEquals(100, SpringPackLag.secondaryAngle(100, Double.NaN, 4));
	}

	@Test
	void theBoundsAreWhatTheCausesAddUpTo() {
		assertEquals(SpringPackLag.WINDUP_MAX + SpringPackLag.BELL_MAX + SpringPackLag.PULSE_MAX, SpringPackLag.MAX_BEHIND, 1e-12);
		assertTrue(SpringPackLag.MAX_BEHIND >= 8, "visible: " + SpringPackLag.MAX_BEHIND + " degrees is over 2 pixels at the rim");
		// What a real running engine gives (measured: an unloaded diesel, ripple 0.03) must still move the disc visibly
		double least = Double.POSITIVE_INFINITY, most = Double.NEGATIVE_INFINITY;
		for (double rpm : new double[] {90, 143})
			for (double phase = 0; phase < 1; phase += 0.01) {
				double t = SpringPackLag.twist(rpm, RES, 0, 0.03, phase);
				least = Math.min(least, t);
				most = Math.max(most, t);
			}
		assertTrue(most - least >= 6, "an engine that is running lightly still swings the disc by " + (most - least) + " degrees");
	}
}
