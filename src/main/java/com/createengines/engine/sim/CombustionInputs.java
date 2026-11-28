package com.createengines.engine.sim;

/**
 * All that {@link Combustion} is told about the engine around the charge: what is set and fitted on its cylinder, the air
 * and block around it, and where its heat goes. A cylinder answers through {@link CylinderInputs}; the gas itself is
 * the {@link Charge} and the geometry is behind {@link CycleDrive}. Read live, so nothing is copied each step.
 */
interface CombustionInputs {

	// The injector and what trims its shot; each as on CylinderModel
	FuelSupply fuelSupply();

	double usualShot(FuelSpec fuel);

	double injectorLimit(FuelSpec fuel);

	double rack();

	double metering();

	double fuelTrim();

	double governor();

	double governedFuel();

	double airMatch();

	InjectorAssist assist();

	// Lighting the charge
	IgnitionSource ignition();

	IgnitionCoil coil();

	FuelSupply pilotSupply();

	double lightingHeat();

	/** Whether its valves are ports, as a rotor chamber's are: it never lights itself. */
	boolean ported();

	/** Whether a worn plug that misses {@code share} of its sparks drops this one; counts it, so ask once a spark. */
	boolean missesSpark(double share);

	// The burn
	double ignitionDelay();

	double burnDuration();

	double intakePressure();

	double coldness();

	double hotness();

	Wear.State wear();

	double ringSeal();

	double rotorEarlyLoss();

	double rotorBoostGain();

	/** Takes the heat a burn released, as it counts towards heating the block. */
	void burned(double heat);
}
