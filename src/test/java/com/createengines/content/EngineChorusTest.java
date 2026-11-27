package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * A build full of engines stays bearable (the 2.4 sound rework, "Easy to live with in a build"): sixteen engines are never far
 * louder than one, the shared voice grows fuller but only so far, and a steady engine settles back but comes straight up when
 * it changes.
 */
class EngineChorusTest {

	/** Heard together, engines' powers add: the total is the root of the sum of squares. */
	private static double together(float[] levels, float share) {
		double power = 0;
		for (float level : levels)
			power += (level * share) * (level * share);
		return Math.sqrt(power);
	}

	@Test
	void sixteenEnginesAreNoMoreThanSixDecibelsLouderThanOne() {
		float[] sixteen = new float[16];
		Arrays.fill(sixteen, 1);
		float share = EngineChorus.ceiling(sixteen, sixteen.length);
		double db = 20 * Math.log10(together(sixteen, share) / 1);
		assertTrue(db <= 6, "Sixteen engines are " + db + " dB louder than one");
		// Without the ceiling they would be 12 dB louder
		assertEquals(12, 20 * Math.log10(together(sixteen, 1)), 0.1);
	}

	/** Three loud engines in the shared voice with twenty barely audible ones past them: the fill must not lift the loud ones. */
	@Test
	void faintEnginesPastTheSharedVoiceDoNotPushTheTotalOverTheCeiling() {
		float[] levels = new float[23];
		Arrays.fill(levels, 0.01f);
		Arrays.fill(levels, 0, 3, 1f);
		float share = EngineChorus.ceiling(levels, levels.length);
		// fill takes the engines' powers: three at 1, twenty at 0.01
		float fill = EngineChorus.fill(3 * 1.0, 20 * 0.01 * 0.01);
		double total = Math.sqrt(3 * Math.pow(share * fill, 2));
		assertTrue(total <= EngineChorus.CEILING + 1e-4, "All together are " + total + " times the loudest alone");
	}

	@Test
	void oneEngineAloneOrAFewQuietOnesAreNotTurnedDown() {
		assertEquals(1, EngineChorus.ceiling(new float[] {1}, 1));
		assertEquals(1, EngineChorus.ceiling(new float[] {1, 0.2f, 0.2f}, 3));
		assertEquals(1, EngineChorus.ceiling(new float[0], 0));
	}

	@Test
	void theCeilingIgnoresWhatIsNotANumber() {
		float share = EngineChorus.ceiling(new float[] {Float.NaN, 1, Float.POSITIVE_INFINITY, -3}, 4);
		assertTrue(Float.isFinite(share) && share > 0 && share <= 1, "share " + share);
	}

	@Test
	void anEngineFadesToNothingAtTheEdgeOfItsReach() {
		assertEquals(1, EngineChorus.reach(0));
		assertEquals(0.5f, EngineChorus.reach(EngineChorus.REACH / 2), 1e-6);
		assertEquals(0, EngineChorus.reach(EngineChorus.REACH + 5));
		assertEquals(0, EngineChorus.reach(Double.NaN));
	}

	@Test
	void theSharedVoiceGrowsFullerButOnlySoFar() {
		// Powers: three engines at full level, and as many again counted, fill it by the root of two
		assertEquals(1, EngineChorus.fill(3, 0));
		assertTrue(EngineChorus.fill(3, 3) > 1.3f);
		assertEquals(EngineChorus.MOST_FILL, EngineChorus.fill(3, 1000));
		assertEquals(1, EngineChorus.fill(0, 10));
		assertEquals(1, EngineChorus.fill(Double.NaN, 10));
		assertEquals(1, EngineChorus.fill(3, Double.NaN));
	}

	@Test
	void aSteadyEngineSettlesBackAndComesStraightUpWhenItChanges() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		float gain = settle.update(256, 0.5f, 1, 0.05f);
		assertEquals(1, gain, "A new engine starts at full level");
		// A minute held steady
		for (int i = 0; i < 1200; i++)
			gain = settle.update(256, 0.5f, 1, 0.05f);
		assertTrue(gain <= EngineChorus.Settle.SETTLED + 0.02f, "A steady engine settles back: " + gain);
		assertTrue(gain >= EngineChorus.Settle.SETTLED - 1e-6f, "It never goes below where it settles: " + gain);
		// The throttle opens: within half a second it is back up
		for (int i = 0; i < 10; i++)
			gain = settle.update(256 + 40 * (i + 1), 0.9f, 1, 0.05f);
		assertTrue(gain >= 0.95f, "A change brings it straight back up: " + gain);
	}

	/** The roadmap: quieter over the first half minute, not all at once. */
	@Test
	void aSteadyEngineSettlesOverHalfAMinuteNotAtOnce() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		float gain = held(settle, 256, 0.5f, 2);
		assertTrue(gain > 0.9f, "Two seconds in it is still near full level: " + gain);
		gain = held(settle, 256, 0.5f, 28);
		assertTrue(gain < EngineChorus.Settle.SETTLED + 0.03f, "By half a minute it has settled: " + gain);
		double quieter = -20 * Math.log10(EngineChorus.Settle.SETTLED);
		assertEquals(3, quieter, 0.2, "Settled is about 3 dB down");
	}

	/** A running engine's figures are never dead still; that must not keep it at full level for ever. */
	@Test
	void aSmallWobbleIsStillSteady() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		float gain = 1;
		for (int i = 0; i < 1200; i++)
			gain = settle.update(256 + (i % 2 == 0 ? 3 : -3), 0.5f + (i % 3 - 1) * 0.02f, 1, 0.05f);
		assertTrue(gain <= EngineChorus.Settle.SETTLED + 0.02f, "Wobble kept it up: " + gain);
	}

	@Test
	void aChangeOfLoadAloneBringsItBackWithinAFractionOfASecond() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		held(settle, 256, 0.3f, 60);
		float gain = held(settle, 256, 0.9f, 0.5f);
		assertTrue(gain > 0.9f, "Half a second after the load comes on: " + gain);
	}

	/** The gain after {@code seconds} of ticks at these figures. */
	private static float held(EngineChorus.Settle settle, float rpm, float load, float seconds) {
		float gain = settle.gain();
		for (int i = 0; i < Math.round(seconds * 20); i++)
			gain = settle.update(rpm, load, 1, 0.05f);
		return gain;
	}

	@Test
	void aMisfireCountsAsAChange() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		float gain = 0;
		for (int i = 0; i < 1200; i++)
			gain = settle.update(400, 0.6f, 1, 0.05f);
		for (int i = 0; i < 10; i++)
			gain = settle.update(400, 0.6f, 0.75f, 0.05f);
		assertTrue(gain >= 0.95f, "A cylinder dropping out is heard at full level: " + gain);
	}

	@Test
	void settlingIgnoresWhatIsNotANumber() {
		EngineChorus.Settle settle = new EngineChorus.Settle();
		settle.update(300, 0.5f, 1, 0.05f);
		float gain = settle.update(Float.NaN, Float.POSITIVE_INFINITY, 1, -1);
		assertEquals(1, gain);
	}
}
