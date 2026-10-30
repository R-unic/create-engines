package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.advancement.Milestone;
import com.createengines.engine.EngineMilestones;

/** Several stages of forced induction on one engine: turbos that compound, blowers, and intercoolers. */
class ForcedInductionTest {

	private static final FuelSupply DIESEL = MixtureTest.unlimited(FuelSpec.DIESEL);

	/** A four-cylinder engine firing every 180°, with whatever forced induction the test adds. */
	private static final class Rig {
		final List<CylinderModel> cylinders = new ArrayList<>();
		final EngineSimulation engine;

		Rig(double metering) {
			double[] tdc = {0, 0, 180, 180};
			double[] lobe = {0, 180, 90, 270};
			for (int i = 0; i < 4; i++) {
				CylinderModel cylinder =
					new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc[i], new Camshaft(), lobe[i]);
				cylinder.fuelSupply = DIESEL;
				cylinder.metering = metering;
				cylinders.add(cylinder);
			}
			engine = new EngineSimulation(cylinders, 1, 0.02);
			for (CylinderModel cylinder : cylinders)
				cylinder.resetGas(cylinder.pistonAngle(0, 1));
		}

		Turbocharger turbo(double limit, double share) {
			Turbocharger turbo = new Turbocharger(cylinders, cylinders, share);
			turbo.limit = limit;
			engine.addTurbo(turbo);
			return turbo;
		}

		Supercharger blower(double rpm, double limit) {
			Supercharger blower = new Supercharger(cylinders);
			blower.rpm = rpm;
			blower.limit = limit;
			engine.addSupercharger(blower);
			return blower;
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

		double boost() {
			return cylinders.getFirst().intakePressure - 1;
		}
	}

	@Test
	void twoTurbosOnOneIntakeMakeMoreBoostThanOne() {
		Rig single = new Rig(1);
		single.turbo(1.5, 1);
		single.drive(160, 20);
		Rig twin = new Rig(1);
		twin.turbo(1.5, 0.5);
		twin.turbo(1.5, 0.5);
		twin.drive(160, 20);
		assertTrue(twin.boost() > 1.2 * single.boost(), "one " + single.boost() + ", two " + twin.boost());
	}

	/** The cylinders breathe through both compressors in turn, so the pressure ratios multiply. */
	@Test
	void theTurbosPressureRatiosMultiply() {
		Rig rig = new Rig(1);
		Turbocharger first = rig.turbo(1.5, 0.5);
		Turbocharger second = rig.turbo(1.5, 0.5);
		rig.drive(160, 20);
		assertEquals((1 + first.boost()) * (1 + second.boost()), rig.cylinders.getFirst().intakePressure, 1e-9);
		assertEquals(first.chargeTemperature() * second.chargeTemperature(), rig.cylinders.getFirst().intakeTemperature,
			1e-9);
	}

	@Test
	void eachTurbineHoldsTheExhaustUpSoTheBackPressuresAdd() {
		Rig single = new Rig(1);
		single.turbo(1.5, 1);
		single.drive(160, 20);
		Rig twin = new Rig(1);
		Turbocharger a = twin.turbo(1.5, 0.5);
		Turbocharger b = twin.turbo(1.5, 0.5);
		twin.drive(160, 20);
		assertEquals(a.backPressure() + b.backPressure() - 1, twin.cylinders.getFirst().exhaustPressure, 1e-9);
		assertTrue(a.backPressure() > 1.05 && b.backPressure() > 1.05);
		assertTrue(twin.cylinders.getFirst().exhaustPressure > single.cylinders.getFirst().exhaustPressure);
	}

	@Test
	void twoTurbosWithEnoughFuelMakeMorePowerThanOne() {
		Rig single = new Rig(3);
		single.turbo(1.5, 1);
		single.drive(160, 20);
		Rig twin = new Rig(3);
		twin.turbo(1.5, 0.5);
		twin.turbo(1.5, 0.5);
		twin.drive(160, 20);
		assertTrue(twin.work() > 1.05 * single.work(), "one " + single.work() + ", two " + twin.work());
	}

