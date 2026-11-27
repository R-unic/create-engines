package com.createengines.content;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The bytes the sound engine reads for {@link EngineNoteSound}: the synthesizer's output as mono 16 bit little-endian,
 * a twentieth of a second at most per read for a note. Pure Java so it can be tested.
 *
 * <p>
 * One direct buffer is allocated up front and reused. The sound engine copies a buffer into OpenAL the moment it is
 * read, so it never holds on to one, and a fresh {@code allocateDirect} every tenth of a second per voice fills the
 * JVM's direct memory faster than the garbage collector frees it: the game stalls on forced collections and then the
 * sound thread dies with an {@link OutOfMemoryError}, taking every other sound with it.
 */
final class EngineNotePcm {

	/**
	 * A twentieth of a second of audio at a time, about one game tick: four are queued, so the note is at most about two
	 * tenths behind. The queue is topped up once a tick, so a hitch longer than the three chunks waiting runs it dry, and the game drops the note.
	 */
	static final int CHUNK = (EngineNoteSynth.SAMPLE_RATE + 19) / 20;

	/**
	 * Samples to hand over at read {@code n}, counted from 0. A twentieth of a second is not a whole number of samples, so
	 * reads take turns at one more and one less: a stream read at a fixed length would drift against the game's ticks.
	 */
	static int lengthOfRead(long n) {
		return (int) (samplesBefore(n + 1) - samplesBefore(n));
	}

	/** Samples in the first {@code n} reads. */
	static long samplesBefore(long n) {
		return n * EngineNoteSynth.SAMPLE_RATE / 20;
	}

	/** Fills {@code out[0, count)}; the synthesizer, or something else in a test. */
	interface Renderer {
		void render(short[] out, int offset, int count);
	}

	private final Renderer synth;
	private final short[] scratch;
	private final ByteBuffer data;

	EngineNotePcm(EngineNoteSynth synth) {
		this(synth::render);
	}

	EngineNotePcm(Renderer renderer) {
		this(renderer, CHUNK);
	}

	/** A stream handed {@code chunk} samples at a time, for one that need not follow the game closely. */
	EngineNotePcm(Renderer renderer, int chunk) {
		this.synth = renderer;
		this.scratch = new short[chunk];
		this.data = ByteBuffer.allocateDirect(chunk * 2)
			.order(ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * The next stretch of the note, at most {@code size} bytes. The same buffer comes back every time, so it is only
	 * valid until the next call. A synthesizer that throws gives silence instead of an exception on the sound thread.
	 */
	ByteBuffer read(int size) {
		int samples = Math.max(0, Math.min(scratch.length, size / 2));
		try {
			boolean timed = SoundProbe.active();
			long began = timed ? System.nanoTime() : 0;
			synth.render(scratch, 0, samples);
			if (timed)
				SoundProbe.rendered(samples, System.nanoTime() - began);
		} catch (RuntimeException e) {
			java.util.Arrays.fill(scratch, 0, samples, (short) 0);
		}
		data.clear();
		for (int i = 0; i < samples; i++)
			data.putShort(scratch[i]);
		data.flip();
		return data;
	}
}
