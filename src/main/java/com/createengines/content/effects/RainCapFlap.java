package com.createengines.content.effects;

/**
 * How far a rain cap's flap is lifted, as a small hinged weight. Each exhaust pulse out of the pipe it sits on gives it
 * a shove, gravity pulls it back down more heavily the flatter it lies, a little drag keeps it from swinging for ever, and
 * when it lands it bounces a little before it settles. So a slow engine makes the flap rise and sink once a firing, a fast
 * one holds it up fluttering, and a stopped engine lets it sink and clack shut. Pure arithmetic, kept free of
 * Minecraft so it can be tested; the unit of time is the game tick, and the flap is stepped forward as it is asked about.
 */
public final class RainCapFlap {

	/** The flap's stop, in degrees. */
	public static final double MAX_DEGREES = 70;
	private static final double MAX = Math.toRadians(MAX_DEGREES);
	/** What one pulse adds to the flap's speed, radians per tick. */
	public static final double SHOVE = 0.22;
	/** Gravity's pull on a flat flap, radians per tick squared; it fades to nothing as the flap stands upright. */
	public static final double GRAVITY = 0.025;
	/** Share of its speed the flap loses every tick. */
	public static final double DRAG = 0.06;
	/** Share of its speed the flap keeps, reversed, when it lands shut or hits its stop. */
	public static final double BOUNCE = 0.3;
	private static final double STEP = 0.25;

	private double angle;
	private double speed;
	private double at = Double.NEGATIVE_INFINITY;

	/** An exhaust pulse at {@code now}. */
	public void pulse(double now) {
		advance(now);
		speed += SHOVE;
		if (at == Double.NEGATIVE_INFINITY)
			at = now;
	}

	/** The angle the flap stands open at {@code now}, in degrees. A time before the last one asked about gives the last. */
	public double degrees(double now) {
		advance(now);
		return Math.toDegrees(angle);
	}

	/** The share of its full opening the flap stands at, 0 for shut and 1 at its stop. */
	public double lift(double now) {
		return degrees(now) / MAX_DEGREES;
	}

	private void advance(double now) {
		if (at == Double.NEGATIVE_INFINITY) {
			at = now;
			return;
		}
		// A long gap (a cap out of sight, a paused game) has long since settled: do not step through it
		if (now - at > 400) {
			angle = 0;
			speed = 0;
			at = now;
			return;
		}
		while (at < now) {
			double dt = Math.min(STEP, now - at);
			speed += -GRAVITY * Math.cos(angle) * dt;
			speed *= Math.pow(1 - DRAG, dt);
			angle += speed * dt;
			if (angle <= 0) {
				angle = 0;
				speed = speed < 0 ? -speed * BOUNCE : speed;
				// A flap that has run out of energy lies still instead of chattering on the pipe
				if (speed < 0.01)
					speed = 0;
			} else if (angle >= MAX) {
				angle = MAX;
				speed = speed > 0 ? -speed * BOUNCE : speed;
			}
			at += dt;
		}
	}
}
