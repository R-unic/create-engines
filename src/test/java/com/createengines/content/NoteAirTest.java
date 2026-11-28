package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The air between an engine and the listener, the one thing this mod does to a sound on its way there: walls and rooms
 * are left to the game and to other mods. What is heard in a game is not tested here; that is the sound bench and the
 * author's ears.
 */
class NoteAirTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	@Test
	void theAirTakesNothingNearByAndClosesSteadilyFartherOff() {
		assertEquals(NoteAir.OPEN, NoteAir.cutoff(0));
		assertEquals(NoteAir.OPEN, NoteAir.cutoff(NoteAir.CLEAR_AIR));
		assertEquals(NoteAir.OPEN, NoteAir.cutoff(Double.NaN));
		float last = NoteAir.OPEN;
		for (double d = NoteAir.CLEAR_AIR + 1; d <= EngineChorus.REACH; d++) {
			float cutoff = NoteAir.cutoff(d);
			assertTrue(cutoff < last, "At " + d);
			last = cutoff;
		}
		assertEquals(700, last, 1);
		assertEquals(last, NoteAir.cutoff(500), 1);
	}

	private static float rms(NoteAir air, double hz) {
		double sum = 0;
		for (int i = 0; i < RATE; i++) {
			float out = air.next((float) Math.sin(2 * Math.PI * hz * i / RATE));
			if (i >= RATE / 2)
				sum += out * out;
		}
		return (float) Math.sqrt(sum / (RATE / 2));
	}

	@Test
	void nearByEverySampleComesThroughUntouched() {
		NoteAir air = new NoteAir();
		air.set(NoteAir.OPEN);
		Random random = new Random(3);
		for (int i = 0; i < 10_000; i++) {
			float in = random.nextInt(65536) - 32768;
			assertEquals(in, air.next(in), 0f);
		}
	}

	@Test
	void theAirTakesTheCrackMoreThanTheBoom() {
		NoteAir low = new NoteAir();
		low.set(1400);
		NoteAir high = new NoteAir();
		high.set(1400);
		float boom = rms(low, 90);
		float crack = rms(high, 5000);
		assertTrue(boom > 0.65f, "The boom is kept: " + boom);
		assertTrue(crack < boom * 0.15f, "The crack goes: " + crack);
	}

	@Test
	void steppingAwayGlidesAndComingBackOpensAgain() {
		NoteAir air = new NoteAir();
		air.set(NoteAir.OPEN);
		for (int i = 0; i < 100; i++)
			air.next(0.5f);
		air.set(750);
		float last = air.next(0.5f);
		float most = 0;
		for (int i = 0; i < RATE * 3; i++) {
			float out = air.next(0.5f);
			most = Math.max(most, Math.abs(out - last));
			last = out;
		}
		assertTrue(most < 0.001f, "A jump of " + most);
		assertEquals(0.5f, last, 0.001f);
		air.set(NoteAir.OPEN);
		for (int i = 0; i < RATE * 3; i++)
			last = air.next(0.5f);
		assertEquals(0.5f, last, 0f);
	}

	@Test
	void aStreamStartingFarOffStartsDullAndSoDoesASlotTakenAnew() {
		NoteAir air = new NoteAir();
		air.set(700);
		assertTrue(air.next(1) < 0.5f);
		air.set(NoteAir.OPEN);
		air.restart();
		assertEquals(1, air.next(1), 0f);
	}

	@Test
	void badNumbersNeverStayInTheFilter() {
		NoteAir air = new NoteAir();
		float[] bad = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -5, 0, 1e30f};
		Random random = new Random(11);
		for (int i = 0; i < 200_000; i++) {
			if (i % 97 == 0)
				air.set(bad[random.nextInt(bad.length)]);
			float in = i % 41 == 0 ? bad[random.nextInt(3)] : random.nextFloat() * 2 - 1;
			float out = air.next(in);
			assertTrue(Float.isFinite(out) && Math.abs(out) <= 2, "At " + i + ": " + out);
		}
	}

	// In the streams

	private static short[] played(EngineNoteMix mix) {
		short[] out = new short[RATE];
		mix.render(out, 0, out.length);
		return out;
	}

	private static EngineNoteMix mixOfARunningFour() {
		EngineNoteSynth synth = new EngineNoteSynth(5);
		synth.set(new EngineNoteSynth.Params(1500, new float[] {0, 180, 360, 540}, 1, 0.5f, 1));
		EngineNoteMix mix = new EngineNoteMix(synth);
		mix.setGain(1);
		return mix;
	}

	private static double energy(short[] samples) {
		double sum = 0;
		for (int i = samples.length / 2; i < samples.length; i++)
			sum += (double) samples[i] * samples[i];
		return sum;
	}

	@Test
	void aNoteNearByIsSampleForSampleAsItWas() {
		EngineNoteMix plain = mixOfARunningFour();
		EngineNoteMix placed = mixOfARunningFour();
		placed.setAir(NoteAir.cutoff(3));
		assertArrayEquals(played(plain), played(placed));
	}

	@Test
	void theRoomVoiceDullsOnlyTheEngineFarOff() {
		short[][] heard = new short[2][RATE];
		for (int far = 0; far <= 1; far++) {
			EngineRoomMix mix = new EngineRoomMix();
			EngineNoteSynth synth = new EngineNoteSynth(5);
			synth.set(new EngineNoteSynth.Params(1500, new float[] {0, 180, 360, 540}, 1, 0.5f, 1));
			mix.set(1, synth, 1);
			mix.setAir(1, NoteAir.cutoff(far == 0 ? 3 : EngineChorus.REACH));
			// The air in front of an empty slot changes nothing
			mix.setAir(0, 450);
			for (int at = 0; at + EngineNotePcm.CHUNK <= RATE; at += EngineNotePcm.CHUNK)
				mix.render(heard[far], at, EngineNotePcm.CHUNK);
		}
		assertTrue(energy(heard[1]) < energy(heard[0]));
		assertTrue(energy(heard[1]) > 0);
	}

	// Cost

	/** The filter is held to a tenth of the note's own budget a sample. */
	@Test
	void theFilterIsCheap() {
		NoteAir air = new NoteAir();
		air.set(900);
		double best = Double.MAX_VALUE;
		float sum = 0;
		for (int round = 0; round < 20; round++) {
			long start = System.nanoTime();
			for (int i = 0; i < RATE; i++)
				sum += air.next((i & 63) / 64f - 0.5f);
			best = Math.min(best, (System.nanoTime() - start) / (double) RATE);
		}
		System.out.printf("PERF air: %.1f ns/sample (checksum %s)%n", best, sum);
		assertTrue(best < 60, best + " ns a sample");
	}
}
