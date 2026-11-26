package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The radiator's cooling: how air changes it, what it costs in coolant, and what it must never do to a block. */
class RadiatorTest {

	private static final double DT = 0.05;

	/** Radiators that all have the same coolant and air, and add up what they were told. */
	private static final class Loops implements Radiator.Supply {
		private final int count;
		private final double quality;
		private final double airflow;
		double shed;
		double boiled;
		int told;

		Loops(int count, double quality, double airflow) {
			this.count = count;
			this.quality = quality;
			this.airflow = airflow;
		}

		@Override
		public int count() {
			return count;
		}

		@Override
		public double quality(int i) {
			return quality;
		}

		@Override
		public double airflow(int i) {
			return airflow;
		}

		@Override
		public void ran(int i, double shed, double boiled) {
			this.shed += shed;
			this.boiled += boiled;
			told++;
		}
	}

	/** Where a block fed {@code heatPerCylinder} a second settles. */
	private static double settle(int cylinders, double heatPerCylinder, Radiator.Supply radiators) {
		Thermal thermal = new Thermal(0);
		thermal.setRadiators(radiators);
		for (int tick = 0; tick < 20 * 600; tick++)
			thermal.update(heatPerCylinder * cylinders, cylinders, DT, CoolantSupply.NONE);
		return thermal.temperature();
	}

	@Test
	void airflowIsOneInStillAirAndRisesWithAFanAndWithSpeedUpToItsLimit() {
		assertEquals(1, Radiator.airflow(0, 0));
		assertEquals(1 + Radiator.FAN_AIRFLOW / 2, Radiator.airflow(Radiator.FAN_FULL_RPM / 2, 0), 1e-9);
		assertEquals(Radiator.airflow(128, 0), Radiator.airflow(-128, 0), "a fan's speed counts either way round");
		assertEquals(1 + Radiator.RAM_AIRFLOW, Radiator.airflow(0, 500), 1e-9);
		assertEquals(Radiator.MAX_AIRFLOW, Radiator.airflow(10_000, 10_000));
		assertEquals(1, Radiator.airflow(Double.NaN, Double.NaN));
		assertEquals(1, Radiator.airflow(0, -50));
	}

	@Test
	void theAirIsNamedForWhatAddsMost() {
		assertEquals(Radiator.Air.STILL, Radiator.air(0, 0));
		assertEquals(Radiator.Air.FAN, Radiator.air(256, 2));
		assertEquals(Radiator.Air.RAM, Radiator.air(16, 20));
	}

	@Test
	void airJustWorthNamingIsNamedAndATieGoesToTheFan() {
		// 1 block a second adds exactly the 0.1 that is worth naming
		assertEquals(Radiator.Air.RAM, Radiator.air(0, 1));
		assertEquals(Radiator.Air.STILL, Radiator.air(0, 0.99));
		// A full fan and 15 blocks a second both add exactly 1.5
		assertEquals(Radiator.Air.FAN, Radiator.air(Radiator.FAN_FULL_RPM, 15));
	}

	@Test
	void whatBoilsGoesWithTheTimeItBoilsFor() {
		assertEquals(100 * Radiator.BOIL_PER_HEAT / 0.5 * DT, Radiator.boiled(Wear.CRITICAL, 100, 0.5, DT), 1e-9);
	}

	@Test
	void noRadiatorsAreStillAirWithNothingInThem() {
		assertEquals(0, Radiator.NONE.count());
		assertEquals(0, Radiator.NONE.quality(0));
		assertEquals(1, Radiator.NONE.airflow(0));
	}

	@Test
	void theEnginesSimulationHandsItsRadiatorsToTheBlock() {
		EngineSimulation engine = new EngineSimulation(java.util.List.of(), 1, 0);
		Loops loop = new Loops(1, 1, 1);
		engine.setRadiators(loop);
		engine.setThermalLive(true);
		engine.thermal()
			.setTemperature(1);
		engine.tickFree(0, 100);
		assertEquals(1, loop.told);
		assertTrue(loop.shed > 0);
	}

