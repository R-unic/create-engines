package com.createengines.engine.sim;

/**
 * The wear mode's turbocharger: the bearings and the wheel wear with boost, exhaust heat and a dry oil feed, a worn turbo makes less boost
 * (its wheel no longer seals), and a badly worn one can fail, which leaves it making no boost until it is replaced. A failure never harms
 * the engine: it only loses what the turbo gave. Pure numbers, see {@link Wear}.
 */
public final class TurboWear {

	/** Seconds a turbo lasts working at the reference load (half a bar of boost) with warm, clean oil. */
	public static final double LIFE = 6 * 3600;
	/** The boost, in bars over ambient, that is the reference load. */
	public static final double REFERENCE_BOOST = 0.5;
	/** Idling with no boost still turns the wheel on its bearings: this share of the reference wear. */
	public static final double IDLE_FLOOR = 0.1;
	/** A spent turbo makes this much less boost. */
	public static final double MAX_BOOST_LOSS = 0.3;
	/** Failure chance per second of a spent turbo at the reference boost. */
	public static final double FAILURE_RATE = 0.02;
	/** Boost is read up to this many bars over ambient and no further, so absurd inputs cannot overflow the figures. */
	public static final double MAX_BOOST = 50;
	/** Boost beyond this many bars over ambient is overspeed: the chance of failure doubles for each bar past it. */
	public static final double OVERSPEED = 1.5;

	private TurboWear() {
	}

	/** Wear in {@code dt} seconds at {@code boost} bars over ambient, exhaust {@code temperature} and oil {@code film}. */
	public static double wear(double boost, double temperature, double film, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		double load = Double.isFinite(boost) ? Math.clamp(boost, 0, MAX_BOOST) / REFERENCE_BOOST : 0;
		double factor = Math.max(IDLE_FLOOR, load * load) * Wear.heatFactor(temperature) * Wear.dryFactor(film);
		return Math.min(1, Math.max(0, dt) / LIFE * factor);
	}

	/** The share of its boost a turbo this worn still makes: 1 for one that has not worn. */
	public static double boostShare(double wear) {
		double w = Wear.clamp(wear);
		return 1 - MAX_BOOST_LOSS * w * w;
	}

	/** The chance in {@code dt} seconds that a turbo this worn fails: zero below {@link Wear#FAILURE_FROM}, rising with the cube past it and with boost. */
	public static double failureChance(double wear, double boost, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		double past = Math.max(0, (Wear.clamp(wear) - Wear.FAILURE_FROM) / (1 - Wear.FAILURE_FROM));
		double b = Double.isFinite(boost) ? Math.clamp(boost, 0, MAX_BOOST) : 0;
		double over = Math.pow(2, Math.min(20, Math.max(0, b - OVERSPEED)));
		double load = Math.max(1, b / REFERENCE_BOOST);
		return Math.min(1, FAILURE_RATE * past * past * past * load * over * Math.max(0, dt));
	}
}
