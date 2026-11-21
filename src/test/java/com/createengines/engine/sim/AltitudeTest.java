package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The air thins with height, and a compressor makes up for it. 2.0 only: an existing build at sea level is unchanged. */
class AltitudeTest {

	@Test
	void theAirIsAsThickAsEverUpToTheStartHeightAndNeverNegative() {
		assertEquals(1, Altitude.density(-64));
		assertEquals(1, Altitude.density(0));
		assertEquals(1, Altitude.density(Altitude.START));
		assertEquals(1, Altitude.density(Double.NaN), "a bad height is no change, not a stalled engine");
		assertTrue(Altitude.density(Altitude.START + 1) < 1);
	}

	@Test
	void itThinsSteadilyAndStopsAtTheFloor() {
		double previous = 1;
		for (double y = 130; y < 3000; y += 50) {
			double density = Altitude.density(y);
			assertTrue(density <= previous, "no thicker higher up at " + y);
			assertTrue(density >= Altitude.FLOOR);
			previous = density;
		}
		assertEquals(Altitude.FLOOR, Altitude.density(1_000_000));
	}

	@Test
	void theVanillaBuildLimitIsNoticeableButNotCrippling() {
		double top = Altitude.density(320);
		assertTrue(top > 0.8 && top < 0.9, "about 85 percent of the air at 320: " + top);
	}

	private static EngineSimulation engine(double density, double blowerLimit) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180d;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 2.4, 0.09);
		for (CylinderModel model : models)
			model.resetGas(0);
		sim.setAmbientDensity(density);
		if (blowerLimit > 0) {
			Turbocharger unit = new Turbocharger(models, models, 1);
			unit.limit = blowerLimit;
			sim.addTurbo(unit);
		}
		return sim;
	}

	private static double peak(EngineSimulation sim) {
		double best = 0;
		for (double torque : Dynamometer.torqueCurve(sim))
			best = Math.max(best, torque);
		return best;
	}

	@Test
	void anEngineMakesLessPowerInThinAir() {
		assertTrue(peak(engine(0.7, 0)) < peak(engine(1, 0)) * 0.95);
	}

	@Test
	void seaLevelIsExactlyWhatEveryEngineAlreadyDid() {
		assertEquals(peak(engine(1, 0)), peak(engine(2, 0)), "a density above 1 is clamped to 1");
		assertEquals(peak(engine(1, 0.3)), peak(engine(1, 0.3)));
	}

	@Test
	void aTurbochargerOffsetsThinAir() {
		double thin = peak(engine(0.7, 0));
		double boosted = peak(engine(0.7, 0.6));
		assertTrue(boosted > thin * 1.1, "boost is worth more up there than the thin air alone: " + boosted + " against " + thin);
		assertTrue(boosted < peak(engine(1, 0.6)), "but altitude still costs a boosted engine something");
	}

	@Test
	void aHostileDensityIsClampedSoAnEngineStillRuns() {
		assertTrue(Double.isFinite(peak(engine(-5, 0))));
		assertEquals(peak(engine(Altitude.FLOOR, 0)), peak(engine(-5, 0)), "clamped to the thinnest air there is");
		assertEquals(peak(engine(1, 0)), peak(engine(Double.POSITIVE_INFINITY, 0)));
	}

	@Test
	void aPressureReadFromAnotherModIsHeldBetweenTheFloorAndOne() {
		assertEquals(0.5, Altitude.clamp(0.5, Altitude.FLOOR));
		assertEquals(Altitude.FLOOR, Altitude.clamp(0, Altitude.FLOOR), "zero pressure still leaves an engine running");
		assertEquals(Altitude.FLOOR, Altitude.clamp(-3, Altitude.FLOOR));
		assertEquals(1, Altitude.clamp(7, Altitude.FLOOR), "never thicker than sea level");
		assertEquals(1, Altitude.clamp(Double.NaN, Altitude.FLOOR), "no answer is no change");
	}

	@Test
	void theCurveCanBeTunedAndTheDefaultsAreTheShippedOnes() {
		assertEquals(Altitude.density(500), Altitude.density(500, Altitude.START, Altitude.SCALE, Altitude.FLOOR));
		assertTrue(Altitude.density(500, 128, 300, 0.2) < Altitude.density(500), "a smaller scale is steeper");
		assertEquals(0.25, Altitude.density(1_000_000, 128, 300, 0.25), "the floor given is the floor used");
		assertEquals(1, Altitude.density(100, 200, 300, 0.2), "nothing thins below the start given");
		assertTrue(Double.isFinite(Altitude.density(500, 128, 0, 0.2)), "a zero scale cannot divide by zero");
	}

	@Test
	void theConfiguredFloorIsTheOneAnEngineIsGiven() {
		try {
			Tuning.altitudeFloor = 0.5;
			EngineSimulation sim = engine(0.5, 0);
			sim.setAmbientDensity(0);
			assertEquals(0.5, sim.ambientDensity());
		} finally {
			Tuning.reset();
		}
		EngineSimulation sim = engine(1, 0);
		sim.setAmbientDensity(0);
		assertEquals(Altitude.FLOOR, sim.ambientDensity(), "back to the shipped floor");
	}
}
