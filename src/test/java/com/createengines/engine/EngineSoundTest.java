package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What the engine is built of, as far as its sound goes, is worked out by plain rules, comes back from the sync as it went, and
 * holds hostile values in range. Only the sound: none of it changes how an engine runs. (The summary's own tag is checked by
 * {@code EngineGameTests.theEngineSyncsWhatItIsBuiltOfForItsSound}, as unit tests have no Minecraft.)
 */
class EngineSoundTest {

	@Test
	void aRunsLengthGrowsWithItsBlocksToALongRun() {
		assertEquals(0, EngineSound.pipeLength(1));
		assertTrue(EngineSound.pipeLength(4) > EngineSound.pipeLength(2));
		assertEquals(1, EngineSound.pipeLength(13));
		assertEquals(1, EngineSound.pipeLength(500));
		assertEquals(0, EngineSound.pipeLength(-3));
		assertEquals(0, EngineSound.pipeLength(0));
	}

	@Test
	void eachExhaustRunGetsAnOutletInTheOrderTheCylindersMeetThem() {
		// A vee: its cylinders alternate between two runs, numbered as the scan happened to find them
		EngineSound.Outlets vee = EngineSound.assignOutlets(new int[] {5, 2, 5, 2});
		assertArrayEquals(new int[] {0, 1, 0, 1}, vee.outletOf());
		assertArrayEquals(new int[] {5, 2}, vee.runs());
		EngineSound.Outlets shared = EngineSound.assignOutlets(new int[] {3, 3, 3});
		assertArrayEquals(new int[] {0, 0, 0}, shared.outletOf());
		assertArrayEquals(new int[] {3}, shared.runs());
	}

	@Test
	void runsPastTheLastOutletShareIt() {
		EngineSound.Outlets many = EngineSound.assignOutlets(new int[] {0, 1, 2, 3, 4, 5, 4, 1});
		assertArrayEquals(new int[] {0, 1, 2, 3, 3, 3, 3, 1}, many.outletOf());
		assertArrayEquals(new int[] {0, 1, 2, 3}, many.runs(), "The shared outlet keeps the length of the run that got it first");
	}

	@Test
	void aCylinderWithNoExhaustTakesNoOutletOfItsOwn() {
		EngineSound.Outlets some = EngineSound.assignOutlets(new int[] {-1, 7, -1, 0});
		assertArrayEquals(new int[] {0, 0, 0, 1}, some.outletOf());
		assertArrayEquals(new int[] {7, 0}, some.runs());
		assertEquals(0, EngineSound.assignOutlets(new int[0]).runs().length);
	}

	/** A crossplane's rhythm is only right if each firing keeps its own outlet through the sort. */
	@Test
	void everyOutletStaysWithItsFiringThroughTheSort() {
		float[] angles = {630, 90, 450, 0, 270, 999};
		int[] outlets = {3, 1, 2, 0, 1, 9};
		EngineSound.sortFirings(angles, outlets, 5);
		assertArrayEquals(new float[] {0, 90, 270, 450, 630, 999}, angles, "Only the listed firings are sorted");
		assertArrayEquals(new int[] {0, 1, 1, 2, 3, 9}, outlets);
	}

	@Test
	void theSortMatchesTheLibrarysOnAnyOrder() {
		java.util.Random random = new java.util.Random(3);
		for (int round = 0; round < 500; round++) {
			int count = random.nextInt(32);
			float[] angles = new float[count];
			int[] outlets = new int[count];
			for (int i = 0; i < count; i++) {
				angles[i] = random.nextInt(16) * 45;
				// Tag each firing with its own angle, so a split pair shows
				outlets[i] = (int) angles[i];
			}
			float[] expected = angles.clone();
			java.util.Arrays.sort(expected);
			EngineSound.sortFirings(angles, outlets, count);
			assertArrayEquals(expected, angles);
			for (int i = 0; i < count; i++)
				assertEquals((int) angles[i], outlets[i]);
		}
	}

	/** The server compares what it holds with what was last synced; a length that changed on the way would resync for ever. */
	@Test
	void everyRunLengthSurvivesTheSyncExactly() {
		for (int blocks = 0; blocks <= 20; blocks++) {
			EngineSound sound = new EngineSound(new int[] {0}, new float[] {EngineSound.pipeLength(blocks)}, false,
				1, 0);
			EngineSound read = EngineSound.held(sound.outlets(), sound.pipeHundredths(), false, 1, 0);
			assertArrayEquals(sound.pipes(), read.pipes(), "A run of " + blocks + " blocks");
		}
	}

	@Test
	void theShapeComesBackFromTheSyncAsItWent() {
		EngineSound sound = new EngineSound(new int[] {0, 1, 0, 0, 1, 1, 1, 0}, new float[] {0.25f, 0.75f}, true,
			0.5f, 0.3f);
		EngineSound read = EngineSound.held(sound.outlets(), sound.pipeHundredths(), sound.compression(),
			sound.precision(), sound.enclosure());
		assertArrayEquals(sound.outlets(), read.outlets());
		assertArrayEquals(sound.pipes(), read.pipes(), 0.01f);
		assertTrue(sound.sameAs(read));
		assertFalse(sound.plain());
	}

	@Test
	void aPlainEngineHasNothingNewToSync() {
		assertTrue(EngineSound.NONE.plain());
		assertTrue(new EngineSound(new int[0], new float[0], false, 1, 0).plain());
	}

	@Test
	void hostileValuesAreHeldInRange() {
		EngineSound read = EngineSound.held(new int[] {99, -5, 2}, new int[] {-400, 9000, 50, 50, 50, 50, 50}, false, Float.NaN,
			40);
		for (int outlet : read.outlets())
			assertTrue(outlet >= 0 && outlet < EngineSound.MOST_OUTLETS, "outlet " + outlet);
		assertTrue(read.pipes().length <= EngineSound.MOST_OUTLETS);
		for (float pipe : read.pipes())
			assertTrue(pipe >= 0 && pipe <= 1, "pipe " + pipe);
		assertEquals(0, read.precision());
		assertEquals(1, read.enclosure());
	}

	@Test
	void aChangeWorthHearingIsWorthSyncing() {
		EngineSound sound = new EngineSound(new int[] {0, 1}, new float[] {0.5f, 0.5f}, false, 1, 0);
		assertTrue(sound.sameAs(new EngineSound(new int[] {0, 1}, new float[] {0.5f, 0.5f}, false, 0.98f, 0.01f)));
		assertFalse(sound.sameAs(new EngineSound(new int[] {0, 0}, new float[] {0.5f, 0.5f}, false, 1, 0)));
		assertFalse(sound.sameAs(new EngineSound(new int[] {0, 1}, new float[] {0.5f, 0.5f}, true, 1, 0)));
		assertFalse(sound.sameAs(new EngineSound(new int[] {0, 1}, new float[] {0.5f, 0.5f}, false, 1, 0.5f)));
		assertFalse(sound.sameAs(null));
	}
}
