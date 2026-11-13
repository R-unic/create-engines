package com.createengines.content.transmission;

/**
 * The gears of a {@link TransmissionBlock}. A position is neutral, one of the thirteen forward gears or reverse; on top
 * of those the value panel has one more setting, {@link #AUTO}, which is not a gear but lets the block pick one. A gear's
 * ratio is how many turns the input makes for one of the output, so a low gear gives a slow output: the engine can run
 * where it makes its power while the load, which asks far less torque of the crank in a low gear, turns slowly.
 * <p>
 * The forward gears are spaced evenly in ratio, each about a quarter (23 percent) faster than the last, from twelve
 * turns to one in first to a direct drive in thirteenth.
 */
public final class Gears {

	public static final int NEUTRAL = 0;
	public static final int FIRST = 1;
	public static final int TOP = 13;
	public static final int REVERSE = 14;
	public static final int POSITIONS = 15;
	/** A panel setting past reverse: the block shifts for itself. Never a position the output is in. */
	public static final int AUTO = 15;
	public static final int SETTINGS = 16;

	/** Input turns per output turn in first gear. */
	private static final double LOWEST_RATIO = 12;
	private static final double REVERSE_RATIO = 3;

	/** Input turns per output turn, by position. Neutral has none. */
	private static final double[] RATIOS = new double[POSITIONS];

	static {
		for (int gear = FIRST; gear <= TOP; gear++)
			RATIOS[gear] = Math.round(LOWEST_RATIO * Math.pow(1 / LOWEST_RATIO, (gear - FIRST) / (double) (TOP - FIRST)) * 100) / 100d;
		RATIOS[REVERSE] = REVERSE_RATIO;
	}

	private Gears() {
	}

	/** The position a redstone signal selects, or -1 if there is none and the panel decides. Strength 1 is neutral, 15 reverse. */
	public static int fromSignal(int signal) {
		if (signal <= 0)
			return -1;
		return Math.min(signal - 1, POSITIONS - 1);
	}

	/** Output speed over input speed: 0 in neutral, negative in reverse. */
	public static float speedModifier(int position) {
		int clamped = clamp(position);
		if (clamped == NEUTRAL)
			return 0;
		float modifier = (float) (1 / RATIOS[clamped]);
		return clamped == REVERSE ? -modifier : modifier;
	}

	/** Input turns per output turn, 0 in neutral. */
	public static double ratio(int position) {
		return RATIOS[clamp(position)];
	}

	public static boolean isForward(int position) {
		return position >= FIRST && position <= TOP;
	}

	/** The short name shown on the panel and in goggles: N, 1 to 13, R, or A for auto. */
	public static String label(int setting) {
		if (setting >= AUTO)
			return "A";
		return switch (clamp(setting)) {
			case NEUTRAL -> "N";
			case REVERSE -> "R";
			default -> Integer.toString(setting);
		};
	}

	/** A position, kept between neutral and reverse. */
	public static int clamp(int position) {
		return Math.max(0, Math.min(POSITIONS - 1, position));
	}

	/** A panel setting, kept between neutral and auto. */
	public static int clampSetting(int setting) {
		return Math.max(0, Math.min(SETTINGS - 1, setting));
	}

	/**
	 * The position a block saved with the old four gears and reverse was in: first to fourth were ratios of 4, 2.5, 1.6 and
	 * 1, which sit at sixth, ninth, eleventh and thirteenth of the thirteen now, and reverse was fifth.
	 */
	public static int fromLegacy(int oldPosition) {
		return switch (oldPosition) {
			case 1 -> 6;
			case 2 -> 9;
			case 3 -> 11;
			case 4 -> TOP;
			case 5 -> REVERSE;
			default -> NEUTRAL;
		};
	}
}
