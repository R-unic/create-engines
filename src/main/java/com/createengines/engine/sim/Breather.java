package com.createengines.engine.sim;

/**
 * A {@link TorqueSource} that breathes: it draws air at a pressure and temperature, and blows its exhaust down into a
 * pressure. The engine's turbochargers, superchargers and intercoolers work on these and nothing else, so a source a pack
 * or an addon supplies can sit in an intake or exhaust network the way a cylinder does (see
 * {@code EngineSourceProvider#intakeAt} and {@code exhaustAt}). A source that does not implement this is simply not
 * boosted per port; it still gets the engine's air as a whole through {@link TorqueSource#setIntake}.
 * <p>
 * The engine sets all three values once a tick, before the source is stepped, and the source reads them. All pressures are
 * multiples of the atmosphere (1 is none) and the temperature is relative to ambient.
 * <p>
 * Pure Java, like the rest of the package. Part of the public API (see {@code com.createengines.api.ApiVersion}).
 */
public interface Breather extends TorqueSource {

	/** Pressure of the air the source draws on. */
	double intakePressure();

	/** Called by the engine. */
	void setIntakePressure(double pressure);

	/** Temperature of that air, ambient = 1. */
	double intakeTemperature();

	/** Called by the engine. */
	void setIntakeTemperature(double temperature);

	/** Pressure the source's exhaust blows down into; above 1 where a turbine is in the way. */
	double exhaustPressure();

	/** Called by the engine. */
	void setExhaustPressure(double pressure);
}
