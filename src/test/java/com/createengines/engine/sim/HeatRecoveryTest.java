package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HeatRecoveryTest {

	@Test
	void levelsRiseWithHeat() {
		assertEquals(HeatRecovery.NONE, HeatRecovery.level(0, HeatRecovery.NONE));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(HeatRecovery.SMOULDERING, HeatRecovery.NONE));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(HeatRecovery.KINDLED, HeatRecovery.NONE));
		assertEquals(HeatRecovery.SEETHE, HeatRecovery.level(HeatRecovery.SEETHING, HeatRecovery.NONE));
	}

	@Test
	void aLevelIsHeldThroughASmallDip() {
		double dipped = HeatRecovery.KINDLED * 0.9;
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(dipped, HeatRecovery.KINDLE));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(dipped, HeatRecovery.SMOULDER));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(HeatRecovery.KINDLED * 0.5, HeatRecovery.KINDLE));
	}

	@Test
	void signalIsOffWhenColdAndSaturates() {
		assertEquals(0, HeatRecovery.signal(0));
		assertEquals(1, HeatRecovery.signal(HeatRecovery.SMOULDERING));
		assertEquals(15, HeatRecovery.signal(HeatRecovery.SEETHING * 2));
		assertEquals(15, HeatRecovery.signal(1000));
	}

	/** Exhaust heat per second an in-line engine of {@code cylinders} puts out at {@code rpm}. */
	private static double exhaustHeat(int cylinders, FuelSpec fuel, boolean spark, double rpm) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 1;
			model.airMatch = 1;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, cylinders * 0.35 + 1, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		double total = 0;
		int counted = 0;
		for (int tick = 0; tick < 400; tick++) {
			sim.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
			if (tick < 200)
				continue;
			for (CylinderModel model : models)
				total += model.exhaustHeatLastTick();
			counted++;
		}
		return total / counted / EngineSimulation.TICK;
	}

	@Test
	void aSmallEngineCooksDieselAndABigOneCracksGasoline() {
		double small = exhaustHeat(4, FuelSpec.DIESEL, false, 128);
		assertTrue(small >= HeatRecovery.KINDLED && small < HeatRecovery.SEETHING,
			"a four-cylinder at speed makes enough heat for heated recipes only: " + small);
		double single = exhaustHeat(1, FuelSpec.DIESEL, false, 64);
		assertTrue(single < HeatRecovery.KINDLED, "one cylinder turning slowly doesn't: " + single);
		double big = exhaustHeat(8, FuelSpec.GASOLINE, true, 256);
		assertTrue(big >= HeatRecovery.SEETHING, "an eight-cylinder at speed superheats: " + big);
	}
}
