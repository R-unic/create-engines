package com.createengines.engine.sim;

/**
 * The gas in one cylinder: how much of it, how hot, and the fraction of it that is still fresh (unburned) air. The one
 * thing {@link CylinderModel}, {@link Ports} and {@link Combustion} all work on, so each is handed it rather than
 * keeping a copy.
 */
final class Charge {

	double moles;
	double temperature;
	double fresh;

	/** Pressure of this gas in {@code volume}. */
	double pressure(double volume) {
		return moles * temperature / volume;
	}

	void copyFrom(Charge other) {
		moles = other.moles;
		temperature = other.temperature;
		fresh = other.fresh;
	}
}
