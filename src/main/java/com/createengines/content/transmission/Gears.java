package com.createengines.content.transmission;

/**
 * The gears of a {@link TransmissionBlock}. A position is neutral, one of the forward gears or reverse. A gear's
 * ratio is how many turns the input makes for one of the output, so a low gear gives a slow output: the engine can
 * run where it makes its power while the load, which asks for far less torque of the crank in a low gear, turns slowly.
 */
public final class Gears {

	public static final int NEUTRAL = 0;
	public static final int FIRST = 1;
	public static final int FOURTH = 4;
	public static final int REVERSE = 5;
	public static final int POSITIONS = 6;

	/** Input turns per output turn, by position. Neutral has none. */
	private static final double[] RATIOS = {0, 4, 2.5, 1.6, 1, 3};

	private Gears() {
	}

	/** The position a redstone signal selects, or -1 if there is none and the panel decides. */
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
		return position >= FIRST && position <= FOURTH;
	}

	/** The short name shown on the panel and in goggles. */
	public static String label(int position) {
		return switch (clamp(position)) {
			case NEUTRAL -> "N";
			case REVERSE -> "R";
			default -> Integer.toString(position);
		};
	}

	public static int clamp(int position) {
		return Math.max(0, Math.min(POSITIONS - 1, position));
	}
}
