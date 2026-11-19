package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Verification track item 6. Every advancement earned asks the gate whether a sting may play; with several players, or a
 * server catching up on a world, the asks arrive together. The gate's promise is one sting per quiet minute, and a
 * challenge may follow a goal but not a challenge, however the asks are interleaved.
 */
class StingGateConcurrencyTest {

	private static void join(List<Thread> threads) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		for (Thread thread : threads) {
			thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
			assertTrue(!thread.isAlive(), "thread " + thread.getName() + " is stuck in the gate");
		}
	}

	/** Many threads ask at the same instant: at most one goal and one challenge get through, and at least one thing does. */
	@Test
	void manyAsksAtOnceLetAtMostOneGoalAndOneChallengeThrough() throws Exception {
		for (int round = 0; round < 1500; round++) {
			StingGate gate = new StingGate();
			int threads = 12;
			CountDownLatch go = new CountDownLatch(1);
			AtomicInteger goals = new AtomicInteger();
			AtomicInteger challenges = new AtomicInteger();
			ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
			List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final boolean challenge = t % 3 == 0;
				Thread worker = new Thread(() -> {
					try {
						go.await();
						if (gate.allow(1_000_000, challenge))
							(challenge ? challenges : goals).incrementAndGet();
					} catch (Throwable e) {
						thrown.add(e);
					}
				}, "ask-" + t);
				workers.add(worker);
				worker.start();
			}
			go.countDown();
			join(workers);
			assertNull(thrown.peek(), "round " + round + ": a thread threw: " + thrown.peek());
			assertTrue(goals.get() <= 1, "round " + round + ": " + goals.get() + " goal stings played in one quiet minute");
			assertTrue(challenges.get() <= 1, "round " + round + ": " + challenges.get() + " challenge stings played in one quiet minute");
			assertTrue(goals.get() + challenges.get() >= 1, "round " + round + ": nothing played although the gate was open");
		}
	}

	/** After the quiet minute the gate opens again for exactly one more, even with the asks arriving together. */
	@Test
	void theGateOpensAgainOnceTheQuietMinuteHasPassed() throws Exception {
		StingGate gate = new StingGate();
		assertTrue(gate.allow(0, false));
		for (int round = 0; round < 200; round++) {
			long now = StingGate.QUIET_MILLIS * (round + 1);
			AtomicInteger played = new AtomicInteger();
			CountDownLatch go = new CountDownLatch(1);
			List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < 8; t++) {
				Thread worker = new Thread(() -> {
					try {
						go.await();
					} catch (InterruptedException e) {
						return;
					}
					if (gate.allow(now, false))
						played.incrementAndGet();
				}, "later-" + t);
				workers.add(worker);
				worker.start();
			}
			go.countDown();
			join(workers);
			assertEquals(1, played.get(), "round " + round + ": the gate let " + played.get() + " goal stings through a fresh quiet minute");
		}
	}
}
