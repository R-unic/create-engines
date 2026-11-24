package com.createengines.engine.sim;

/**
 * How much of a dual-mass flywheel's secondary mass smooths the speed the network sees. The primary mass is bolted to
 * the crank and counts as plain inertia like any flywheel; the secondary sits behind a spring pack, and a spring pack
 * isolates only above its resonance, as a real one does: no help at or below the resonance, growing help above it, the
 * whole mass once well past it. A pure function of speed, so the simulation stays free of game imports.
 */
public final class DualMass {

	/** Speeds above the resonance, as a multiple of it, at which the secondary mass is fully isolated. */
	public static final double FULL_AT = 3;

	private DualMass() {
	}

	/**
	 * The share, 0 to 1, of the secondary mass that counts at {@code rpm} for a spring pack that resonates at
	 * {@code resonanceRpm}. Zero at or below the resonance, rising smoothly to one at {@link #FULL_AT} times it. Zero for
	 * any input that is not a finite, positive speed and resonance, so a corrupt value never helps.
	 */
	public static double isolation(double rpm, double resonanceRpm) {
		// A speed of zero or less needs no check of its own: it falls below the resonance and the clamp below gives 0
		if (!(Double.isFinite(rpm) && Double.isFinite(resonanceRpm)) || resonanceRpm <= 0)
			return 0;
		double t = Math.clamp((rpm / resonanceRpm - 1) / (FULL_AT - 1), 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** The inertia the ripple sees: the crank's plus the share of the secondary mass that is isolated at {@code rpm}. */
	public static double effectiveInertia(double crankInertia, double secondaryInertia, double rpm, double resonanceRpm) {
		return crankInertia + Math.max(0, secondaryInertia) * isolation(rpm, resonanceRpm);
	}
}
