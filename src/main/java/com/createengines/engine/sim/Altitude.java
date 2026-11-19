package com.createengines.engine.sim;

/**
 * How thin the air is at a height. An engine's intake charge is scaled by this, so a naturally aspirated engine loses power as
 * it climbs and a compressor, which multiplies whatever air reaches it, earns its place.
 * <p>
 * Flat up to {@link #START} (the whole of an ordinary build), then falling the way real air does, by a fixed share every
 * {@link #SCALE} blocks, down to a floor so that an engine at any height still runs. The numbers are a first choice, not a
 * measured one: at the vanilla build limit (320) the air is about 0.85 of sea level. A running engine feels it about
 * two and a half times over, since friction and pumping do not thin with the air: a dynamometer sweep of a four-cylinder
 * crude engine lost 43 percent of its net peak torque at 0.7.
 */
public final class Altitude {

	/** Height, in blocks, up to which the air is as thick as it is at sea level. */
	public static final double START = 128;

	/** Blocks of climb over which the density falls by a factor of e. */
	public static final double SCALE = 1200;

	/** The thinnest the air gets, so an engine far above the clouds still turns over. */
	public static final double FLOOR = 0.2;

	private Altitude() {
	}

	/** Air density at {@code y}, 1 at or below {@link #START}, never below {@link #FLOOR}. Not a number in, 1 out. */
	public static double density(double y) {
		if (Double.isNaN(y))
			return 1;
		return Math.max(FLOOR, Math.min(1, Math.exp(-(y - START) / SCALE)));
	}
}
