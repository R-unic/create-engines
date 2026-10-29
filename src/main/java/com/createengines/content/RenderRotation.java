package com.createengines.content;

import com.createengines.engine.CrankGeometry;

/**
 * How far a part has turned for drawing, accumulated frame by frame from its speed.
 * <p>
 * Create draws a shaft at render time × speed. That's fine for a speed that rarely changes, but every change moves
 * the whole shaft to a different angle at once, and an engine's speed changes all the time. Accumulating the angle
 * instead means a speed change only changes how fast the part turns.
 */
public final class RenderRotation {

	/** Longest gap between frames that is still animated, in ticks; anything longer is a pause or reload. */
	private static final float MAX_GAP = 20;

	private double degrees = Double.NaN;
	private float lastTime;

	/** Degrees turned at {@code renderTime} (not wrapped), having turned at {@code speed} RPM since the last call. */
	public double at(float renderTime, float speed) {
		if (Double.isNaN(degrees))
			degrees = CrankGeometry.renderAngle(renderTime, speed);
		else {
			float gap = renderTime - lastTime;
			if (gap > 0)
				degrees += Math.min(gap, MAX_GAP) * speed * 3d / 10;
		}
		lastTime = renderTime;
		return degrees;
	}

	/** Takes over an angle worked out elsewhere, so that carrying on from here doesn't jump. */
	public double follow(float renderTime, double degrees) {
		this.degrees = degrees;
		lastTime = renderTime;
		return degrees;
	}
}
