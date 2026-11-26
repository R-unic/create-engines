package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The speed a rough engine gives out, against the speed its crank has at the end of each tick. */
class MeanSpeedEngineTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	/** One crude cylinder, no flywheel and little crank to carry it: as rough as an engine gets. */
	private static EngineSimulation single() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = new EngineSimulationTest.Tank(100000, FuelSpec.DIESEL);
		return new EngineSimulation(List.of(cylinder), 0.05, 0.02);
	}

	/**
	 * How often the engine would send its network a new speed over {@code speeds}, one a tick: by the rule in
	 * {@code EngineController.publishSpeed}, a whole number at least 4% off the last one, and not within ten ticks of it.
	 */
	private static int sends(double[] speeds) {
		long sent = Math.round(speeds[0]);
		int count = 0;
		int since = 10;
		for (double speed : speeds) {
			long target = Math.round(speed);
			since++;
			if (target != sent && Math.abs(target - sent) >= Math.max(1, sent * 0.04) && since >= 10) {
				sent = target;
				since = 0;
				count++;
			}
		}
		return count;
	}

	private static EngineSimulation running() {
		EngineSimulation engine = single();
		for (int i = 0; i < 200; i++)
			engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
		for (int i = 0; i < 2400; i++)
			engine.tickFree(0, MAX_OMEGA);
		return engine;
	}

	@Test
	void aRoughEngineRunningSteadilyHasASteadySpeedToGiveOut() {
		EngineSimulation engine = running();
		double[] instant = new double[1200];
		double[] mean = new double[instant.length];
		for (int i = 0; i < instant.length; i++) {
			engine.tickFree(0, MAX_OMEGA);
			instant[i] = engine.rpm();
			mean[i] = engine.meanRpm();
		}
		assertTrue(engine.ripple() > 0.1, "the engine should be rough: " + engine.ripple());
		assertTrue(sends(instant) > 20, "the instant speed should keep being sent anew: " + sends(instant));
		assertTrue(sends(mean) <= 1, "the mean should settle: sent " + sends(mean) + " times in a minute");
	}

	@Test
	void aRebuiltEngineCarriesTheMeanOn() {
		EngineSimulation old = running();
		EngineSimulation rebuilt = single();
		rebuilt.restore(old.angle(), old.omega(), old.direction());
		rebuilt.carryMeanSpeed(old);
		assertEquals(old.meanRpm(), rebuilt.meanRpm(), 0);
		// Its first new cycle is not whole yet, so the old one's mean is still the figure to go by
		rebuilt.tickFree(0, MAX_OMEGA);
		assertEquals(old.meanRpm(), rebuilt.meanRpm(), old.meanRpm() * 0.01);
	}

	@Test
	void beforeAFirstCycleWhileCrankedAndStoppedItIsTheInstantSpeed() {
		EngineSimulation engine = single();
		assertEquals(0, engine.meanRpm());
		engine.restore(0, EngineSimulation.rpmToOmega(90), 1);
		assertEquals(90, engine.meanRpm(), 1e-9, "a restored engine has no cycle yet, and is not at 0");
		engine.tickFree(0, MAX_OMEGA);
		assertEquals(engine.rpm(), engine.meanRpm(), 1e-9);

		EngineSimulation cranked = single();
		for (int i = 0; i < 100; i++) {
			cranked.tickCranked(5, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
			if (cranked.crankingTorque() > 0)
				assertEquals(cranked.rpm(), cranked.meanRpm(), 1e-9, "tick " + i);
		}

		EngineSimulation stalled = running();
		for (int i = 0; i < 2000 && !stalled.isStopped(); i++)
			stalled.tickFree(100, MAX_OMEGA);
		assertTrue(stalled.isStopped());
		assertEquals(0, stalled.meanRpm());
	}
}
