package com.createengines.content.effects;

/** Pitches sounds are played at: how far a one-off's strays from take to take, and where a turbo's whistle sits. Pure Java, so it is tested. */
public final class Pitches {

	/** Either way of its own pitch: more than this and the same engine sounds like a different one each time. */
	public static final float SPREAD = 0.03f;

	private Pitches() {
	}

	/**
	 * The pitch of a turbo's whistle with its wheel at {@code spool} of design speed (1 is design speed; past 1.2 it climbs no
	 * further): from a low breath at a slow wheel to a whistle, climbing well over an octave on the way.
	 */
	public static float turboWhistle(float spool) {
		return 0.4f + 0.7f * Math.min(1.2f, spool);
	}

	/** {@code pitch}, moved by up to {@link #SPREAD} of itself, for {@code roll} from 0 to 1. */
	public static float near(float pitch, float roll) {
		return pitch * (1 + SPREAD * (2 * roll - 1));
	}
}
