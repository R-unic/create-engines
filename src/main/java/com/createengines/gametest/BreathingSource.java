package com.createengines.gametest;

import com.createengines.engine.sim.Breather;

/** An idle source that also breathes through a port network: it records the pressures the engine sets on it. */
final class BreathingSource extends IdleSource implements Breather {
	volatile double intakePressure = -1;
	volatile double intakeTemperature = -1;
	volatile double exhaustPressure = -1;

	@Override
	public double intakePressure() {
		return intakePressure;
	}

	@Override
	public void setIntakePressure(double pressure) {
		intakePressure = pressure;
	}

	@Override
	public double intakeTemperature() {
		return intakeTemperature;
	}

	@Override
	public void setIntakeTemperature(double temperature) {
		intakeTemperature = temperature;
	}

	@Override
	public double exhaustPressure() {
		return exhaustPressure;
	}

	@Override
	public void setExhaustPressure(double pressure) {
		exhaustPressure = pressure;
	}
}
