package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What fills a note in between its firings, and how an engine's tone changes with its speed. The author heard the fill as a
 * broken-up recording at low speeds, a crossplane V8 as far too quiet at speed and a diesel as not like an engine
 * (2026-11-27). Pure synth, no Minecraft.
 */
class NoteSystemTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final float[] CROSSPLANE_LOPE = EngineNoteVoicing.lope(even(8), new int[] {0, 1, 0, 0, 1, 1, 1, 0});

	private static float[] even(int firings) {
		float[] angles = new float[firings];
		for (int i = 0; i < firings; i++)
			angles[i] = i * 720f / firings;
		return angles;
	}

	private static NoteTimbre timbre(float[] lope, boolean compression) {
		return NoteTimbre.of(lope, 0.5f, 0, compression, false);
	}

	/** How strong what is left long after one pulse is, beside the pulse, with firings said to come this often. */
	private static double tail(float firingsASecond) {
		NoteSystem system = new NoteSystem(3);
		system.pace(firingsASecond);
		NoteTimbre timbre = timbre(EngineNoteVoicing.lope(even(4), new int[0]), false);
		double pulse = 0;
		double after = 0;
		for (int i = 0; i < RATE / 5; i++) {
			// A short burst at 90 Hz, as a pipe rings
			float in = i < RATE / 50 ? (float) Math.sin(2 * Math.PI * 90 * i / RATE) * 40 : 0;
			float out = system.next(in, timbre);
			if (i < RATE / 50)
				pulse += out * out;
			else if (i >= RATE / 25 && i < RATE / 25 + RATE / 50)
				after += out * out;
		}
		return Math.sqrt(after / pulse);
	}

	@Test
	void aPulseIsStillHeardAfterItHasGone() {
		assertTrue(tail(30) > 0.03, "left over: " + tail(30));
	}

	@Test
	void theChambersAndRoarDieAway() {
		NoteSystem system = new NoteSystem(3);
		system.pace(0);
		NoteTimbre timbre = timbre(EngineNoteVoicing.lope(even(4), new int[0]), false);
		for (int i = 0; i < RATE / 10; i++)
			system.next(i < 100 ? 200 : 0, timbre);
		for (int i = 0; i < 4 * RATE; i++)
			system.next(0, timbre);
		assertTrue(system.isSilent());
		assertEquals(0, system.next(0, timbre), 0.01);
	}

	@Test
	void unevenBanksOpenUpWithSpeedAndKeepTheirDeepVoiceAtIdle() {
		NoteTimbre burble = timbre(CROSSPLANE_LOPE, false);
		NoteOpening opening = new NoteOpening();
		assertSame(burble, opening.at(burble, 10), "idling, it is as it was");
		NoteTimbre fast = opening.at(burble, 70);
		assertTrue(fast.lowHz() > burble.lowHz() && fast.thin() > 0.5f && fast.thump() < 0.5f * burble.thump());
		assertSame(fast, opening.at(burble, 90), "and it is not made again while it stays open");
		assertNotSame(fast, opening.at(burble, 30));
	}

	/** The share of a crossplane V8's note under about 150 Hz. */
	private static double bass(float rpm) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(new EngineNoteSynth.Params(rpm, even(8), 1, 0.8f, 1, 0.5f, 0.5f, 0.05f,
			new EngineNoteSynth.Voice(new int[] {0, 1, 0, 0, 1, 1, 1, 0}, new float[] {0.54f, 0.54f},
				EngineNoteSynth.Ignition.SPARK, 0.5f)));
		for (int i = 0; i < RATE; i++)
			synth.next();
		double pole = 1 - Math.exp(-2 * Math.PI * 150 / RATE);
		double[] stage = new double[4];
		double low = 0;
		double all = 0;
		for (int i = 0; i < 2 * RATE; i++) {
			float sample = synth.next();
			double value = sample;
			for (int k = 0; k < stage.length; k++) {
				stage[k] += pole * (value - stage[k]);
				value = stage[k];
			}
			low += value * value;
			all += sample * sample;
		}
		return low / all;
	}

	@Test
	void aRevvingCrossplaneV8IsNotMostlyBass() {
		double idle = bass(150);
		double fast = bass(1100);
		assertTrue(fast < 0.3 && fast < 0.5 * idle, "bass share at speed " + fast + ", idling " + idle);
	}

	@Test
	void anEvenEngineKeepsOneToneAtAnySpeed() {
		NoteTimbre six = timbre(EngineNoteVoicing.lope(even(6), new int[0]), false);
		assertSame(six, new NoteOpening().at(six, 90));
	}

	@Test
	void aDieselHasMoreLowBodyThanThePetrolEngineOfItsLayout() {
		float[] lope = EngineNoteVoicing.lope(even(6), new int[0]);
		NoteTimbre petrol = timbre(lope, false);
		NoteTimbre diesel = timbre(lope, true);
		assertTrue(diesel.lowHz() < 0.6f * petrol.lowHz(), diesel.lowHz() + " against " + petrol.lowHz());
		assertTrue(diesel.roar() > petrol.roar());
	}
}
