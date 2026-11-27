package com.createengines.engine.sim;

/**
 * The speed an engine is turning at, seen from outside: the mean over a whole firing cycle rather than where the crank is on
 * its ripple at the instant it is looked at. A tick is not locked to the firing cycle, so that instant lands somewhere
 * else on the surge every time, and a rough engine would seem to wander at a speed it is not wandering at.
 * <p>
 * A cycle at idle lasts many ticks, so rather than stepping to each new mean the figure ramps to it over the length of the
 * cycle that made it: smooth, never past it, and no more than a cycle behind. With no whole cycle to go by it is the
 * instant speed. It is only read from outside; nothing in the simulation runs on it.
 */
public final class MeanSpeed {

	private double value;
	private double target = Double.NaN;
	/** RPM per second the value moves toward the target. */
	private double rate;

	/**
	 * @param instant     the crank's speed now, RPM
	 * @param cycleMean   the mean speed over the last whole cycle, RPM; 0 (or not a number) when there is none to go by
	 * @param cycleLength how long that cycle took, seconds
	 * @param dt          seconds since the last call
	 */
	void update(double instant, double cycleMean, double cycleLength, double dt) {
		double now = Double.isFinite(instant) ? instant : 0;
		if (!(now > 0 && cycleMean > 0 && cycleLength > 0) || !Double.isFinite(cycleMean) || !Double.isFinite(cycleLength)) {
			follow(now);
			return;
		}
		if (cycleMean != target) {
			target = cycleMean;
			rate = Math.abs(target - value) / Math.max(cycleLength, dt);
		}
		double step = rate * dt;
		value = target > value ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	/** Takes the instant speed as it is, and forgets what it was ramping to. */
	private void follow(double instant) {
		value = instant;
		target = Double.NaN;
	}

	/** Carries on from where {@code other} is, mid-ramp and all. */
	void copyFrom(MeanSpeed other) {
		value = other.value;
		target = other.target;
		rate = other.rate;
	}

	/** RPM. */
	double rpm() {
		return value;
	}
}
