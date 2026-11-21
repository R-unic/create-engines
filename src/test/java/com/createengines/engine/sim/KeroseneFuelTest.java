package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Kerosene: the all-rounder between diesel and gasoline, a spark fuel that holds out against boost better than gasoline. */
class KeroseneFuelTest {

	private static final double STRESS_PER_TORQUE = 78.7;
	/** {@code CEDataMaps.PILOT_IGNITION_LIMIT}, which a unit test cannot load; {@code FuelTooltipsTest} style checks hold the real one. */
	private static final double PILOT_IGNITION_LIMIT = 2.0;

	private static double peakPower(FuelSpec fuel, boolean spark, boolean turbo) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / 12;
		for (int i = 0; i < 12; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 12 * 0.35 + 1, (12 * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		if (turbo) {
			Turbocharger unit = new Turbocharger(models, models, 1);
			unit.limit = 0.6;
			sim.addTurbo(unit);
		}
		double[] curve = Dynamometer.torqueCurve(sim);
		double best = 0;
		for (int i = 0; i < curve.length; i++)
			best = Math.max(best, curve[i] * Dynamometer.SPEEDS[i] * STRESS_PER_TORQUE);
		return best;
	}

	@Test
	void keroseneSitsBetweenDieselAndGasolineInEnergy() {
		assertTrue(FuelSpec.KEROSENE.energyPerMb() > FuelSpec.DIESEL.energyPerMb());
		assertTrue(FuelSpec.KEROSENE.energyPerMb() < FuelSpec.GASOLINE.energyPerMb());
	}

	@Test
	void keroseneNeedsASparkAndCannotBePilotedFromADieselShot() {
		assertTrue(FuelSpec.KEROSENE.autoignitionTemp() > FuelSpec.DIESEL.autoignitionTemp());
		assertTrue(FuelSpec.KEROSENE.autoignitionTemp() < FuelSpec.GASOLINE.autoignitionTemp());
		assertTrue(FuelSpec.KEROSENE.autoignitionTemp() > PILOT_IGNITION_LIMIT,
			"a fuel past the pilot limit is not a pilot fuel");
	}

	@Test
	void aSparkEngineOnKeroseneRunsBetweenDieselAndGasoline() {
		double diesel = peakPower(FuelSpec.DIESEL, false, false);
		double kerosene = peakPower(FuelSpec.KEROSENE, true, false);
		double gasoline = peakPower(FuelSpec.GASOLINE, true, false);
		assertTrue(kerosene > diesel, "kerosene " + kerosene + " against diesel " + diesel);
		assertTrue(kerosene < gasoline, "kerosene " + kerosene + " against gasoline " + gasoline);
	}

	@Test
	void anEngineWithNoSparkPlugDoesNotRunOnKerosene() {
		assertTrue(peakPower(FuelSpec.KEROSENE, false, false) < 0.05 * peakPower(FuelSpec.KEROSENE, true, false));
	}

	@Test
	void keroseneHoldsOutAgainstTheChargeThatSetsGasolineOffButNotAgainstAnyCharge() {
		assertTrue(FuelSpec.GASOLINE.preignitionLoss(3.0) > 0);
		assertEquals(0, FuelSpec.KEROSENE.preignitionLoss(3.0), 0);
		assertTrue(FuelSpec.KEROSENE.preignitionLoss(3.8) > 0, "kerosene still has a limit, ethanol's is the higher one");
		assertTrue(FuelSpec.KEROSENE.preignitionLoss(3.8) < FuelSpec.GASOLINE.preignitionLoss(3.8));
		assertEquals(0, FuelSpec.ETHANOL.preignitionLoss(3.8), 0);
		assertTrue(FuelSpec.KEROSENE.preignitionTemp() > FuelSpec.GASOLINE.preignitionTemp());
		assertTrue(FuelSpec.KEROSENE.preignitionTemp() < FuelSpec.ETHANOL.preignitionTemp());
	}

	@Test
	void keroseneIsASootyOrdinaryFuelWithNoChargeCooling() {
		assertEquals(0, FuelSpec.KEROSENE.chargeCooling(), 0);
		assertEquals(1, FuelSpec.KEROSENE.sootFactor(), 0);
		assertEquals(1, FuelSpec.KEROSENE.sulfur(), 0);
		assertEquals(1, FuelSpec.KEROSENE.injectorFlow(), 0, "an injector sized for gasoline feeds it in full");
	}

	@Test
	void aWindowTintExistsAndTheOlderTintsKeepTheirNumbers() {
		assertNotNull(WindowTint.ofPath("kerosene"));
		assertTrue(WindowTint.ofPath("kerosene") != WindowTint.AMBER);
		assertEquals(WindowTint.ENGINE_OIL.ordinal() + 1, WindowTint.KEROSENE.ordinal(), "added after the older tints");
		assertEquals(9, WindowTint.ENGINE_OIL.ordinal());
	}
}
