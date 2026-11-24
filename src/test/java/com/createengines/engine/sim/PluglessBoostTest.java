package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A spark fuel needs a plug (or a pilot shot) however much boost has heated the charge. Two superchargers on a standard-part
 * V8 made a plugless engine light gasoline and kerosene by compression and run on them: the dyno read 60 000 SU on kerosene
 * and 480 000 SU on gasoline for the same engine with no plugs fitted.
 */
class PluglessBoostTest {

	private static final double STRESS_PER_TORQUE = 78.7;

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** A pilot tank that never runs dry. */
	private static final FuelSupply DIESEL_PILOT = new FuelSupply() {
		@Override
		public double draw(double amount) {
			return amount;
		}

		@Override
		public FuelSpec fuel() {
			return FuelSpec.DIESEL;
		}
	};

	/** Peak dyno power, SU, of a standard-part V8 on two superchargers at their highest limit. */
	private static double peakPower(FuelSpec fuel, IgnitionSource ignition, FuelSupply pilot, int blowers) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / 8;
		for (int i = 0; i < 8; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.pilotSupply = pilot;
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = ignition;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 8 * 0.35 + 1, (8 * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		for (int i = 0; i < blowers; i++) {
			Supercharger unit = new Supercharger(models);
			unit.rpm = Supercharger.DESIGN_RPM;
			unit.limit = Supercharger.MAX_LIMIT;
			sim.addSupercharger(unit);
		}
		double[] curve = Dynamometer.torqueCurve(sim);
		double best = 0;
		for (int i = 0; i < curve.length; i++)
			best = Math.max(best, curve[i] * Dynamometer.SPEEDS[i] * STRESS_PER_TORQUE);
		return best;
	}

	@Test
	void aBoostedEngineWithNoPlugMakesNoPowerOnASparkFuel() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.KEROSENE, FuelSpec.ETHANOL, FuelSpec.METHANOL})
			for (int blowers = 1; blowers <= 2; blowers++) {
				double lit = peakPower(fuel, IgnitionSource.UNLIMITED, FuelSupply.NONE, blowers);
				double plugless = peakPower(fuel, IgnitionSource.NONE, FuelSupply.NONE, blowers);
				assertTrue(plugless < 0.05 * lit, fuel + " on " + blowers + " blowers: plugless " + plugless + " against lit " + lit);
			}
	}

	@Test
	void theSameBoostedEngineWithPlugsStillRunsOnASparkFuel() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.KEROSENE})
			assertTrue(peakPower(fuel, IgnitionSource.UNLIMITED, FuelSupply.NONE, 2) > 0, fuel.toString());
	}

	@Test
	void aBoostedCompressionFuelStillLightsItselfWithNoPlug() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.DIESEL, FuelSpec.PLANT_OIL})
			assertTrue(peakPower(fuel, IgnitionSource.NONE, FuelSupply.NONE, 2) > 0, fuel.toString());
	}

	@Test
	void aPilotShotStillLightsABoostedSparkFuelWithNoPlug() {
		assertTrue(peakPower(FuelSpec.GASOLINE, IgnitionSource.NONE, DIESEL_PILOT, 2) > 0);
	}

	@Test
	void theServerCanStillSwitchPilotInjectionOffForABoostedPluglessEngine() {
		Tuning.pilotInjection = false;
		assertEquals(0, peakPower(FuelSpec.GASOLINE, IgnitionSource.NONE, DIESEL_PILOT, 2), 0);
	}

	@Test
	void theSparkLineSitsWhereTheTooltipsAndThePilotLimitPutIt() {
		assertEquals(2.0, FuelSpec.COMPRESSION_LIMIT, 0);
		assertFalse(FuelSpec.DIESEL.needsSpark());
		assertFalse(FuelSpec.PLANT_OIL.needsSpark());
		assertFalse(new FuelSpec(22, 1.85, 1.6).needsSpark(), "used oil is a compression fuel");
		assertTrue(FuelSpec.GASOLINE.needsSpark());
		assertTrue(FuelSpec.KEROSENE.needsSpark());
		assertTrue(FuelSpec.ETHANOL.needsSpark());
		assertTrue(FuelSpec.METHANOL.needsSpark());
	}

	@Test
	void aFuelAtExactlyTheCompressionLimitIsACompressionFuelAndOneJustAboveItIsNot() {
		assertFalse(new FuelSpec(22, FuelSpec.COMPRESSION_LIMIT, 1.6).needsSpark(), "at the limit it lights by compression");
		assertTrue(new FuelSpec(22, Math.nextUp(FuelSpec.COMPRESSION_LIMIT), 1.6).needsSpark(), "a hair above it needs a spark");
		assertFalse(new FuelSpec(22, Math.nextDown(FuelSpec.COMPRESSION_LIMIT), 1.6).needsSpark());
	}
}
