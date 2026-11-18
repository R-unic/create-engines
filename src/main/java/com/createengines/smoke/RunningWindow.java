package com.createengines.smoke;

/**
 * Whether an engine that is meant to be running is, judged over a short window instead of one instant.
 *
 * <p>An engine is {@code RUNNING} only while some cylinder's last cycle fired. A single-cylinder spark engine fed rich
 * and boosted stutters by design (a fouled plug misfires, scrubs clean and fires again), so it reads {@code COASTING}
 * for a few cycles every few hundred ticks. A check that looks at one instant lands in such a gap now and then and
 * fails for a reason that is not a fault (seen once in 20 Aeronautics audit runs, 2026-11-18). What must never pass is
 * an engine that stays down, so the window is far longer than any stutter seen (20 ticks) and far shorter than a stall.
 */
public final class RunningWindow {

	/** Ticks an engine may have been out of {@code RUNNING} and still count as running; the longest stutter seen was 20. */
	public static final int DEFAULT_WINDOW = 100;

	private long lastRunning = Long.MIN_VALUE;

	/** Records what the engine was doing on {@code tick}. */
	public void observe(long tick, boolean running) {
		if (running && tick > lastRunning)
			lastRunning = tick;
	}

	/** True if the engine was seen running on {@code tick} or within the {@code window} ticks before it. */
	public boolean ranWithin(long tick, int window) {
		return lastRunning != Long.MIN_VALUE && window >= 0 && tick >= lastRunning && tick - lastRunning <= window;
	}

	/** Forgets everything seen, for an engine that has been out of the world. */
	public void reset() {
		lastRunning = Long.MIN_VALUE;
	}
}
