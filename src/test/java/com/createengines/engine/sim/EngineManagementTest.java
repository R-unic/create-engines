package com.createengines.engine.sim;

import static com.createengines.engine.sim.MixtureTest.UNLIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The MAF sensor's fuel metering, in both directions, and the signals the sensors put out. */
class EngineManagementTest {

	/** The smallest shot Fuel Metering allows, a tenth of the usual. */
	private static final double MIN_METERING = 0.1;

	private static CylinderModel runAt(double rpm, double metering, double airMatch) {
		return runAt(rpm, metering, airMatch, 1, 1);
	}

	private static CylinderModel runAt(double rpm, double metering, double airMatch, double rack, double intakePressure) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		cylinder.metering = metering;
		cylinder.airMatch = airMatch;
		cylinder.rack = rack;
		cylinder.intakePressure = intakePressure;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder;
	}

	/** Without a sensor a fixed shot is rich at speed; with one the shot follows the air and burns clean. */
	@Test
	void aMafSensorKeepsAFastEngineOutOfTheRich() {
		assertTrue(runAt(256, 1, 0).lastCycle()
			.rich(), "the unsensed engine should run rich at 256 RPM");
		for (double rpm : new double[] {64, 128, 192, 256}) {
			CycleStats cycle = runAt(rpm, 1, 1).lastCycle();
			assertTrue(cycle.result.fired(), rpm + " RPM should fire");
			assertFalse(cycle.rich(), rpm + " RPM ran rich at " + cycle.equivalence);
			assertTrue(cycle.equivalence <= 1 + 1e-6, rpm + " RPM mixture " + cycle.equivalence);
		}
	}

	/** It works both ways: where the air can burn more than the shot, the sensor tops it up. */
	@Test
	void aMafSensorAddsFuelToAShotThatIsTooSmall() {
		CylinderModel plain = runAt(32, 0.3, 0);
		CylinderModel sensed = runAt(32, 0.3, 1);
		assertTrue(sensed.lastCycle().fuelInjected > plain.lastCycle().fuelInjected * 1.5,
			"sensed " + sensed.lastCycle().fuelInjected + " against " + plain.lastCycle().fuelInjected);
		assertTrue(sensed.lastCycle().mafAdded > 0.3, "the sensor should say it added fuel: " + sensed.lastCycle().mafAdded);
		assertEquals(0, sensed.lastCycle().mafCut, 1e-9);
		assertEquals(0, plain.lastCycle().mafAdded, 1e-9);
		assertTrue(Mixture.balanced(sensed.lastCycle().equivalence), "mixture " + sensed.lastCycle().equivalence);
	}

	/** A starved engine runs a balanced mixture at every speed, with no tuning. */
	@Test
	void aMafSensorFeedsAStarvedEngineAtEverySpeed() {
		for (double rpm : new double[] {32, 96, 160, 256}) {
			CycleStats cycle = runAt(rpm, MIN_METERING, 1).lastCycle();
			assertTrue(cycle.result.fired(), rpm + " RPM should fire");
			assertTrue(Mixture.balanced(cycle.equivalence), rpm + " RPM mixture " + cycle.equivalence);
		}
	}

	/** Fuel Metering says what the injector is set to give; a sensor takes no notice of it. */
	@Test
	void fuelMeteringDoesNotMatterToAMafSensor() {
		double small = runAt(96, 0.1, 1).lastCycle().fuelInjected;
		double big = runAt(96, 2, 1).lastCycle().fuelInjected;
		assertEquals(big, small, big * 1e-6);
	}

	@Test
	void aMafSensorCutsMoreAsTheAirThins() {
		double slow = runAt(64, 2, 1).lastCycle().mafCut;
		double fast = runAt(256, 2, 1).lastCycle().mafCut;
		assertTrue(fast > slow, "cut " + fast + " at 256 RPM against " + slow + " at 64");
		assertTrue(fast > 0.5, "most of a double shot should go at 256 RPM: " + fast);
	}

	/** The injector is only so big: boost that the air could burn more fuel in than it can give goes unmatched. */
	@Test
	void aMafSensorCannotAddMoreThanTheInjectorCanGive() {
		CycleStats boosted = runAt(32, 1, 1.25, 1, 6).lastCycle();
		double most = CylinderSpec.CRUDE.maxFuelPerShot() * CylinderModel.INJECTOR_CAPACITY;
		assertEquals(most, boosted.fuelInjected, 1e-9);
		assertTrue(boosted.equivalence < 1.25, "a starved-for-fuel boosted cylinder runs leaner than the target: " + boosted.equivalence);
	}

	/** The rack still says how much of the matched shot is wanted. */
	@Test
	void aPartlyClosedRackStillScalesTheMatchedShot() {
		double open = runAt(96, 1, 1, 1, 1).lastCycle().fuelInjected;
		double half = runAt(96, 1, 1, 0.5, 1).lastCycle().fuelInjected;
		assertEquals(0.5, half / open, 0.1);
	}

	/** A leaner target gives a leaner mixture. */
	@Test
	void theTargetSetsTheMixture() {
		double lean = runAt(256, 2, 0.8).lastCycle().equivalence;
		double rich = runAt(256, 2, 1.25).lastCycle().equivalence;
		assertTrue(lean < rich, "0.8 target gave " + lean + ", 1.25 target gave " + rich);
		assertEquals(0.8, lean, 0.05);
	}

	@Test
	void aCylinderWithNoAirGetsNoFuelAndSaysSo() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		cylinder.airMatch = 1;
		cylinder.intakeFactor = 0;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 10; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(64), 1);
		assertEquals(0, cylinder.lastCycle().fuelInjected, 1e-9);
		assertEquals(CycleResult.NO_AIR, cylinder.lastCycle().result);
	}

	@Test
	void aRackClosedByRedstoneStillReadsAsNoFuel() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		cylinder.airMatch = 1;
		cylinder.rack = 0;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 10; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(64), 1);
		assertEquals(CycleResult.NO_FUEL, cylinder.lastCycle().result);
	}

	@Test
	void signalsScaleAndClamp() {
		assertEquals(0, Sensors.mafSignal(0));
		assertEquals(10, Sensors.mafSignal(1));
		assertEquals(15, Sensors.mafSignal(1.5));
		assertEquals(15, Sensors.mafSignal(4));
		assertEquals(0, Sensors.mafSignal(-1));
	}

	@Test
	void airChargeIsTheShareOfABoreFull() {
		double bore = CylinderSpec.CRUDE.volumeAt(180);
		assertEquals(1, Sensors.airCharge(CylinderSpec.CRUDE, bore), 1e-9);
		assertEquals(0, Sensors.airCharge(CylinderSpec.CRUDE, -1), 1e-9);
		CycleStats cycle = runAt(32, 1, 0).lastCycle();
		double charge = Sensors.airCharge(CylinderSpec.CRUDE, cycle.freshAirAtInjection);
		assertTrue(charge > 0.3 && charge < 1.2, "a slow motored breath was " + charge);
	}
}
