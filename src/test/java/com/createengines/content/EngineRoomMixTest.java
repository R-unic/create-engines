package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Params;

/**
 * The shared "engine room" voice mixes the engines past the nearest few: an empty room is silent, an engine put in a slot
 * fades in rather than starting with a click, the mix stays in range however many are in it, and a read allocates nothing.
 */
class EngineRoomMixTest {

	/** A tenth of a second, which the room is read in two chunks. */
	private static final int TENTH = 2 * EngineNotePcm.CHUNK;

	private static void tenth(EngineRoomMix mix, short[] out) {
		mix.render(out, 0, EngineNotePcm.CHUNK);
		mix.render(out, EngineNotePcm.CHUNK, EngineNotePcm.CHUNK);
	}

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static EngineNoteSynth running(int seed, int cylinders, float rpm) {
		EngineNoteSynth synth = new EngineNoteSynth(seed);
		synth.set(new Params(rpm, even(cylinders), 1, 0.7f, 1));
		return synth;
	}

	@Test
	void anEmptyRoomIsSilent() {
		EngineRoomMix mix = new EngineRoomMix();
		short[] out = new short[EngineNotePcm.CHUNK];
		mix.render(out, 0, out.length);
		for (short sample : out)
			assertEquals(0, sample);
		assertTrue(mix.isSilent());
	}

	@Test
	void anEngineInASlotFadesInWithoutAClick() {
		EngineRoomMix mix = new EngineRoomMix();
		mix.set(0, running(3, 4, 900), 1);
		short[] out = new short[TENTH];
		tenth(mix, out);
		assertTrue(Math.abs(out[0]) < 200, "The first sample comes in from nothing: " + out[0]);
		int loudest = 0;
		for (short sample : out)
			loudest = Math.max(loudest, Math.abs(sample));
		assertTrue(loudest > 500, "And then it is heard: " + loudest);
		// The game thread fades a slot to nothing before it empties it; once empty the room is silent again
		mix.set(0, null, 0);
		tenth(mix, out);
		assertTrue(mix.isSilent(), "An emptied room is silent");
	}

	/** A new gain is glided to, so nothing clicks; a new engine in a used slot starts from nothing again. */
	@Test
	void aGainGlidesAndANewEngineInTheSlotStartsFromNothing() {
		EngineRoomMix mix = new EngineRoomMix();
		EngineRoomMix twin = new EngineRoomMix();
		EngineNoteSynth first = running(3, 4, 900);
		mix.set(0, first, 1);
		twin.set(0, running(3, 4, 900), 1);
		short[] out = new short[TENTH];
		short[] same = new short[TENTH];
		for (int i = 0; i < 5; i++) {
			tenth(mix, out);
			tenth(twin, same);
		}
		mix.set(0, first, 0);
		tenth(mix, out);
		tenth(twin, same);
		assertTrue(Math.abs(out[0] - same[0]) < 300, "Turned down all at once: " + out[0] + " against " + same[0]);
		// A tenth of a second is over three of the glide's own time: by the next one there is next to nothing left
		tenth(mix, out);
		tenth(twin, same);
		assertTrue(rms(out, 0) < rms(same, 0) * 0.05, "But soon gone: " + rms(out, 0) + " against " + rms(same, 0));
		mix.set(0, first, 1);
		for (int i = 0; i < 5; i++)
			tenth(mix, out);
		mix.set(0, running(9, 4, 900), 1);
		tenth(mix, out);
		assertTrue(Math.abs(out[0]) < 200, "The next engine comes in from nothing: " + out[0]);
	}

	/** The level of the samples from {@code from} on. */
	private static double rms(short[] samples, int from) {
		double sum = 0;
		for (int i = from; i < samples.length; i++)
			sum += (double) samples[i] * samples[i];
		return Math.sqrt(sum / (samples.length - from));
	}

