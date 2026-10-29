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
	/** Fuel-air equivalence ratio when fuel was injected: 1 is exactly enough air to burn it, above 1 is rich. */
	public double equivalence;
	/** Sparks the plug fired this cycle, and attempts that produced nothing because the supply was flat. */
	public int sparks;
	public int deadSparks;
	/** Why a spark failed to light the charge: -1 too lean, 1 too rich, 0 it didn't fail. */
	public int flameOut;
	public CycleResult result = CycleResult.NONE;

	public double unburnedFraction() {
		return fuelInjected <= 0 ? 0 : Math.max(0, 1 - fuelBurned / fuelInjected);
	}

	public boolean rich() {
		return result.fired() && unburnedFraction() > 0.15;
	}

	/** Fired with air to spare: more than a third of the charge's air went unused. */
	public boolean lean() {
		return result.fired() && equivalence > 0 && equivalence < 0.65;
	}

	public void copyFrom(CycleStats other) {
		work = other.work;
		fuelInjected = other.fuelInjected;
		fuelBurned = other.fuelBurned;
		compressionTemp = other.compressionTemp;
		peakPressure = other.peakPressure;
		freshAirAtInjection = other.freshAirAtInjection;
		equivalence = other.equivalence;
		sparks = other.sparks;
		deadSparks = other.deadSparks;
		flameOut = other.flameOut;
		result = other.result;
	}

	void reset() {
		work = 0;
		fuelInjected = 0;
		fuelBurned = 0;
		compressionTemp = 0;
		peakPressure = 0;
		freshAirAtInjection = 0;
		equivalence = 0;
		sparks = 0;
		deadSparks = 0;
		flameOut = 0;
		result = CycleResult.NONE;
	}
}
