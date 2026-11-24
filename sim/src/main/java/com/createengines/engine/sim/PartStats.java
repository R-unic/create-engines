package com.createengines.engine.sim;

/**
 * A pack maker's retuning of one kind of cylinder, as scales on the stats the built-in tiers give it (a data map, see
 * {@code CEDataMaps.PART_STATS}; a format frozen at 2.0). 1 leaves a stat as it is, so an
 * entry names only what it changes, and with no entry at all {@link #apply} hands back the very spec it was given: a pack
 * that does not use this sees exactly what it saw before. Plain Java so the arithmetic is tested without a game.
 *
 * @param compressionRatio scale on the compression ratio (it never ends up below {@link #MIN_COMPRESSION})
 * @param intakeFlow       scale on how freely the intake valve breathes
 * @param exhaustFlow      scale on how freely the exhaust valve breathes
 * @param maxFuelPerShot   scale on the most fuel a shot can hold
 * @param ignitionDelay    scale on the time from injection to ignition
 * @param burnDuration     scale on how long a shot takes to burn
 * @param frictionStatic   scale on the friction that does not depend on speed
 * @param frictionViscous  scale on the friction that grows with speed
 * @param inertia          scale on the rotating and reciprocating inertia
 * @param displacement     scale on swept volume (gas and work scale with it; scale {@code maxFuelPerShot} to match)
 * @param injectorFlow     scale on how much more than the usual most the injector can deliver
 */
public record PartStats(double compressionRatio, double intakeFlow, double exhaustFlow, double maxFuelPerShot,
						double ignitionDelay, double burnDuration, double frictionStatic, double frictionViscous,
						double inertia, double displacement, double injectorFlow) {

	/** Every scale 1: changes nothing. */
	public static final PartStats NONE = new PartStats(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);

	/** Smallest and largest scale a data map entry may carry. */
	public static final double MIN_SCALE = 0.1;
	public static final double MAX_SCALE = 10;

	/** A compression ratio below this is not an engine that runs, so a scale cannot take a spec under it. */
	public static final double MIN_COMPRESSION = 1.5;

	/** Whether this changes anything. */
	public boolean isNone() {
		return this.equals(NONE);
	}

	/** The spec with these scales applied: the same instance when nothing changes. */
	public CylinderSpec apply(CylinderSpec spec) {
		if (isNone())
			return spec;
		return new CylinderSpec(Math.max(MIN_COMPRESSION, spec.compressionRatio() * compressionRatio), spec.rodRatio(),
			spec.intakeFlow() * intakeFlow, spec.exhaustFlow() * exhaustFlow, spec.maxFuelPerShot() * maxFuelPerShot,
			spec.ignitionDelay() * ignitionDelay, spec.burnDuration() * burnDuration, spec.frictionStatic() * frictionStatic,
			spec.frictionViscous() * frictionViscous, spec.inertia() * inertia, spec.displacement() * displacement,
			spec.injectorFlow() * injectorFlow);
	}
}
