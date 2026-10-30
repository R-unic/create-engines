package com.createengines.engine.sim;

import static com.createengines.engine.sim.MixtureTest.UNLIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The MAF sensor's fuel cap, the rev limiter's fuel cut, and the signals the sensors put out. */
class EngineManagementTest {

	private static CylinderModel runAt(double rpm, double metering, double airMatch, boolean fuelCut) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		cylinder.metering = metering;
		cylinder.airMatch = airMatch;
		cylinder.fuelCut = fuelCut;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder;
	}

	/** Without a sensor a fixed shot is rich at speed; with one the shot follows the air and burns clean. */
	@Test
	void aMafSensorKeepsAFastEngineOutOfTheRich() {
		assertTrue(runAt(256, 1, 0, false).lastCycle()
			.rich(), "the unsensed engine should run rich at 256 RPM");
		for (double rpm : new double[] {64, 128, 192, 256}) {
			CycleStats cycle = runAt(rpm, 1, 1, false).lastCycle();
			assertTrue(cycle.result.fired(), rpm + " RPM should fire");
			assertFalse(cycle.rich(), rpm + " RPM ran rich at " + cycle.equivalence);
			assertTrue(cycle.equivalence <= 1 + 1e-6, rpm + " RPM mixture " + cycle.equivalence);
		}
	}

	/** It is a cap: where the air can burn more than the shot, the shot is what it was set to. */
	@Test
	void aMafSensorNeverAddsFuel() {
		CylinderModel plain = runAt(32, 0.3, 0, false);
		CylinderModel sensed = runAt(32, 0.3, 1, false);
		assertEquals(plain.lastCycle().fuelInjected, sensed.lastCycle().fuelInjected, 1e-9);
		assertEquals(0, sensed.lastCycle().mafCut, 1e-9);
	}

	@Test
	void aMafSensorCutsMoreAsTheAirThins() {
		double slow = runAt(64, 2, 1, false).lastCycle().mafCut;
		double fast = runAt(256, 2, 1, false).lastCycle().mafCut;
		assertTrue(fast > slow, "cut " + fast + " at 256 RPM against " + slow + " at 64");
		assertTrue(fast > 0.5, "most of a double shot should go at 256 RPM: " + fast);
	}

	/** The sensor is what lets a big shot suit every speed: lean at the bottom, balanced and clean at the top. */
	@Test
	void aMafSensorLetsOneBigShotSuitEverySpeed() {
		for (double rpm : new double[] {32, 96, 160, 256}) {
			CycleStats cycle = runAt(rpm, 2, 1, false).lastCycle();
			assertTrue(Mixture.balanced(cycle.equivalence) || cycle.equivalence < 1,
				rpm + " RPM mixture " + cycle.equivalence);
			assertFalse(cycle.rich(), rpm + " RPM ran rich");
		}
	}

	/** A leaner target gives a leaner mixture. */
	@Test
	void theTargetSetsTheMixture() {
		double lean = runAt(256, 2, 0.8, false).lastCycle().equivalence;
		double rich = runAt(256, 2, 1.25, false).lastCycle().equivalence;
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
	void theRevLimiterCutsTheInjectorAndSaysItDid() {
		CylinderModel cut = runAt(64, 1, 0, true);
		assertEquals(0, cut.lastCycle().fuelInjected, 1e-9);
		assertEquals(CycleResult.FUEL_CUT, cut.lastCycle().result);
		assertFalse(cut.lastCycle().result.fired());
		assertTrue(cut.lastCycle().limited);
	}

	/** A cut engine slows, and fuel returns once it has fallen below the limit. */
	/** Fastest an unloaded single cylinder gets, started from a cranked 60 RPM, with {@code limit} (0 for none) fitted. */
	private static double[] runUnloaded(double limit) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = UNLIMITED;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 3 * 0.15 + 1, 0.02);
		cylinder.resetGas(0);
		RevLimiter limiter = new RevLimiter();
		double top = 0;
		boolean cut = false;
		boolean resumed = false;
		for (int i = 0; i < 20 * 60; i++) {
			boolean cutting = limit > 0 && limiter.update(engine.rpm(), limit);
			cylinder.fuelCut = cutting;
			cut |= cutting;
			resumed |= cut && !cutting;
			// Cranked as a starter would for ten seconds, then left to run on its own
			engine.tickCranked(0, EngineSimulation.rpmToOmega(256), i < 200 ? EngineSimulation.rpmToOmega(32) : 0, 1);
			if (i >= 200)
				top = Math.max(top, engine.rpm());
		}
		return new double[] {top, cut ? 1 : 0, resumed ? 1 : 0};
	}

	@Test
	void theRevLimiterHoldsASpeedByBouncingOffIt() {
		double free = runUnloaded(0)[0];
		assertTrue(free > 80, "the unlimited engine only reached " + free);
		double limit = free * 0.7;
		double[] limited = runUnloaded(limit);
		assertEquals(1, limited[1], "an engine that can reach " + free + " RPM should hit a limit of " + limit);
		assertEquals(1, limited[2], "fuel should come back once the engine slows");
		assertTrue(limited[0] < limit * 1.1, "the limiter let it reach " + limited[0] + " against " + limit);
	}

	@Test
	void theLimiterHasHysteresis() {
		RevLimiter limiter = new RevLimiter();
		assertFalse(limiter.update(119, 120));
		assertTrue(limiter.update(120, 120));
		assertTrue(limiter.update(118, 120), "still cutting just under the limit");
		assertTrue(limiter.update(115, 120), "still cutting at 96% of the limit");
		assertFalse(limiter.update(113, 120), "fuel returns below 95% of the limit");
		assertFalse(limiter.update(119, 120));
	}

	@Test
	void signalsScaleAndClamp() {
		assertEquals(0, Sensors.mafSignal(0));
		assertEquals(10, Sensors.mafSignal(1));
		assertEquals(15, Sensors.mafSignal(1.5));
		assertEquals(15, Sensors.mafSignal(4));
		assertEquals(0, Sensors.mafSignal(-1));

		assertEquals(0, Sensors.lambdaSignal(0));
		assertEquals(8, Sensors.lambdaSignal(1));
		assertEquals(6, Sensors.lambdaSignal(Mixture.LEAN_BAND));
		assertEquals(9, Sensors.lambdaSignal(Mixture.RICH_BAND));
		assertEquals(15, Sensors.lambdaSignal(2.5));

		assertEquals(0, Sensors.limiterSignal(0, 120));
		assertEquals(8, Sensors.limiterSignal(60, 120));
		assertEquals(15, Sensors.limiterSignal(120, 120));
		assertEquals(15, Sensors.limiterSignal(200, 120));
		assertEquals(0, Sensors.limiterSignal(60, 0));
	}

	@Test
	void airChargeIsTheShareOfABoreFull() {
		double bore = CylinderSpec.CRUDE.volumeAt(180);
		assertEquals(1, Sensors.airCharge(CylinderSpec.CRUDE, bore), 1e-9);
		assertEquals(0, Sensors.airCharge(CylinderSpec.CRUDE, -1), 1e-9);
		CycleStats cycle = runAt(32, 1, 0, false).lastCycle();
		double charge = Sensors.airCharge(CylinderSpec.CRUDE, cycle.freshAirAtInjection);
		assertTrue(charge > 0.3 && charge < 1.2, "a slow motored breath was " + charge);
	}
}
