package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Variable-geometry vanes and anti-lag. */
class TurboMechanicsTest {

	private static final FuelSupply DIESEL = MixtureTest.unlimited(FuelSpec.DIESEL);

	/** Fuel for anti-lag that counts what was drawn. */
	private static final class Can implements FuelSupply {
		double drawn;

		@Override
		public double draw(double mb) {
			drawn += mb;
			return mb;
		}

		@Override
		public FuelSpec fuel() {
			return FuelSpec.DIESEL;
		}
	}

	private static final class Rig {
		final List<CylinderModel> cylinders = new ArrayList<>();
		final EngineSimulation engine;
		final Turbocharger turbo;

		Rig(boolean variable, double limit) {
			double[] tdc = {0, 0, 180, 180};
			double[] lobe = {0, 180, 90, 270};
			for (int i = 0; i < 4; i++) {
				CylinderModel cylinder =
					new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc[i], new Camshaft(), lobe[i]);
				cylinder.fuelSupply = DIESEL;
				cylinders.add(cylinder);
			}
			engine = new EngineSimulation(cylinders, 1, 0.02);
			for (CylinderModel cylinder : cylinders)
				cylinder.resetGas(cylinder.pistonAngle(0, 1));
			turbo = new Turbocharger(cylinders, cylinders, 1);
			turbo.limit = limit;
			turbo.variableGeometry = variable;
			engine.addTurbo(turbo);
		}

		void drive(double rpm, double seconds) {
			for (int i = 0; i < seconds * 20; i++)
				engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		}
	}

	@Test
	void closedVanesSpoolAThinExhaustSooner() {
		Rig fixed = new Rig(false, 2);
		Rig variable = new Rig(true, 2);
		fixed.drive(48, 4);
		variable.drive(48, 4);
		assertTrue(variable.turbo.vanes() > 0.2, "vanes " + variable.turbo.vanes());
		assertTrue(variable.turbo.boost() > fixed.turbo.boost() * 1.2,
			"variable " + variable.turbo.boost() + " vs fixed " + fixed.turbo.boost());
	}

	@Test
	void aFixedTurboHasNoVanes() {
		Rig fixed = new Rig(false, 2);
		fixed.drive(48, 4);
		assertEquals(0, fixed.turbo.vanes());
	}

	@Test
	void openVanesChokeTheExhaustLessAtFullFlow() {
		Rig fixed = new Rig(false, 0.5);
		Rig variable = new Rig(true, 0.5);
		fixed.drive(240, 15);
		variable.drive(240, 15);
		// Both are held at the same boost by the wastegate, so the difference is the turbine's restriction
		assertEquals(fixed.turbo.boost(), variable.turbo.boost(), 0.15);
		assertTrue(variable.turbo.vanes() < 0.3, "vanes " + variable.turbo.vanes());
		assertTrue(variable.turbo.backPressure() < fixed.turbo.backPressure(),
			"variable " + variable.turbo.backPressure() + " vs fixed " + fixed.turbo.backPressure());
	}

	private static void liftOff(Rig rig) {
		for (CylinderModel cylinder : rig.cylinders)
			cylinder.rack = 0;
		rig.turbo.offThrottle = true;
	}

	@Test
	void withoutAntiLagTheBoostFallsAwayOffTheThrottle() {
		Rig rig = new Rig(false, 2);
		rig.drive(128, 10);
		double boost = rig.turbo.boost();
		assertTrue(boost > 0.3);
		liftOff(rig);
		rig.drive(128, 4);
		assertTrue(rig.turbo.boost() < 0.3 * boost, "boost " + rig.turbo.boost() + " of " + boost);
	}

	@Test
	void antiLagHoldsTheBoostUpOffTheThrottleAndBurnsFuelForIt() {
		Rig rig = new Rig(false, 2);
		Can can = new Can();
		rig.turbo.antiLagFuel = can;
		rig.turbo.antiLag = 3;
		rig.drive(128, 10);
		double boost = rig.turbo.boost();
		assertEquals(0, can.drawn, 1e-12, "anti-lag does nothing on the throttle");
		liftOff(rig);
		rig.drive(128, 6);
		assertTrue(rig.turbo.antiLagging());
		assertTrue(can.drawn > 0, "it burns fuel");
		assertTrue(rig.turbo.boost() > 0.4 * boost, "boost " + rig.turbo.boost() + " of " + boost);
	}

	@Test
	void antiLagCostsFuelInProportionToItsSetting() {
		double[] used = new double[2];
		for (int level = 1; level <= 2; level++) {
			Rig rig = new Rig(false, 2);
			Can can = new Can();
			rig.turbo.antiLagFuel = can;
			rig.turbo.antiLag = level;
			liftOff(rig);
			rig.drive(128, 5);
			used[level - 1] = can.drawn;
		}
		assertEquals(2 * used[0], used[1], 0.01 * used[1]);
	}

	@Test
	void antiLagNeedsFuelToBurn() {
		Rig rig = new Rig(false, 2);
		rig.turbo.antiLag = 3;
		rig.drive(128, 10);
		liftOff(rig);
		rig.drive(128, 4);
		assertFalse(rig.turbo.antiLagging());
	}
}
