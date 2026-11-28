package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Revving has to be heard as pitch and not only as the same sound coming faster, by enough to be plain and not so much
 * that it sounds like one recording sped up (the author, 2026-11-27). Pure synth, no Minecraft.
 */
class NoteRevTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static double semitones(float from, float to) {
		return 12 * Math.log(NoteRev.of(to) / NoteRev.of(from)) / Math.log(2);
	}

	@Test
	void theRiseFromASlowIdleToTheUsualLimitIsPlainButNoSpedUpRecording() {
		double rise = semitones(80, 256);
		assertTrue(rise > 5 && rise < 8, "semitones from 80 to 256 RPM: " + rise);
	}

	@Test
	void pastTheUsualLimitItKeepsRisingMoreGentlyAndThenStops() {
		for (float rpm = 256; rpm < NoteRev.HIGHEST_RPM; rpm *= 2) {
			double rise = semitones(rpm, 2 * rpm);
			assertTrue(rise > 1 && rise < semitones(128, 256), "semitones from " + rpm + " to twice it: " + rise);
		}
		assertEquals(NoteRev.of(NoteRev.HIGHEST_RPM), NoteRev.of(20000));
	}

	@Test
	void unevenBanksTakeUpTheRisePastTheLimitOnlyOnceTheyHaveOpened() {
		float opened = NoteOpening.openedBy(8);
		assertTrue(opened > 600 && opened < 660, "a V8 has opened by " + opened);
		assertEquals(1, NoteRev.of(512, opened));
		assertTrue(NoteRev.of(1024, opened) > 1.05f && NoteRev.of(1024, opened) < NoteRev.of(1024));
		assertEquals(NoteRev.of(128), NoteRev.of(128, opened), "below the limit they rise as any engine");
	}

	@Test
	void thePitchOnlyEverRisesWithTheRevsAndNeverInAJump() {
		float before = NoteRev.of(0);
		for (float rpm = 1; rpm <= 4000; rpm++) {
			float now = NoteRev.of(rpm);
			assertTrue(now >= before, "falls at " + rpm);
			assertTrue(rpm <= NoteRev.LOWEST_RPM || now / before < 1.01f, "jumps at " + rpm);
			before = now;
		}
	}

	@Test
	void atTheUsualLimitTheVoiceIsAsBuiltAndAnythingUnusableLeavesItSo() {
		for (float rpm : new float[] {256, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
			assertEquals(1, NoteRev.of(rpm), "at " + rpm);
		assertEquals(NoteRev.of(NoteRev.LOWEST_RPM), NoteRev.of(0), "and it stops falling");
		assertEquals(NoteRev.of(0), NoteRev.of(-5));
	}

	@Test
	void aPipeBentLowerRingsLowerAndComesBack() {
		ExhaustPipe pipe = new ExhaustPipe();
		pipe.tune(120);
		int built = crossings(pipe);
		pipe.bend(0.65f);
		assertEquals(78, pipe.note(), 0.01);
		int low = crossings(pipe);
		assertTrue(low < 0.8 * built, "crossings bent " + low + ", as built " + built);
		pipe.bend(1);
		assertEquals(120, pipe.note(), 0.01);
		assertEquals(built, crossings(pipe), 2);
	}

	/** How often a struck pipe's ring under about 300 Hz changes sign in a quarter of a second of strikes. */
	private static int crossings(ExhaustPipe pipe) {
		pipe.clear();
		// Settles the glide first, then strikes it
		for (int i = 0; i < RATE / 10; i++)
			pipe.next(0);
		pipe.clear();
		double pole = Math.exp(-2 * Math.PI * 300 / RATE);
		double one = 0;
		double two = 0;
		int count = 0;
		boolean positive = false;
		for (int i = 0; i < RATE / 4; i++) {
			one = pole * one + (1 - pole) * pipe.next(i % 800 == 0 ? 100 : 0);
			two = pole * two + (1 - pole) * one;
			if (two > 0 != positive) {
				positive = !positive;
				count++;
			}
		}
		return count;
	}

	/** Where a four's first pipe rings after a second at {@code rpm}, the synthesizer having been somewhere else before. */
	private static double ringing(EngineNoteSynth synth, float rpm) {
		synth.set(new EngineNoteSynth.Params(rpm, new float[] {0, 180, 360, 540}, 1, 0.5f, 1));
		for (int i = 0; i < 2 * RATE; i++)
			synth.next();
		return synth.pipeNote(0);
	}

	@Test
	void aRunningEnginesPipesRiseAndFallWithItsRevs() {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		double idle = ringing(synth, 80);
		double limit = ringing(synth, 256);
		assertEquals(NoteRev.of(256) / NoteRev.of(80), limit / idle, 0.005);
		assertEquals(idle, ringing(synth, 80), 0.005 * idle);
	}
}
