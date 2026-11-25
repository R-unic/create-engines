package com.createengines.engine.sim;

/**
 * The wear mode's spark plug: the electrode erodes a little with every spark, faster with a high-energy coil and a fouled plug, and a worn
 * plug lights a leaner mixture less well and misses some sparks. Like {@link Wear} this is pure numbers; the block entity keeps the wear
 * and the engine reads it. Light wear is cleaned and regapped with a Service Kit, heavy wear is cured by fitting a new plug. A worn plug is
 * weak, never dead: it costs lean-burn range and the odd cycle, and it cannot stop an engine on its own.
 */
public final class PlugWear {

	/** Seconds a plug lasts at the reference speed, with no coil and no soot. */
	public static final double LIFE = 4 * 3600;
	/** A coiled spark is hotter, so it erodes the electrode this many times as fast. */
	public static final double COIL_FACTOR = 1.5;
	/** A fouled plug (soot 1) wears this much faster than a clean one. */
	public static final double FOULING_FACTOR = 2;
	/** A plug this worn (and no less) starts to miss sparks. */
	public static final double MISFIRE_FROM = 0.35;
	/** The most sparks a spent plug misses, as a share. */
	public static final double MISFIRE_MAX = 0.4;
	/** What a spent plug multiplies the leanest mixture it can light by (above 1 is richer, so a narrower window). */
	public static final double LEAN_LOSS = 1.5;

	private PlugWear() {
	}

	/** Wear a plug takes in {@code dt} seconds at {@code rpm}. {@code fouling} is the soot on it, 0 to 1. */
	public static double wear(double rpm, boolean coiled, double fouling, double dt) {
		if (!Double.isFinite(dt))
			return 0;
		double soot = Double.isFinite(fouling) ? Math.clamp(fouling, 0, 1) : 0;
		return Math.min(1, Math.max(0, dt) / LIFE * Wear.speedFactor(rpm) * (coiled ? COIL_FACTOR : 1) * (1 + (FOULING_FACTOR - 1) * soot));
	}

	/** The share of sparks a plug this worn misses: nothing until {@link #MISFIRE_FROM}, then rising with the square. */
	public static double misfireChance(double wear) {
		double past = Math.max(0, (Wear.clamp(wear) - MISFIRE_FROM) / (1 - MISFIRE_FROM));
		return MISFIRE_MAX * past * past;
	}

	/** What the leanest mixture the plug can light is multiplied by: exactly 1 for a plug that is not worn. */
	public static double leanScale(double wear) {
		double w = Wear.clamp(wear);
		return 1 + (LEAN_LOSS - 1) * w * w;
	}
}
