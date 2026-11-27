package com.createengines.content.effects;

/**
 * How loud and how high an engine's own machinery is, apart from its exhaust: the squeal of its bearings, which follows
 * how short of oil it is, and the roar of its intake. Kept free of the game so it can be tested.
 */
public final class MechanicalNoise {

	/** Speed, RPM, by which dry bearings are squealing as loud as they will. */
	public static final float SQUEAL_FULL_RPM = 48;
	/** Lubrication below which bearings start to squeal: the figure at which goggles call the engine dry. */
	public static final float DRY_BELOW = 0.3f;
	/** Speed against which pitch is reckoned: Create's usual limit. */
	private static final float TOP_RPM = 256;

	private MechanicalNoise() {
	}

	/** How loud the bearings squeal, 0 to 1: silent with oil on them, loudest bone dry at any real speed. */
	public static float squealLevel(float rpm, float lubrication) {
		float speed = Math.abs(rpm);
		if (speed < 1 || lubrication >= DRY_BELOW)
			return 0;
		float dryness = (DRY_BELOW - Math.max(0, lubrication)) / DRY_BELOW;
		return dryness * Math.min(1, speed / SQUEAL_FULL_RPM);
	}

	/** Squeal level past which dry bearings are running hot enough to smoke out of the crankcase. */
	public static final float SMOKES_FROM = 0.35f;

	/**
	 * Chance, per check, that a dry engine's crankcase breathes out a puff of oil smoke, 0 to 0.6: none until the bearings
	 * squeal hard, then more the drier and faster it runs.
	 */
	public static float crankcaseSmoke(float rpm, float lubrication) {
		float squeal = squealLevel(rpm, lubrication);
		if (squeal < SMOKES_FROM)
			return 0;
		return 0.6f * (squeal - SMOKES_FROM) / (1 - SMOKES_FROM);
	}

	/** Speed, RPM, at which the intake is as loud as it gets with the throttle wide. */
	public static final float ROAR_FULL_RPM = 160;
	/** Boost, as the engine's boost reading, at which a blower has added all the roar it will. */
	public static final float ROAR_FULL_BOOST = 1.5f;

	/**
	 * How loud the air rushing into the intake is, 0 to 1. Never constant: nothing at a standstill, a murmur at idle, and
	 * more with speed, with how hard the engine is working (a hard pull against a light one) and with boost. A
	 * coasting engine only pumps air, so it is quiet.
	 *
	 * @param load  share of the engine's available torque it is working against, 0 to 1.3 or so
	 * @param boost how hard a turbo or blower is pushing, 0 for none
	 */
	public static float intakeRoarLevel(float rpm, float load, float boost) {
		float speed = Math.abs(rpm);
		if (speed < 1)
			return 0;
		float revs = Math.min(1, speed / ROAR_FULL_RPM);
		float work = Math.min(1, Math.max(0, load));
		float pull = Math.min(1, Math.max(0, boost) / ROAR_FULL_BOOST);
		float level = revs * (0.12f + 0.55f * work + 0.33f * pull);
		return Math.min(1, level);
	}

	/**
	 * The intake's pitch, which sets how fast its gulps come: the loop has eight a second, so it runs at the pace the
	 * cylinders take air, one gulp a cylinder for every two turns of the crank.
	 */
	public static float intakeRoarPitch(float rpm, int cylinders) {
		float gulpsPerSecond = Math.abs(rpm) / 60f * Math.max(1, cylinders) / 2f;
		return Math.min(2f, Math.max(0.5f, gulpsPerSecond / INTAKE_LOOP_PULSES));
	}

	/** Gulps in one second of the recording. */
	private static final float INTAKE_LOOP_PULSES = 8f;

	/** The squeal's pitch, which rises a little with speed. */
	public static float squealPitch(float rpm) {
		return 0.85f + 0.3f * Math.min(1, Math.abs(rpm) / TOP_RPM);
	}
}