	@Test
	void aRadiatorKeepsABlockCoolerAndMoreAirCoolerStill() {
		double bare = settle(4, 11.5, Radiator.NONE);
		double still = settle(4, 11.5, new Loops(1, 1, 1));
		double fanned = settle(4, 11.5, new Loops(1, 1, Radiator.airflow(256, 0)));
		assertTrue(bare > 1.4, "four hard-worked cylinders overheat bare: " + bare);
		assertTrue(still < 1.0, "one radiator in still air holds them: " + still);
		assertTrue(fanned < still, fanned + " against " + still);
	}

	@Test
	void oneRadiatorWithAFanOrFastAirflowHandlesALargeEngine() {
		double bare = settle(24, 11.6, Radiator.NONE);
		double fanned = settle(24, 11.6, new Loops(1, 1, Radiator.airflow(256, 0)));
		double flying = settle(24, 11.6, new Loops(1, 1, Radiator.airflow(0, Radiator.RAM_FULL_SPEED)));
		assertTrue(bare > Thermal.CRITICAL, "24 hard-worked cylinders are critical bare: " + bare);
		assertTrue(fanned < Thermal.HOT, "one with a fan keeps them warm: " + fanned);
		assertTrue(flying < Thermal.HOT, "and so does one flying fast: " + flying);
	}

	@Test
	void waterCoolsLessThanCoolant() {
		assertTrue(settle(4, 11.5, new Loops(1, CoolingQualities.WATER, 1)) > settle(4, 11.5, new Loops(1, 1, 1)));
	}

	@Test
	void nothingIsBoiledBelowCritical() {
		Loops loop = new Loops(1, 1, 1);
		settle(4, 11.5, loop);
		assertTrue(loop.shed > 0);
		assertEquals(0, loop.boiled);
		assertEquals(0, Radiator.boiled(Wear.CRITICAL - 0.001, 100, 1, 1));
	}

	@Test
	void aCriticalBlockBoilsCoolantOffAndPoorCoolantFaster() {
		double coolant = Radiator.boiled(Wear.CRITICAL, 100, 1, 1);
		assertEquals(100 * Radiator.BOIL_PER_HEAT, coolant, 1e-9);
		assertTrue(Radiator.boiled(Wear.CRITICAL, 100, 0.5, 1) > coolant);
		assertEquals(0, Radiator.boiled(Wear.CRITICAL + 1, 100, 0, 1));
		Loops loop = new Loops(1, 1, 1);
		Thermal thermal = new Thermal(2);
		thermal.setRadiators(loop);
		thermal.update(0, 8, DT, CoolantSupply.NONE);
		assertTrue(loop.boiled > 0);
	}

	@Test
	void manyRadiatorsNeverCarryABlockBelowTheThermostat() {
		Thermal thermal = new Thermal(1.5);
		thermal.setRadiators(new Loops(64, 1, Radiator.MAX_AIRFLOW));
		double lowest = thermal.temperature();
		for (int tick = 0; tick < 200; tick++) {
			thermal.update(2, 1, DT, CoolantSupply.NONE);
			lowest = Math.min(lowest, thermal.temperature());
		}
		assertTrue(lowest > Thermal.THERMOSTAT - 0.01, "lowest " + lowest);
		assertTrue(thermal.temperature() < Thermal.THERMOSTAT + 0.05);
	}

	@Test
	void panCoolantAndRadiatorsTogetherStopAtTheThermostat() {
		Thermal thermal = new Thermal(1.5);
		thermal.setRadiators(new Loops(64, 1, Radiator.MAX_AIRFLOW));
		CoolantSupply pan = new CoolantSupply() {
			@Override
			public double quality() {
				return 1;
			}

			@Override
			public double draw(double mb) {
				return mb;
			}
		};
		// One tick of far more cooling than it takes: what the pan carries comes out of the radiators' share
		thermal.update(0, 1, DT, pan);
		assertEquals(Thermal.THERMOSTAT, thermal.temperature(), 0.01);
	}

	@Test
	void anEmptyRadiatorChangesNothingButStillHearsFromTheEngine() {
		Loops empty = new Loops(3, 0, 1);
		assertEquals(settle(4, 8, Radiator.NONE), settle(4, 8, empty));
		assertEquals(0, empty.shed);
		assertEquals(3 * 20 * 600, empty.told);
	}

	private static final class CoolingQualities {
		/** As {@code CEFluids.WATER_COOLING}, which this package cannot name. */
		static final double WATER = 0.5;
	}
}
