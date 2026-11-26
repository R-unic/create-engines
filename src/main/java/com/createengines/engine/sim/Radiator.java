package com.createengines.engine.sim;

/**
 * What a Radiator does for a hot block: a closed coolant loop that gives the block's heat to the air going through its core.
 * It spends no coolant below {@link Wear#CRITICAL}, which is what sets it apart from the coolant in an oil pan (that boils off
 * carrying heat, see {@link Thermal}). From critical up it boils some of its own away, so it is not a way to ignore heat.
 * <p>
 * How much it sheds goes with the air through it. Still air is {@code 1}; a fan blowing at it and the speed of a moving structure
 * each add to that, up to {@link #MAX_AIRFLOW}.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class Radiator {

	/** Heat one radiator sheds per second, per unit above {@link Thermal#THERMOSTAT}, on the best coolant in still air. */
	public static final double CONDUCTANCE = 160;
	/** What a fan at {@link #FAN_FULL_RPM} or faster adds to the airflow. */
	public static final double FAN_AIRFLOW = 1.5;
	public static final double FAN_FULL_RPM = 256;
	/** What a structure moving at {@link #RAM_FULL_SPEED} blocks a second or faster adds to the airflow. */
	public static final double RAM_AIRFLOW = 2;
	public static final double RAM_FULL_SPEED = 20;
	/** The most air that counts, however it is made. */
	public static final double MAX_AIRFLOW = 3;
	/** Coolant boiled off per unit of heat shed while the block is critical, mB, at the best quality. */
	public static final double BOIL_PER_HEAT = 0.15;

	/** Where most of a radiator's air is coming from. */
	public enum Air {
		STILL, FAN, RAM
	}

	/** The radiators joined to one engine, as the block's thermal sum sees them. */
	public interface Supply {
		int count();

		/** How well radiator {@code i}'s coolant carries heat: 0 when it is empty. */
		double quality(int i);

		/** The air through radiator {@code i}, at least 1 (see {@link Radiator#airflow}). */
		double airflow(int i);

		/**
		 * Tells radiator {@code i} what it did this tick. Called every tick the engine's temperature is worked out, with zeroes
		 * when it did nothing, so a radiator knows it is on an engine.
		 *
		 * @param shed   heat per second it gave to the air
		 * @param boiled coolant it boiled off over the tick, mB
		 */
		void ran(int i, double shed, double boiled);
	}

	public static final Supply NONE = new Supply() {
		@Override
		public int count() {
			return 0;
		}

		@Override
		public double quality(int i) {
			return 0;
		}

		@Override
		public double airflow(int i) {
			return 1;
		}

		@Override
		public void ran(int i, double shed, double boiled) {
		}
	};

	private Radiator() {
	}

	/** What the fan adds; either way round it turns, and nothing for a speed that is not a number. */
	private static double fanAir(double fanRpm) {
		return share(Math.abs(fanRpm) / FAN_FULL_RPM) * FAN_AIRFLOW;
	}

	private static double ramAir(double speed) {
		return share(speed / RAM_FULL_SPEED) * RAM_AIRFLOW;
	}

	private static double share(double value) {
		return Double.isNaN(value) ? 0 : Math.clamp(value, 0, 1);
	}

	/**
	 * The air through a radiator, 1 in still air.
	 *
	 * @param fanRpm speed of the fan blowing at it, 0 with none
	 * @param speed  how fast the structure carrying it moves, blocks a second, 0 off a moving structure
	 */
	public static double airflow(double fanRpm, double speed) {
		return Math.min(MAX_AIRFLOW, 1 + fanAir(fanRpm) + ramAir(speed));
	}

	/** Which of the two adds more, or still air when neither adds enough to notice. */
	public static Air air(double fanRpm, double speed) {
		double fan = fanAir(fanRpm);
		double ram = ramAir(speed);
		if (Math.max(fan, ram) < 0.1)
			return Air.STILL;
		return ram > fan ? Air.RAM : Air.FAN;
	}

	/** Heat per second one radiator sheds from a block at {@code temperature}: nothing at or below the thermostat. */
	public static double shed(double temperature, double quality, double airflow) {
		return CONDUCTANCE * quality * Math.max(0, temperature - Thermal.THERMOSTAT) * airflow;
	}

	/** Coolant boiled off by shedding {@code shed} a second for {@code dt} seconds: nothing below critical. */
	public static double boiled(double temperature, double shed, double quality, double dt) {
		return temperature < Wear.CRITICAL || quality <= 0 ? 0 : shed * BOIL_PER_HEAT / quality * dt;
	}
}
