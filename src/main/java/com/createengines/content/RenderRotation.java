package com.createengines.content;

/**
 * How far a part has turned for drawing, accumulated frame by frame from its speed.
 * <p>
 * Create draws a shaft at render time × speed. That's fine for a speed that rarely changes, but every change moves
 * the whole shaft to a different angle at once, and an engine's speed changes all the time. Accumulating the angle
 * instead means a speed change only changes how fast the part turns.
 * <p>
 * The shafts and cogs joined to an engine do still jump with every change, so an accumulated angle slowly falls out of
 * step with them. {@link #alignedAt} eases it back.
 */
public final class RenderRotation {

	/** Longest gap between frames that is still animated, in ticks; anything longer is a pause or reload. */
	private static final float MAX_GAP = 20;

	/** A shaft looks the same every quarter turn, so an aligned angle only has to agree with Create's to within one. */
	static final double ALIGN_PERIOD = 90;
	/** How quickly a gap to Create's angle closes: the share left after this many ticks is 1/e. */
	static final double ALIGN_TICKS = 30;
	/** The most the angle is nudged toward Create's, per tick: slow enough that nobody sees the crank speed up or slow. */
	static final double ALIGN_MAX_PER_TICK = 0.5;

	private double degrees = Double.NaN;
	private float lastTime;

	/** Degrees turned at {@code renderTime} (not wrapped), having turned at {@code speed} RPM since the last call. */
	public double at(float renderTime, float speed) {
		return advance(renderTime, speed, false);
	}

	/**
	 * Like {@link #at}, but also eases the angle toward the one Create draws a shaft at, to the nearest quarter turn.
	 * A crank's ends are shaft-shaped, so one that settles on Create's angle meets the real shafts and cogs joined to it
	 * square, and not at whatever angle its last speed change left it.
	 */
	public double alignedAt(float renderTime, float speed) {
		return advance(renderTime, speed, true);
	}

	private double advance(float renderTime, float speed, boolean align) {
		if (Double.isNaN(degrees))
			degrees = createAngle(renderTime, speed);
		else {
			float gap = renderTime - lastTime;
			if (gap > 0) {
				float ticks = Math.min(gap, MAX_GAP);
				degrees += ticks * speed * 3d / 10;
				if (align)
					degrees += nudge(createAngle(renderTime, speed), degrees, ticks);
			}
		}
		lastTime = renderTime;
		return degrees;
	}

	/**
	 * Create's own angle for a shaft at this time and speed: {@code CrankGeometry.renderAngle}, written out so that this
	 * class carries no Minecraft types and unit tests can run it.
	 */
	private static float createAngle(float renderTime, float speed) {
		return (renderTime * speed * 3f / 10) % 360;
	}

	/**
	 * How far to move an accumulated angle {@code degrees}, over {@code ticks}, toward {@code target}: along the shorter
	 * way to the nearest quarter turn of it, a fraction of the way, and never more than {@link #ALIGN_MAX_PER_TICK}.
	 */
	static double nudge(double target, double degrees, double ticks) {
		double gap = ((target - degrees) % ALIGN_PERIOD + ALIGN_PERIOD * 1.5) % ALIGN_PERIOD - ALIGN_PERIOD / 2;
		double step = gap * (1 - Math.exp(-ticks / ALIGN_TICKS));
		double limit = ALIGN_MAX_PER_TICK * ticks;
		return Math.max(-limit, Math.min(limit, step));
	}

	/** Takes over an angle worked out elsewhere, so that carrying on from here doesn't jump. */
	public double follow(float renderTime, double degrees) {
		this.degrees = degrees;
		lastTime = renderTime;
		return degrees;
	}
}
