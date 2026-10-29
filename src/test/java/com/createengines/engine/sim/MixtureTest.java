package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MixtureTest {

	static final FuelSupply UNLIMITED = unlimited(FuelSpec.PLANT_OIL);

	static FuelSupply unlimited(FuelSpec fuel) {
		return new FuelSupply() {
			@Override
			public double draw(double mb) {
				return mb;
			}

			@Override
			public FuelSpec fuel() {
				return fuel;
			}
		};
	}

	static CylinderModel runAt(double rpm, FuelSupply fuel, double metering, IgnitionSource ignition) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = fuel;
		cylinder.metering = metering;
		cylinder.ignition = ignition;
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return cylinder;
	}

	private static CylinderModel runAt(double rpm, FuelSupply fuel) {
		return runAt(rpm, fuel, 1, IgnitionSource.NONE);
	}

	/** Breathing falls with speed, so one fixed setting is right at one speed and rich above it. */
	@Test
	void aFixedShotRunsRicherAsTheEngineSpeedsUp() {
		CycleStats slow = runAt(32, UNLIMITED).lastCycle();
		CycleStats fast = runAt(256, UNLIMITED).lastCycle();
		assertTrue(slow.result.fired() && fast.result.fired());
		assertFalse(slow.rich(), "32 RPM should not run rich");
		assertTrue(fast.rich(), "256 RPM should run rich on the same setting");
		assertTrue(fast.equivalence > slow.equivalence);
		assertTrue(fast.unburnedFraction() > 0.5, "most of the fuel should be wasted at 256 RPM");
	}

	/** The cure for a rich engine: turn the injector down until it matches the air at the speed it runs at. */
	@Test
	void meteringMatchedToTheSpeedBurnsCleanlyAtAnySpeed() {
		for (double rpm : Dynamometer.SPEEDS) {
			double air = runAt(rpm, UNLIMITED).lastCycle().freshAirAtInjection;
			double matched = 0.9 * air / FuelSpec.PLANT_OIL.airPerMb() / CylinderSpec.CRUDE.maxFuelPerShot();
			CycleStats cycle = runAt(rpm, UNLIMITED, matched, IgnitionSource.NONE).lastCycle();
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

	@Test
	void meteringScalesTheShot() {
		CycleStats half = runAt(32, UNLIMITED, 0.5, IgnitionSource.NONE).lastCycle();
		assertEquals(CylinderSpec.CRUDE.maxFuelPerShot() / 2, half.fuelInjected, 1e-9);
	}

	/** Starving the cylinder is a fault too: it fires, but weakly, and it says so. */
	@Test
	void tooLittleFuelRunsLeanAndMakesLessWork() {
		CycleStats tuned = runAt(64, UNLIMITED, 1, IgnitionSource.NONE).lastCycle();
		CycleStats lean = runAt(64, UNLIMITED, 0.25, IgnitionSource.NONE).lastCycle();
		assertTrue(lean.result.fired());
		assertTrue(lean.lean(), "equivalence " + lean.equivalence);
		assertFalse(tuned.lean());
		assertTrue(lean.work < 0.6 * tuned.work, "lean " + lean.work + " vs tuned " + tuned.work);
	}

	/** Fuel the air can't burn isn't just wasted: it soaks up heat the piston would have had. */
	@Test
	void aRichMixtureMakesLessWorkThanAMatchedOne() {
		double air = runAt(160, UNLIMITED).lastCycle().freshAirAtInjection;
		double matched = 0.9 * air / FuelSpec.PLANT_OIL.airPerMb() / CylinderSpec.CRUDE.maxFuelPerShot();
		CycleStats tuned = runAt(160, UNLIMITED, matched, IgnitionSource.NONE).lastCycle();
		CycleStats rich = runAt(160, UNLIMITED, 2, IgnitionSource.NONE).lastCycle();
		assertTrue(rich.rich());
		assertTrue(rich.work < tuned.work, "rich " + rich.work + " vs matched " + tuned.work);
	}

	/** Metering has to leave a cylinder with no air at all reading as starved, not as unfuelled. */
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
