package com.createengines.engine.sim;

/**
 * The alternator's charge-rate dial as plain numbers. The dial has four settings; a setting is the share of the alternator's full
 * output it is allowed, and the charge it makes and the stress it puts on its shaft both scale with that share, so a lower setting
 * is a lighter load for a smaller charge. The top setting is 100%, which is what every alternator did before the dial existed.
 * No Minecraft types here, so it is unit tested.
 */
public final class ChargeRate {

	/** The lowest and highest setting of the dial. */
	public static final int MIN_SETTING = 0;
	public static final int MAX_SETTING = 3;
	/** The setting a new alternator has, and a saved one with no dial reads as: full output, as it always was. */
	public static final int DEFAULT_SETTING = MAX_SETTING;

	private static final int[] PERCENT = {25, 50, 75, 100};

	private ChargeRate() {
	}

	/** A setting held to the dial's range. */
	public static int clamp(int setting) {
		return SimMath.clamp(setting, MIN_SETTING, MAX_SETTING);
	}

	/** The share of full output a setting allows, 0.25 to 1. */
	public static double share(int setting) {
		return PERCENT[clamp(setting)] / 100.0;
	}

	/** The same share as a whole percent, for the goggles and the dial. */
	public static int percent(int setting) {
		return PERCENT[clamp(setting)];
	}
}
