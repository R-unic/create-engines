package com.createengines.engine;

/**
 * The masses of a flywheel that is a pack's or an addon's own, as the {@code flywheel} data map gives them: a primary mass
 * that joins the crank's inertia like any flywheel, and an optional secondary mass behind a spring pack that only smooths the
 * ripple the network sees (see {@link com.createengines.engine.sim.DualMass}). With no secondary mass it is a plain flywheel
 * of its primary mass; with the Cast Flywheel's numbers it is a Cast Flywheel.
 * <p>
 * The spring pack's resonance is the server's {@code dualMassResonance} setting, one for every dual-mass part, so a flywheel
 * of this kind has no resonance of its own. Plain Java: the game side reads the data map and the scanner calls this.
 */
public record FlywheelSpec(double primaryInertia, double secondaryInertia) {

	/** A flywheel that adds nothing: a block of this kind with no data map entry. */
	public static final FlywheelSpec NONE = new FlywheelSpec(0, 0);

	/** The largest mass either may carry, the same as the server settings for the flywheels of this mod. */
	public static final double MAX_INERTIA = 100.0;

	public FlywheelSpec {
		if (!(primaryInertia >= 0 && primaryInertia <= MAX_INERTIA))
			throw new IllegalArgumentException("primary_inertia is between 0 and " + MAX_INERTIA + ", not " + primaryInertia);
		if (!(secondaryInertia >= 0 && secondaryInertia <= MAX_INERTIA))
			throw new IllegalArgumentException("secondary_inertia is between 0 and " + MAX_INERTIA + ", not " + secondaryInertia);
	}
}
