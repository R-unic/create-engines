package com.createengines.engine.sim;

/**
 * How many parts of each kind have failed in a build, packed into one int a byte to a kind, so the engine's summary can carry it and the Black Box can
 * tell when one more has failed. Pure arithmetic: {@code WearKeeper} counts the failures, {@code FlightRecorder} watches them rise.
 */
public final class WearFailures {

	/** The failures the wear mode has, in the order they are packed (the first is the lowest byte). */
	public enum Kind {
		ROD_THROWN, GASKET_BLOWN, PISTON_SEIZED, APEX_FAILED
	}

	private WearFailures() {
	}

	/**
	 * The counts as one int; a count past 127 is held at 127 (so the top bit is never set and a packed figure is never negative, which is how
	 * "no reading" is told from one) and one below nothing is none.
	 */
	public static int pack(int rods, int gaskets, int seizures, int apex) {
		return clamp(rods) | clamp(gaskets) << 8 | clamp(seizures) << 16 | clamp(apex) << 24;
	}

	/** How many of {@code kind} the packed counts hold. */
	public static int count(int packed, Kind kind) {
		return packed >>> (8 * kind.ordinal()) & 0xFF;
	}

	/** What a figure is when there is no reading (an old sample, or a tape just loaded). */
	public static final int NO_READING = -1;

	/** Whether there are more of {@code kind} after than before. Either being {@link #NO_READING} is never a rise. */
	public static boolean rose(int before, int after, Kind kind) {
		return before != NO_READING && after != NO_READING && count(after, kind) > count(before, kind);
	}

	private static int clamp(int count) {
		return Math.clamp(count, 0, 127);
	}
}
