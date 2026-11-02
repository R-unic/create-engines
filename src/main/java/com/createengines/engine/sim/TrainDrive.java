package com.createengines.engine.sim;

/**
 * An engine pushing a train. A Create train is not driven by a shaft, and a built engine cannot run on a moving carriage,
 * so a carriage carries a <i>snapshot</i> of an engine instead: its rated torque curve and how much fuel its power costs,
 * taken from the engine while it ran on the ground. This class turns that snapshot, a gearing and a throttle into the
 * boost the train gets: a higher top speed and a harder pull, and the fuel it costs.
 * <p>
 * The boost is stated against the train's own figures (the top speed and acceleration Create is configured with), so
 * the model does not depend on Create's units. Power per carriage is what counts: one carriage with the output of a steam
 * engine gets {@link #SPEED_GAIN} more top speed, the same engine spread over four carriages a good deal less, and because
 * the pull needed to hold a speed grows with its cube, the gain follows the cube root of the power. A wrong gearing puts
 * the engine off the end of its curve, where it gives nothing; the right one ({@link #idealGearing}) puts its best power at
 * the speed the train is meant to reach.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class TrainDrive {

	/** Power, SU, of one Create steam engine at its speed: the yardstick for how strong a carriage's engine is. */
	public static final double REFERENCE_POWER = 16384;
	/** Extra top speed, as a share of the base, for each cube-rooted steam engine of power per carriage. */
	public static final double SPEED_GAIN = 0.35;
	/** The most extra top speed any engine gives, as a share of the base. */
	public static final double MAX_SPEED_BONUS = 1.5;
	/** Extra pull, as a share of the base acceleration, for each steam engine of power per carriage. */
	public static final double ACCEL_GAIN = 0.5;
	/** The most extra pull any engine gives, as a share of the base acceleration. */
	public static final double MAX_ACCEL_BONUS = 2;
	/** The speed, as a share of the base top speed, below which power is worked out as if at this speed: a standing train. */
	private static final double MIN_SPEED_SHARE = 0.25;

	private TrainDrive() {
	}

	/**
	 * An engine as a carriage carries it.
	 *
	 * @param rpm    the speeds the engine was rated at, ascending
	 * @param torque stress capacity it can be rated for at each, SU per RPM (what a dynamometer shows)
	 * @param suPerMb power, SU, the engine makes for each mB of fuel burned per second at its rating
	 */
	public record Spec(float[] rpm, float[] torque, double suPerMb) {

		/** Stress capacity at {@code rpm}, SU per RPM, between the measured points; none outside the curve. */
		public double capacityAt(double rpm) {
			int n = this.rpm.length;
			if (n == 0 || rpm < this.rpm[0] || rpm > this.rpm[n - 1])
				return 0;
			for (int i = 1; i < n; i++)
				if (rpm <= this.rpm[i]) {
					double span = this.rpm[i] - this.rpm[i - 1];
					double t = span <= 0 ? 1 : (rpm - this.rpm[i - 1]) / span;
					return Math.max(0, torque[i - 1] + (torque[i] - torque[i - 1]) * t);
				}
			return Math.max(0, torque[n - 1]);
		}

		/** Power the engine makes at {@code rpm}, SU. */
		public double power(double rpm) {
			return capacityAt(rpm) * rpm;
		}

		/** The speed, RPM, the engine makes its most power at. */
		public double peakPowerRpm() {
			double best = 0;
			double at = 0;
			for (float speed : rpm) {
				double power = power(speed);
				if (power > best) {
					best = power;
					at = speed;
				}
			}
			return at;
		}

		public double peakPower() {
			return power(peakPowerRpm());
		}
	}

	/**
	 * What the train gets this tick.
	 *
	 * @param topSpeed       the highest speed the engine can take the train to, in the train's own units: its base top speed
	 *                       at least
	 * @param acceleration   extra pull the engine adds while the train is below {@code topSpeed}, in the train's own units
	 * @param power          power the engine is making at the train's speed, SU
	 * @param fuelPerSecond  fuel burned making it, mB per second
	 * @param engineRpm      how fast the engine is turning, RPM
	 */
	public record Boost(double topSpeed, double acceleration, double power, double fuelPerSecond, double engineRpm) {

		public static final Boost NONE = new Boost(0, 0, 0, 0, 0);
	}

	/** RPM the engine turns at when the train goes {@code speed}, for a gearing of RPM per unit of speed. */
	public static double engineRpm(double speed, double gearing) {
		return speed * gearing;
	}

	/** The gearing that puts the engine's best power at {@code targetSpeed}. */
	public static double idealGearing(Spec spec, double targetSpeed) {
		return targetSpeed <= 0 ? 0 : spec.peakPowerRpm() / targetSpeed;
	}

	/**
	 * Works out the boost.
	 *
	 * @param gearing   RPM of the engine per unit of train speed
	 * @param carriages how many carriages the engine's power is shared by, at least one
	 * @param throttle  0 (coasting) to 1 (full): a share of the engine's power
	 * @param baseTop   the train's top speed without the engine
	 * @param baseAccel the train's acceleration without the engine
	 * @param speed     the train's speed now
	 */
	public static Boost boost(Spec spec, double gearing, int carriages, double throttle, double baseTop,
							  double baseAccel, double speed) {
		throttle = Math.clamp(throttle, 0, 1);
		if (throttle <= 0 || baseTop <= 0 || gearing <= 0 || spec.rpm().length == 0)
			return Boost.NONE;
		int shared = Math.max(1, carriages);

		// The top speed is where the pull the engine can give holds the train against drag. The pull depends on the speed
		// it is geared to, so find it by settling: each pass works out the power at the speed the last one gave
		double top = baseTop;
		for (int pass = 0; pass < 40; pass++) {
			double power = spec.power(engineRpm(top, gearing)) * throttle;
			double bonus = Math.min(MAX_SPEED_BONUS, SPEED_GAIN * Math.cbrt(power / (shared * REFERENCE_POWER)));
			top = 0.5 * (top + baseTop * (1 + bonus));
		}

		double pulling = Math.max(speed, baseTop * MIN_SPEED_SHARE);
		double power = spec.power(engineRpm(pulling, gearing)) * throttle;
		double pull = Math.min(MAX_ACCEL_BONUS, ACCEL_GAIN * power / (shared * REFERENCE_POWER));
		double acceleration = speed < top ? baseAccel * pull : 0;
		double fuel = spec.suPerMb() <= 0 ? 0 : power / spec.suPerMb();
		return new Boost(top, acceleration, power, fuel, engineRpm(pulling, gearing));
	}
}
