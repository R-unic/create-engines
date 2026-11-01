package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The server's balance settings reach the simulation, and the shipped defaults leave it exactly as designed. */
class TuningTest {

	@AfterEach
	void restoreDefaults() {
		Tuning.reset();
	}

	/** A four-cylinder engine firing every 180 degrees, all breathing through one turbo if there is one. */
	private static final class Rig {
		final List<CylinderModel> cylinders = new ArrayList<>();
		final EngineSimulation engine;
		final Turbocharger turbo;

		Rig(boolean turbocharged, FuelSpec fuel, boolean spark, double metering) {
			double[] tdc = {0, 0, 180, 180};
			double[] lobe = {0, 180, 90, 270};
			for (int i = 0; i < 4; i++) {
				CylinderModel cylinder =
					new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc[i], new Camshaft(), lobe[i]);
				cylinder.fuelSupply = MixtureTest.unlimited(fuel);
				cylinder.metering = metering;
				cylinder.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
				cylinders.add(cylinder);
			}
			engine = new EngineSimulation(cylinders, 1, 0.02);
			for (CylinderModel cylinder : cylinders)
				cylinder.resetGas(cylinder.pistonAngle(0, 1));
			if (turbocharged) {
				turbo = new Turbocharger(cylinders, cylinders, 1);
				turbo.limit = 2;
				engine.addTurbo(turbo);
			} else
				turbo = null;
		}

		void drive(double rpm, double seconds) {
			for (int i = 0; i < seconds * 20; i++)
				engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		}

		double fouling() {
			double worst = 0;
			for (CylinderModel cylinder : cylinders)
				worst = Math.max(worst, cylinder.fouling());
			return worst;
		}
	}

	@Test
	void theDefaultsBuildTheCrudeAndPrecisionSpecsAsDesigned() {
		assertEquals(CylinderSpec.CRUDE, CylinderSpec.build(false, false, false, false));
		assertSame(CylinderSpec.ROTARY, CylinderSpec.buildRotary(false));
		assertEquals(CylinderSpec.CRUDE.compressionRatio() * CylinderSpec.PRECISION_COMPRESSION,
			CylinderSpec.build(true, false, false, false).compressionRatio(), 1e-12);
	}

	@Test
	void aPrecisionBonusOfZeroMakesPrecisionPartsCosmetic() {
		Tuning.precisionBonus = 0;
		assertEquals(CylinderSpec.CRUDE, CylinderSpec.build(true, true, true, true));
		assertEquals(CylinderSpec.ROTARY, CylinderSpec.buildRotary(true));
	}

	@Test
	void aPrecisionBonusOfTwoPullsTwiceAsFar() {
		Tuning.precisionBonus = 2;
		double crude = CylinderSpec.CRUDE.compressionRatio();
		assertEquals(crude * (1 + (CylinderSpec.PRECISION_COMPRESSION - 1) * 2),
			CylinderSpec.build(true, false, false, false).compressionRatio(), 1e-12);
		assertTrue(CylinderSpec.build(false, false, true, false)
			.inertia() < CylinderSpec.CRUDE.inertia() * CylinderSpec.PRECISION_PISTON_INERTIA);
	}

	@Test
	void rotaryPowerScalesTheChamberAndTheFuelItCanBurn() {
		Tuning.rotaryPower = 0.5;
		CylinderSpec half = CylinderSpec.buildRotary(false);
		assertNotSame(CylinderSpec.ROTARY, half);
		assertEquals(CylinderSpec.ROTARY.displacement() * 0.5, half.displacement(), 1e-12);
		assertEquals(CylinderSpec.ROTARY.maxFuelPerShot() * 0.5, half.maxFuelPerShot(), 1e-12);
	}

	@Test
	void boostStrengthScalesTheIntakePressureAndZeroTurnsItOff() {
		Rig full = new Rig(true, FuelSpec.DIESEL, false, 1);
		full.drive(128, 10);
		double boost = full.turbo.boost();
		assertTrue(boost > 0.3, "boost " + boost);

		Tuning.boostStrength = 0.5;
		Rig half = new Rig(true, FuelSpec.DIESEL, false, 1);
		half.drive(128, 10);
		for (CylinderModel cylinder : half.cylinders)
			assertEquals(1 + half.turbo.boost() * 0.5, cylinder.intakePressure, 1e-9);

		Tuning.boostStrength = 1;
		Tuning.forcedInduction = false;
		Rig off = new Rig(true, FuelSpec.DIESEL, false, 1);
		off.drive(128, 10);
		for (CylinderModel cylinder : off.cylinders) {
			assertEquals(1, cylinder.intakePressure, 1e-9);
			assertEquals(1, cylinder.exhaustPressure, 1e-9);
		}
	}

	@Test
	void sparkFoulingCanBeSwitchedOff() {
		Rig rich = new Rig(false, FuelSpec.GASOLINE, true, 1.6);
		rich.drive(100, 10);
		assertTrue(rich.fouling() > 0, "a rich spark engine fouls its plugs by default");

		Tuning.sparkFouling = false;
		Rig clean = new Rig(false, FuelSpec.GASOLINE, true, 1.6);
		clean.drive(100, 10);
		assertEquals(0, clean.fouling(), 0);
	}

	@Test
	void oilDilutionCanBeSwitchedOff() {
		Rig rich = new Rig(false, FuelSpec.DIESEL, false, 3);
		rich.engine.setWashesOil(true);
		rich.drive(100, 20);
		assertTrue(rich.engine.lubrication()
			.dilution() > 0, "a rich engine thins its oil by default");

		Tuning.oilDilution = false;
		Rig clean = new Rig(false, FuelSpec.DIESEL, false, 3);
		clean.engine.setWashesOil(true);
		clean.drive(100, 20);
		assertEquals(0, clean.engine.lubrication()
			.dilution(), 0);
	}

	@Test
	void blendIsIdentityAtOneAndNoEffectAtZero() {
		assertEquals(0.7, Tuning.blend(0.7, 1), 1e-12);
		assertEquals(1, Tuning.blend(0.7, 0), 1e-12);
		assertEquals(0.4, Tuning.blend(0.7, 2), 1e-12);
	}
}
