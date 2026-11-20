package com.createengines.content.headphones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The rules of the noise-cancelling headphones: what is muffled, how much, and that nothing is ever silenced. */
class MuffleTest {

	@Test
	void everyWorldSoundIsMuffledAndMusicInterfaceAndVoicesAreNot() {
		for (String world : List.of("record", "weather", "block", "hostile", "neutral", "player", "ambient"))
			assertTrue(Muffle.muffles(world), world + " comes from the world, so the headphones muffle it");
		for (String untouched : List.of("master", "music", "voice"))
			assertFalse(Muffle.muffles(untouched), untouched + " is music, the interface or a voice, which they leave alone");
	}

	@Test
	void theCategoryNameIsReadWhateverItsCase() {
		assertTrue(Muffle.muffles("BLOCK"));
		assertFalse(Muffle.muffles("Music"));
	}

	@Test
	void everyKnownCategoryHasADecisionAndTheExemptOnesAreKnown() {
		Set<String> known = new HashSet<>(Muffle.KNOWN);
		assertEquals(Muffle.KNOWN.size(), known.size(), "no category is listed twice");
		assertTrue(known.containsAll(Muffle.EXEMPT), "an exempt category must be one the game has");
		long muffled = Muffle.KNOWN.stream()
			.filter(Muffle::muffles)
			.count();
		assertEquals(Muffle.KNOWN.size() - Muffle.EXEMPT.size(), muffled, "every category that is not exempt is muffled");
	}

	@Test
	void anUnwornPairChangesNothing() {
		assertEquals(1f, Muffle.gain(false));
		assertEquals(1f, Muffle.highFrequencyGain(false));
		assertEquals(1f, Muffle.fallbackMaxGain(false));
	}

	@Test
	void aWornPairLowersEveryWorldSoundAndRollsOffItsTop() {
		assertTrue(Muffle.gain(true) < 1f, "quieter");
		assertTrue(Muffle.highFrequencyGain(true) < Muffle.gain(true), "and duller: the top is cut more than the whole");
		assertTrue(Muffle.fallbackMaxGain(true) < 1f, "and with no filter, still turned down");
	}

	@Test
	void aMuffledSoundIsNeverSilent() {
		assertTrue(Muffle.gain(true) > 0f, "muffled, not muted");
		assertTrue(Muffle.highFrequencyGain(true) > 0f, "muffled, not muted");
		assertTrue(Muffle.fallbackMaxGain(true) > 0f, "muffled, not muted");
	}

	@Test
	void theFilterValuesAreWithinWhatOpenAlAccepts() {
		// OpenAL's low-pass filter takes both gains in 0..1
		assertTrue(Muffle.GAIN >= 0f && Muffle.GAIN <= 1f);
		assertTrue(Muffle.GAIN_HF >= 0f && Muffle.GAIN_HF <= 1f);
	}
}
