package com.createengines.engine.sim;

/** How a throttle body's pedal maps to its valve. */
public final class Throttle {

	/** Strongest redstone signal. */
	public static final int FULL_SIGNAL = 15;

	/** How sharply air gain flattens off as the valve opens: a half-open valve already passes most of the air. */
	private static final double AIR_CURVE = 4;

	/**
	 * Furthest a throttle body's bore can be opened past the plain intake, as a multiple of it. Past 1 the valve passes
	 * more than the intake would on its own, which is what an engine that breathes badly at speed needs to turn faster.
	 */
	public static final double MAX_WIDE_OPEN = 1.5;

	private Throttle() {
	}

	/**
	 * The share of the injector's shot a throttle body's linkage lets through. It follows the curve of air against
	 * opening, not the air the cylinder actually breathes, which also falls with engine speed: closing the throttle
	 * no longer floods the engine, but the mixture still drifts with speed, so metering stays the player's to tune.
	 * Past full opening it gives no more: the shot stays the injector's, and a bigger bore only lets a fast engine, which
	 * runs rich because it cannot fill, breathe enough to burn it.
	 */
	public static double fuelTrim(double opening) {
		double clamped = Math.max(0, Math.min(1, opening));
		return (1 - Math.exp(-AIR_CURVE * clamped)) / (1 - Math.exp(-AIR_CURVE));
	}

	/** How open the valve is: its idle stop with no signal, rising evenly to wide open at full signal strength. */
	public static double opening(double idle, int signal) {
		return opening(idle, 1, signal);
	}

	/**
	 * How open the valve is with a bore that opens to {@code wideOpen} (1 for the plain intake, up to
	 * {@link #MAX_WIDE_OPEN}) at full signal strength.
	 */
	public static double opening(double idle, double wideOpen, int signal) {
		double wide = Math.max(idle, Math.min(MAX_WIDE_OPEN, wideOpen));
		return idle + (wide - idle) * Math.max(0, Math.min(FULL_SIGNAL, signal)) / FULL_SIGNAL;
	}
}
