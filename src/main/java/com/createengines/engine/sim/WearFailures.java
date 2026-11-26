package com.createengines.engine.sim;

/**
 * How many parts of each kind have failed in a build, packed into one long a byte to a kind, so the engine's summary can carry it and the Black Box can
 * tell when one more has failed. Pure arithmetic: {@code WearKeeper} counts the failures, {@code FlightRecorder} watches them rise.
 */
public final class WearFailures {

	/** The failures the wear mode has, in the order they are packed (the first is the lowest byte). */
	public enum Kind {
		ROD_THROWN, GASKET_BLOWN, PISTON_SEIZED, APEX_FAILED, HEAD_WARPED, TURBO_FAILED
	}

	private WearFailures() {
	}

	/** The first four kinds, with none of the later ones: see {@link #pack(int, int, int, int, int, int)}. */
	public static long pack(int rods, int gaskets, int seizures, int apex) {
		return pack(rods, gaskets, seizures, apex, 0, 0);
	}

	/**
	 * The counts as one long; a count past 127 is held at 127 and one below nothing is none, so a packed figure is never negative, which is
	 * how "no reading" is told from one. The first four kinds sit where they always did, so a figure saved when it was an int reads the same.
	 */
	public static long pack(int rods, int gaskets, int seizures, int apex, int warps, int turbos) {
		return clamp(rods) | (long) clamp(gaskets) << 8 | (long) clamp(seizures) << 16 | (long) clamp(apex) << 24
			| (long) clamp(warps) << 32 | (long) clamp(turbos) << 40;
	}

	/** How many of {@code kind} the packed counts hold. */
	public static int count(long packed, Kind kind) {
		return (int) (packed >>> (8 * kind.ordinal()) & 0xFF);
	}

	/** What a figure is when there is no reading (an old sample, or a tape just loaded). */
	public static final int NO_READING = -1;

	/** Whether there are more of {@code kind} after than before. Either being {@link #NO_READING} is never a rise. */
	public static boolean rose(long before, long after, Kind kind) {
		return before != NO_READING && after != NO_READING && count(after, kind) > count(before, kind);
	}

	private static int clamp(int count) {
		return Math.clamp(count, 0, 127);
	}
}
