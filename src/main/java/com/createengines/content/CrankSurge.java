package com.createengines.content;

import com.createengines.content.flywheel.SpringPackLag;

/**
 * How far a rough engine's crank throws, and the pistons on them, are drawn ahead of or behind a steady turn, in degrees:
 * the speed ripple as a surge, once for each firing. The speed an engine sends is its mean over a cycle, so without this
 * a rough crank would be drawn turning as evenly as a smooth one.
 * <p>
 * It follows the crank's own angle, so it keeps time with the firing and holds where the crank holds. Only drawn: it
 * changes no figure. The crank's plain segments, whose ends meet Create's shafts, are left square. A pure function with no
 * game imports, so it is unit-tested and bounded for every input.
 */
public final class CrankSurge {

	/**
	 * The most the throws are drawn off a steady turn, in degrees: small beside the 45 degrees {@link RenderRotation}
	 * may have to catch up, and under a quarter of a pixel at the corner of a shaft joined to a throw.
	 */
	public static final double CAP = 4;

	private CrankSurge() {
	}

	/**
	 * @param degrees how far the engine's crank has turned, not wrapped
	 * @param rpm     the crank's speed; none is drawn on a crank that is not turning
	 * @param ripple  the engine's speed ripple, a fraction of its mean speed
	 * @param firing  how many cylinders fire in each cycle of two turns
	 */
	public static double offset(double degrees, double rpm, double ripple, int firing) {
		if (!Double.isFinite(degrees) || !Double.isFinite(rpm) || rpm == 0 || firing <= 0 || !(ripple > 0))
			return 0;
		// The angle swing that makes the speed swing by half the ripple either side, once a firing. A ripple over 1 is held
		// there, which also keeps the drawn crank from ever turning backwards
		double swing = Math.min(ripple, 1) / 2 * 720 / (2 * Math.PI * firing);
		return Math.min(CAP, swing) * SpringPackLag.firingWave(degrees / 720 * firing);
	}
}
