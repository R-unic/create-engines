package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EngineRockTest {

	@Test
	void aSmoothEngineSitsPerfectlyStill() {
		assertEquals(0, EngineRock.strength(0), 0);
		assertEquals(0, EngineRock.strength(EngineRock.STILL_BELOW * 0.9f), 0);
		float[] stray = EngineRock.stray(0.01f, 123, 5);
		assertEquals(0, stray[0], 0);
		assertEquals(0, stray[1], 0);
		assertEquals(0, EngineRock.dustChance(0.01f), 0);
	}

	@Test
	void theRougherTheEngineTheHarderItBucks() {
		float mild = EngineRock.strength(0.1f);
		float rough = EngineRock.strength(0.25f);
		assertTrue(0 < mild && mild < rough && rough < 1);
		assertEquals(1, EngineRock.strength(EngineRock.ROUGHEST), 1e-6);
		assertEquals(1, EngineRock.strength(5), 0, "no rougher than the roughest rating");
		assertTrue(EngineRock.dustChance(0.25f) > EngineRock.dustChance(0.1f));
		assertEquals(EngineRock.MOST_DUST, EngineRock.dustChance(EngineRock.ROUGHEST), 1e-6);
	}

	@Test
	void aPistonNeverStraysFurtherThanItsBoreAllows() {
		for (int angle = 0; angle < 720; angle += 7)
			for (int phase = 0; phase < 8; phase++) {
				float[] stray = EngineRock.stray(EngineRock.ROUGHEST, angle, phase);
				assertTrue(Math.abs(stray[0]) <= EngineRock.MOST_STRAY + 1e-6f);
				assertTrue(Math.abs(stray[1]) <= EngineRock.MOST_STRAY + 1e-6f);
			}
	}

	@Test
	void theBuckFollowsTheCrankAndCylindersAreOutOfStepWithEachOther() {
		float[] first = EngineRock.stray(0.3f, 40, 0);
		float[] again = EngineRock.stray(0.3f, 40, 0);
		float[] other = EngineRock.stray(0.3f, 40, 3);
		assertEquals(first[0], again[0], 0);
		assertTrue(first[0] != other[0] || first[1] != other[1]);
		float[] later = EngineRock.stray(0.3f, 100, 0);
		assertTrue(first[0] != later[0] || first[1] != later[1], "it moves with the crank");
	}
}
