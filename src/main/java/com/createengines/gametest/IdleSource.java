package com.createengines.gametest;

import com.createengines.engine.sim.TorqueSource;

/** A source that sits on the crank and does nothing: no work, no friction, no inertia. For testing what carries it. */
final class IdleSource implements TorqueSource {
	int steps;

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
}
