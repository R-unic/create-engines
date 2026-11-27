package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The shape of a backfire and a spit, and when each is said to be heard. The author found them too sharp, wrong in
 * character and late after their flash (2026-11-27): the backfire rose in a third of a millisecond into hard clipping, and
 * over half of the spit was above 2 kHz.
 */
class NoteBangShapeTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static float[] bang(NoteBangs.Kind kind) {
		NoteBangs bangs = new NoteBangs();
		bangs.ask(kind, 1, 1);
		bangs.fired(75, 0);
		float[] out = new float[RATE / 2];
		for (int i = 0; i < out.length; i++)
			out[i] = bangs.next();
		return out;
	}

	/** Milliseconds from a tenth to seven tenths of the loudest, of the level over two thousandths of a second. */
	private static double rise(float[] samples) {
		int window = RATE / 500;
		double[] level = new double[samples.length - window];
		double loudest = 0;
		for (int i = 0; i < level.length; i++) {
			double sum = 0;
			for (int k = i; k < i + window; k++)
				sum += samples[k] * samples[k];
			level[i] = Math.sqrt(sum / window);
			loudest = Math.max(loudest, level[i]);
		}
		int from = 0;
		while (level[from] <= 0.1 * loudest)
			from++;
		int to = from;
		while (level[to] <= 0.7 * loudest)
			to++;
		return 1000.0 * (to - from) / RATE;
	}

	/** The share of the energy above about {@code cutoff} Hz, through two gentle high-pass stages. */
	private static double above(float[] samples, double cutoff) {
		double pole = Math.exp(-2 * Math.PI * cutoff / RATE);
		double first = 0;
		double second = 0;
		double high = 0;
		double all = 0;
		for (float sample : samples) {
			first = pole * first + (1 - pole) * sample;
			double once = sample - first;
			second = pole * second + (1 - pole) * once;
			double twice = once - second;
			high += twice * twice;
			all += sample * sample;
		}
		return high / all;
	}

	@Test
	void neitherBangStartsWithAClick() {
		assertTrue(rise(bang(NoteBangs.Kind.POP)) > 1.2, "backfire: " + rise(bang(NoteBangs.Kind.POP)) + " ms");
		assertTrue(rise(bang(NoteBangs.Kind.SPIT)) > 2, "spit: " + rise(bang(NoteBangs.Kind.SPIT)) + " ms");
	}

	@Test
	void neitherBangIsClipped() {
		for (NoteBangs.Kind kind : NoteBangs.Kind.values())
			for (float sample : bang(kind))
				assertTrue(Math.abs(sample) < 0.9f, kind + " reaches " + sample);
	}

	@Test
	void aBackfireIsABangAndASpitIsNoHiss() {
		double backfire = above(bang(NoteBangs.Kind.POP), 3000);
		double spit = above(bang(NoteBangs.Kind.SPIT), 3000);
		assertTrue(backfire < 0.04, "backfire above 3 kHz: " + backfire);
		assertTrue(spit < 0.12, "spit above 3 kHz: " + spit);
	}

	@Test
	void aBackfireIsStillWellHeard() {
		float peak = 0;
		for (float sample : bang(NoteBangs.Kind.POP))
			peak = Math.max(peak, Math.abs(sample));
		assertTrue(peak > 0.45f, "peak " + peak);
	}

	@Test
	void twoBangsWaitingShareOneCue() {
		NoteBangs bangs = new NoteBangs();
		NoteCue first = bangs.ask(NoteBangs.Kind.SPIT, 0.2f, 1);
		assertNotNull(first);
		assertSame(first, bangs.ask(NoteBangs.Kind.POP, 1, 1), "the stronger takes its place");
		assertSame(first, bangs.ask(NoteBangs.Kind.SPIT, 0.1f, 1), "a weaker one is heard as the one held");
		assertNull(bangs.ask(NoteBangs.Kind.POP, Float.NaN, 1));
		assertNull(bangs.takeStarted(), "nothing has gone off yet");
		bangs.fired(86, 0);
		assertSame(first, bangs.takeStarted());
		assertNull(bangs.takeStarted(), "said once");
	}

	/** The note is made ahead of what is heard, so the cue is later than the moment the bang goes off in it. */
	@Test
	void theNoteSaysWhenItsBangIsHeard() {
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new EngineNoteSynth.Params(1500, new float[] {10}, 1, 0.5f, 1));
		short[] block = new short[EngineNotePcm.CHUNK];
		for (int i = 0; i < 8; i++)
			synth.render(block, 0, block.length);
		long asked = System.nanoTime();
		NoteCue cue = synth.bang(NoteBangs.Kind.POP, 1, 1);
		assertFalse(cue.heardBy(asked + 10_000_000_000L), "unsaid until the bang goes off");
		// Two chunks, 100 ms: longer than a single at this speed is between firings
		synth.render(block, 0, block.length);
		synth.render(block, 0, block.length);
		assertFalse(cue.heardBy(asked - 1_000_000_000L));
		assertTrue(cue.heardBy(System.nanoTime() + 1_000_000_000L));
	}
}
