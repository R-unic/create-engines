package com.createengines.gametest;

import java.util.List;

import com.createengines.engine.sim.StatusLine;
import com.createengines.engine.sim.TorqueSource;

/** A source that sits on the crank and does nothing: no work, no friction, no inertia. For testing what carries it. */
class IdleSource implements TorqueSource {
	int steps;
	/** What the engine last told it about the air it breathes; negative until it has. */
	volatile double pressure = -1;
	volatile double temperature = -1;

	@Override
	public double inertia() {
		return 0;
	}

	@Override
	public double frictionStatic() {
		return 0;
	}

	@Override
	public double frictionViscous() {
		return 0;
	}

	@Override
	public double frictionAt(double omega, double film) {
		return 0;
	}

	@Override
	public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
		steps++;
		return 0;
	}

	@Override
	public double fuelUsedLastStep() {
		return 0;
	}

	@Override
	public void endTick() {
	}

	@Override
	public double exhaustHeatLastTick() {
		return 0;
	}

	@Override
	public void setColdness(double coldness) {
	}

	@Override
	public void setDirection(int direction) {
	}

	@Override
	public double unburnedFraction() {
		return -1;
	}

	@Override
	public boolean fired() {
		return false;
	}

	@Override
	public double availableWork() {
		return 0;
	}

	@Override
	public void setIntake(double pressure, double temperature) {
		this.pressure = pressure;
		this.temperature = temperature;
	}

	@Override
	public List<StatusLine> status() {
		return List.of(StatusLine.of("test.idle.spool", StatusLine.Tone.WARN, "80"), StatusLine.of("test.idle.ok", StatusLine.Tone.GOOD));
	}
}
