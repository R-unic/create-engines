package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceClockTest {

	@Test
	void aNewVoiceGetsItsMomentToStart() {
		assertFalse(VoiceClock.settled(100, 100));
		assertFalse(VoiceClock.settled(100 + VoiceClock.SETTLE_TICKS - 1, 100));
		assertTrue(VoiceClock.settled(100 + VoiceClock.SETTLE_TICKS, 100));
	}

	/** Joining a world whose clock is behind the last one's must not leave the old voices holding their slots for ever. */
	@Test
	void aVoiceFromAnotherWorldsLaterClockIsStale() {
		assertTrue(VoiceClock.settled(50, 1_000_000));
		assertTrue(VoiceClock.settled(0, Long.MAX_VALUE / 2));
	}

	@Test
	void extremeClocksDoNotOverflowIntoNonsense() {
		assertTrue(VoiceClock.settled(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2));
		assertTrue(VoiceClock.settled(Long.MAX_VALUE / 2, 0));
	}
}
