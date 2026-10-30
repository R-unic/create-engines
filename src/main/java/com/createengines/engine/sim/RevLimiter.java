package com.createengines.engine.sim;

/**
 * A hard fuel cut at a set speed, with a little hysteresis so it bounces off the limit rather than chattering on it:
 * it cuts once the engine reaches the limit and lets fuel back in only after the engine has fallen a few percent
 * below it.
 */
public final class RevLimiter {

	/** Share of the limit the engine has to fall to before the fuel comes back. */
	public static final double RESUME = 0.95;

	private boolean cutting;

	/**
	 * @param rpm   the crank's speed now
	 * @param limit the speed to cut at, RPM
	 * @return whether the injectors are cut this tick
	 */
	public boolean update(double rpm, double limit) {
		if (cutting) {
			if (rpm < limit * RESUME)
				cutting = false;
		} else if (rpm >= limit)
			cutting = true;
		return cutting;
	}

	public boolean cutting() {
		return cutting;
	}

	/** Forgets a cut, e.g. when the limiter is removed. */
	public void reset() {
		cutting = false;
	}
}
