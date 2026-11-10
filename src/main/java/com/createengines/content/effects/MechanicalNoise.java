package com.createengines.content.effects;

/**
 * How loud and how high an engine's own machinery is, apart from its exhaust: the tick of its valve gear, which follows
 * the crank, and the squeal of its bearings, which follows how short of oil it is. Kept free of the game so it can be
 * tested.
 */
public final class MechanicalNoise {

	/** Speed, RPM, at which the valve gear is as loud as it gets. */
	public static final float CLATTER_FULL_RPM = 200;
	/** Speed, RPM, by which dry bearings are squealing as loud as they will. */
	public static final float SQUEAL_FULL_RPM = 48;
	/** Lubrication below which bearings start to squeal: the figure at which goggles call the engine dry. */
	public static final float DRY_BELOW = 0.3f;
	/** Speed against which pitch is reckoned: Create's usual limit. */
	private static final float TOP_RPM = 256;

	private MechanicalNoise() {
	}

	/**
	 * How loud the valve gear is, 0 to 1: it ticks over quietly as soon as the crank turns and grows with speed, and an
	 * engine that runs rough or short of oil rattles more than one that runs sweetly.
	 *
	 * @param roughness   share of the engine's rating its uneven running takes off, 0 to 0.4
	 * @param lubrication how well oiled the moving parts are, 0 (dry) to 1
	 */
	public static float clatterLevel(float rpm, float roughness, float lubrication) {
		float speed = Math.abs(rpm);
		if (speed < 1)
			return 0;
		float level = 0.3f + 0.5f * Math.min(1, speed / CLATTER_FULL_RPM);
		level *= 1 + Math.max(0, roughness);
		level += 0.25f * squealLevel(rpm, lubrication);
		return Math.min(1, level);
	}

	/** The valve gear's pitch: a slow tick at idle, half as fast again as the recording flat out. */
	public static float clatterPitch(float rpm) {
		return 0.6f + 0.9f * Math.min(1, Math.abs(rpm) / TOP_RPM);
	}

	/** How loud the bearings squeal, 0 to 1: silent with oil on them, loudest bone dry at any real speed. */
	public static float squealLevel(float rpm, float lubrication) {
		float speed = Math.abs(rpm);
		if (speed < 1 || lubrication >= DRY_BELOW)
			return 0;
		float dryness = (DRY_BELOW - Math.max(0, lubrication)) / DRY_BELOW;
		return dryness * Math.min(1, speed / SQUEAL_FULL_RPM);
	}

	/** The squeal's pitch, which rises a little with speed. */
	public static float squealPitch(float rpm) {
		return 0.85f + 0.3f * Math.min(1, Math.abs(rpm) / TOP_RPM);
	}
}
