package com.createengines.engine.sim;

/**
 * A cylinder's intake and exhaust ports: the gas that waits in them and what flows between them and the cylinder.
 * Moved out of {@link CylinderModel}, which owns the {@link Charge} and calls this each step the valves are open.
 */
final class Ports {

	/** Moles of breathed-out gas it takes to replace what the exhaust port is holding. */
	private static final double EXHAUST_MEMORY = 1;
	/** Seconds for the gas in the exhaust port to leave and be replaced by ambient air. */
	private static final double EXHAUST_CLEARING = 1;
	/** The coldest charge evaporative cooling can make, ambient temperatures: a cool engine and an intercooler already reach this far. */
	private static final double MIN_CHARGE_TEMP = 0.6;

	/** Gas pushed back into the intake manifold, and how much of it is still fresh air. */
	private double intakeBackflow;
	private double intakeBackflowFresh = 1;

	/** Temperature of the spent gas in the exhaust port, which is what reversion draws back in. Ambient until it fires. */
	private double exhaustTemp = 1;
	/** Heat carried out of the exhaust valve since the tick began, and during the last whole tick. */
	private double exhaustHeat;
	private double exhaustHeatLastTick;

	/** How the exhaust port's gas decays over the last step length, which is the same for every step but a tick's last. */
	private double clearingDt = Double.NaN;
	private double clearingDecay;

	/**
	 * Closes a tick's accounting of the heat the cylinder sent out of its exhaust valve, in moles of gas times how far
	 * hotter than ambient it was. That is what a turbine has to work with: gas that was never heated carries nothing.
	 */
	void endTick() {
		exhaustHeatLastTick = exhaustHeat;
		exhaustHeat = 0;
	}

	/** Heat the cylinder put into its exhaust during the last whole tick. */
	double exhaustHeatLastTick() {
		return exhaustHeatLastTick;
	}

	/** Empties the ports, as for a cylinder with stale gas at ambient pressure. */
	void reset() {
		intakeBackflow = 0;
		intakeBackflowFresh = 1;
		exhaustTemp = 1;
		exhaustHeat = 0;
		exhaustHeatLastTick = 0;
	}

	void copyFrom(Ports other) {
		intakeBackflow = other.intakeBackflow;
		intakeBackflowFresh = other.intakeBackflowFresh;
		exhaustTemp = other.exhaustTemp;
		exhaustHeat = other.exhaustHeat;
		exhaustHeatLastTick = other.exhaustHeatLastTick;
	}

	/**
	 * The intake port is a manifold, not open sky: gas pushed back out through the intake valve (reversion, or all of
	 * it if the exhaust can't open) waits in the manifold, cools, and is the first thing drawn back in.
	 *
	 * @param intakePressure    pressure of the air the intake valve draws on
	 * @param intakeTemperature temperature of that air
	 * @param evaporativeCooling how far the last shot's evaporating cooled the charge, ambient temperatures
	 */
	void exchangeIntake(Charge charge, double volume, double rate, double dt, double intakePressure,
						double intakeTemperature, double evaporativeCooling) {
		if (rate <= 0)
			return;
		double p = charge.pressure(volume);
		double target = p + (intakePressure - p) * SimMath.approach(rate * dt);
		if (target < p) {
			double out = (p - target) * volume / charge.temperature;
			intakeBackflowFresh = (intakeBackflow * intakeBackflowFresh + out * charge.fresh) / (intakeBackflow + out);
			intakeBackflow += out;
			charge.moles -= out;
			return;
		}
		// Gas at the intake's temperature is what raises the pressure, so a hotter charge means fewer moles for it. A fuel that
		// boils off heat (ethanol) cools the charge it was mixed into, and a cooler charge is a denser one
		double chargeTemp = evaporativeCooling <= 0 ? intakeTemperature
			: Math.max(MIN_CHARGE_TEMP, intakeTemperature - evaporativeCooling);
		double in = (target - p) * volume / chargeTemp;
		double reingested = Math.min(intakeBackflow, in);
		double inFresh = in <= 0 ? 1 : (reingested * intakeBackflowFresh + (in - reingested)) / in;
		intakeBackflow -= reingested;
		charge.fresh = (charge.moles * charge.fresh + in * inFresh) / (charge.moles + in);
		charge.temperature = (charge.moles * charge.temperature + in * chargeTemp) / (charge.moles + in);
		charge.moles += in;
	}

	/**
	 * The exhaust port holds spent gas: it never counts as fresh air. It is hot only if this cylinder has just burned
	 * fuel, and that heat is what reversion draws back in. A cylinder that isn't burning anything can't get hot gas
	 * out of its exhaust (that would be heat from nowhere), and the gas in the port drifts back to ambient anyway.
	 *
	 * @param exhaustPressure pressure the exhaust valve blows down into
	 * @param current         this cycle's figures, and {@code last} the previous cycle's: exhaust heat is only made once fuel has burned
	 */
	void exchangeExhaust(Charge charge, double volume, double rate, double dt, double exhaustPressure,
						 CycleStats current, CycleStats last) {
		if (dt != clearingDt) {
			clearingDt = dt;
			clearingDecay = Math.exp(-dt / EXHAUST_CLEARING);
		}
		exhaustTemp = 1 + (exhaustTemp - 1) * clearingDecay;
		if (rate <= 0)
			return;
		double p = charge.pressure(volume);
		double target = p + (exhaustPressure - p) * SimMath.approach(rate * dt);
		if (target > p) {
			double in = (target - p) * volume / exhaustTemp;
			charge.fresh = charge.moles * charge.fresh / (charge.moles + in);
			charge.temperature = (charge.moles * charge.temperature + in * exhaustTemp) / (charge.moles + in);
			charge.moles += in;
		} else {
			double out = (p - target) * volume / charge.temperature;
			if (current.fuelBurned > 0 || last.fuelBurned > 0) {
				exhaustTemp += (charge.temperature - exhaustTemp) * Math.min(1, out / EXHAUST_MEMORY);
				// Only burning makes heat: a turbine can't be spun by compressed air that was never lit
				exhaustHeat += out * Math.max(0, charge.temperature - 1);
			}
			charge.moles -= out;
		}
	}
}
