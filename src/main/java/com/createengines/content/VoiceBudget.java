package com.createengines.content;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Decides whether a looping voice may be started, so that no engine state, however it flaps, can make the sound engine
 * start and drop voices faster than it can bear. Every start allocates a sound instance and an OpenAL source, and a
 * dry or half-broken engine whose figures hover around a threshold would otherwise do that every few ticks, for every
 * engine in earshot. Three limits, all independent of what the caller asks for:
 * <ul>
 * <li>a cap on voices alive at once,</li>
 * <li>a rest after a voice finishes before its owner may start another, and</li>
 * <li>a cap on starts in any one second.</li>
 * </ul>
 * Pure Java so it can be tested; game time is passed in.
 */
public final class VoiceBudget {

	/** Ticks an owner must wait after its voice finished before it may start another. */
	static final int REST_TICKS = 40;
	/** Length, in ticks, of the window in which starts are counted. */
	static final int WINDOW_TICKS = 20;
	/** Most starts in one window, across every owner. */
	static final int STARTS_PER_WINDOW = 4;
	/** Rest entries older than this are dropped, so owners that are gone do not pile up. */
	private static final int FORGET_TICKS = 400;

	private final int maxAlive;
	private final Map<Object, Long> finishedAt = new IdentityHashMap<>();
	private long windowStart = Long.MIN_VALUE / 2;
	private int startsInWindow;

	public VoiceBudget(int maxAlive) {
		this.maxAlive = maxAlive;
	}

	/** Whether {@code owner} may start a voice now, given {@code alive} voices already going. Counts the start if so. */
	public boolean tryStart(Object owner, long now, int alive) {
		if (alive >= maxAlive)
			return false;
		Long finished = finishedAt.get(owner);
		if (finished != null && now - finished >= 0 && now - finished < REST_TICKS)
			return false;
		long age = now - windowStart;
		if (age < 0 || age >= WINDOW_TICKS) {
			windowStart = now;
			startsInWindow = 0;
		}
		if (startsInWindow >= STARTS_PER_WINDOW)
			return false;
		startsInWindow++;
		return true;
	}

	/** {@code owner}'s voice has ended, by fading out or by the sound engine dropping it. */
	public void finished(Object owner, long now) {
		finishedAt.put(owner, now);
	}

	/**
	 * {@code owner}'s voice was lost without its owner asking: the sound engine dropped it, or stopped reading it, as it does
	 * to a stream that ran dry in a hitch. One that had played and lived at least as long as a rest may be replaced at once,
	 * so the hitch is heard as a short gap and not a two-second one. Anything shorter-lived, or never played (a start the
	 * sound engine declined), rests as ever, so no owner can start more than one voice a rest this way either.
	 */
	public void lost(Object owner, long now, long age, boolean played) {
		if (played && age >= REST_TICKS)
			finishedAt.remove(owner);
		else
			finished(owner, now);
	}

	/** Forgets rests that have run out, and any from another world's clock. */
	public void prune(long now) {
		Iterator<Map.Entry<Object, Long>> it = finishedAt.entrySet()
			.iterator();
		while (it.hasNext()) {
			long age = now - it.next()
				.getValue();
			if (age < 0 || age >= FORGET_TICKS)
				it.remove();
		}
	}

	/** Forgets everything, for when the player leaves a world. */
	public void clear() {
		finishedAt.clear();
		windowStart = Long.MIN_VALUE / 2;
		startsInWindow = 0;
	}

	/** How many owners are being remembered, for tests. */
	int remembered() {
		return finishedAt.size();
	}
}
