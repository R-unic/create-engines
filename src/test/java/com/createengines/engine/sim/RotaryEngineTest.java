package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The Wankel rotor: three ported chambers on a shaft that turns three times to the rotor's once. */
class RotaryEngineTest {

	private static final double STRESS_PER_TORQUE = 100;
	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	/** The three chambers of a rotor whose eccentric points at its mark at {@code eccentric} degrees. */
	private static List<CylinderModel> rotor(double eccentric, CylinderSpec spec, FuelSpec fuel, boolean spark) {
		List<CylinderModel> chambers = new ArrayList<>();
		for (int chamber = 0; chamber < 3; chamber++) {
			CylinderModel model = CylinderModel.rotary(spec, CamProfile.ROTARY, eccentric, chamber);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			chambers.add(model);
		}
		return chambers;
	}

	private static EngineSimulation engineOf(List<CylinderModel> models, double inertia) {
		EngineSimulation sim = new EngineSimulation(models, inertia, 0.04);
		sim.restore(0, 0, 1);
		for (CylinderModel model : models)
			model.resetGas(model.pistonAngle(0, 1));
		return sim;
	}

	private static double peakPower(double[] curve) {
		double best = 0;
		for (int i = 0; i < curve.length; i++)
			best = Math.max(best, curve[i] * Dynamometer.SPEEDS[i] * STRESS_PER_TORQUE);
		return best;
	}

	private static double pistonPower(int cylinders, FuelSpec fuel, boolean spark) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, cylinders * 0.35 + 1, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		return peakPower(Dynamometer.torqueCurve(sim));
	}

	@Test
	void aRotorCycleIsAThirdOfAShaftTurnPerStroke() {
		CylinderModel chamber = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 0);
		// One shaft turn is two-thirds of a chamber's cycle degrees: 240 of its 720
		assertEquals(0, chamber.pistonAngle(0, 0, 1), 1e-9);
		assertEquals(240, chamber.pistonAngle(360, 360, 1), 1e-9);
		assertEquals(480, chamber.pistonAngle(720 % 720, 720, 1), 1e-9);
		// The cycle comes round again after three turns
		assertEquals(0, chamber.pistonAngle(0, 1080 % 1080, 1), 1e-9);
		assertEquals(chamber.pistonAngle(0, 0, 1), chamber.pistonAngle(0, 1080, 1), 1e-9);
	}

	@Test
	void theThreeChambersAreAShaftTurnApart() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true);
		for (double shaft : new double[] {0, 100, 500, 900}) {
			double first = chambers.get(0).pistonAngle(shaft, shaft, 1);
			double second = chambers.get(1).pistonAngle(shaft, shaft, 1);
			double third = chambers.get(2).pistonAngle(shaft, shaft, 1);
			assertEquals(240, (second - first + 720) % 720, 1e-6);
			assertEquals(240, (third - second + 720) % 720, 1e-6);
		}
	}

	@Test
	void aRotorFiresOncePerShaftTurn() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true);
		int[] injections = new int[3];
		for (int i = 0; i < 3; i++) {
			int chamber = i;
			FuelSupply counting = new FuelSupply() {
				@Override
				public double draw(double mb) {
					injections[chamber]++;
					return mb;
				}

				@Override
				public FuelSpec fuel() {
					return FuelSpec.GASOLINE;
				}
			};
			chambers.get(i).fuelSupply = counting;
		}
		EngineSimulation sim = engineOf(chambers, 1.2);
		// 90 RPM for 80 seconds is 120 shaft turns: 40 turns of the rotor, each chamber lighting once in each
		double omega = EngineSimulation.rpmToOmega(90);
		for (int t = 0; t < 20 * 80; t++)
			sim.tickDriven(omega, 1);
		for (int count : injections)
			assertTrue(count >= 39 && count <= 41, "each chamber injects once a rotor turn: " + count);
		for (CylinderModel chamber : chambers)
			assertTrue(chamber.lastCycle().result.fired(), "and lights: " + chamber.lastCycle().result);
	}

	@Test
	void aRotorEngineRunsOnItsOwn() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true);
		for (CylinderModel chamber : chambers)
			chamber.metering = 1;
		EngineSimulation sim = engineOf(chambers, 1.2);
		for (int i = 0; i < 20 * 3; i++)
			sim.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(40), 1);
		for (int i = 0; i < 20 * 10; i++)
			sim.tickFree(0, MAX_OMEGA);
		assertTrue(sim.rpm() > 60, "a released rotor keeps turning under its own power: " + sim.rpm());
		assertTrue(sim.isSustaining());
	}

	@Test
	void aRotorCannotBeLitByCompressionAlone() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.DIESEL, false);
		EngineSimulation sim = engineOf(chambers, 1.2);
		for (int i = 0; i < 20 * 3; i++)
			sim.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(40), 1);
		for (int i = 0; i < 20 * 8; i++)
			sim.tickFree(0, MAX_OMEGA);
		assertFalse(sim.isSustaining(), "diesel does not light at the rotor's compression");
	}

	@Test
	void aRotorMakesMoreThanAPistonCylinderOnItsOwn() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true);
		double rotary = peakPower(Dynamometer.torqueCurve(engineOf(chambers, 1.2)));
		double piston = pistonPower(1, FuelSpec.GASOLINE, true);
		double two = pistonPower(2, FuelSpec.GASOLINE, true);
		assertTrue(rotary > piston * 1.2, "a rotor out-makes a cylinder: " + rotary + " against " + piston);
		assertTrue(rotary < two * 1.3, "but is no better than the two it replaces: " + rotary + " against " + two);
	}

	@Test
	void aRotorKeepsBreathingAtSpeedBetterThanAPiston() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true);
		double[] rotary = Dynamometer.torqueCurve(engineOf(chambers, 1.2));
		Camshaft cam = new Camshaft();
		CylinderModel piston = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, cam, 0);
		piston.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
		piston.metering = 2;
		piston.airMatch = 1;
		piston.ignition = IgnitionSource.UNLIMITED;
		EngineSimulation sim = new EngineSimulation(List.of(piston), 1.4, 0.04);
		piston.resetGas(0);
		double[] pistonCurve = Dynamometer.torqueCurve(sim);
		int fast = Dynamometer.SPEEDS.length - 4;
		int slow = 3;
		double rotaryFall = rotary[fast] / rotary[slow];
		double pistonFall = pistonCurve[fast] / pistonCurve[slow];
		assertTrue(rotaryFall > pistonFall, "the rotor holds its torque at speed: " + rotaryFall + " against " + pistonFall);
	}

	@Test
	void aRotorWithNoSparkMakesNoTorque() {
		List<CylinderModel> chambers = rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, false);
		double[] curve = Dynamometer.torqueCurve(engineOf(chambers, 1.2));
		assertTrue(Dynamometer.peakTorque(curve, 256) <= 0, "gasoline cannot be lit without a plug");
	}

	@Test
	void aSecondRotorHalfATurnOnSmoothsTheRunning() {
		List<CylinderModel> both = new ArrayList<>(rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true));
		both.addAll(rotor(180, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true));
		EngineSimulation twin = engineOf(both, 1.6);
		double[] curve = Dynamometer.torqueCurve(twin);
		double single = peakPower(Dynamometer.torqueCurve(engineOf(rotor(0, CylinderSpec.ROTARY, FuelSpec.GASOLINE, true), 1.2)));
		double pair = peakPower(curve);
		assertTrue(pair > single * 1.7, "two rotors make about twice one: " + pair + " against " + single);
	}
}
