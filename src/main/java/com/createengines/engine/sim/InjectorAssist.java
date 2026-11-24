package com.createengines.engine.sim;

/**
 * A cylinder's electric injector assist and the supply behind it. An assist is a bonus on top of an injector, never a requirement:
 * each shot it can draw {@link #COST} sparks of charge for, the fuel is atomised more finely (it lights a little sooner and burns a
 * little faster) and the nozzle passes a little more at its limit. A shot it could not draw charge for is exactly the shot a cylinder
 * without one would have had, so an engine with no battery near the assist, or none that can spare the charge, runs as it always did.
 * <p>
 * The numbers are here, with no Minecraft types, so they are unit tested; {@code Combustion} applies them and
 * {@code BatteryAssist} supplies the charge.
 */
public interface InjectorAssist {

	/** Charge one assisted shot takes from the bank, in sparks: half what a spark plug's spark takes. */
	double COST = 0.5;
	/** What an assisted shot multiplies the injector's ignition delay by. */
	double DELAY = 0.85;
	/** What an assisted shot multiplies the burn duration by. */
	double BURN = 0.8;
	/**
	 * What an assisted shot multiplies the most the injector can deliver by. This is the number that matters on a boosted engine
	 * with a MAF sensor: the shot is capped here, so a thin fuel like methanol runs lean at high boost until the cap is raised.
	 */
	double FLOW = 1.5;

	/** No assist fitted. */
	InjectorAssist NONE = new InjectorAssist() {
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

	/** An assist with all the electricity it could want. For measuring an engine's torque curve apart from its battery. */
	InjectorAssist UNLIMITED = new InjectorAssist() {
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

	/** Whether the cylinder has an assist fitted at all. */
	boolean installed();

	/** Whether a shot could be assisted right now, without using any charge up. */
	boolean powered();

	/** Takes the charge of one shot. False if the supply behind it cannot spare that much, and the shot goes as it would without an assist. */
	boolean fire();

	/** The ignition delay of a shot, {@code assisted} or not. Unassisted it is {@code delay}, exactly. */
	static double delay(double delay, boolean assisted) {
		return assisted ? delay * DELAY : delay;
	}

	/** The burn duration of a shot, {@code assisted} or not. Unassisted it is {@code burn}, exactly. */
	static double burn(double burn, boolean assisted) {
		return assisted ? burn * BURN : burn;
	}

	/** The most an injector can deliver in a shot, {@code assisted} or not. Unassisted it is {@code limit}, exactly. */
	static double limit(double limit, boolean assisted) {
		return assisted ? limit * FLOW : limit;
	}
}
