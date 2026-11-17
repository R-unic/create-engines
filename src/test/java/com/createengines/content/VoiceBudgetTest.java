package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceBudgetTest {

	@Test
	void noMoreVoicesThanTheCapAreEverAllowed() {
		VoiceBudget budget = new VoiceBudget(3);
		assertTrue(budget.tryStart(new Object(), 0, 2));
		assertFalse(budget.tryStart(new Object(), 100, 3));
		assertFalse(budget.tryStart(new Object(), 100, 50));
	}

	@Test
	void anOwnerRestsAfterItsVoiceFinishes() {
		VoiceBudget budget = new VoiceBudget(10);
		Object owner = new Object();
		budget.finished(owner, 100);
		assertFalse(budget.tryStart(owner, 100, 0));
		assertFalse(budget.tryStart(owner, 100 + VoiceBudget.REST_TICKS - 1, 0));
		assertTrue(budget.tryStart(owner, 100 + VoiceBudget.REST_TICKS, 0));
	}

	@Test
	void startsInOneWindowAreCappedAcrossOwners() {
		VoiceBudget budget = new VoiceBudget(1000);
		int allowed = 0;
		for (int i = 0; i < 100; i++)
			if (budget.tryStart(new Object(), 500, 0))
				allowed++;
		assertEquals(VoiceBudget.STARTS_PER_WINDOW, allowed);
		assertTrue(budget.tryStart(new Object(), 500 + VoiceBudget.WINDOW_TICKS, 0));
	}

	/** A flapping dry engine: its source asks to start every tick for ten minutes. Starts stay far below one per tick. */
	@Test
	void aFlappingSourceCannotChurnVoices() {
		VoiceBudget budget = new VoiceBudget(16);
		Object owner = new Object();
		int starts = 0;
		long lastStart = Long.MIN_VALUE;
		for (long tick = 0; tick < 12_000; tick++) {
			if (lastStart != Long.MIN_VALUE && tick - lastStart == 5)
				budget.finished(owner, tick); // the voice lives five ticks, then the engine drops it
			if (tick - lastStart >= 5 && budget.tryStart(owner, tick, 0)) {
				starts++;
				lastStart = tick;
			}
		}
		assertTrue(starts <= 12_000 / VoiceBudget.REST_TICKS + 1, "starts " + starts);
	}

	@Test
	void manyOwnersDoNotLeakAndAnotherWorldsClockIsForgotten() {
		VoiceBudget budget = new VoiceBudget(16);
		for (int i = 0; i < 10_000; i++)
			budget.finished(new Object(), i);
		budget.prune(10_000);
		assertTrue(budget.remembered() <= 500, "remembered " + budget.remembered());
		budget.finished(new Object(), 1_000_000);
		budget.prune(5);
		assertEquals(0, budget.remembered());
	}

	@Test
	void extremeClocksNeverThrowOrBlockForever() {
		VoiceBudget budget = new VoiceBudget(4);
		Object owner = new Object();
		budget.finished(owner, Long.MAX_VALUE / 2);
		assertTrue(budget.tryStart(owner, 0, 0));
		budget.clear();
		assertTrue(budget.tryStart(owner, Long.MIN_VALUE / 2, 0));
		assertEquals(0, budget.remembered());
	}
}
