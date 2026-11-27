package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;
import com.createengines.engine.EngineSound;

/**
 * A big engine rumbles. The author found no build that sounded deep the way a V8 should; the nearest was a W24 on very long
 * manifolds (2026-11-26). A V8 had been voiced as eight small cylinders: higher pipes and thumps, with silence between the
 * firings at idle. Pure synth, no Minecraft.
 */
class EngineNoteDepthTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final int[] CROSSPLANE = {0, 1, 0, 0, 1, 1, 1, 0};
	private static final int[] FLATPLANE = {0, 1, 0, 1, 0, 1, 0, 1};

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	/** An idling engine, voiced as the game would voice one with this many cylinders on runs of this many blocks. */
	private static float[] idle(float rpm, int[] outlets, int blocks) {
		int cylinders = outlets.length;
		float run = EngineSound.pipeLength(blocks);
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(new Params(rpm, even(cylinders), 1, 0.25f, 1, EngineNoteVoicing.size(cylinders), 0.4f, 0.15f,
			new Voice(outlets, new float[] {run, run}, Ignition.SPARK, 0.6f)));
		for (int i = 0; i < RATE; i++)
			synth.next();
		float[] out = new float[4 * RATE];
		for (int i = 0; i < out.length; i++)
			out[i] = synth.next();
		return out;
	}

	/** The power at {@code hz}, by Goertzel's method. */
	private static double power(float[] samples, double hz) {
		double turn = 2 * Math.cos(2 * Math.PI * hz / RATE);
		double last = 0;
		double before = 0;
		for (float sample : samples) {
			double now = sample + turn * last - before;
			before = last;
			last = now;
		}
		return last * last + before * before - turn * last * before;
	}

	private static double band(float[] samples, double from, double to) {
		double sum = 0;
		for (double hz = from; hz < to; hz += 0.25)
			sum += power(samples, hz);
		return sum;
	}

	/** On two blocks of manifold a bank, well under half of a V8 once sat below 120 Hz. */
	@Test
	void aV8OnShortManifoldsIsMostlyLowDown() {
		for (float rpm : new float[] {80, 160, 240}) {
			float[] v8 = idle(rpm, CROSSPLANE, 2);
			double low = band(v8, 25, 120);
			double share = low / (low + band(v8, 120, 600));
			assertTrue(share > 0.65, "a short-manifold V8 at " + rpm + " has this share below 120 Hz: " + share);
		}
	}

	@Test
	void moreCylindersDoNotTurnAnEngineThin() {
		assertEquals(1, EngineNoteVoicing.size(1));
		assertTrue(EngineNoteVoicing.size(8) >= 0.5f, "a V8 is still voiced as a middling cylinder or bigger");
		assertTrue(EngineNoteVoicing.size(24) > 0, "even a W24 is not the smallest there is");
		for (int cylinders = 1; cylinders < 32; cylinders++)
			assertTrue(EngineNoteVoicing.size(cylinders + 1) <= EngineNoteVoicing.size(cylinders));
	}

	@Test
	void onlyAnEngineOfSeveralCylindersBooms() {
		assertEquals(0, EngineNoteVoicing.depth(1));
		assertEquals(0, EngineNoteVoicing.depth(2));
		assertEquals(0.5f, EngineNoteVoicing.depth(4), 1e-6f);
		assertEquals(1, EngineNoteVoicing.depth(8), 1e-6f);
		assertEquals(1, EngineNoteVoicing.depth(24), 1e-6f);
	}

	@Test
	void aBankThatFiresEvenlyDoesNotLope() {
		for (float strength : EngineNoteVoicing.lope(even(8), FLATPLANE))
			assertEquals(1, strength, 1e-5f);
		for (float strength : EngineNoteVoicing.lope(even(4), new int[0]))
			assertEquals(1, strength, 1e-5f);
		assertEquals(1, EngineNoteVoicing.lope(new float[] {0}, new int[0])[0], 1e-5f, "a single waits a whole cycle");
	}

	@Test
	void aCrossplanesBanksLopeAndTheFiringAfterALongWaitHitsHardest() {
		float[] angles = even(8);
		float[] lope = EngineNoteVoicing.lope(angles, CROSSPLANE);
		// The left bank fires at 0, 180, 270 and 630, after waits of 90, 180, 90 and 360. An even bank of four waits 180
		assertTrue(lope[0] < 0.9f, "0 comes right after 630: " + lope[0]);
		assertEquals(1, lope[2], 1e-5f, "180 waited what an even bank would");
		assertTrue(lope[3] < 0.9f, "270 comes right after 180: " + lope[3]);
		assertTrue(lope[7] > 1.2f, "630 comes after the longest wait: " + lope[7]);
		float sum = 0;
		for (float strength : lope) {
			assertTrue(strength >= 0.7f && strength <= 1.35f);
			sum += strength;
		}
		assertEquals(1, sum / lope.length, 0.08f, "the lope moves weight about without making the engine louder");
	}

	@Test
	void twoFiringsAtOneAngleDoNotWaitOnEachOther() {
		for (float strength : EngineNoteVoicing.lope(new float[] {0, 0, 360, 360}, new int[0]))
			assertTrue(Float.isFinite(strength) && strength >= 0.7f && strength <= 1.35f);
	}

	@Test
	void theBoomRingsOnSoftlyAndThenDies() {
		NoteBoom boom = new NoteBoom();
		boom.tune(55);
		boom.strike(1);
		float peak = 0;
		float step = 0;
		float last = 0;
		float later = 0;
		for (int i = 0; i < RATE; i++) {
			float out = boom.next();
			peak = Math.max(peak, Math.abs(out));
			step = Math.max(step, Math.abs(out - last));
			last = out;
			// A V8's banks wait about this long between firings at 160 RPM
			if (i > RATE / 20 && i < RATE / 10)
				later = Math.max(later, Math.abs(out));
		}
		assertTrue(peak > 0.4f && peak < 1.2f, "one firing swings it to about 1: " + peak);
		assertTrue(step < 0.03f, "no corner to click on: " + step);
		assertTrue(later > 0.2f * peak, "still sounding when the next firing lands: " + later);
		assertTrue(boom.isSilent(), "and gone within a second");
	}

	@Test
	void theBoomStaysDeepOnAnyRunAndALongerRunTakesItLower() {
		assertTrue(EngineNoteSynth.boomNote(1) < EngineNoteSynth.boomNote(0));
		for (float run : new float[] {0, 0.5f, 1}) {
			double hz = EngineNoteSynth.boomNote(run);
			assertTrue(hz > 40 && hz < 80, "at run " + run + ": " + hz);
		}
	}

	@Test
	void aBoomingEngineStillFallsSilentWhenItStops() {
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(160, even(8), 1, 0.5f, 1));
		for (int i = 0; i < RATE; i++)
			synth.next();
		synth.set(Params.SILENT);
		for (int i = 0; i < 3 * RATE; i++)
			synth.next();
		assertTrue(synth.isSilent());
	}
}
