package com.createengines.content.transmission;

/**
 * How a transmission in auto picks its gear. It knows one thing about the engine behind it: how much of what the engine
 * can pull the network is asking for, its utilization. The load a network puts on a crank is its stress at the speed the
 * crank turns, and the output turns at the crank's speed over the gear's ratio, so in any other gear the same machines ask
 * for the utilization times the ratio now over the ratio then. That is enough to see which gears the engine could carry.
 * <p>
 * It holds the tallest gear that leaves room. A load past a profile's {@code downshiftAbove} (or an overstressed network,
 * which is a utilization over one) drops straight to the tallest gear that would bring it under {@code fit}; with room to
 * spare it climbs one gear at a time, and only when the next gear up would still sit under {@code climbTo}. The gaps
 * between those numbers are wider than a gear's step, so it settles instead of hunting.
 * <p>
 * The three {@link Profile}s move those numbers and the waits. {@link Profile#NORMAL} is the original behaviour, kept
 * exactly, and is the default, so a transmission placed before profiles existed shifts as it always did.
 */
public final class AutoGearbox {

	/** Utilization above which the engine is judged to be struggling (normal profile). */
	public static final double DOWNSHIFT_ABOVE = 0.9;
	/** What a downshift aims to get the utilization under (normal profile). */
	public static final double FIT = 0.8;
	/** The most the next gear up may load the engine for the box to climb into it (normal profile). */
	public static final double CLIMB_TO = 0.7;
	/** Ticks after a shift before a downshift is allowed: long enough for the load to be read afresh (normal profile). */
	public static final int DOWN_DWELL = 10;
	/** Ticks after a shift before it climbs (normal profile). */
	public static final int UP_DWELL = 30;

	/**
	 * A shift personality. Each keeps {@code climbTo <= fit < downshiftAbove}: a downshift never lands where the box would
	 * climb straight back, and a climb never lands where it would drop straight out again.
	 *
	 * @param downshiftAbove utilization past which it drops gears
	 * @param fit            what a downshift aims to get the utilization under
	 * @param climbTo        the most the next gear up may load the engine for it to climb
	 * @param downDwell      ticks after a shift before a downshift is allowed
	 * @param upDwell        ticks after a shift before it climbs
	 */
	public enum Profile {
		/** Climbs early and sits tall, leaning on the engine's torque, so it runs slow and quiet and works harder. */
		ECONOMY(0.95, 0.85, 0.8, 15, 20),
		/** The original behaviour. */
		NORMAL(DOWNSHIFT_ABOVE, FIT, CLIMB_TO, DOWN_DWELL, UP_DWELL),
		/** Holds low gears and drops early, so the engine stays up in its revs with torque in hand. */
		SPORT(0.75, 0.6, 0.5, 5, 60);

		public final double downshiftAbove;
		public final double fit;
		public final double climbTo;
		public final int downDwell;
		public final int upDwell;

		Profile(double downshiftAbove, double fit, double climbTo, int downDwell, int upDwell) {
			this.downshiftAbove = downshiftAbove;
			this.fit = fit;
			this.climbTo = climbTo;
			this.downDwell = downDwell;
			this.upDwell = upDwell;
		}

		/** The value the panel's dial holds for this profile. */
		public int setting() {
			return ordinal();
		}

		/** The profile for a dial value; anything out of range is the normal one. */
		public static Profile fromSetting(int setting) {
			Profile[] all = values();
			return setting >= 0 && setting < all.length ? all[setting] : NORMAL;
		}
	}

	private AutoGearbox() {
	}

	/** The gear to be in, on the normal profile. */
	public static int next(int gear, double utilization, int ticksSinceShift) {
		return next(Profile.NORMAL, gear, utilization, ticksSinceShift);
	}

	/**
	 * The gear to be in, given the one it is in.
	 *
	 * @param profile          how eager it is to shift
	 * @param gear             a forward gear
	 * @param utilization      stress the network asks for over what the engine could give, in this gear; above 1 when overstressed
	 * @param ticksSinceShift  how long it has been in this gear
	 */
	public static int next(Profile profile, int gear, double utilization, int ticksSinceShift) {
		if (!Gears.isForward(gear))
			return Gears.FIRST;
		if (utilization > profile.downshiftAbove) {
			if (ticksSinceShift < profile.downDwell)
				return gear;
			for (int candidate = gear - 1; candidate >= Gears.FIRST; candidate--)
				if (predicted(utilization, gear, candidate) <= profile.fit)
					return candidate;
			return Gears.FIRST;
		}
		if (gear < Gears.TOP && ticksSinceShift >= profile.upDwell && predicted(utilization, gear, gear + 1) <= profile.climbTo)
			return gear + 1;
		return gear;
	}

	/** The utilization the same load would make in {@code to}, having been measured in {@code from}. */
	public static double predicted(double utilization, int from, int to) {
		return utilization * Gears.ratio(from) / Gears.ratio(to);
	}
}
