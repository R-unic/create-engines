package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MufflingTest {

	@Test
	void anEngineWithNoMufflerIsAsLoudAsEver() {
		assertEquals(1f, Muffling.volume(0f), 1e-6);
		assertEquals(0f, Muffling.run(false, false), 1e-6);
		assertEquals(0f, Muffling.engine(new int[] {4}, new float[] {0f}), 1e-6);
	}

	@Test
	void aMufflerThatEveryPathGoesThroughTakesTheMostOutOfTheNoise() {
		float muffling = Muffling.engine(new int[] {6}, new float[] {Muffling.run(true, false)});
		assertEquals(1f, muffling, 1e-6);
		assertEquals(1 - Muffling.QUIET, Muffling.volume(muffling), 1e-6);
		assertTrue(Muffling.volume(muffling) > 0, "quiet, not silent");
	}

	@Test
	void aMufflerTheExhaustCanGoRoundOnlyWorksOnHalfOfIt() {
		assertEquals(Muffling.BYPASSED, Muffling.run(false, true), 1e-6);
		assertTrue(Muffling.volume(Muffling.BYPASSED) > Muffling.volume(1f));
		assertTrue(Muffling.volume(Muffling.BYPASSED) < Muffling.volume(0f));
	}

	@Test
	void aVeeWithOneBankMuffledIsHalfMuffled() {
		float muffling = Muffling.engine(new int[] {4, 4}, new float[] {1f, 0f});
		assertEquals(0.5f, muffling, 1e-6);
	}

	@Test
	void banksCountByTheHeadsTheyServe() {
		float muffling = Muffling.engine(new int[] {6, 2}, new float[] {1f, 0f});
		assertEquals(0.75f, muffling, 1e-6);
	}

	@Test
	void theVolumeStaysInRangeWhateverIsAsked() {
		assertEquals(1f, Muffling.volume(-3f), 1e-6);
		assertEquals(1 - Muffling.QUIET, Muffling.volume(9f), 1e-6);
	}

	@Test
	void anEngineWithNoExhaustRunsIsNotMuffled() {
		assertEquals(0f, Muffling.engine(new int[0], new float[0]), 1e-6);
	}
}
