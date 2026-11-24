package com.createengines.engine.sim;

/**
 * The spring-pack dial of a Dual-Mass Flywheel as plain numbers. The dial has nine settings and the middle one is the part as it
 * was before the dial existed. A softer pack resonates at a lower speed, so its secondary mass starts to help earlier, but it
 * is a lighter secondary mass and helps less once it is isolated; a stiffer pack resonates higher and helps nothing until the
 * engine is further up the revs, but it is a heavier secondary mass and helps more there. Neither end is better everywhere, and
 * the middle setting is not a penalty, so there is nothing to fail. No Minecraft types here, so it is unit tested.
 */
public final class SpringPack {

	/** The lowest and highest setting of the dial. */
	public static final int MIN_SETTING = 0;
	public static final int MAX_SETTING = 8;
	/** The setting a new flywheel has, and a saved one with no dial reads as: the numbers the server settings give. */
	public static final int DEFAULT_SETTING = 4;

	/** How much higher the resonance is at the stiffest setting than at the middle one (and how much lower at the softest, by the same ratio). */
	public static final double RESONANCE_RATIO = 1.5;
	/** How much heavier the secondary mass is at the stiffest setting than at the middle one (and how much lighter at the softest). */
	public static final double MASS_RATIO = 1.25;

	private SpringPack() {
	}

	/** A setting held to the dial's range. */
	public static int clamp(int setting) {
		return SimMath.clamp(setting, MIN_SETTING, MAX_SETTING);
	}

	/** Where a setting sits from the softest, -1, through the middle, 0, to the stiffest, 1. */
	public static double position(int setting) {
		return (clamp(setting) - DEFAULT_SETTING) / (double) (MAX_SETTING - DEFAULT_SETTING);
	}

	/** What the server's resonance is multiplied by: exactly 1 at the middle setting, so an untouched flywheel is unchanged. */
	public static double resonanceFactor(int setting) {
		return Math.pow(RESONANCE_RATIO, position(setting));
	}

	/** What the server's secondary mass is multiplied by: exactly 1 at the middle setting. */
	public static double massFactor(int setting) {
		return Math.pow(MASS_RATIO, position(setting));
	}
}
