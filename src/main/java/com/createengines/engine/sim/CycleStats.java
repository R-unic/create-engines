package com.createengines.engine.sim;

/** Figures accumulated over one cylinder cycle. */
public final class CycleStats {
	/** Net p·dV work done on the crank (normalized units). */
	public double work;
	public double fuelInjected;
	public double fuelBurned;
	/** Charge temperature from injection until ignition: what compression achieved, compared with autoignition. */
	public double compressionTemp;
	public double peakPressure;
	/** Fresh charge in the cylinder when fuel was injected. */
	public double freshAirAtInjection;
	public CycleResult result = CycleResult.NONE;

	public double unburnedFraction() {
		return fuelInjected <= 0 ? 0 : Math.max(0, 1 - fuelBurned / fuelInjected);
	}

	public boolean rich() {
		return result.fired() && unburnedFraction() > 0.15;
	}

	public void copyFrom(CycleStats other) {
		work = other.work;
		fuelInjected = other.fuelInjected;
		fuelBurned = other.fuelBurned;
		compressionTemp = other.compressionTemp;
		peakPressure = other.peakPressure;
		freshAirAtInjection = other.freshAirAtInjection;
		result = other.result;
	}

	void reset() {
		work = 0;
		fuelInjected = 0;
		fuelBurned = 0;
		compressionTemp = 0;
		peakPressure = 0;
		freshAirAtInjection = 0;
		result = CycleResult.NONE;
	}
}
