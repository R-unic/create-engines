package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PilotInjectionTest {

	private static final FuelSpec ETHANOL = FuelSpec.ETHANOL;

	/** A pilot tank that counts what it gives. */
	private static final class Pilot implements FuelSupply {
		final FuelSpec fuel;
		double mb;
		double drawn;

		Pilot(FuelSpec fuel, double mb) {
			this.fuel = fuel;
			this.mb = mb;
		}

		@Override
		public double draw(double amount) {
			double given = Math.min(mb, amount);
			mb -= given;
			drawn += given;
			return given;
		}

		@Override
		public FuelSpec fuel() {
			return fuel;
		}
	}

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static CylinderModel run(double rpm, FuelSpec main, FuelSupply pilot, IgnitionSource ignition) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = MixtureTest.unlimited(main);
		cylinder.pilotSupply = pilot;
		cylinder.ignition = ignition;
		cylinder.metering = 0.9;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder;
	}

	@Test
	void gasolineAndEthanolWillNotLightByCompressionAlone() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, ETHANOL})
			for (double rpm : new double[] {32, 64, 128})
				assertEquals(CycleResult.NO_IGNITION, run(rpm, fuel, FuelSupply.NONE, IgnitionSource.NONE).lastCycle().result);
	}

	@Test
	void aDieselPilotLightsGasolineAndEthanolWithNoSparkPlug() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, ETHANOL})
			for (double rpm : new double[] {32, 64, 128, 192}) {
				CycleStats cycle = run(rpm, fuel, new Pilot(FuelSpec.DIESEL, 1e9), IgnitionSource.NONE).lastCycle();
				assertEquals(CycleResult.FIRED, cycle.result, fuel + " at " + rpm + " RPM");
				assertTrue(cycle.work > 0, "and it should make power: " + cycle.work);
			}
	}

	@Test
	void thePilotShotIsAFewPercentOfTheMainShot() {
		Pilot pilot = new Pilot(FuelSpec.DIESEL, 1e9);
		CycleStats cycle = run(64, FuelSpec.GASOLINE, pilot, IgnitionSource.NONE).lastCycle();
		assertEquals(CylinderModel.PILOT_SHARE * cycle.fuelInjected, cycle.pilotShot, 1e-9);
		assertTrue(pilot.drawn >= cycle.pilotShot && pilot.drawn < 200 * cycle.pilotShot);
	}

	@Test
	void aFuelThatLightsItselfNeverUsesThePilot() {
		Pilot pilot = new Pilot(FuelSpec.DIESEL, 1e9);
		CycleStats cycle = run(64, FuelSpec.DIESEL, pilot, IgnitionSource.NONE).lastCycle();
		assertEquals(CycleResult.FIRED, cycle.result);
		assertEquals(0, pilot.drawn);
	}

	@Test
	void withoutPilotFuelLeftTheChargeIsNotLit() {
		Pilot pilot = new Pilot(FuelSpec.DIESEL, 0);
		assertEquals(CycleResult.NO_IGNITION, run(64, FuelSpec.GASOLINE, pilot, IgnitionSource.NONE).lastCycle().result);
	}

	@Test
	void aPilotThatIsItselfTooHardToLightLightsNothing() {
		Pilot pilot = new Pilot(ETHANOL, 1e9);
		assertEquals(CycleResult.NO_IGNITION, run(64, FuelSpec.GASOLINE, pilot, IgnitionSource.NONE).lastCycle().result);
		assertEquals(0, pilot.drawn);
	}

	@Test
	void aCylinderWithASparkPlugLightsWithItsPlugAndKeepsItsPilotFuel() {
		Pilot pilot = new Pilot(FuelSpec.DIESEL, 1e9);
		CycleStats cycle = run(64, FuelSpec.GASOLINE, pilot, IgnitionSource.UNLIMITED).lastCycle();
		assertEquals(CycleResult.FIRED, cycle.result);
		assertEquals(0, pilot.drawn);
		assertEquals(0, cycle.pilotShot);
	}

	@Test
	void aPilotLitChargeBurnsAboutAsWellAsASparkLitOne() {
		double sparked = run(96, FuelSpec.GASOLINE, FuelSupply.NONE, IgnitionSource.UNLIMITED).lastCycle().work;
		double piloted = run(96, FuelSpec.GASOLINE, new Pilot(FuelSpec.DIESEL, 1e9), IgnitionSource.NONE).lastCycle().work;
		assertTrue(piloted > 0.7 * sparked && piloted < 1.1 * sparked, "piloted " + piloted + " against sparked " + sparked);
	}

	@Test
	void theServerCanSwitchPilotInjectionOff() {
		Tuning.pilotInjection = false;
		assertFalse(run(64, FuelSpec.GASOLINE, new Pilot(FuelSpec.DIESEL, 1e9), IgnitionSource.NONE).lastCycle().result.fired());
	}
}
