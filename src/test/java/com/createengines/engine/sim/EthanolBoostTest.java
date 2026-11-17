package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Ethanol is the fuel that boost cannot light early: a precision engine on twin turbos and an intercooler runs clean on it. */
class EthanolBoostTest {

	/** Most of the burn lost to pre-ignition in any cycle of a twin-turbo run, with a precision cylinder and an intercooler. */
	private static double worstPreignition(FuelSpec fuel, double rpm) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.build(true, true, true, true), CamProfile.BASIC, 0,
			new Camshaft(), 0);
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.fuelSupply = MixtureTest.unlimited(fuel);
		cylinder.ignition = IgnitionSource.UNLIMITED;
		cylinder.metering = 0.6;
		cylinder.resetGas(0);
		for (int i = 0; i < 2; i++) {
			Turbocharger turbo = new Turbocharger(List.of(cylinder), List.of(cylinder), 0.5);
			turbo.limit = 1.5;
			engine.addTurbo(turbo);
		}
		engine.addIntercooler(new Intercooler(List.of(cylinder)));
		double worst = 0;
		for (int i = 0; i < 20 * 40; i++) {
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
			worst = Math.max(worst, cylinder.lastCycle().preignition);
		}
		return worst;
	}

	@Test
	void aPrecisionEngineOnTwinTurbosAndAnIntercoolerNeverPreignitesOnEthanol() {
		for (double rpm : new double[] {256, 512, 640})
			assertEquals(0, worstPreignition(FuelSpec.ETHANOL, rpm), 0, "ethanol at " + rpm + " RPM");
	}

	@Test
	void theSameEngineOnGasolineStillLosesPowerToIt() {
		assertTrue(worstPreignition(FuelSpec.GASOLINE, 512) > 0.1);
	}

	@Test
	void ethanolStillIgnitesAndPilotsAtItsOwnAutoignitionPoint() {
		assertEquals(3.4, FuelSpec.ETHANOL.autoignitionTemp(), 0);
		assertTrue(FuelSpec.ETHANOL.preignitionTemp() > FuelSpec.ETHANOL.autoignitionTemp());
	}

	@Test
	void everyOtherFuelPreignitesAtItsAutoignitionPoint() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, new FuelSpec(10, 2, 1.2)})
			assertEquals(fuel.autoignitionTemp(), fuel.preignitionTemp(), 0);
	}
}
