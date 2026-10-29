package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MixtureTest {

	private static final FuelSupply UNLIMITED = new FuelSupply() {
		@Override
		public double draw(double mb) {
			return mb;
		}

		@Override
		public FuelSpec fuel() {
			return FuelSpec.PLANT_OIL;
		}
	};

	private static CylinderModel runAt(double rpm, FuelSupply fuel) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = fuel;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder;
	}

	/** Breathing falls with speed, so a fixed shot used to go unburned (and smoke) from about 128 RPM up. */
	@Test
	void injectorsNeverOutrunTheAirAtAnySpeed() {
		for (double rpm : Dynamometer.SPEEDS) {
			CycleStats cycle = runAt(rpm, UNLIMITED).lastCycle();
			assertTrue(cycle.result.fired(), rpm + " RPM should fire");
			assertFalse(cycle.rich(), rpm + " RPM should not run rich");
			assertEquals(0, cycle.unburnedFraction(), 0.02, rpm + " RPM left fuel unburned");
		}
	}

	@Test
	void slowEnginesStillGetTheFullShot() {
		CycleStats cycle = runAt(32, UNLIMITED).lastCycle();
		assertEquals(CylinderSpec.CRUDE.maxFuelPerShot(), cycle.fuelInjected, 1e-9);
	}

	/** The limiter must not hide a cylinder that has no air as one that has no fuel. */
	@Test
	void cylinderWithNoAirIsStillStarvedNotUnfuelled() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		cylinder.intakeFactor = 0;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(32), 1);
		assertEquals(CycleResult.NO_AIR, cylinder.lastCycle().result);
	}
}