	private static Turbocharger parallelTurbo(Rig rig, double limit, int bank) {
		Turbocharger turbo = rig.turbo(limit, 1d / bank);
		turbo.parallelCompressor = true;
		turbo.parallelTurbine = true;
		turbo.flow = 1d / bank;
		return turbo;
	}

	/** Side by side, each compressor moves half the air, so the cylinder gets one compressor's pressure and not two's. */
	@Test
	void turbosSideBySideShareTheAirInsteadOfCompounding() {
		Rig single = new Rig(1);
		single.turbo(1.5, 1);
		single.drive(160, 20);
		Rig twin = new Rig(1);
		Turbocharger a = parallelTurbo(twin, 1.5, 2);
		Turbocharger b = parallelTurbo(twin, 1.5, 2);
		twin.drive(160, 20);
		assertEquals((a.boost() + b.boost()) / 2, twin.boost(), 1e-9);
		assertEquals(single.boost(), twin.boost(), 0.1 * single.boost());
	}

	/** The pair are smaller wheels each doing half the work, so they come up to the same boost sooner. */
	@Test
	void turbosSideBySideSpoolSoonerThanOneBigTurbo() {
		Rig single = new Rig(1);
		single.turbo(1.5, 1);
		single.drive(160, 2);
		Rig twin = new Rig(1);
		parallelTurbo(twin, 1.5, 2);
		parallelTurbo(twin, 1.5, 2);
		twin.drive(160, 2);
		assertTrue(twin.boost() > 1.3 * single.boost(), "one " + single.boost() + ", pair " + twin.boost());
	}

	@Test
	void turbinesSideBySideAverageTheirBackPressure() {
		Rig twin = new Rig(1);
		Turbocharger a = parallelTurbo(twin, 1.5, 2);
		Turbocharger b = parallelTurbo(twin, 1.5, 2);
		twin.drive(160, 20);
		assertEquals((a.backPressure() + b.backPressure()) / 2, twin.cylinders.getFirst().exhaustPressure, 1e-9);
	}

	/** A blower in parallel with a turbo averages with it, and one in series still multiplies. */
	@Test
	void aParallelBlowerAveragesWithTheTurboBesideIt() {
		Rig rig = new Rig(1);
		Turbocharger turbo = parallelTurbo(rig, 1.5, 2);
		Supercharger blower = rig.blower(128, 1.5);
		blower.parallel = true;
		rig.drive(160, 10);
		assertEquals(((1 + turbo.boost()) + (1 + blower.boost())) / 2, rig.cylinders.getFirst().intakePressure, 1e-9);
	}

	/** Turbos on separate intakes do not feel each other: each cylinder only breathes what its own run feeds. */
	@Test
	void aTurboOnlyBoostsTheCylindersOnItsOwnIntake() {
		Rig rig = new Rig(1);
		Turbocharger bank = new Turbocharger(rig.cylinders.subList(0, 2), rig.cylinders.subList(0, 2), 1);
		bank.limit = 1.5;
		rig.engine.addTurbo(bank);
		rig.drive(160, 20);
		assertTrue(rig.cylinders.get(0).intakePressure > 1.1);
		assertEquals(1, rig.cylinders.get(2).intakePressure, 1e-9);
	}

	@Test
	void aSuperchargerMakesBoostFromItsShaftSpeedAlone() {
		Rig rig = new Rig(1);
		Supercharger blower = rig.blower(128, 1.5);
		rig.drive(60, 3);
		assertEquals(Supercharger.FULL_BOOST, blower.boost(), 0.02);
		assertEquals(1 + blower.boost(), rig.cylinders.getFirst().intakePressure, 1e-9);
	}

