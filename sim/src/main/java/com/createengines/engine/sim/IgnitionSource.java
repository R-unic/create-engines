package com.createengines.engine.sim;

/**
 * A cylinder's spark plug and the electrical supply behind it. A cylinder without one burns fuel only by compression
 * heating it past its autoignition temperature; with one, the charge is lit by a spark at a fixed point in the cycle
 * whatever its temperature, so fuels that compression can't light become usable.
 */
public interface IgnitionSource {

	/** No spark plug: compression ignition only. */
	IgnitionSource NONE = new IgnitionSource() {
		@Override
		public boolean installed() {
			return false;
		}

		@Override
		public boolean powered() {
			return false;
		}

		@Override
		public boolean spark() {
			return false;
		}
	};

	/** A plug with all the electricity it could want. For measuring an engine's torque curve apart from its battery. */
	IgnitionSource UNLIMITED = new IgnitionSource() {
		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return true;
		}

		@Override
		public boolean spark() {
			return true;
		}
	};

	/** Whether the cylinder has a spark plug at all. */
	boolean installed();

	/** Whether a spark could be had right now, without using any up. */
	boolean powered();

	/** Fires the plug, using up whatever charge that takes. False if the supply behind it is flat or missing. */
	boolean spark();
}
