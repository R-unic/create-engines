package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TurbochargerTest {

	private static final FuelSupply DIESEL = MixtureTest.unlimited(FuelSpec.DIESEL);

	/** A four-cylinder engine firing every 180°, all breathing through one turbo if there is one. */
	private static final class Rig {
		final List<CylinderModel> cylinders = new ArrayList<>();
		final EngineSimulation engine;
		final Turbocharger turbo;

		Rig(boolean turbocharged, FuelSupply fuel, double metering, double limit) {
			double[] tdc = {0, 0, 180, 180};
			double[] lobe = {0, 180, 90, 270};
			for (int i = 0; i < 4; i++) {
				CylinderModel cylinder =
					new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc[i], new Camshaft(), lobe[i]);
				cylinder.fuelSupply = fuel;
				cylinder.metering = metering;
				cylinders.add(cylinder);
			}
			engine = new EngineSimulation(cylinders, 1, 0.02);
			for (CylinderModel cylinder : cylinders)
				cylinder.resetGas(cylinder.pistonAngle(0, 1));
			if (turbocharged) {
				turbo = new Turbocharger(cylinders, cylinders, 1);
				turbo.limit = limit;
				engine.addTurbo(turbo);
			} else
				turbo = null;
		}

		void drive(double rpm, double seconds) {
			for (int i = 0; i < seconds * 20; i++)
				engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		}

		double work() {
			double work = 0;
			for (CylinderModel cylinder : cylinders)
				work += cylinder.lastCycle().work;
			return work;
		}
	}

	@Test
	void firingCylindersSpoolTheTurboAndLiftTheIntakePressure() {
		Rig rig = new Rig(true, DIESEL, 1, 2);
		rig.drive(128, 10);
		assertTrue(rig.turbo.boost() > 0.3, "boost " + rig.turbo.boost());
		for (CylinderModel cylinder : rig.cylinders) {
			assertEquals(1 + rig.turbo.boost(), cylinder.intakePressure, 1e-9);
			assertTrue(cylinder.intakeTemperature > 1);
		}
	}

	/** Compressed air that never burned is no use to a turbine: a motored engine makes no boost. */
	@Test
	void anEngineThatIsNotBurningFuelMakesNoBoost() {
		Rig rig = new Rig(true, FuelSupply.NONE, 1, 2);
		rig.drive(128, 10);
		assertEquals(0, rig.turbo.boost(), 0.01);
	}

	/** The wheel has inertia: the boost arrives over a second or two, it is not there the moment the engine fires. */
	@Test
	void theBoostBuildsWithALag() {
		Rig rig = new Rig(true, DIESEL, 1, 2);
		rig.drive(128, 1);
		double early = rig.turbo.boost();
		rig.drive(128, 10);
		double settled = rig.turbo.boost();
		assertTrue(early < 0.6 * settled, "after 1 s " + early + ", settled " + settled);
	}

	@Test
	void moreExhaustMeansMoreBoost() {
		Rig slow = new Rig(true, DIESEL, 1, 2);
		slow.drive(48, 15);
		Rig fast = new Rig(true, DIESEL, 1, 2);
		fast.drive(160, 15);
		assertTrue(fast.turbo.boost() > slow.turbo.boost() + 0.2,
			"48 RPM " + slow.turbo.boost() + ", 160 RPM " + fast.turbo.boost());
	}

	@Test
	void theWastegateHoldsTheBoostAtItsLimit() {
		for (double limit : new double[] {0.2, 0.4, 0.6}) {
			Rig rig = new Rig(true, DIESEL, 1, limit);
			rig.drive(240, 15);
			assertTrue(rig.turbo.boost() <= limit + 0.12, "limit " + limit + " held at " + rig.turbo.boost());
			assertTrue(rig.turbo.boost() >= limit - 0.05, "limit " + limit + " only reached " + rig.turbo.boost());
			assertTrue(rig.turbo.wastegate() > 0);
		}
	}

	@Test
	void dumpingTheBoostBlowsItOffAndItBuildsAgain() {
		Rig rig = new Rig(true, DIESEL, 1, 2);
		rig.drive(128, 10);
		double boost = rig.turbo.boost();
		assertTrue(boost > 0.3);
		rig.turbo.dump = true;
		rig.drive(128, 3);
		assertTrue(rig.turbo.boost() < 0.2 * boost, "boost after dumping " + rig.turbo.boost());
		rig.turbo.dump = false;
		rig.drive(128, 10);
		assertEquals(boost, rig.turbo.boost(), 0.1 * boost);
	}

	/** The turbine is in the exhaust's way, so the cylinders blow down against a pressure that rises with boost. */
	@Test
	void theTurbineHoldsTheExhaustAboveAmbient() {
		Rig rig = new Rig(true, DIESEL, 1, 2);
		rig.drive(128, 10);
		assertTrue(rig.cylinders.getFirst().exhaustPressure > 1.1, "" + rig.cylinders.getFirst().exhaustPressure);
		rig.turbo.dump = true;
		rig.drive(128, 5);
		assertTrue(rig.cylinders.getFirst().exhaustPressure < 1.05);
	}

	/** Pressure alone would double the air; heating it on the way in gives some of that density back. */
	@Test
	void compressingTheAirHeatsItSoItGainsLessDensityThanPressure() {
		Rig rig = new Rig(true, DIESEL, 1, 2);
		rig.drive(160, 15);
		CylinderModel cylinder = rig.cylinders.getFirst();
		double density = cylinder.intakePressure / cylinder.intakeTemperature;
		assertTrue(density > 1, "density " + density);
		assertTrue(density < cylinder.intakePressure, "density " + density + " pressure " + cylinder.intakePressure);
	}

	/** More air in the same cylinder, same injector setting: the mixture leans out. */
	@Test
	void boostPacksMoreAirIntoTheSameCylinder() {
		Rig plain = new Rig(false, DIESEL, 1, 0);
		plain.drive(160, 15);
		Rig boosted = new Rig(true, DIESEL, 1, 2);
		boosted.drive(160, 15);
		CycleStats before = plain.cylinders.getFirst().lastCycle();
		CycleStats after = boosted.cylinders.getFirst().lastCycle();
		assertTrue(after.freshAirAtInjection > 1.15 * before.freshAirAtInjection,
			"air " + before.freshAirAtInjection + " -> " + after.freshAirAtInjection);
		assertTrue(after.equivalence < before.equivalence);
	}

	/** Air is only worth having if there is fuel for it: with the injectors turned up, boost makes real power. */
	@Test
	void boostWithEnoughFuelMakesMorePower() {
		Rig plain = new Rig(false, DIESEL, 2, 0);
		plain.drive(160, 15);
		Rig boosted = new Rig(true, DIESEL, 2, 2);
		boosted.drive(160, 15);
		assertTrue(boosted.work() > 1.15 * plain.work(), "plain " + plain.work() + " boosted " + boosted.work());
	}

	/** Squeezing hotter air hotter still: diesel lights more easily and a spark fuel is nearer to pre-igniting. */
	@Test
	void boostRaisesTheTemperatureAtTheEndOfCompression() {
		Rig plain = new Rig(false, DIESEL, 1, 0);
		plain.drive(160, 15);
		Rig boosted = new Rig(true, DIESEL, 1, 2);
		boosted.drive(160, 15);
		assertTrue(boosted.cylinders.getFirst().lastCycle().compressionTemp
			> plain.cylinders.getFirst().lastCycle().compressionTemp);
	}

	/** Gasoline, which a spark engine runs on, pre-ignites by compression alone when boosted hard enough. */
	@Test
	void aHardBoostedSparkEngineLightsItsGasolineBeforeTheSpark() {
		FuelSupply gasoline = MixtureTest.unlimited(FuelSpec.GASOLINE);
		double unboosted = peakChargeTemperature(false, gasoline);
		double boosted = peakChargeTemperature(true, gasoline);
		assertTrue(boosted > unboosted + 0.2, "unboosted " + unboosted + ", boosted " + boosted);
	}

	private static double peakChargeTemperature(boolean turbocharged, FuelSupply fuel) {
		// Diesel to get the turbo going, then the compression temperature of the gasoline-sized charge is read off
		Rig rig = new Rig(turbocharged, DIESEL, 1, 2);
		rig.drive(200, 15);
		return rig.cylinders.getFirst().lastCycle().compressionTemp;
	}

	@Test
	void theDynamometerRatesABoostedEngineHigher() {
		Rig plain = new Rig(false, DIESEL, 2, 0);
		Rig boosted = new Rig(true, DIESEL, 2, 2);
		for (CylinderModel cylinder : plain.cylinders)
			cylinder.camshaft().ratio = 0.5;
		for (CylinderModel cylinder : boosted.cylinders)
			cylinder.camshaft().ratio = 0.5;
		double[] flat = Dynamometer.torqueCurve(plain.engine);
		double[] curve = Dynamometer.torqueCurve(boosted.engine);
		double flatPeak = Dynamometer.peakTorque(flat, 256);
		double boostedPeak = Dynamometer.peakTorque(curve, 256);
		assertTrue(boostedPeak > 1.1 * flatPeak, "plain " + flatPeak + " boosted " + boostedPeak);
		// The dyno measures copies: it must not have wound up the live turbo
		assertEquals(0, boosted.turbo.boost(), 1e-9);
	}

	@Test
	void theDynamometerLeavesTheTurboWastegateAsSet() {
		Rig rig = new Rig(true, DIESEL, 1, 0.25);
		for (CylinderModel cylinder : rig.cylinders)
			cylinder.camshaft().ratio = 0.5;
		double[] low = Dynamometer.torqueCurve(rig.engine);
		rig.turbo.limit = 1.5;
		double[] high = Dynamometer.torqueCurve(rig.engine);
		assertFalse(java.util.Arrays.equals(low, high));
	}

	@Test
	void aTurbolessCopyOfAnEngineIsUnaffected() {
		Rig plain = new Rig(false, DIESEL, 1, 0);
		plain.drive(128, 5);
		assertEquals(1, plain.cylinders.getFirst().intakePressure);
		assertEquals(1, plain.cylinders.getFirst().exhaustPressure);
		assertTrue(plain.engine.turbos().isEmpty());
	}
}
