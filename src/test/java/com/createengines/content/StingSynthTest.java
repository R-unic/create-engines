package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

/** The advancement sting: a short rev-up that is always bounded, never clicks and always ends in silence. */
class StingSynthTest {

	private static short[] renderAll(StingSynth synth, int chunk) {
		short[] all = new short[synth.length()];
		short[] part = new short[chunk];
		int at = 0;
		while (at < all.length) {
			int n = Math.min(chunk, all.length - at);
			synth.render(part, 0, n);
			System.arraycopy(part, 0, all, at, n);
			at += n;
		}
		return all;
	}

	private static int peak(short[] samples, int from, int to) {
		int peak = 0;
		for (int i = from; i < to; i++)
			peak = Math.max(peak, Math.abs(samples[i]));
		return peak;
	}

	@Test
	void aGoalIsAboutAsLongAsAnnounced() {
		assertEquals(Math.round(StingSynth.seconds(false) * StingSynth.SAMPLE_RATE), new StingSynth(false).length());
		assertTrue(StingSynth.seconds(true) > StingSynth.seconds(false), "a challenge lasts longer");
		assertTrue(StingSynth.seconds(true) < 4, "a flourish, not a song");
	}

	@Test
	void itIsAudibleButQuietAndBounded() {
		for (boolean challenge : new boolean[] { false, true }) {
			short[] samples = renderAll(new StingSynth(challenge), 441);
			int loudest = peak(samples, 0, samples.length);
			assertTrue(loudest > 1500, "something to hear: " + loudest);
			assertTrue(loudest < Short.MAX_VALUE * 0.9, "never at full scale: " + loudest);
		}
	}

	@Test
	void itStartsAndEndsInSilenceSoItNeverClicks() {
		for (boolean challenge : new boolean[] { false, true }) {
			short[] samples = renderAll(new StingSynth(challenge), 441);
			assertTrue(peak(samples, 0, 8) < 200, "fades in");
			assertTrue(peak(samples, samples.length - 8, samples.length) < 50, "fades out");
		}
	}

	@Test
	void theSameStingIsTheSameWhateverTheReadSize() {
		short[] a = renderAll(new StingSynth(true), 441);
		short[] b = renderAll(new StingSynth(true), 4410);
		short[] c = renderAll(new StingSynth(true), 977);
		for (int i = 0; i < a.length; i++) {
			assertEquals(a[i], b[i], "sample " + i);
			assertEquals(a[i], c[i], "sample " + i);
		}
	}

	@Test
	void pastTheEndItIsSilenceAndStaysFinished() {
		StingSynth synth = new StingSynth(false);
		renderAll(synth, 441);
		assertTrue(synth.finished());
		short[] more = new short[5000];
		java.util.Arrays.fill(more, (short) 7);
		synth.render(more, 0, more.length);
		assertEquals(0, peak(more, 0, more.length));
	}

	@Test
	void theSoundEngineCanReadItInOddSizesForeverWithOneBuffer() {
		StingSynth synth = new StingSynth(true);
		EngineNotePcm pcm = new EngineNotePcm(synth::render);
		ByteBuffer first = null;
		for (int i = 0; i < 400; i++) {
			int ask = switch (i % 5) {
				case 0 -> 0;
				case 1 -> 1;
				case 2 -> 2205 * 2;
				case 3 -> Integer.MAX_VALUE;
				default -> 4410 * 2 + 3;
			};
			ByteBuffer read = pcm.read(ask);
			if (first == null)
				first = read;
			assertTrue(read == first, "one reused buffer, never a fresh allocation per read");
			assertTrue(read.remaining() <= EngineNotePcm.CHUNK * 2);
		}
		assertTrue(synth.finished());
	}

	@Test
	void theSpeedRisesThenFalls() {
		for (boolean challenge : new boolean[] { false, true }) {
			assertTrue(StingSynth.rpmAt(0, challenge) < StingSynth.rpmAt(0.3f, challenge));
			assertTrue(StingSynth.rpmAt(0.3f, challenge) < StingSynth.rpmAt(0.6f, challenge));
			assertTrue(StingSynth.rpmAt(1, challenge) < StingSynth.rpmAt(0.65f, challenge));
		}
		assertTrue(StingSynth.rpmAt(0.65f, true) > StingSynth.rpmAt(0.65f, false), "a challenge climbs higher");
	}
}
