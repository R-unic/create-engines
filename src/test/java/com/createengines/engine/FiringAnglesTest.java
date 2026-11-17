package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth;
import com.createengines.content.EngineNoteSynth.Params;

/**
 * An engine's firing angles reach the client in tenths of a degree. One that rounded up to the whole cycle used to make
 * the engine note refuse the lot and go silent, so a big engine with a cylinder at the wrap point had no firing sound.
 */
class FiringAnglesTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static float[] roundTrip(float[] angles) {
		return FiringAngles.fromTenths(FiringAngles.toTenths(angles));
	}

	private static double rms(float[] angles, float rpm) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(new Params(rpm, angles, 1, 0.5f, 1, 0, 0.5f));
		for (int i = 0; i < RATE; i++)
			synth.next();
		double sum = 0;
		for (int i = 0; i < RATE; i++) {
			float sample = synth.next();
			sum += sample * sample;
		}
		return Math.sqrt(sum / RATE);
	}

	@Test
	void evenlySpacedAnglesComeBackUnchanged() {
		for (int cylinders : new int[] {1, 2, 3, 4, 6, 8, 12, 16, 24, 32})
			assertArrayEquals(even(cylinders), roundTrip(even(cylinders)), 0.051f, cylinders + " cylinders");
	}

	@Test
	void anAngleThatRoundsUpToTheWholeCycleLandsAtZero() {
		for (float nearlyAround : new float[] {719.96f, 719.99f, Math.nextDown(720f), 720f}) {
			float[] back = roundTrip(new float[] {0, 30, 60, nearlyAround});
			assertEquals(4, back.length);
			assertEquals(0f, back[0], "the wrapped angle is the first firing of the cycle, from " + nearlyAround);
			for (float angle : back)
				assertTrue(angle >= 0 && angle < 720, angle + " from " + nearlyAround + " is outside the cycle");
		}
	}

	@Test
	void aDoubleAHairUnderTheCycleDoesNotSilenceTheEngineNote() {
		// 24 cylinders, one of which a floating-point wobble put a hair under 720
		float[] raw = even(24);
		raw[0] = (float) 719.9999999999999;
		float[] unsorted = raw.clone();
		java.util.Arrays.sort(unsorted);
		assertTrue(rms(unsorted, 448) < 0.01, "the synthesizer still refuses an angle at the whole cycle, so this is what was heard");
		assertTrue(rms(roundTrip(raw), 448) > 0.1, "wrapped, the 24 cylinders sound");
	}

	@Test
	void outputIsSortedBecauseTheSynthesizerFiresInOrder() {
		float[] back = roundTrip(new float[] {500, 719.99f, 100, 0, 300});
		for (int i = 1; i < back.length; i++)
			assertTrue(back[i - 1] <= back[i], "out of order at " + i);
	}

	@Test
	void whateverTheWireHoldsComesOutInsideTheCycle() {
		float[] back = FiringAngles.fromTenths(new int[] {7200, -1, -7200, 0, 36000, Integer.MAX_VALUE, Integer.MIN_VALUE, 7199});
		assertEquals(8, back.length);
		for (float angle : back)
			assertTrue(angle >= 0 && angle < 720, "angle " + angle);
	}

	@Test
	void anglesThatAreNotNumbersAreLeftOut() {
		int[] tenths = FiringAngles.toTenths(new float[] {10, Float.NaN, Float.POSITIVE_INFINITY, 20, Float.NEGATIVE_INFINITY});
		assertArrayEquals(new int[] {100, 200}, tenths);
	}

	@Test
	void noAnglesIsNoAngles() {
		assertEquals(0, FiringAngles.toTenths(new float[0]).length);
		assertEquals(0, FiringAngles.fromTenths(new int[0]).length);
	}
}
