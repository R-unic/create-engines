package com.createengines.advancement;

/**
 * Keeps the sting a treat: after one plays, the next waits a minute, so a player earning several goals at once, or a
 * server catching up on a world, hears one flourish and not a pile. A challenge may follow a goal at once, because it is
 * the bigger moment, but never another challenge within the minute.
 */
public final class StingGate {

	public static final long QUIET_MILLIS = 60_000;

	/** Whether an advancement is worth a sting: a goal or a challenge, and one that shows its toast. */
	public static boolean worthIt(boolean goalOrChallenge, boolean showsToast) {
		return goalOrChallenge && showsToast;
	}

	private boolean played;
	private long lastAt;
	private boolean lastWasChallenge;

	/** Whether a sting may play at {@code nowMillis}; if it may, the gate counts it as played. */
	public synchronized boolean allow(long nowMillis, boolean challenge) {
		// A clock that went backwards must not silence the sting for good
		boolean quiet = played && nowMillis >= lastAt && nowMillis - lastAt < QUIET_MILLIS;
		if (quiet && !(challenge && !lastWasChallenge))
			return false;
		played = true;
		lastAt = nowMillis;
		lastWasChallenge = challenge;
		return true;
	}
}
