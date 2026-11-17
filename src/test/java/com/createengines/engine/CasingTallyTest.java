package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The tally replaced a HashSet and a counter; it must count exactly what they counted, up to its limit. */
class CasingTallyTest {

	@Test
	void aBlockSeenTwiceCountsOnce() {
		CasingTally tally = new CasingTally(4);
		tally.add(7);
		tally.add(7);
		tally.add(-3);
		tally.add(7);
		assertEquals(2, tally.count());
		assertFalse(tally.full());
	}

	@Test
	void countingStopsAtTheLimitAndNothingAfterItChangesTheCount() {
		CasingTally tally = new CasingTally(EngineMilestones.CLAD_BLOCKS);
		for (long key = 1; key <= 10; key++)
			tally.add(key * 1_000_003L);
		assertEquals(EngineMilestones.CLAD_BLOCKS, tally.count());
		assertTrue(tally.full());
		tally.add(Long.MIN_VALUE);
		assertEquals(EngineMilestones.CLAD_BLOCKS, tally.count());
	}

	@Test
	void anEmptyTallyIsEmptyAndZeroIsAKeyLikeAnyOther() {
		CasingTally tally = new CasingTally(4);
		assertEquals(0, tally.count());
		tally.add(0);
		tally.add(0);
		assertEquals(1, tally.count());
	}

	/** Against the set it replaced, over random streams with plenty of repeats, below, at and above the limit. */
	@Test
	void itAgreesWithAHashSetCappedAtTheLimit() {
		Random random = new Random(11);
		for (int trial = 0; trial < 2000; trial++) {
			int limit = 1 + random.nextInt(6);
			CasingTally tally = new CasingTally(limit);
			Set<Long> expected = new HashSet<>();
			int length = random.nextInt(30);
			for (int i = 0; i < length; i++) {
				long key = random.nextInt(8) - 2;
				tally.add(key);
				expected.add(key);
				assertEquals(Math.min(limit, expected.size()), tally.count(), "trial " + trial + " step " + i);
			}
		}
	}

	/** What the milestone reads: whether the count reaches the limit is the same as whether the full count would. */
	@Test
	void reachingTheLimitIsTheSameAsTheFullCountReachingIt() {
		Random random = new Random(5);
		for (int trial = 0; trial < 500; trial++) {
			CasingTally tally = new CasingTally(EngineMilestones.CLAD_BLOCKS);
			Set<Long> all = new HashSet<>();
			for (int i = 0; i < random.nextInt(20); i++) {
				long key = random.nextInt(10);
				tally.add(key);
				all.add(key);
			}
			assertEquals(all.size() >= EngineMilestones.CLAD_BLOCKS, tally.count() >= EngineMilestones.CLAD_BLOCKS);
		}
	}
}
