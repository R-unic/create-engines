package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OilConditionTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void freshOilStartsNewAndAgesWithTurning() {
		Lubrication oil = new Lubrication(1);
		assertEquals(1, oil.condition());
		oil.age(0, 1);
		assertEquals(1, oil.condition());
		oil.age(300, 1);
		assertEquals(1 - 300 * Lubrication.AGE_PER_REV, oil.condition(), 1e-12);
		oil.age(1e9, 1);
		assertEquals(0, oil.condition(), "oil is spent, not negative");
	}

	@Test
	void hotAndThinnedOilAgesFasterAndAFilterSlowsIt() {
		Lubrication oil = new Lubrication(1);
		double warm = oil.stress(Thermal.NORMAL, false);
		assertEquals(1, warm, 1e-12, "a warm engine burning clean works the oil at the reference rate");
		assertTrue(oil.stress(1.3, false) > 1.5 * warm, "hot oil ages faster");
		assertEquals(warm, oil.stress(0.3, false), "cool oil is no better than warm");
		oil.setDilution(0.4);
		assertTrue(oil.stress(Thermal.NORMAL, false) > 1.5, "fuel in the oil ages it");
		assertEquals(oil.stress(Thermal.NORMAL, false) * Lubrication.FILTER_AGEING, oil.stress(Thermal.NORMAL, true), 1e-12);
	}

	@Test
	void changingOilBringsTheConditionBackByTheShareChanged() {
		Lubrication oil = new Lubrication(1);
		oil.setCondition(0);
		oil.refresh(0);
		assertEquals(0, oil.condition());
		oil.refresh(Lubrication.CHARGE_VOLUME);
		assertEquals(1 - Math.exp(-1), oil.condition(), 1e-9, "changing one charge restores the usual 63 percent");
		oil.refresh(Lubrication.CHARGE_VOLUME * 20);
		assertTrue(oil.condition() > 0.999, "changing it all out leaves it new");
	}

	@Test
	void freshOilCleansFuelOutOfTheOilToo() {
		Lubrication oil = new Lubrication(1);
		oil.setDilution(0.4);
		oil.refresh(Lubrication.CHARGE_VOLUME);
		assertEquals(0.4 * Math.exp(-1), oil.dilution(), 1e-9);
	}

	@Test
	void spentOilDragsByAtMostAQuarterAndNeverStopsAnEngine() {
		EngineSimulation sim = steadyEngine();
		sim.lubrication().setCondition(1);
		double fresh = sim.frictionTorque();
		sim.lubrication().setCondition(0);
		double spent = sim.frictionTorque();
		assertEquals(fresh * (1 + Lubrication.MAX_AGED_FRICTION), spent, 1e-9);
		// Still runs, just a little slower
		double freshSpeed = freeRunning(1);
		double spentSpeed = freeRunning(0);
		assertTrue(spentSpeed > 0.8 * freshSpeed, "spent oil runs at " + spentSpeed + " against " + freshSpeed);
		assertTrue(spentSpeed < freshSpeed, "it should still drag a little");
	}

	@Test
	void oilThatNeverGoesOffStaysNewWhenTheServerSaysSo() {
		Tuning.oilAgeing = false;
		Lubrication oil = new Lubrication(1);
		oil.age(5000, 3);
		assertEquals(1, oil.condition());
	}

	@Test
	void anExchangeRateSettlesTheConditionHigherTheFasterItIs() {
		double off = settle(0);
		double trickle = settle(0.25);
		double steady = settle(1);
		double flush = settle(4);
		assertTrue(off < 0.02, "no exchange leaves the oil spent: " + off);
		assertTrue(trickle > off && steady > trickle && flush > steady, off + " " + trickle + " " + steady + " " + flush);
		assertTrue(trickle > 0.4 && trickle < 0.9, "a trickle keeps most of the life in it: " + trickle);
		assertTrue(flush > 0.95, "a flush keeps it as good as new: " + flush);
	}

	@Test
	void aFilterRaisesTheSettledConditionWithoutAnyExchange() {
		double bare = settleWith(0.25, false);
		double filtered = settleWith(0.25, true);
		assertTrue(filtered > bare, bare + " against " + filtered);
	}

	@Test
	void anEngineAgesItsOilThroughTheSimulation() {
		EngineSimulation sim = steadyEngine();
		double before = sim.lubrication().condition();
		for (int i = 0; i < 20 * 120; i++) {
			// Something holds the crank at 128 RPM, as a starter or another source would
			sim.tickCranked(0, EngineSimulation.rpmToOmega(1024), EngineSimulation.rpmToOmega(128), 1);
			sim.ageOil(false);
		}
		double spent = before - sim.lubrication().condition();
		// 128 RPM for two minutes is 256 turns of a 3000-turn oil life
		assertEquals(256 * Lubrication.AGE_PER_REV, spent, 0.02, "spent " + spent);
	}

	/** Condition an engine at 128 RPM settles at, exchanging {@code rate} mB of oil per second. */
	private static double settle(double rate) {
		return settleWith(rate, false);
	}

	private static double settleWith(double rate, boolean filtered) {
		Lubrication oil = new Lubrication(1);
		double revsPerSecond = 128 / 60d;
		for (int second = 0; second < 6000; second++) {
			oil.age(revsPerSecond, oil.stress(Thermal.NORMAL, filtered));
			oil.refresh(rate);
		}
		return oil.condition();
	}

	private static EngineSimulation steadyEngine() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = MixtureTest.unlimited(FuelSpec.DIESEL);
		cylinder.airMatch = 1;
		EngineSimulation sim = new EngineSimulation(List.of(cylinder), 1, 0.02);
		cylinder.resetGas(0);
		return sim;
	}

	/** Speed a lone cylinder settles at, free-running with oil of the given condition. */
	private static double freeRunning(double condition) {
		EngineSimulation sim = steadyEngine();
		sim.lubrication().setCondition(condition);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		for (int i = 0; i < 20 * 60; i++)
			sim.tickFree(0, EngineSimulation.rpmToOmega(1024));
		assertFalse(sim.isStopped(), "the engine should still be running at condition " + condition);
		return sim.rpm();
	}
}
