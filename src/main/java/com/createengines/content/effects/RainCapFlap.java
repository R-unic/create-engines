package com.createengines.content.effects;

/**
 * How far a rain cap's flap is lifted. Each exhaust pulse out of the pipe it sits on kicks it up, and gravity drops it
 * back: the kick fades away exponentially, so a slow engine makes the flap clack once a firing, a fast one holds it
 * fluttering part open, and a stopped engine lets it fall shut. Pure arithmetic, kept free of Minecraft so it can be
 * tested; the unit of time is the game tick.
 */
public final class RainCapFlap {

	/** The flap's most open angle, in degrees, when pulses come faster than it can fall. */
	public static final double MAX_DEGREES = 70;
	/** What one pulse adds, as a share of the full lift. */
	public static final double KICK = 0.55;
	/** The ticks the lift takes to fall to 1/e of itself. */
	public static final double FALL_TICKS = 3.5;

	private double lift;
	private double at = Double.NEGATIVE_INFINITY;

	/** An exhaust pulse at {@code now}. */
	public void pulse(double now) {
		lift = Math.min(1, lift(now) + KICK);
		at = now;
	}

	/** The share of full lift at {@code now}, 0 for shut and 1 for wide open. */
	public double lift(double now) {
		if (lift <= 0)
			return 0;
		return lift * Math.exp(-Math.max(0, now - at) / FALL_TICKS);
	}

	/** The angle the flap stands open at {@code now}, in degrees. */
	public double degrees(double now) {
		return MAX_DEGREES * lift(now);
	}
}
