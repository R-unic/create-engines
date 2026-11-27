package com.createengines.content.effects;

import static com.createengines.content.effects.StartStallTake.Kind.CATCH;
import static com.createengines.content.effects.StartStallTake.Kind.HARD_CATCH;
import static com.createengines.content.effects.StartStallTake.Kind.LUGGED_STALL;
import static com.createengines.content.effects.StartStallTake.Kind.STALL;
import static com.createengines.content.effects.StartStallTake.Kind.STARVED_STALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.Diagnosis;

class StartStallTakeTest {

	private static final float WARM = 0.7f;

	private static StartStallTake caught(int cylinders, boolean compression, float temperature, long cranked) {
		return StartStallTake.catching(cylinders, compression, temperature, cranked, 0.5f, 0.5f);
	}

	private static StartStallTake died(EngineCues cue, Diagnosis.Cause cause, int cylinders, float rpm) {
		return StartStallTake.stalling(cue, cause, cylinders, false, WARM, rpm, 0.5f, 0.5f);
	}

	@Test
	void aColdBlockOrALongCrankCatchesHard() {
		assertEquals(CATCH, caught(4, false, WARM, 20).kind());
		assertEquals(HARD_CATCH, caught(4, false, 0, 20).kind());
		assertEquals(HARD_CATCH, caught(4, false, WARM, StartStallTake.LONG_CRANK_TICKS).kind());
		assertEquals(CATCH, caught(4, false, WARM, StartStallTake.LONG_CRANK_TICKS - 1).kind());
		assertEquals(CATCH, caught(4, false, WARM, 0).kind());
	}

	@Test
	void howItDiedPicksTheStall() {
		assertEquals(LUGGED_STALL, died(EngineCues.OVERLOAD, Diagnosis.Cause.NONE, 4, 64).kind());
		assertEquals(STARVED_STALL, died(EngineCues.STALL, Diagnosis.Cause.NO_FUEL, 4, 64).kind());
		assertEquals(STARVED_STALL, died(EngineCues.STALL, Diagnosis.Cause.TOO_LEAN, 4, 64).kind());
		assertEquals(STALL, died(EngineCues.STALL, Diagnosis.Cause.NONE, 4, 64).kind());
		assertEquals(STALL, died(EngineCues.STALL, Diagnosis.Cause.NO_SPARK, 4, 64).kind());
		assertEquals(STALL, died(EngineCues.FLOOD, Diagnosis.Cause.WATER, 4, 64).kind());
	}

	@Test
	void moreCylindersSoundDeeperAndLouder() {
		StartStallTake last = caught(1, false, WARM, 20);
		for (int cylinders = 2; cylinders <= 12; cylinders++) {
			StartStallTake next = caught(cylinders, false, WARM, 20);
			assertTrue(next.pitch() < last.pitch(), "pitch at " + cylinders);
			assertTrue(next.loudness() > last.loudness(), "loudness at " + cylinders);
			last = next;
		}
		assertEquals(last, caught(40, false, WARM, 20));
		assertTrue(died(EngineCues.STALL, Diagnosis.Cause.NONE, 12, 64).pitch()
			< died(EngineCues.STALL, Diagnosis.Cause.NONE, 1, 64).pitch());
	}

	@Test
	void compressionAndColdSoundLower() {
		assertTrue(caught(4, true, WARM, 20).pitch() < caught(4, false, WARM, 20).pitch());
		assertTrue(caught(4, false, 0, 20).pitch() < caught(4, false, WARM, 20).pitch());
	}

	@Test
	void aFasterEngineDiesLouder() {
		float slow = died(EngineCues.STALL, Diagnosis.Cause.NONE, 4, 16).loudness();
		float fast = died(EngineCues.STALL, Diagnosis.Cause.NONE, 4, 128).loudness();
		assertTrue(fast > slow);
		assertEquals(fast, died(EngineCues.STALL, Diagnosis.Cause.NONE, 4, 256).loudness());
		assertEquals(fast, died(EngineCues.STALL, Diagnosis.Cause.NONE, 4, -128).loudness());
	}

	@Test
	void everyTakeStaysWithinWhatTheSoundEngineCanPlay() {
		for (int cylinders : new int[] {0, 1, 12, 64})
			for (float roll : new float[] {0, 1})
				for (float temperature : new float[] {-1, 0, 2})
					for (StartStallTake take : new StartStallTake[] {
						StartStallTake.catching(cylinders, true, temperature, 0, roll, roll),
						StartStallTake.stalling(EngineCues.STALL, Diagnosis.Cause.NONE, cylinders, true, temperature, 9999,
							roll, roll)}) {
						assertTrue(take.pitch() >= 0.5f && take.pitch() <= 2, take.toString());
						assertTrue(take.loudness() > 0 && take.loudness() <= 1, take.toString());
					}
	}

	@Test
	void theRollsVaryTheTake() {
		StartStallTake low = StartStallTake.catching(4, false, WARM, 20, 0, 0);
		StartStallTake high = StartStallTake.catching(4, false, WARM, 20, 1, 1);
		assertTrue(low.pitch() < high.pitch());
		assertTrue(low.loudness() < high.loudness());
	}
}
