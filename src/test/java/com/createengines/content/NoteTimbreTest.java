package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;

/**
 * Engines are told apart by more than their speed. The author found them too alike (2026-11-27): every build was the same
 * pipe over the same thump, and a crossplane and a flat-plane V8 had the same spectrum to a tenth of a decibel. Pure
 * synth, no Minecraft.
 */
class NoteTimbreTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final int[] CROSSPLANE = {0, 1, 0, 0, 1, 1, 1, 0};
	private static final int[] FLATPLANE = {0, 1, 0, 1, 0, 1, 0, 1};
	/** Band edges, about two thirds of an octave apart, over what tells engines apart. */
	private static final double[] EDGES = {45, 70, 100, 140, 220, 350, 560, 890, 1400, 2200, 3500};

	private static float[] even(int firings) {
		float[] angles = new float[firings];
		for (int i = 0; i < firings; i++)
			angles[i] = i * 720f / firings;
		return angles;
	}

	private static float[] render(float[] angles, int[] outlets, float[] runs, boolean rotary) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(new Params(420, angles, 1, 0.6f, 1, EngineNoteVoicing.size(angles.length), 0.5f, 0.1f,
			new Voice(outlets, runs, Ignition.SPARK, 0.5f, 0f, new float[0], rotary)));
		for (int i = 0; i < RATE; i++)
			synth.next();
		float[] out = new float[2 * RATE];
		for (int i = 0; i < out.length; i++)
			out[i] = synth.next();
		return out;
	}

	private static float[] piston(int firings) {
		return render(even(firings), new int[0], new float[] {0.54f}, false);
	}

	private static float[] v8(int[] outlets) {
		return render(even(8), outlets, new float[] {0.54f, 0.54f}, false);
	}

	/** Each band's level in dB of all of them: the shape of the sound, whatever its loudness. */
	private static double[] shape(float[] samples) {
		int window = 2048;
		double[] bands = new double[EDGES.length - 1];
		double all = 0;
		for (int b = 0; b < bands.length; b++) {
			int from = (int) Math.ceil(EDGES[b] * window / RATE);
			int to = (int) Math.ceil(EDGES[b + 1] * window / RATE);
			for (int bin = from; bin < to; bin++) {
				double turn = 2 * Math.cos(2 * Math.PI * bin / window);
				for (int start = 0; start + window <= samples.length; start += window) {
					double last = 0;
					double before = 0;
					for (int i = 0; i < window; i++) {
						double hann = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / window);
						double now = samples[start + i] * hann + turn * last - before;
						before = last;
						last = now;
					}
					bands[b] += last * last + before * before - turn * last * before;
				}
			}
			all += bands[b];
		}
		for (int b = 0; b < bands.length; b++)
			bands[b] = 10 * Math.log10(bands[b] / all + 1e-9);
		return bands;
	}

	/** The mean difference between two shapes, in dB. */
	private static double apart(float[] one, float[] other) {
		double[] a = shape(one);
		double[] b = shape(other);
		double sum = 0;
		for (int i = 0; i < a.length; i++)
			sum += Math.abs(a[i] - b[i]);
		return sum / a.length;
	}

	@Test
	void aCrossplaneAndAFlatPlaneV8AreToldApartByTheirTone() {
		double difference = apart(v8(CROSSPLANE), v8(FLATPLANE));
		assertTrue(difference > 4, "dB apart: " + difference);
	}

	@Test
	void aFourASixAndATwelveHaveTonesOfTheirOwn() {
		float[] four = piston(4);
		float[] six = piston(6);
		float[] twelve = piston(12);
		assertTrue(apart(four, six) > 4, "four and six, dB apart: " + apart(four, six));
		assertTrue(apart(four, twelve) > 4, "four and twelve, dB apart: " + apart(four, twelve));
	}

	@Test
	void aTwinRotaryDoesNotSoundLikeTheFourThatFiresAsItDoes() {
		float[] four = render(even(4), new int[0], new float[] {0.54f}, false);
		float[] rotary = render(even(4), new int[0], new float[] {0.54f}, true);
		assertTrue(apart(four, rotary) > 3, "dB apart: " + apart(four, rotary));
	}

	@Test
	void unevenBanksBurbleLowerThanEvenOnes() {
		float[] even = EngineNoteVoicing.lope(even(8), FLATPLANE);
		float[] uneven = EngineNoteVoicing.lope(even(8), CROSSPLANE);
		assertTrue(NoteTimbre.of(uneven, 0.5f, 0, false, false)
			.lowHz() < 0.5f * NoteTimbre.of(even, 0.5f, 0, false, false)
				.lowHz());
	}

	@Test
	void longerRunsUnequalRunsAndDieselEachSitLower() {
		float[] four = EngineNoteVoicing.lope(even(4), new int[0]);
		NoteTimbre plain = NoteTimbre.of(four, 0.5f, 0, false, false);
		assertTrue(NoteTimbre.of(four, 1, 0, false, false)
			.lowHz() < plain.lowHz());
		assertTrue(NoteTimbre.of(four, 0.5f, 0.5f, false, false)
			.highHz() < plain.highHz());
		assertTrue(NoteTimbre.of(four, 0.5f, 0, true, false)
			.highHz() < plain.highHz());
	}

	@Test
	void everyCountOfFiringsHasATone() {
		for (int firings = 0; firings < 48; firings++) {
			NoteTimbre timbre = NoteTimbre.of(EngineNoteVoicing.lope(even(firings), new int[0]), 0.5f, 0, false, false);
			assertTrue(timbre.lowHz() > 40 && timbre.highHz() > timbre.lowHz() && timbre.level() > 0, "at " + firings);
		}
	}

	@Test
	void theRushOfGasLeavesNothingBehindAndItsNoiseTheFiringsAsTheyWere() {
		NoteFlow flow = new NoteFlow();
		flow.breathe(0, 100);
		double heard = 0;
		for (int i = 0; i < RATE; i++) {
			flow.advance();
			heard = Math.max(heard, Math.abs(flow.next(0)));
			assertEquals(0, flow.next(1), "an outlet nothing fired out of");
		}
		assertTrue(heard > 0.5, "heard " + heard);
		assertTrue(flow.isStill(), "and gone within a second");
	}

	@Test
	void theServersWordThatItIsARotaryReachesTheVoice() {
		com.createengines.engine.EngineSound sound = new com.createengines.engine.EngineSound(new int[] {0},
			new float[] {0.5f}, false, 1, 0);
		assertTrue(Voice.of(sound.asRotary(true))
			.rotary());
		assertTrue(!Voice.of(sound)
			.rotary());
	}

	@Test
	void everyToneStaysInRangeFlatOut() {
		for (int firings : new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 24})
			for (boolean rotary : new boolean[] {false, true})
				for (float sample : render(even(firings), new int[0], new float[] {0}, rotary))
					assertTrue(Float.isFinite(sample) && Math.abs(sample) <= 1, firings + " firings");
	}
}
