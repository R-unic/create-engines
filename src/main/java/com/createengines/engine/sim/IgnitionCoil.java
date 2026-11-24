package com.createengines.engine.sim;

/**
 * A cylinder's high-energy ignition coil and the supply behind it. A coil sits on a spark plug and is a bonus on top of it, never a
 * requirement: each time the plug has a charge to light, the coil can draw {@link #COST} more sparks of charge for a hotter, longer
 * spark. That spark lights a leaner mixture ({@link #LEAN}) and burns through the soot of a rich one more easily
 * ({@link #FOULING}), so a lean or boosted spark fuel misfires less. A spark it could not draw charge for is exactly the spark a plug
 * without a coil would have made, so an engine with no battery that can spare the charge runs as it always did.
 * <p>
 * The numbers are here, with no Minecraft types, so they are unit tested; {@code Combustion} applies them and {@code BatteryIgnition}
 * supplies the charge.
 */
public interface IgnitionCoil {

	/** Charge one coiled spark takes from the bank on top of the plug's own, in sparks. */
	double COST = 0.5;
	/** What a coiled spark multiplies the leanest equivalence ratio it can still light by (lower is leaner). */
	double LEAN = 0.7;
	/** What a coiled spark multiplies the way soot narrows the richest mixture it can light by (lower is more forgiving). */
	double FOULING = 0.4;
	/** What a coiled spark multiplies the burn duration by: a hotter spark starts a bigger flame front, so the charge burns faster. */
	double BURN = 0.9;

	/** No coil fitted. */
	IgnitionCoil NONE = new IgnitionCoil() {
		@Override
		public boolean installed() {
			return false;
		}

		@Override
		public boolean powered() {
			return false;
		}

		@Override
		public boolean fire() {
			return false;
		}
	};

	/** A coil with all the electricity it could want. For measuring an engine's torque curve apart from its battery. */
	IgnitionCoil UNLIMITED = new IgnitionCoil() {
		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return true;
		}

		@Override
		public boolean fire() {
			return true;
		}
	};

	/** Whether the cylinder has a coil fitted at all. */
	boolean installed();

	/** Whether a spark could be coiled right now, without using any charge up. */
	boolean powered();

	/** Takes the charge of one coiled spark. False if the supply behind it cannot spare that much, and the spark is the plug's own. */
	boolean fire();

	/** The leanest equivalence ratio a spark can light, {@code coiled} or not. Uncoiled it is {@code limit}, exactly. */
	static double leanLimit(double limit, boolean coiled) {
		return coiled ? limit * LEAN : limit;
	}

	/** The burn duration of a charge lit by a spark, {@code coiled} or not. Uncoiled it is {@code burn}, exactly. */
	static double burn(double burn, boolean coiled) {
		return coiled ? burn * BURN : burn;
	}

	/** How far soot pulls the richest mixture in, {@code coiled} or not. Uncoiled it is {@code loss}, exactly. */
	static double fouledLoss(double loss, boolean coiled) {
		return coiled ? loss * FOULING : loss;
	}
}
