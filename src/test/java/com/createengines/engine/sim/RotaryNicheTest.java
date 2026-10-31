package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A rotary engine is a place in the mix, not a better piston engine: compact, and willing to rev, but weak at low
 * speed and thirstier than a piston engine built from precision parts, so the precision piston engine stays the ceiling.
 */
class RotaryNicheTest {

	private static EngineSimulation pistons(int cylinders, CylinderSpec spec) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, cylinders * 0.35 + 1.0, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		return sim;
	}

	private static EngineSimulation rotors(int count) {
		List<CylinderModel> models = new ArrayList<>();
		for (int r = 0; r < count; r++)
			for (int chamber = 0; chamber < 3; chamber++) {
				CylinderModel model =
					CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 360d / count * r, chamber);
				model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
				model.metering = 2;
				model.airMatch = 1;
				model.ignition = IgnitionSource.UNLIMITED;
				models.add(model);
			}
		EngineSimulation sim = new EngineSimulation(models, count * 0.8 + 1.0, (count * 3 + 1) * 0.01);
		sim.restore(0, 0, 1);
		for (CylinderModel model : models)
			model.resetGas(model.pistonAngle(0, 1));
		return sim;
	}

	/** Net torque at {@code rpm}, found the way the dynamometer finds it. */
	private static double torqueAt(EngineSimulation sim, double rpm) {
		double[] curve = Dynamometer.torqueCurve(sim);
		for (int i = 0; i < Dynamometer.SPEEDS.length; i++)
			if (Dynamometer.SPEEDS[i] == rpm)
				return curve[i];
		throw new IllegalArgumentException("not a dynamometer speed: " + rpm);
	}

	/** Power made per unit of fuel burned, at {@code rpm}, with the racks open. */
	private static double powerPerFuel(EngineSimulation sim, double rpm) {
		for (CylinderModel c : sim.cylinders())
			c.resetGas(c.pistonAngle(0, 1));
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return sim.netTorque() * rpm / sim.fuelRate();
	}

	@Test
	void aRotorIsWeakAtLowSpeedNextToTheCylindersItIsWorth() {
		// At high speed a rotor is worth about two crude cylinders; slowly it is worth little more than one
		double crudePair = torqueAt(pistons(2, CylinderSpec.CRUDE), 32);
		double rotor = torqueAt(rotors(1), 32);
		assertTrue(rotor < 0.65 * crudePair, "rotor " + rotor + " vs two crude cylinders " + crudePair);
	}

	@Test
	void aRotorIsThirstierThanAPrecisionPistonEngine() {
		CylinderSpec precise = CylinderSpec.build(true, true, true, true);
		double piston = powerPerFuel(pistons(4, precise), 128);
		double rotor = powerPerFuel(rotors(2), 128);
		assertTrue(rotor < 0.75 * piston, "rotor " + rotor + " vs precision pistons " + piston);
	}

	@Test
	void aPrecisionPistonEngineRevsOnWhereARotorRunsOutOfBreath() {
		CylinderSpec precise = CylinderSpec.build(true, true, true, true);
		double piston = torqueAt(pistons(4, precise), 256) / 4;
		double rotor = torqueAt(rotors(1), 256);
		assertTrue(piston > 0 && rotor < piston, "precision cylinder " + piston + " vs rotor " + rotor);
	}
}
