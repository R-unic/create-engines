package com.createengines.engine.sim;

/** A {@link CylinderModel} as its {@link Combustion} sees it. */
record CylinderInputs(CylinderModel model) implements CombustionInputs {

	@Override
	public FuelSupply fuelSupply() {
		return model.fuelSupply;
	}

	@Override
	public double usualShot(FuelSpec fuel) {
		return model.usualShot(fuel);
	}

	@Override
	public double injectorLimit(FuelSpec fuel) {
		return model.injectorLimit(fuel);
	}

	@Override
	public double rack() {
		return model.rack;
	}

	@Override
	public double metering() {
		return model.metering;
	}

	@Override
	public double fuelTrim() {
		return model.fuelTrim;
	}

	@Override
	public double governor() {
		return model.governor;
	}

	@Override
	public double governedFuel() {
		return model.governedFuel();
	}

	@Override
	public double airMatch() {
		return model.airMatch;
	}

	@Override
	public InjectorAssist assist() {
		return model.assist;
	}

	@Override
	public IgnitionSource ignition() {
		return model.ignition;
	}

	@Override
	public IgnitionCoil coil() {
		return model.coil;
	}

	@Override
	public FuelSupply pilotSupply() {
		return model.pilotSupply;
	}

	@Override
	public double lightingHeat() {
		return model.lightingHeat;
	}

	@Override
	public boolean ported() {
		return model.ported();
	}

	@Override
	public boolean missesSpark(double share) {
		return model.missesSpark(share);
	}

	@Override
	public double ignitionDelay() {
		return model.spec.ignitionDelay();
	}

	@Override
	public double burnDuration() {
		return model.spec.burnDuration();
	}

	@Override
	public double intakePressure() {
		return model.intakePressure;
	}

	@Override
	public double coldness() {
		return model.coldness;
	}

	@Override
	public double hotness() {
		return model.hotness;
	}

	@Override
	public Wear.State wear() {
		return model.wear;
	}

	@Override
	public double ringSeal() {
		return model.ringSeal;
	}

	@Override
	public double rotorEarlyLoss() {
		return model.rotorEarlyLoss;
	}

	@Override
	public double rotorBoostGain() {
		return model.rotorBoostGain;
	}

	@Override
	public void burned(double heat) {
		model.burned(heat);
	}
}
