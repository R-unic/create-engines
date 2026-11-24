package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The spring-pack dial of a Dual-Mass Flywheel: a coupled trade between where it resonates and how heavy its secondary mass is. */
class SpringPackTest {

	private static final double RESONANCE = 48;
	private static final double SECONDARY = 1.4;

	@Test
	void theMiddleSettingScalesByExactlyOne() {
		assertEquals(1.0, SpringPack.resonanceFactor(SpringPack.DEFAULT_SETTING), 0);
		assertEquals(1.0, SpringPack.massFactor(SpringPack.DEFAULT_SETTING), 0);
		assertEquals(0.0, SpringPack.position(SpringPack.DEFAULT_SETTING), 0);
	}

	@Test
	void theEndsAreTheStatedRatios() {
		assertEquals(1 / SpringPack.RESONANCE_RATIO, SpringPack.resonanceFactor(SpringPack.MIN_SETTING), 1e-12);
		assertEquals(SpringPack.RESONANCE_RATIO, SpringPack.resonanceFactor(SpringPack.MAX_SETTING), 1e-12);
		assertEquals(1 / SpringPack.MASS_RATIO, SpringPack.massFactor(SpringPack.MIN_SETTING), 1e-12);
		assertEquals(SpringPack.MASS_RATIO, SpringPack.massFactor(SpringPack.MAX_SETTING), 1e-12);
	}

	@Test
	void stifferNeverMeansLowerOrLighter() {
		for (int s = SpringPack.MIN_SETTING; s < SpringPack.MAX_SETTING; s++) {
			assertTrue(SpringPack.resonanceFactor(s + 1) > SpringPack.resonanceFactor(s), "resonance at " + s);
			assertTrue(SpringPack.massFactor(s + 1) > SpringPack.massFactor(s), "mass at " + s);
		}
	}

	@Test
	void aSettingOutsideTheDialIsHeldToItsEnds() {
		assertEquals(SpringPack.MIN_SETTING, SpringPack.clamp(-5));
		assertEquals(SpringPack.MAX_SETTING, SpringPack.clamp(99));
		assertEquals(SpringPack.resonanceFactor(SpringPack.MAX_SETTING), SpringPack.resonanceFactor(1000), 0);
		assertEquals(SpringPack.massFactor(SpringPack.MIN_SETTING), SpringPack.massFactor(Integer.MIN_VALUE), 0);
	}

	/** The two ends each win somewhere, so no setting is best everywhere: soft helps first, stiff helps most. */
	@Test
	void neitherEndIsBetterAtEverySpeed() {
		int soft = SpringPack.MIN_SETTING;
		int stiff = SpringPack.MAX_SETTING;
		double softResonance = RESONANCE * SpringPack.resonanceFactor(soft);
		double stiffResonance = RESONANCE * SpringPack.resonanceFactor(stiff);
		double softMass = SECONDARY * SpringPack.massFactor(soft);
		double stiffMass = SECONDARY * SpringPack.massFactor(stiff);
		// Just above the soft pack's resonance only it helps
		double low = softResonance * 1.5;
		assertTrue(low < stiffResonance, "the test speed is below the stiff pack's resonance");
		assertTrue(DualMass.effectiveInertia(0, softMass, low, softResonance) > DualMass.effectiveInertia(0, stiffMass, low, stiffResonance),
			"soft helps more at low speed");
		// Well past both, the stiff pack's heavier mass helps more
		double high = stiffResonance * DualMass.FULL_AT;
		assertTrue(DualMass.effectiveInertia(0, stiffMass, high, stiffResonance) > DualMass.effectiveInertia(0, softMass, high, softResonance),
			"stiff helps more at high speed");
	}
}