	@Test
	void aFullRoomStaysInRange() {
		EngineRoomMix mix = new EngineRoomMix();
		for (int s = 0; s < EngineRoomMix.SLOTS; s++)
			mix.set(s, running(11 + s, 8, 600 + 300 * s), 2);
		short[] out = new short[EngineNotePcm.CHUNK];
		for (int read = 0; read < 50; read++) {
			mix.render(out, 0, out.length);
			for (short sample : out)
				assertTrue(Math.abs(sample) <= 30000, "Out of range: " + sample);
		}
	}

	@Test
	void aHostileGainIsHeldInRange() {
		EngineRoomMix mix = new EngineRoomMix();
		mix.set(0, running(5, 2, 300), Float.NaN);
		mix.set(1, running(6, 2, 300), Float.POSITIVE_INFINITY);
		short[] out = new short[EngineNotePcm.CHUNK];
		mix.render(out, 0, out.length);
		for (short sample : out)
			assertTrue(Math.abs(sample) <= 30000);
	}

	/** A full room renders its engines on the sound thread: no more than a note's budget for each of them. */
	@Test
	void aFullRoomStaysUnderThreeNotesBudget() {
		EngineRoomMix mix = new EngineRoomMix();
		for (int s = 0; s < EngineRoomMix.SLOTS; s++)
			mix.set(s, running(31 + s, 8, 3000), 0.8f);
		short[] out = new short[EngineNotePcm.CHUNK];
		for (int i = 0; i < 300; i++)
			mix.render(out, 0, out.length);
		double best = Double.MAX_VALUE;
		for (int round = 0; round < 9; round++) {
			long start = System.nanoTime();
			for (int block = 0; block < 20; block++)
				mix.render(out, 0, out.length);
			best = Math.min(best, (System.nanoTime() - start) / (20.0 * out.length));
		}
		System.out.printf("PERF room of %d: %.1f ns/sample (budget %d)%n", EngineRoomMix.SLOTS, best, 600 * EngineRoomMix.SLOTS);
		assertTrue(best < 600 * EngineRoomMix.SLOTS, "A full room takes " + best + " ns a sample");
	}

	@Test
	void aReadAllocatesAlmostNothing() {
		var bean = ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean sun) || !sun.isThreadAllocatedMemorySupported())
			return;
		EngineRoomMix mix = new EngineRoomMix();
		for (int s = 0; s < EngineRoomMix.SLOTS; s++)
			mix.set(s, running(21 + s, 6, 500), 0.8f);
		EngineNotePcm pcm = new EngineNotePcm(mix);
		for (int i = 0; i < 200; i++)
			pcm.read(EngineNotePcm.CHUNK * 2);
		long id = Thread.currentThread()
			.threadId();
		long before = sun.getThreadAllocatedBytes(id);
		int reads = 500;
		for (int i = 0; i < reads; i++)
			pcm.read(EngineNotePcm.CHUNK * 2);
		long perRead = (sun.getThreadAllocatedBytes(id) - before) / reads;
		assertTrue(perRead < 64, "A read of the shared voice allocates " + perRead + " bytes");
	}

	/** An engine heard through the shared voice keeps time with its cam as one with its own note does. */
	@org.junit.jupiter.api.Test
	void anEngineInTheSharedVoiceTakesUpItsCamsPosition() {
		EngineNoteSynth.Params single = new EngineNoteSynth.Params(120, new float[] {360}, 1, 0.5f, 1);
		short[][] heard = new short[2][EngineNotePcm.CHUNK];
		for (int aimed = 0; aimed < 2; aimed++) {
			EngineNoteSynth synth = new EngineNoteSynth(7);
			synth.set(single);
			EngineRoomMix mix = new EngineRoomMix();
			mix.set(0, synth, 1);
			// The cam says the firing is about to happen; unaimed, it is half a second off
			if (aimed == 1)
				synth.lockTo(359);
			for (int chunk = 0; chunk < 3; chunk++)
				mix.render(heard[aimed], 0, EngineNotePcm.CHUNK);
		}
		org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(heard[0], heard[1]));
	}
}
