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

	/** The throttle can close far enough to hold a spark engine at a slow setting, not just the speed it idles at. */
	@Test
	void aSparkEngineCanBeGovernedSlowly() {
		for (int cylinders : new int[] {4, 12}) {
			EngineSimulation sim = engine(cylinders, FuelSpec.GASOLINE, true);
			double[] state = run(sim, new Governor(), 40, 0, 25, null);
			assertEquals(40, state[0], 8, cylinders + " cylinders held at 40 RPM");
		}
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

	/** Fuel drawn in 20 seconds with nothing to drive, governed to {@code target} unless that is 0. */
	private static double fuelUsed(double target) {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		double[] used = {0};
		for (CylinderModel cylinder : sim.cylinders()) {
			FuelSupply supply = cylinder.fuelSupply;
			cylinder.fuelSupply = new FuelSupply() {
				@Override
				public double draw(double mb) {
					used[0] += mb;
					return supply.draw(mb);
				}

				@Override
				public FuelSpec fuel() {
					return supply.fuel();
				}
			};
		}
		run(sim, new Governor(), target, 0, 20, null);
		return used[0];
	}

	/** With nothing to drive, a governed engine eases off the fuel where an ungoverned one races on full shots. */
	@Test
	void anUnloadedGovernedEngineBurnsLessFuelThanARacingOne() {
		double racing = fuelUsed(0);
		double governed = fuelUsed(80);
		assertTrue(governed < racing * 0.6, "governed " + governed + " mB against racing " + racing + " mB");
	}

	/** No signal leaves the setting alone; one asks for that share of it, 15 for all of it, and never a stall. */
	@Test
	void aRedstoneSignalScalesTheGovernedSpeed() {
		assertEquals(120, Governor.commanded(120, 0), 1e-9);
		assertEquals(120, Governor.commanded(120, 15), 1e-9);
		assertEquals(64, Governor.commanded(120, 8), 1e-9);
		assertEquals(40, Governor.commanded(120, 5), 1e-9);
		assertEquals(Governor.MIN_COMMANDED, Governor.commanded(120, 1), 1e-9);
		assertEquals(Governor.MIN_COMMANDED, Governor.commanded(Governor.MIN_COMMANDED, 3), 1e-9);
		assertEquals(120, Governor.commanded(120, 40), 1e-9, "a signal can't ask for more than the setting");
	}

	/** A governor turned down by redstone holds the lower speed, steadily. */
	@Test
	void aCommandedGovernorHoldsTheLowerSpeed() {
		EngineSimulation sim = engine(4, FuelSpec.DIESEL, false);
		double[] range = {1e9, 0};
		double[] state = run(sim, new Governor(), Governor.commanded(120, 8), 0, 20, range);
		assertEquals(64, state[0], 3, "commanded speed");
		assertTrue(range[1] - range[0] < 8, "steady: " + range[0] + " to " + range[1]);
	}

	/** Picking up from a saved rack position carries on from it rather than throwing the rack open. */
	@Test
	void aSeededGovernorContinuesFromItsSavedRack() {
		Governor governor = new Governor();
		governor.seed(0.4, 80, 80);
		assertEquals(0.4, governor.update(80, 80, EngineSimulation.TICK), 0.05);
		governor.reset();
		governor.seed(Double.NaN, 80, 80);
		assertEquals(1, governor.update(0, 80, EngineSimulation.TICK), 1e-9);
	}

	/** Bad input never produces a NaN rack. */
	@Test
	void badInputLeavesTheRackAlone() {
		Governor governor = new Governor();
		governor.update(80, 80, EngineSimulation.TICK);
		double before = governor.position();
		assertEquals(before, governor.update(Double.NaN, 80, EngineSimulation.TICK), 1e-9);
		assertEquals(before, governor.update(80, 0, EngineSimulation.TICK), 1e-9);
	}

	/** An overspeed shuts the rack at once, however smooth the reading. */
	@Test
	void anOverspeedShutsTheRackAtOnce() {
		Governor governor = new Governor();
		governor.update(80, 80, EngineSimulation.TICK);
		assertEquals(0, governor.update(120, 80, EngineSimulation.TICK), 1e-9);
	}

	@Test
	void theSignalIsFifteenWhenOpenAndZeroWhenShut() {
		assertEquals(15, Governor.signal(1));
		assertEquals(0, Governor.signal(0));
		assertEquals(8, Governor.signal(0.5));
	}
}
