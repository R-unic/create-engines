package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Params;

/**
 * The streamed note runs on the sound engine's own thread for as long as an engine is near, so it must never allocate,
 * throw, or wedge: a failure there stalls the game or silences every other sound until a reload (reported on 1.2.0).
 */
class EngineNoteStabilityTest {

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	/** The sound engine copies each buffer into OpenAL at once, so one direct buffer can serve every read. */
	@Test
	void everyReadReusesTheSameDirectBuffer() {
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(1800, even(4), 1, 0.5f, 1));
		EngineNotePcm pcm = new EngineNotePcm(synth);
		ByteBuffer first = pcm.read(44100);
		assertTrue(first.isDirect());
		// Ten minutes of reads: a per-read allocation would be 6000 buffers per voice
		for (int i = 0; i < 6000; i++)
			assertSame(first, pcm.read(44100));
	}

	@Test
	void aReadIsAtMostATenthOfASecondAndNeverOverrunsWhatWasAskedFor() {
		EngineNotePcm pcm = new EngineNotePcm(new EngineNoteSynth(1));
		assertEquals(EngineNotePcm.CHUNK * 2, pcm.read(44100).remaining());
		assertEquals(200, pcm.read(200).remaining());
		assertEquals(0, pcm.read(0).remaining());
		assertEquals(0, pcm.read(1).remaining());
		assertEquals(0, pcm.read(-5).remaining());
		assertEquals(EngineNotePcm.CHUNK * 2, pcm.read(Integer.MAX_VALUE).remaining());
	}

	/** Whatever the engine reports, the note must stay finite and in range, and must recover once the engine does. */
	@Test
	void hostileEngineStatesNeverBreakTheStream() {
		Random random = new Random(42);
		EngineNoteSynth synth = new EngineNoteSynth(9);
		EngineNotePcm pcm = new EngineNotePcm(synth);
		float[][] shapes = { new float[0], even(1), even(4), even(8), even(16), new float[] { 0, 0, 0 }, new float[] { 719.99f } };
		float[] extremes = { 0, -1, 1, 1e-6f, 30, 3000, 100000, 1e9f, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY,
			Float.NEGATIVE_INFINITY };
		for (int step = 0; step < 600; step++) {
			float rpm = random.nextBoolean() ? extremes[random.nextInt(extremes.length)] : random.nextFloat() * 4000;
			float share = random.nextBoolean() ? extremes[random.nextInt(extremes.length)] : random.nextFloat();
			float load = random.nextBoolean() ? extremes[random.nextInt(extremes.length)] : random.nextFloat() * 1.3f;
			float combustion = random.nextBoolean() ? extremes[random.nextInt(extremes.length)] : random.nextFloat();
			synth.set(new Params(rpm, shapes[random.nextInt(shapes.length)], share, load, combustion));
			ByteBuffer data = pcm.read(44100);
			assertEquals(EngineNotePcm.CHUNK * 2, data.remaining());
			assertTrue(data.remaining() % 2 == 0);
		}
		// After all that, a sane engine must sound sane: a state poisoned by NaN would stay silent or stuck forever
		synth.set(new Params(1500, even(4), 1, 0.5f, 1));
		float peak = 0;
		for (int block = 0; block < 30; block++) {
			ByteBuffer data = pcm.read(44100);
			peak = 0;
			while (data.remaining() >= 2)
				peak = Math.max(peak, Math.abs(data.order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort()) / 32768f);
		}
		assertTrue(peak > 0.02f, "the note never came back after bad input, peak " + peak);
	}

	/** A synthesizer that throws gives silence, not an exception on the sound thread. */
	@Test
	void aFailingSynthesizerIsSilentNotFatal() {
		ByteBuffer data = new EngineNotePcm((out, offset, count) -> {
			throw new IllegalStateException("boom");
		}).read(44100);
		assertEquals(EngineNotePcm.CHUNK * 2, data.remaining());
		while (data.remaining() > 0)
			assertEquals(0, data.get());
	}

	/** Engines starting and stopping all day: the state must settle to silence each time, not drift. */
	@Test
	void startingAndStoppingForeverStaysBounded() {
		EngineNoteSynth synth = new EngineNoteSynth(5);
		EngineNotePcm pcm = new EngineNotePcm(synth);
		for (int cycle = 0; cycle < 200; cycle++) {
			synth.set(new Params(800 + cycle * 20, even(2 + cycle % 7), 1, 1.3f, 1));
			for (int i = 0; i < 8; i++)
				pcm.read(44100);
			synth.set(Params.SILENT);
			for (int i = 0; i < 40; i++)
				pcm.read(44100);
			assertTrue(synth.isSilent(), "still ringing after four seconds of silence, cycle " + cycle);
		}
		assertFalse(Float.isNaN(synth.next()));
	}
}
