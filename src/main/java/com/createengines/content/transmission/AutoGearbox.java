package com.createengines.content.transmission;

/**
 * How a transmission in auto picks its gear. It knows one thing about the engine behind it: how much of what the engine
 * can pull the network is asking for, its utilization. The load a network puts on a crank is its stress at the speed the
 * crank turns, and the output turns at the crank's speed over the gear's ratio, so in any other gear the same machines ask
 * for the utilization times the ratio now over the ratio then. That is enough to see which gears the engine could carry.
 * <p>
 * It holds the tallest gear that leaves room. A load past {@link #DOWNSHIFT_ABOVE} (or an overstressed network, which is a
 * utilization over one) drops straight to the tallest gear that would bring it under {@link #FIT}; with room to spare it
 * climbs one gear at a time, and only when the next gear up would still sit under {@link #CLIMB_TO}. The gaps between
 * those numbers are wider than a gear's step, so it settles instead of hunting.
 */
public final class AutoGearbox {

	/** Utilization above which the engine is judged to be struggling. */
	public static final double DOWNSHIFT_ABOVE = 0.9;
	/** What a downshift aims to get the utilization under. */
	public static final double FIT = 0.8;
	/** The most the next gear up may load the engine for the box to climb into it. */
	public static final double CLIMB_TO = 0.7;
	/** Ticks after a shift before a downshift is allowed: long enough for the load to be read afresh. */
	public static final int DOWN_DWELL = 10;
	/** Ticks after a shift before it climbs. */
	public static final int UP_DWELL = 30;

	private AutoGearbox() {
	}

	/**
	 * The gear to be in, given the one it is in.
	 *
	 * @param gear             a forward gear
	 * @param utilization      stress the network asks for over what the engine could give, in this gear; above 1 when overstressed
	 * @param ticksSinceShift  how long it has been in this gear
	 */
	public static int next(int gear, double utilization, int ticksSinceShift) {
		if (!Gears.isForward(gear))
			return Gears.FIRST;
		if (utilization > DOWNSHIFT_ABOVE) {
			if (ticksSinceShift < DOWN_DWELL)
				return gear;
			for (int candidate = gear - 1; candidate >= Gears.FIRST; candidate--)
				if (predicted(utilization, gear, candidate) <= FIT)
					return candidate;
			return Gears.FIRST;
		}
		if (gear < Gears.TOP && ticksSinceShift >= UP_DWELL && predicted(utilization, gear, gear + 1) <= CLIMB_TO)
			return gear + 1;
		return gear;
	}

	/** The utilization the same load would make in {@code to}, having been measured in {@code from}. */
	public static double predicted(double utilization, int from, int to) {
		return utilization * Gears.ratio(from) / Gears.ratio(to);
	}
}
