package com.createengines.content.flywheel;

import com.createengines.engine.sim.DualMass;

/**
 * How far a Dual-Mass Flywheel's secondary disc is drawn behind its primary, in degrees, from what the engine is doing. Only
 * drawn: it moves no rating and is not read by the simulation. Three things twist the spring pack, so there is something to
 * see from the first turn to the redline:
 * <ul>
 * <li><b>Load.</b> The springs wind up as the network asks for more torque: from {@link #PRELOAD} of {@link #WINDUP_MAX} degrees
 * with none to all of it at full load.</li>
 * <li><b>The firing pulses.</b> Each power stroke pushes the primary on and the secondary lags, then catches up: a swing of up to
 * {@link #PULSE_MAX} degrees either side at the engine's firing rate, larger with the ripple (full at {@link #RIPPLE_FULL}) and
 * fuller as the secondary becomes isolated ({@link DualMass#isolation}); below the resonance the two masses move almost as one
 * and the swing is {@link #RIGID_SWING} of it.</li>
 * <li><b>The resonance.</b> A little extra wind-up, {@link #BELL_MAX} degrees, as the crank passes through it.</li>
 * </ul>
 * A pure function with no game imports, so it is unit-tested and bounded for every input.
 */
public final class SpringPackLag {

	public static final double WINDUP_MAX = 8;
	public static final double PULSE_MAX = 8;
	public static final double BELL_MAX = 3;
	/**
	 * What is there however light the load: a spring pack is always under its preload and the engine's own friction and
	 * compression, so a turning engine always winds it up to this share of {@link #WINDUP_MAX}, and its pulses always swing it
	 * by at least this share of {@link #PULSE_MAX}. Measured on a running diesel the figures that drive the rest are small by
	 * design (a dual-mass flywheel exists to make the ripple small, and an unloaded engine asks the network for nothing), so
	 * without a floor there was nothing to see.
	 */
	public static final double PRELOAD = 0.4;
	/** The share of the swing that is there below the resonance, where the two masses move almost as one. */
	public static final double RIGID_SWING = 0.35;
	/** The ripple (fraction of mean speed) at which the pulses reach their full swing. */
	public static final double RIPPLE_FULL = 0.15;
	/** The most the secondary is drawn behind the primary, and the most ahead of it, in degrees. */
	public static final double MAX_BEHIND = WINDUP_MAX + BELL_MAX + PULSE_MAX;
	public static final double MAX_AHEAD = PULSE_MAX;

	private SpringPackLag() {
	}

	private static double unit(double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
	}

	/** The pulse of the firing, -1 to 1, at {@code phase} in cycles of it (only the part past a whole one matters). */
	public static double firingWave(double phase) {
		// A huge phase would overflow the product with 2 pi
		double cycle = Double.isFinite(phase) ? phase - Math.floor(phase) : 0;
		return Math.sin(2 * Math.PI * cycle);
	}

	/**
	 * The extra wind-up of a spring pack that resonates at {@code resonanceRpm} turning at {@code rpm} (its size, not its
	 * direction), 0 to 1: nothing at a standstill, one at the resonance, nothing again once the secondary is fully isolated.
	 */
	static double bell(double rpm, double resonanceRpm) {
		if (!(Double.isFinite(rpm) && Double.isFinite(resonanceRpm)))
			return 0;
		double speed = Math.abs(rpm);
		if (speed <= 0 || resonanceRpm <= 0)
			return 0;
		double r = speed / resonanceRpm;
		double t = r <= 1 ? r : 1 - (r - 1) / (DualMass.FULL_AT - 1);
		t = Math.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/**
	 * How far behind the primary, in degrees, the secondary is drawn. Negative is ahead of it.
	 *
	 * @param rpm       the shaft's speed (its size and not its direction decide the lag; a shaft at rest has none)
	 * @param resonance the spring pack's resonance, RPM
	 * @param load      the share of the engine's torque the network is asking for, 0 to 1
	 * @param ripple    the engine's speed ripple, a fraction of its mean speed
	 * @param phase     where the crank is in the firing cycle, in cycles (the whole part does not matter)
	 */
	public static double twist(double rpm, double resonance, double load, double ripple, double phase) {
		if (!Double.isFinite(rpm) || rpm == 0)
			return 0;
		double wave = firingWave(phase);
		double swing = RIGID_SWING + (1 - RIGID_SWING) * DualMass.isolation(Math.abs(rpm), resonance);
		double pulse = PULSE_MAX * (0.6 + 0.4 * unit(ripple / RIPPLE_FULL)) * swing * wave;
		double twist = WINDUP_MAX * (PRELOAD + (1 - PRELOAD) * unit(load)) + BELL_MAX * bell(rpm, resonance) + pulse;
		return Math.clamp(twist, -MAX_AHEAD, MAX_BEHIND);
	}

	/**
	 * The angle, in degrees, to draw the secondary disc at when the primary is at {@code primaryDegrees}: {@code twist} behind
	 * it against the way the shaft turns. A shaft that is not turning, or a value that is not a number, leaves it with the
	 * primary (a corrupt angle is drawn as zero).
	 */
	public static double secondaryAngle(double primaryDegrees, double rpm, double twist) {
		if (!Double.isFinite(primaryDegrees))
			return 0;
		if (!Double.isFinite(rpm) || !Double.isFinite(twist))
			return primaryDegrees;
		return primaryDegrees - Math.signum(rpm) * Math.clamp(twist, -MAX_BEHIND, MAX_BEHIND);
	}
}
