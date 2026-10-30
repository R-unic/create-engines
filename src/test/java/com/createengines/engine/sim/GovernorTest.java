package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The mechanical governor holds an engine at its set speed, and gets out of the way when the load is too heavy. */
class GovernorTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	private static EngineSimulation engine(int cylinders, FuelSpec fuel, boolean spark) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.airMatch = 1;
			model.metering = 1.5;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 1 + cylinders * 0.35, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		sim.restore(0, EngineSimulation.rpmToOmega(50), 1);
		return sim;
	}

	/** Runs for {@code seconds} against {@code load}, governed to {@code target} unless that is 0; returns the final state. */
	private static double[] run(EngineSimulation sim, Governor governor, double target, double load, double seconds,
								double[] range) {
		for (int t = 0; t < seconds * 20; t++) {
			double position = target > 0 ? governor.update(sim.rpm(), target, EngineSimulation.TICK) : 1;
			for (CylinderModel cylinder : sim.cylinders())
				cylinder.governor = position;
			sim.tickFree(load, MAX_OMEGA);
			if (range != null && t >= seconds * 20 - 60) {
				range[0] = Math.min(range[0], sim.rpm());
				range[1] = Math.max(range[1], sim.rpm());
			}
		}
		return new double[] {sim.rpm(), governor.position()};
	}

	@Test
	void anUnloadedEngineIsHeldAtItsSetSpeed() {
		EngineSimulation free = engine(4, FuelSpec.DIESEL, false);
		double ungoverned = run(free, new Governor(), 0, 0, 20, null)[0];
		assertTrue(ungoverned > 130, "an ungoverned engine should rev well past 80: " + ungoverned);

		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		double[] range = {1e9, 0};
		double[] state = run(sim, new Governor(), 80, 0, 20, range);
		assertEquals(80, state[0], 3, "governed speed");
		assertTrue(range[1] - range[0] < 8, "speed should hold steady: " + range[0] + " to " + range[1]);
		assertTrue(state[1] < 0.6, "an unloaded engine needs little fuel: " + state[1]);
	}

	@Test
	void aLoadComingAndGoingDoesNotMoveIt() {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		Governor governor = new Governor();
		run(sim, governor, 80, 0, 10, null);

		double[] loaded = {1e9, 0};
		run(sim, governor, 80, 0.8, 15, loaded);
		assertTrue(loaded[0] > 74 && loaded[1] < 86, "a load should not drag the speed far: " + loaded[0] + " to " + loaded[1]);

		double[] relieved = {1e9, 0};
		run(sim, governor, 80, 0, 15, relieved);
		assertTrue(relieved[0] > 74 && relieved[1] < 86, "dropping the load should not let it race: " + relieved[0] + " to " + relieved[1]);
	}

	@Test
	void theResetBringsSpeedBackToTheSettingUnderLoad() {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		Governor governor = new Governor();
		double[] state = run(sim, governor, 80, 0.9, 30, null);
		assertEquals(80, state[0], 2, "the reset should remove the droop");
		assertTrue(state[1] > 0.3 && state[1] < 1, "carrying a load takes some rack: " + state[1]);
	}

	/** A load the engine cannot carry at the set speed pulls it down; the governor has the rack wide open and keeps it so. */
	@Test
	void anOverloadedEngineSlowsWithTheRackWideOpen() {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		Governor governor = new Governor();
		double[] state = run(sim, governor, 80, 3, 25, null);
		assertTrue(state[0] < 70, "it can't hold the speed: " + state[0]);
		assertEquals(1, state[1], 1e-9, "the rack should be wide open");
		assertTrue(state[0] > 8, "it should be slowed, not stalled: " + state[0]);
	}

	/** A spark engine is governed on its air, so the charge stays rich enough to light. */
	@Test
	void aSparkEngineIsGovernedOnItsThrottle() {
		EngineSimulation sim = engine(4, FuelSpec.GASOLINE, true);
		double[] range = {1e9, 0};
		double[] state = run(sim, new Governor(), 80, 0, 25, range);
		assertEquals(80, state[0], 4, "governed speed");
		assertTrue(range[1] - range[0] < 10, "steady: " + range[0] + " to " + range[1]);
		int firing = 0;
		for (CylinderModel cylinder : sim.cylinders())
			if (cylinder.lastCycle().result.fired())
				firing++;
		assertTrue(firing >= 3, "most cylinders should still be firing under the throttle: " + firing);
	}

	@Test
	void aGovernorDoesNotChangeWhatTheEngineIsRatedAt() {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		double[] plain = Dynamometer.torqueCurve(sim);
		for (CylinderModel cylinder : sim.cylinders())
			cylinder.governor = 0.1;
		double[] governed = Dynamometer.torqueCurve(sim);
		for (int i = 0; i < plain.length; i++)
			assertEquals(plain[i], governed[i], 1e-9);
	}

	@Test
	void theSignalIsFifteenWhenOpenAndZeroWhenShut() {
		assertEquals(15, Governor.signal(1));
		assertEquals(0, Governor.signal(0));
		assertEquals(8, Governor.signal(0.5));
	}
}