	@Test
	void superchargerBoostGoesWithTheSquareOfTheSpeed() {
		Rig half = new Rig(1);
		Supercharger slow = half.blower(64, 1.5);
		half.drive(60, 3);
		assertEquals(Supercharger.FULL_BOOST / 4, slow.boost(), 0.02);
		Rig fast = new Rig(1);
		Supercharger quick = fast.blower(192, 1.5);
		fast.drive(60, 3);
		assertEquals(Math.min(1.5, Supercharger.FULL_BOOST * 2.25), quick.boost(), 0.03);
	}

	/** No exhaust heat is needed and there is no wheel to spin up: the boost is there within a moment. */
	@Test
	void aSuperchargerBoostsAnEngineThatIsNotBurningAnything() {
		Rig rig = new Rig(1);
		for (CylinderModel cylinder : rig.cylinders)
			cylinder.fuelSupply = FuelSupply.NONE;
		Supercharger blower = rig.blower(128, 1.5);
		rig.drive(60, 1);
		assertTrue(blower.boost() > 0.8 * Supercharger.FULL_BOOST, "boost " + blower.boost());
	}

	@Test
	void theBypassValveHoldsTheBoostAtItsLimit() {
		Rig rig = new Rig(1);
		Supercharger blower = rig.blower(256, 0.4);
		rig.drive(60, 3);
		assertEquals(0.4, blower.boost(), 0.02);
		assertTrue(blower.bypass() > 0.5);
	}

	@Test
	void dumpingTheBlowerBlowsTheBoostOff() {
		Rig rig = new Rig(1);
		Supercharger blower = rig.blower(128, 1.5);
		rig.drive(60, 3);
		blower.dump = true;
		rig.drive(60, 3);
		assertTrue(blower.boost() < 0.02, "boost " + blower.boost());
	}

	@Test
	void aSuperchargedEngineWithEnoughFuelMakesMorePower() {
		Rig plain = new Rig(2);
		plain.drive(120, 15);
		Rig blown = new Rig(2);
		blown.blower(128, 1.5);
		blown.drive(120, 15);
		assertTrue(blown.work() > 1.15 * plain.work(), "plain " + plain.work() + " blown " + blown.work());
	}

	/** A turbo's boost needs time and fuel, a blower's does not; run together the two multiply. */
	@Test
	void aTurboAndASuperchargerTogetherMultiply() {
		Rig rig = new Rig(1);
		Turbocharger turbo = rig.turbo(1.5, 1);
		Supercharger blower = rig.blower(128, 1.5);
		rig.drive(160, 20);
		assertEquals((1 + turbo.boost()) * (1 + blower.boost()), rig.cylinders.getFirst().intakePressure, 1e-9);
		assertTrue(turbo.boost() > 0.3 && blower.boost() > 0.5);
	}

	@Test
	void aBlowerHeatsTheAirMoreThanATurboAtTheSameBoost() {
		Rig rig = new Rig(1);
		Supercharger blower = rig.blower(128, 0.5);
		rig.drive(60, 3);
		Turbocharger turbo = new Turbocharger(rig.cylinders, rig.cylinders, 1);
		assertTrue(blower.chargeTemperature() > 1);
		// Same pressure ratio through a more efficient compressor: a turbo at the blower's boost would be cooler
		double turboTemp = 1 + (Math.pow(1 + blower.boost(), 0.3 / 1.3) - 1) / 0.7;
		assertTrue(blower.chargeTemperature() > turboTemp, blower.chargeTemperature() + " vs " + turboTemp);
		assertTrue(turbo.compressor().size() == 4);
	}

	@Test
	void anIntercoolerTakesHeatOutOfTheChargeAndGivesDensityBack() {
		Rig bare = new Rig(1);
		bare.blower(128, 0.9);
		bare.drive(60, 3);
		Rig cooled = new Rig(1);
		cooled.blower(128, 0.9);
		cooled.engine.addIntercooler(new Intercooler(cooled.cylinders));
		cooled.drive(60, 3);
		CylinderModel a = bare.cylinders.getFirst();
		CylinderModel b = cooled.cylinders.getFirst();
		assertTrue(b.intakeTemperature < a.intakeTemperature - 0.1, a.intakeTemperature + " -> " + b.intakeTemperature);
		assertTrue(b.intakePressure < a.intakePressure, "the core is a restriction");
		assertTrue(b.intakePressure / b.intakeTemperature > a.intakePressure / a.intakeTemperature,
			"density " + a.intakePressure / a.intakeTemperature + " -> " + b.intakePressure / b.intakeTemperature);
	}

