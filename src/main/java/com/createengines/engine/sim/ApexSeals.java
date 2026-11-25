package com.createengines.engine.sim;

/**
 * How a rotor housing's apex seals wear in the opt-in wear mode (see {@link Wear}). The three seals at the rotor's tips are what a Wankel
 * is known for losing: they stand in for the rings and the rod a rotor housing does not have. One set of seals belongs to a housing, so
 * its wear is one number, from 0 (new) to 1 (spent), shared by the three chambers.
 * <p>
 * The curve is the rotary's own, not the piston's: the seals run at the shaft's speed times three apexes, hate heat (a rotor housing runs
 * hot), and live on the oil film that lubricates their tips, so running dry is far harder on them than on a piston. A worn set lets the
 * chambers leak into each other and so costs power in all three, and a set that fails stops the housing until the seals are replaced.
 * Plain numbers with no Minecraft types, so it is unit tested; {@code RotorHousingBlockEntity} holds the wear and {@code WearKeeper}
 * applies it. With the mode off none of it is called.
 * <p>
 * First-pass numbers, the author's to tune. A set lasts four hours at its reference speed, warm and oiled.
 */
public final class ApexSeals {

	/** A shaft speed, in RPM, at which a housing running warm on good oil wears its seals at the basic rate (a rotary turns faster than a piston engine). */
	public static final double REFERENCE_RPM = 192;
	/** Seconds a set of seals lasts at the reference speed, warm and oiled. */
	public static final double LIFE = 4 * 3600;
	/** How much worse heat is on the seals than on a piston: this much more for each unit over normal. */
	public static final double HEAT_STRESS = 4;
	/** How much worse running without oil is on the seals: 1 with them fully filmed, {@code 1 + DRY_STRESS} with none. */
	public static final double DRY_STRESS = 6;
	/** A completely worn set costs each chamber this much of its burn (the leak between chambers). */
	public static final double SEAL_LOSS = 0.35;
	/**
	 * How many times a plain cylinder's static friction each chamber of a failed set grips the shaft with: the broken seal is wedged between the
	 * rotor and its housing, so a lone rotor stops within a few seconds instead of coasting. It adds no inertia (that would keep it turning longer).
	 */
	public static final double WRECK_GRIP = 40;

	private ApexSeals() {
	}

	/** How much wear the seals take on top of the basic rate at this speed: a floor when slow, squared up to a cap when fast. */
	public static double speedFactor(double rpm) {
		if (!(rpm > 0))
			return 0;
		double share = Math.min(Wear.SPEED_CAP, rpm / REFERENCE_RPM);
		return Math.max(Wear.IDLE_FLOOR, share * share);
	}

	/** How much worse hot running is for the seals: nothing at or below normal temperature. */
	public static double heatFactor(double temperature) {
		double t = Double.isFinite(temperature) ? temperature : Thermal.NORMAL;
		return 1 + HEAT_STRESS * Math.max(0, t - Thermal.NORMAL);
	}

	/** How much worse running without oil is for the seals: 1 with the film whole. */
	public static double dryFactor(double film) {
		double f = Double.isFinite(film) ? Math.clamp(film, 0, 1) : 1;
		return 1 + DRY_STRESS * (1 - f);
	}

	/** Wear the seals take in {@code dt} seconds. */
	public static double wear(double rpm, double temperature, double film, double dt) {
		// A step that is not a finite span of time wears nothing, and no one step can spend more than a whole set
		if (!Double.isFinite(dt))
			return 0;
		return Math.min(1, Math.max(0, dt) / LIFE * speedFactor(rpm) * heatFactor(temperature) * dryFactor(film));
	}

	/** The share of its burn a chamber keeps with seals this worn: 1 when sound, falling with the square of the wear. */
	public static double seal(double wear) {
		double w = Wear.clamp(wear);
		return 1 - SEAL_LOSS * w * w;
	}

	/**
	 * The chance, in {@code dt} seconds, that seals this worn fail: zero below {@link Wear#FAILURE_FROM}, then rising with the cube of how far
	 * past it the wear is and with the shaft's speed against this part's reference. Never above 1.
	 */
	public static double failureChance(double wear, double rpm, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		double past = Math.max(0, (Wear.clamp(wear) - Wear.FAILURE_FROM) / (1 - Wear.FAILURE_FROM));
		return Math.min(1, Wear.FAILURE_RATE * past * past * past * Math.max(1, speedFactor(rpm)) * Math.max(0, dt));
	}

	/** What a chamber reads from seals this worn: it keeps less of its burn, and drags no harder (the seals leak; they do not bind). */
	public static Wear.State state(double wear) {
		double seal = seal(wear);
		return new Wear.State() {
			@Override
			public double seal() {
				return seal;
			}

			@Override
			public double drag() {
				return 1;
			}
		};
	}
}
