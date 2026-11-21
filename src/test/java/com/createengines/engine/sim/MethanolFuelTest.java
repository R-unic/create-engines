package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Methanol: the boost fuel with the least in the bucket, the hardest to light early and the one that cools the charge most. */
class MethanolFuelTest {

	private static final double STRESS_PER_TORQUE = 78.7;
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
	void methanolCarriesTheLeastEnergyOfAnyFuel() {
		for (FuelSpec other : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.KEROSENE, FuelSpec.ETHANOL})
			assertTrue(FuelSpec.METHANOL.energyPerMb() < other.energyPerMb());
	}

	@Test
	void methanolNeedsASparkAndCannotBePilotedFromADieselShot() {
		assertTrue(FuelSpec.METHANOL.autoignitionTemp() > PILOT_IGNITION_LIMIT);
		assertTrue(FuelSpec.METHANOL.autoignitionTemp() > FuelSpec.ETHANOL.autoignitionTemp(), "harder to light than ethanol");
		assertTrue(peakPower(FuelSpec.METHANOL, false, false) < 0.05 * peakPower(FuelSpec.METHANOL, true, false));
	}

	@Test
	void methanolIsTheHardestFuelToLightEarlyAndNoFuelCoolsTheChargeMore() {
		assertTrue(FuelSpec.METHANOL.preignitionTemp() > FuelSpec.ETHANOL.preignitionTemp());
		assertEquals(0, FuelSpec.METHANOL.preignitionLoss(5.5), 0, "a charge that costs ethanol power costs methanol none");
		assertTrue(FuelSpec.ETHANOL.preignitionLoss(5.5) > 0);
		assertTrue(FuelSpec.METHANOL.preignitionLoss(7.5) > 0, "it still has a limit");
		for (FuelSpec other : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.KEROSENE, FuelSpec.ETHANOL})
			assertTrue(FuelSpec.METHANOL.chargeCooling() > other.chargeCooling());
	}

	@Test
	void methanolBurnsCleanAndAnInjectorNeedsMoreFlowThanForEthanol() {
		assertEquals(0, FuelSpec.METHANOL.sootFactor(), 0);
		assertEquals(0, FuelSpec.METHANOL.sulfur(), 0);
		assertTrue(FuelSpec.METHANOL.injectorFlow() > FuelSpec.ETHANOL.injectorFlow());
	}

	@Test
	void anEngineOnMethanolIsASmallStepOverEthanolAndNotAPowerStepOverGasoline() {
		// Measured 2026-11-21 with the crude 12-cylinder rig: methanol +3.2% over ethanol unboosted and +4.2% on one turbo,
		// both within about 1% of gasoline. The bands are the point of the fuel: a clear edge over ethanol, no jump past gasoline.
		for (boolean turbo : new boolean[] {false, true}) {
			double methanol = peakPower(FuelSpec.METHANOL, true, turbo);
			double ethanol = peakPower(FuelSpec.ETHANOL, true, turbo);
			double gasoline = peakPower(FuelSpec.GASOLINE, true, turbo);
			assertTrue(methanol > ethanol * 1.02, "turbo " + turbo + ": methanol " + methanol + " against ethanol " + ethanol);
			assertTrue(methanol < ethanol * 1.08, "turbo " + turbo + ": methanol " + methanol + " against ethanol " + ethanol);
			assertTrue(methanol < gasoline * 1.02, "turbo " + turbo + ": methanol " + methanol + " against gasoline " + gasoline);
		}
	}

	@Test
	void methanolNeedsMoreFuelThanEthanolForTheSameAir() {
		// Energy per unit of air is about the same as ethanol's; the bucket is what is small, so the same engine burns more of it.
		assertTrue(FuelSpec.METHANOL.energyPerMb() / FuelSpec.METHANOL.airPerMb()
			> FuelSpec.ETHANOL.energyPerMb() / FuelSpec.ETHANOL.airPerMb() * 0.95);
		assertTrue(FuelSpec.METHANOL.airPerMb() < FuelSpec.ETHANOL.airPerMb());
	}

	@Test
	void aWindowTintExistsAndTheOlderTintsKeepTheirNumbers() {
		assertNotEquals(WindowTint.AMBER, WindowTint.ofPath("methanol"));
		assertEquals(WindowTint.KEROSENE.ordinal() + 1, WindowTint.METHANOL.ordinal(), "added after the older tints");
		assertEquals(10, WindowTint.KEROSENE.ordinal());
	}
}