	@Test
	void anIntercoolerWithoutBoostDoesNothing() {
		Rig rig = new Rig(1);
		rig.engine.addIntercooler(new Intercooler(rig.cylinders));
		rig.drive(60, 2);
		assertEquals(1, rig.cylinders.getFirst().intakePressure, 1e-9);
		assertEquals(1, rig.cylinders.getFirst().intakeTemperature, 1e-9);
	}

	@Test
	void theDynamometerRatesASuperchargedEngineHigherAndRunsTheBlowerWithTheCrank() {
		Rig plain = new Rig(2);
		Rig blown = new Rig(2);
		Supercharger blower = blown.blower(0, 1.5);
		blower.driveRatio = 2;
		for (CylinderModel cylinder : plain.cylinders)
			cylinder.camshaft().ratio = 0.5;
		for (CylinderModel cylinder : blown.cylinders)
			cylinder.camshaft().ratio = 0.5;
		double flat = Dynamometer.peakTorque(Dynamometer.torqueCurve(plain.engine), 256);
		double curve = Dynamometer.peakTorque(Dynamometer.torqueCurve(blown.engine), 256);
		assertTrue(curve > 1.1 * flat, "plain " + flat + " blown " + curve);
		// The measurement used copies, which turned with the crank: the live blower, off its shaft, is untouched
		assertEquals(0, blower.boost(), 1e-9);
	}

	@Test
	void aBlowerGearedUpMakesMoreBoostOnTheDynamometer() {
		Rig slow = new Rig(2);
		slow.blower(0, 1.5).driveRatio = 0.5;
		Rig fast = new Rig(2);
		fast.blower(0, 1.5).driveRatio = 1.5;
		for (CylinderModel cylinder : slow.cylinders)
			cylinder.camshaft().ratio = 0.5;
		for (CylinderModel cylinder : fast.cylinders)
			cylinder.camshaft().ratio = 0.5;
		double low = Dynamometer.peakTorque(Dynamometer.torqueCurve(slow.engine), 256);
		double high = Dynamometer.peakTorque(Dynamometer.torqueCurve(fast.engine), 256);
		assertTrue(high > low, "geared down " + low + ", geared up " + high);
	}

	@Test
	void inductionMilestonesNeedARunningEngine() {
		assertTrue(EngineMilestones.reachedInduction(new EngineMilestones.Induction(false, 0.9f, 2, 2, 2, 0.9f))
			.isEmpty());
		var reached = EngineMilestones.reachedInduction(new EngineMilestones.Induction(true, 0.4f, 2, 1, 0, 0.4f));
		assertTrue(reached.contains(Milestone.SUPERCHARGED));
		assertTrue(reached.contains(Milestone.COMPOUND_BOOST));
		assertTrue(reached.contains(Milestone.TWINCHARGED));
		assertTrue(reached.contains(Milestone.COOLED_CHARGE));
		assertFalse(reached.contains(Milestone.TWIN_TURBO));
		var weak = EngineMilestones.reachedInduction(new EngineMilestones.Induction(true, 0.1f, 1, 0, 0, 0.1f));
		assertTrue(weak.isEmpty(), weak.toString());
	}

	@Test
	void twoTurbosSideBySideEarnTheTwinTurboMilestoneAndNotTheCompoundOne() {
		var reached = EngineMilestones.reachedInduction(new EngineMilestones.Induction(true, 0, 0, 0, 2, 0));
		assertTrue(reached.contains(Milestone.TWIN_TURBO));
		assertFalse(reached.contains(Milestone.COMPOUND_BOOST));
	}
}
