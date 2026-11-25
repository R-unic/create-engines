package com.createengines.engine.sim;

/**
 * The wear mode's knock: a spark engine whose charge lights itself early (pre-ignition, see {@code FuelSpec#preignitionLoss}) hammers its
 * pistons. The engine already loses power to it; with the wear mode on it also wears the piston faster and can be heard. Knock never makes
 * a part fail by itself: it feeds the piston's ordinary wear, so a worn-out piston is cured the ordinary way. Pure numbers, see {@link Wear}.
 * <p>
 * Nothing here runs with the wear mode off. Knock was on the Declined list; the author approved it as a wear-mode-only failure on
 * 2026-11-24 (see "2.3 Wear and Tear" in docs/ROADMAP-3.0.md). {@code WearKeeper} adds the wear to each knocking piston, and the client
 * plays the ping. A rotor housing has no piston, so it does not knock.
 */
public final class Knock {

	/** The share of the burn lost to pre-ignition below which nothing is felt: the goggles show it from 2%. */
	public static final double FROM = 0.04;
	/** Seconds a piston lasts at full knock, at the reference speed. */
	public static final double LIFE = 20 * 60;
	/** Pre-ignition losing this share of the burn (and more) can be heard as pinging. */
	public static final double AUDIBLE_FROM = 0.07;

	private Knock() {
	}

	/** How hard the engine is knocking, 0 (none) to 1 (as bad as pre-ignition gets), from the share of the burn it loses to early lighting. */
	public static double intensity(double preignitionLoss) {
		if (!Double.isFinite(preignitionLoss))
			return 0;
		return Math.clamp((preignitionLoss - FROM) / (FuelSpec.PREIGNITION_MAX_LOSS - FROM), 0, 1);
	}

	/** The extra piston wear in {@code dt} seconds: nothing without knock, growing with the square of how hard and with speed. */
	public static double pistonWear(double preignitionLoss, double rpm, double dt) {
		double hard = intensity(preignitionLoss);
		if (!Double.isFinite(dt))
			return 0;
		return Math.min(1, Math.max(0, dt) / LIFE * hard * hard * Wear.speedFactor(rpm));
	}

	/** A piston's knock wear for a standard or a precision piston, which lasts {@link Wear#PRECISION_LIFE} times as long against it too. */
	public static double pistonWear(double preignitionLoss, double rpm, double dt, boolean precision) {
		double wear = pistonWear(preignitionLoss, rpm, dt);
		return precision ? wear / Wear.PRECISION_LIFE : wear;
	}

	/** Whether the knock is loud enough to hear. */
	public static boolean audible(double preignitionLoss) {
		return preignitionLoss >= AUDIBLE_FROM;
	}
}
