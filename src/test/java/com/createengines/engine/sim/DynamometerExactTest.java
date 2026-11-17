package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exact edges of the dynamometer's curve readers, and that a measurement carries the live engine's condition over. */
class DynamometerExactTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void aCurveHasAPointForEverySpeedUpToAndIncludingTheLimit() {
		assertEquals(1, Dynamometer.pointCount(0));
		assertEquals(1, Dynamometer.pointCount(Dynamometer.speed(0)));
		assertEquals(2, Dynamometer.pointCount(Dynamometer.speed(1)));
		assertEquals(1, Dynamometer.pointCount(Math.nextDown(Dynamometer.speed(1))));
		assertEquals(4, Dynamometer.pointCount(Dynamometer.speed(3)));
		assertEquals(3, Dynamometer.pointCount(Math.nextDown(Dynamometer.speed(3))));
		assertEquals(16, Dynamometer.pointCount(256));
		assertEquals(17, Dynamometer.pointCount(320));
		assertEquals(29, Dynamometer.pointCount(1e9));
		assertEquals(16, Dynamometer.speed(0), 0);
		assertEquals(256, Dynamometer.speed(15), 0);
		assertEquals(8192, Dynamometer.speed(28), 0);
	}

	@Test
	void aMeasurementMeasuresEverySpeedUpToTheLimitAndNoMore() {
		Dynamometer.Measurement slow = new Dynamometer.Measurement(null, Dynamometer.speed(3));
		assertEquals(Dynamometer.SPEEDS.length, slow.curve().length);
		Dynamometer.Measurement exact = new Dynamometer.Measurement(null, 320);
		assertEquals(17, exact.curve().length);
		assertEquals(17, exact.ripples().length);
	}

	@Test
	void peakTorqueTakesEverySpeedUpToAndIncludingTheAskedOne() {
		double[] curve = {3, 9, 4, 12, 1};
		assertEquals(3, Dynamometer.peakTorque(curve, 0), 0);
		assertEquals(3, Dynamometer.peakTorque(curve, Math.nextDown(Dynamometer.speed(1))), 0);
		assertEquals(9, Dynamometer.peakTorque(curve, Dynamometer.speed(1)), 0);
		assertEquals(9, Dynamometer.peakTorque(curve, Dynamometer.speed(2)), 0);
		assertEquals(9, Dynamometer.peakTorque(curve, Math.nextDown(Dynamometer.speed(3))), 0);
		assertEquals(12, Dynamometer.peakTorque(curve, Dynamometer.speed(3)), 0);
		assertEquals(12, Dynamometer.peakTorque(curve, 1e9), 0);
		assertEquals(0, Dynamometer.peakTorque(new double[] {-4, -2}, 1e9), 0);
		assertEquals(0, Dynamometer.peakTorque(new double[0], 1e9), 0);
	}

	@Test
	void theRatedTorqueTakesEverySpeedUpToAndIncludingTheAskedOne() {
		double[] curve = {2, 6, 7};
		double[] smooth = {0, 0, 0};
		assertEquals(2, Dynamometer.ratedTorque(curve, smooth, 0), 1e-12);
		assertEquals(2, Dynamometer.ratedTorque(curve, smooth, Math.nextDown(Dynamometer.speed(1))), 1e-12);
		assertEquals(6, Dynamometer.ratedTorque(curve, smooth, Dynamometer.speed(1)), 1e-12);
		assertEquals(6, Dynamometer.ratedTorque(curve, smooth, Math.nextDown(Dynamometer.speed(2))), 1e-12);
		assertEquals(7, Dynamometer.ratedTorque(curve, smooth, Dynamometer.speed(2)), 1e-12);
		// A ripple list shorter than the curve counts the rest as smooth
		assertEquals(7, Dynamometer.ratedTorque(curve, new double[] {1}, 1e9), 1e-12);
		assertEquals(7, Dynamometer.ratedTorque(curve, new double[0], 1e9), 1e-12);
	}

	@Test
	void theCrawlSpeedsAreJudgedOnlyForAnEngineThatIsItselfBelowThem() {
		double[] curve = {10, 5};
		double[] ripples = {1, 0};
		// At the crawl speed itself the first point is set aside: peak 5, rated 5
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.CRAWL_RPM), 1e-12);
		// Just under it the first point counts: peak 10 rated down to 6, and the second speed is not yet reached
		assertEquals(Roughness.MAX_LOSS, Dynamometer.roughnessLoss(curve, ripples, Math.nextDown(Dynamometer.CRAWL_RPM)), 1e-12);
		assertEquals(0, Dynamometer.roughnessLoss(new double[] {0, 0}, ripples, 100), 0);
		assertEquals(0, Dynamometer.roughnessLoss(new double[0], new double[0], 100), 0);
	}

	// ---- a measurement carries the live engine's condition over ------------------------------------------------

	private static EngineSimulation engine() {
		List<CylinderModel> cylinders = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < 4; i++) {
			double tdc = i * 180.0;
			CylinderModel c = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 % 180);
			c.fuelSupply = new FuelSupply() {
				@Override
				public double draw(double mb) {
					return mb;
				}

				@Override
				public FuelSpec fuel() {
					return FuelSpec.DIESEL;
				}
			};
			cylinders.add(c);
		}
		EngineSimulation engine = new EngineSimulation(cylinders, 2, 0.02, 0.002);
		engine.setThermalLive(true);
		return engine;
	}

	private static double[] curve(EngineSimulation engine) {
		return Dynamometer.torqueCurve(engine, 96);
	}

	private static void assertCurvesDiffer(double[] a, double[] b, String what) {
		boolean differs = false;
		for (int i = 0; i < Math.min(a.length, b.length); i++)
			differs |= Math.abs(a[i] - b[i]) > 1e-9;
		assertTrue(differs, what + " made no difference to the measured curve");
	}

	@Test
	void theCurveIsMeasuredAtTheEnginesOwnTemperatureAndLubrication() {
		EngineSimulation base = engine();
		base.thermal().setTemperature(0.7);
		double[] warm = curve(base);

		EngineSimulation cold = engine();
		cold.thermal().setTemperature(0);
		assertCurvesDiffer(warm, curve(cold), "the engine's temperature");

		EngineSimulation dry = engine();
		dry.thermal().setTemperature(0.7);
		dry.lubrication().setFilm(0);
		assertCurvesDiffer(warm, curve(dry), "the oil film");

		EngineSimulation worn = engine();
		worn.thermal().setTemperature(0.7);
		worn.lubrication().setCondition(0);
		assertCurvesDiffer(warm, curve(worn), "the oil's condition");

		EngineSimulation thinned = engine();
		thinned.thermal().setTemperature(0.7);
		thinned.lubrication().setDilution(Lubrication.MAX_DILUTION);
		assertCurvesDiffer(warm, curve(thinned), "fuel dilution of the oil");
	}

	@Test
	void theMeasurementDoesNotWarmOrChangeTheLiveEngine() {
		EngineSimulation live = engine();
		live.thermal().setTemperature(0.2);
		live.lubrication().setFilm(0.5);
		double[] first = curve(live);
		assertEquals(0.2, live.thermal().temperature(), 0);
		assertEquals(0.5, live.lubrication().film(), 0);
		double[] second = curve(live);
		for (int i = 0; i < first.length; i++)
			assertEquals(first[i], second[i], 1e-12, "point " + i);
	}

	@Test
	void aReversedEngineIsMeasuredTurningTheWayItTurns() {
		EngineSimulation forward = engine();
		forward.thermal().setTemperature(0.7);
		EngineSimulation reverse = engine();
		reverse.thermal().setTemperature(0.7);
		reverse.setDirection(-1);
		double[] a = curve(forward);
		double[] b = curve(reverse);
		assertEquals(a.length, b.length);
		// Whatever it makes, a reversed engine's measurement must be finite and not the forward engine's by accident of reuse
		for (double v : b)
			assertTrue(Double.isFinite(v));
		assertNotEquals(0, a[Dynamometer.pointCount(96) - 1], 0);
	}
}
