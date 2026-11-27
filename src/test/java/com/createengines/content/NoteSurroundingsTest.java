package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The world around an engine's sound (the sound plan's step 4): air, walls and the listener's room, and the two filters
 * that put them on the audio. What is heard in a game is not tested here; that is the sound bench and the author's ears.
 */
class NoteSurroundingsTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	// Air and walls

	@Test
	void theAirTakesNothingNearByAndClosesSteadilyFartherOff() {
		assertEquals(NoteSurroundings.OPEN, NoteSurroundings.airCutoff(0));
		assertEquals(NoteSurroundings.OPEN, NoteSurroundings.airCutoff(NoteSurroundings.CLEAR_AIR));
		assertEquals(NoteSurroundings.OPEN, NoteSurroundings.airCutoff(Double.NaN));
		float last = NoteSurroundings.OPEN;
		for (double d = NoteSurroundings.CLEAR_AIR + 1; d <= EngineChorus.REACH; d++) {
			float cutoff = NoteSurroundings.airCutoff(d);
			assertTrue(cutoff < last, "At " + d);
			last = cutoff;
		}
		assertEquals(700, last, 1);
		assertEquals(last, NoteSurroundings.airCutoff(500), 1);
	}

	@Test
	void eachWallMufflesMoreAndPastThreeNoMore() {
		for (int walls = 1; walls <= 3; walls++) {
			assertTrue(NoteSurroundings.cutoff(4, walls) < NoteSurroundings.cutoff(4, walls - 1));
			assertTrue(NoteSurroundings.wallLevel(walls) < NoteSurroundings.wallLevel(walls - 1));
		}
		assertEquals(NoteSurroundings.cutoff(4, 3), NoteSurroundings.cutoff(4, 40));
		assertEquals(NoteSurroundings.wallLevel(3), NoteSurroundings.wallLevel(40));
		// Far off, the air is already duller than one wall
		assertEquals(NoteSurroundings.airCutoff(EngineChorus.REACH), NoteSurroundings.cutoff(EngineChorus.REACH, 1));
	}

	@Test
	void wallsAreTheSolidBlocksBetweenTheListenerAndTheEngine() {
		assertEquals(0, NoteSurroundings.walls((x, y, z) -> false, 0.5, 1.5, 0.5, 10.5, 1.5, 0.5));
		assertEquals(1, NoteSurroundings.walls((x, y, z) -> x == 5, 0.5, 1.5, 0.5, 10.5, 1.5, 0.5));
		assertEquals(2, NoteSurroundings.walls((x, y, z) -> x == 3 || x == 6, 0.5, 1.5, 0.5, 10.5, 1.5, 0.5));
		assertEquals(3, NoteSurroundings.walls((x, y, z) -> true, 0.5, 1.5, 0.5, 20.5, 7.5, -9.5));
		// Going the other way, and on a slant, finds the same wall
		assertEquals(1, NoteSurroundings.walls((x, y, z) -> x == -5, 0.5, 1.5, 0.5, -10.5, 1.5, 0.5));
		assertEquals(1, NoteSurroundings.walls((x, y, z) -> y == 5, 0.5, 0.5, 0.5, 2.5, 10.5, 1.5));
	}

	@Test
	void theEnginesOwnBlocksAndTheOneTheListenerIsInAreNotWalls() {
		assertEquals(0, NoteSurroundings.walls((x, y, z) -> x == 10 || x == 9, 0.5, 1.5, 0.5, 10.5, 1.5, 0.5));
		assertEquals(0, NoteSurroundings.walls((x, y, z) -> x == 0, 0.7, 1.5, 0.5, 10.5, 1.5, 0.5));
	}

	@Test
	void aPlaceThatIsNowhereHasNoWallsAndNoRoom() {
		NoteSurroundings.Blocks all = (x, y, z) -> true;
		assertEquals(0, NoteSurroundings.walls(all, 0, 0, 0, Double.NaN, 0, 0));
		// A moving structure's raw position, millions of blocks off, is not walked to
		assertEquals(0, NoteSurroundings.walls(all, 0, 0, 0, 2e7, 0, 0));
		assertEquals(0, NoteSurroundings.walls(all, 3, 3, 3, 3, 3, 3));
		assertEquals(NoteSurroundings.Room.OUTDOORS, NoteSurroundings.room(all, Double.NaN, 0, 0));
	}

	// The room

	/** Walls {@code half} blocks out each way, a floor under y 0 and, if {@code roofed}, a ceiling. */
	private static NoteSurroundings.Blocks box(int half, int height, boolean roofed) {
		return (x, y, z) -> y < 0 || roofed && y >= height
			|| (y < height && (Math.abs(x) >= half || Math.abs(z) >= half));
	}

	@Test
	void outOfDoorsThereIsNoRoom() {
		assertEquals(0, NoteSurroundings.room((x, y, z) -> false, 0.5, 1.6, 0.5)
			.echo());
		assertEquals(0, NoteSurroundings.room((x, y, z) -> y < 0, 0.5, 1.6, 0.5)
			.echo(), "Flat ground is not a room");
		assertEquals(0, NoteSurroundings.room(box(4, 6, false), 0.5, 1.6, 0.5)
			.echo(), "A pit open to the sky is not one either");
	}

	@Test
	void indoorsHasAShortReflectionThatComesLaterInALargerRoom() {
		NoteSurroundings.Room small = NoteSurroundings.room(box(3, 4, true), 0.5, 1.6, 0.5);
		NoteSurroundings.Room large = NoteSurroundings.room(box(8, 7, true), 0.5, 1.6, 0.5);
		assertEquals(NoteSurroundings.LOUDEST_ECHO, small.echo(), 1e-4);
		assertEquals(NoteSurroundings.LOUDEST_ECHO, large.echo(), 1e-4);
		assertTrue(small.seconds() < large.seconds());
		assertTrue(small.seconds() >= 0.008f && large.seconds() <= NoteEcho.LONGEST);
	}

	// The filter over the distance and through walls

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
	void withNothingInTheWayEverySampleComesThroughUntouched() {
		NoteAir air = new NoteAir();
		air.set(NoteSurroundings.OPEN, 1);
		NoteEcho echo = new NoteEcho();
		echo.set(0, 0.02f);
		Random random = new Random(3);
		for (int i = 0; i < 10_000; i++) {
			float in = random.nextInt(65536) - 32768;
			assertEquals(in, echo.next(air.next(in)), 0f);
		}
		assertTrue(echo.isDry());
	}

	@Test
	void aWallTakesTheCrackMoreThanTheBoom() {
		NoteAir low = new NoteAir();
		low.set(1400, 1);
		NoteAir high = new NoteAir();
		high.set(1400, 1);
		float boom = rms(low, 90);
		float crack = rms(high, 5000);
		assertTrue(boom > 0.65f, "The boom is kept: " + boom);
		assertTrue(crack < boom * 0.15f, "The crack goes: " + crack);
	}

	@Test
	void steppingBehindAWallGlidesAndSteppingOutOpensAgain() {
		NoteAir air = new NoteAir();
		air.set(NoteSurroundings.OPEN, 1);
		for (int i = 0; i < 100; i++)
			air.next(0.5f);
		air.set(750, 0.55f);
		float last = air.next(0.5f);
		float most = 0;
		for (int i = 0; i < RATE * 3; i++) {
			float out = air.next(0.5f);
			most = Math.max(most, Math.abs(out - last));
			last = out;
		}
		assertTrue(most < 0.001f, "A jump of " + most);
		assertEquals(0.5f * 0.55f, last, 0.001f);
		air.set(NoteSurroundings.OPEN, 1);
		for (int i = 0; i < RATE * 3; i++)
			last = air.next(0.5f);
		assertEquals(0.5f, last, 0f);
	}

	@Test
	void aStreamStartingBehindAWallStartsMuffledAndSoDoesASlotTakenAnew() {
		NoteAir air = new NoteAir();
		air.set(NoteSurroundings.OPEN, 0.5f);
		assertEquals(0.5f, air.next(1), 0f);
		air.set(NoteSurroundings.OPEN, 1);
		air.restart();
		assertEquals(1, air.next(1), 0f);
	}

	@Test
	void badNumbersNeverStayInTheFilters() {
		NoteAir air = new NoteAir();
		NoteEcho echo = new NoteEcho();
		float[] bad = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -5, 0, 1e30f};
		Random random = new Random(11);
		for (int i = 0; i < 200_000; i++) {
			if (i % 97 == 0) {
				air.set(bad[random.nextInt(bad.length)], bad[random.nextInt(bad.length)]);
				echo.set(bad[random.nextInt(bad.length)], bad[random.nextInt(bad.length)]);
			}
			float in = i % 41 == 0 ? bad[random.nextInt(3)] : random.nextFloat() * 2 - 1;
			float out = echo.next(air.next(in));
			assertTrue(Float.isFinite(out) && Math.abs(out) <= 2, "At " + i + ": " + out);
		}
	}

	// The room on the audio

	@Test
	void aRoomAnswersAfterItsDelayAndThenIsQuietAgain() {
		NoteEcho echo = new NoteEcho();
		echo.set(NoteSurroundings.LOUDEST_ECHO, 0.02f);
		// Long enough for the room to have come up
		for (int i = 0; i < RATE * 2; i++)
			echo.next(0);
		float direct = echo.next(1);
		assertTrue(direct > 0.9f, "The engine itself comes first: " + direct);
		int delay = Math.round(0.02f * RATE);
		float[] after = new float[RATE / 4];
		for (int i = 0; i < after.length; i++)
			after[i] = echo.next(0);
		for (int i = 0; i < delay - 1; i++)
			assertEquals(0, after[i], 1e-6f, "Early at " + i);
		assertTrue(after[delay - 1] > 0.05f, "The first reflection: " + after[delay - 1]);
		// Nothing is fed back, so it dies right away
		for (int i = RATE / 10; i < after.length; i++)
			assertEquals(0, after[i], 1e-6f);
	}

	@Test
	void indoorsIsNoLouderThanOut() {
		NoteEcho echo = new NoteEcho();
		echo.set(NoteSurroundings.LOUDEST_ECHO, 0.023f);
		Random random = new Random(5);
		double in = 0;
		double out = 0;
		for (int i = 0; i < RATE * 5; i++) {
			float sample = random.nextFloat() * 2 - 1;
			float heard = echo.next(sample);
			if (i >= RATE) {
				in += sample * sample;
				out += heard * heard;
			}
		}
		assertTrue(Math.sqrt(out / in) <= 1.0, "Ratio " + Math.sqrt(out / in));
		assertTrue(Math.sqrt(out / in) > 0.85, "Nor much quieter: " + Math.sqrt(out / in));
	}

	@Test
	void aRoomThatChangesSizeDoesNotClick() {
		NoteEcho echo = new NoteEcho();
		echo.set(NoteSurroundings.LOUDEST_ECHO, 0.012f);
		float last = 0;
		float most = 0;
		for (int i = 0; i < RATE * 4; i++) {
			if (i % RATE == 0)
				echo.set(NoteSurroundings.LOUDEST_ECHO, i / RATE % 2 == 0 ? 0.012f : 0.045f);
			float out = echo.next((float) Math.sin(2 * Math.PI * 110 * i / RATE));
			if (i > 0)
				most = Math.max(most, Math.abs(out - last));
			last = out;
		}
		// A 110 Hz sine moves 0.022 a sample at full scale
		assertTrue(most < 0.04f, "A jump of " + most);
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
	void aNoteInClearAirOutOfDoorsIsSampleForSampleAsItWas() {
		EngineNoteMix plain = mixOfARunningFour();
		EngineNoteMix placed = mixOfARunningFour();
		placed.setAir(NoteSurroundings.cutoff(3, 0), NoteSurroundings.wallLevel(0));
		placed.setRoom(NoteSurroundings.Room.OUTDOORS);
		assertArrayEquals(played(plain), played(placed));
	}

	@Test
	void aNoteBehindAWallIsQuieter() {
		EngineNoteMix walled = mixOfARunningFour();
		walled.setAir(NoteSurroundings.cutoff(3, 2), NoteSurroundings.wallLevel(2));
		assertTrue(energy(played(walled)) < 0.6 * energy(played(mixOfARunningFour())));
	}

	@Test
	void theRoomVoiceMufflesOnlyTheEngineBehindTheWall() {
		short[][] heard = new short[2][RATE];
		for (int walls = 0; walls <= 1; walls++) {
			EngineRoomMix mix = new EngineRoomMix();
			EngineNoteSynth synth = new EngineNoteSynth(5);
			synth.set(new EngineNoteSynth.Params(1500, new float[] {0, 180, 360, 540}, 1, 0.5f, 1));
			mix.set(1, synth, 1);
			mix.setAir(1, NoteSurroundings.cutoff(3, walls * 3), NoteSurroundings.wallLevel(walls * 3));
			// A wall in front of an empty slot changes nothing
			mix.setAir(0, 450, 0);
			for (int at = 0; at + EngineNotePcm.CHUNK <= RATE; at += EngineNotePcm.CHUNK)
				mix.render(heard[walls], at, EngineNotePcm.CHUNK);
		}
		assertTrue(energy(heard[1]) < 0.4 * energy(heard[0]));
		assertTrue(energy(heard[1]) > 0);
	}

	// Cost

	/**
	 * The budget the plan sets for this step: it goes in only if it is cheap. The filters are held to a tenth of the
	 * note's own budget a sample, and one second's looking (every ray for six engines and the room) to a millisecond, a
	 * fiftieth of one tick.
	 */
	@Test
	void theFiltersAndTheLookingAreCheap() {
		NoteAir air = new NoteAir();
		air.set(900, 0.7f);
		NoteEcho echo = new NoteEcho();
		echo.set(0.3f, 0.02f);
		NoteSurroundings.Blocks open = (x, y, z) -> false;
		double bestSample = Double.MAX_VALUE;
		double bestLook = Double.MAX_VALUE;
		float sum = 0;
		int walls = 0;
		for (int round = 0; round < 20; round++) {
			long start = System.nanoTime();
			for (int i = 0; i < RATE; i++)
				sum += echo.next(air.next((i & 63) / 64f - 0.5f));
			bestSample = Math.min(bestSample, (System.nanoTime() - start) / (double) RATE);
			start = System.nanoTime();
			// With nothing solid every ray runs its whole length, the most a look can cost
			for (int engine = 0; engine < EngineChorus.NOTE_VOICES + EngineChorus.ROOM_ENGINES; engine++)
				walls += NoteSurroundings.walls(open, 0.5, 1.6, 0.5, 18.5 + engine, 12.5, 20.5);
			walls += (int) NoteSurroundings.room(open, 0.5, 1.6, 0.5)
				.echo();
			bestLook = Math.min(bestLook, System.nanoTime() - start);
		}
		System.out.printf("PERF surroundings: %.1f ns/sample, %.1f us a second's looking (checksum %s %d)%n", bestSample,
			bestLook / 1000, sum, walls);
		assertTrue(bestSample < 60, bestSample + " ns a sample");
		assertTrue(bestLook < 1_000_000, bestLook / 1000 + " us to look");
	}
}
