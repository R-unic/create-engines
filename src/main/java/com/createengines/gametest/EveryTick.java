package com.createengines.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Runs a task on every tick of a test, in place of {@link GameTestHelper#onEachTick}.
 * <p>
 * The vanilla method stores one entry for every tick up to the timeout and looks at all of them every tick, so its cost grows
 * with the square of the timeout: a sweep with a timeout in the tens of thousands spent most of its time on that list. This
 * keeps one entry, adding the next tick's from a sequence, which is ticked after the list has been walked and so may add to it.
 * The task itself still runs from the list, so a failed assertion in it fails the test as before.
 */
final class EveryTick {

	private EveryTick() {
	}

	/** Runs {@code task} on this tick and each one after it, until the test ends. Call from the test method itself. */
	static void run(GameTestHelper helper, Runnable task) {
		helper.runAtTickTime(helper.getTick(), task::run);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.runAfterDelay(1, task::run);
				// Never satisfied, so it is asked again next tick; at the timeout this is the failure the test reports
				throw new GameTestAssertException("Didn't succeed or fail before the timeout");
			});
	}
}
