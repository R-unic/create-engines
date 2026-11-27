package com.createengines.engine.sim;

/**
 * The {@code balance.rpmScale} setting: a loss-free gearing between the engine and the shaft it drives. The engine itself
 * is simulated exactly as it is at 1; its shaft turns {@code scale} times as fast and offers its stress capacity per RPM
 * divided by {@code scale}, so capacity times speed (the total SU), and what the engine burns to make it, do not change.
 * <p>
 * A faster shaft needs room under Create's own speed limit ({@code maxRotationSpeed}). A scale above 1 is only applied as
 * far as that limit leaves the engine itself {@value #KEPT_RPM} RPM (Create's default limit) to run in, so turning the
 * setting up without raising Create's limit never takes power away.
 */
public final class OutputGearing {

	/** The engine speed a scale above 1 never squeezes the engine under: Create's default RPM limit. */
	public static final int KEPT_RPM = 256;

	private OutputGearing() {
	}

	/**
	 * The scale in force.
	 *
	 * @param setting   {@code balance.rpmScale}
	 * @param createMax Create's {@code maxRotationSpeed}, RPM
	 */
	public static double scale(double setting, double createMax) {
		// A setting of 1 or less is below the ceiling, which is never under 1, so it is always in force
		return Math.min(setting, Math.max(1, createMax / KEPT_RPM));
	}

	/** The fastest the engine itself may turn, RPM, so that its shaft stays within Create's limit. */
	public static double engineLimit(double scale, double createMax) {
		// Slowed down, the engine keeps the limit it has at 1, so it runs as it does there
		return createMax / Math.max(1, scale);
	}
}
