package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** An uneven engine is rated for less, and rotating mass is how a builder gets that back. */
class RoughnessTest {

	private static final double JOURNAL = 0.15;
	private static final double THROW = 0.2;
	private static final FuelSupply DIESEL = new FuelSupply() {
		@Override
		public double draw(double mb) {
			return mb;
		}

		@Override
		public FuelSpec fuel() {
			return FuelSpec.DIESEL;
		}
	};

	/** {@code cylinders} evenly spaced cylinders on one cam, with {@code flywheel} inertia on the shaft. */
	private static EngineSimulation build(int cylinders, double flywheel) {
		List<CylinderModel> list = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < cylinders; i++) {
			double fire = 720.0 * i / cylinders;
			double tdc = fire % 360;
			int revolution = fire >= 360 ? 1 : 0;
			CylinderModel model =
				new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 + 180 * revolution);
			model.fuelSupply = DIESEL;
			list.add(model);
		}
		for (CylinderModel model : list)
			model.resetGas(model.pistonAngle(0, 1));
		return new EngineSimulation(list, 3 * JOURNAL + cylinders * THROW + flywheel, 0.02 * (1 + cylinders));
	}

	private static double rippleAt(int cylinders, double flywheel, double rpm) {
		EngineSimulation dyno = build(cylinders, flywheel);
		dyno.trackSwing();
		dyno.restore(0, 0, 1);
		double omega = EngineSimulation.rpmToOmega(rpm);
		for (int t = 0; t < 400; t++)
			dyno.tickDriven(omega, 1);
		return dyno.predictedRipple();
	}

	private static double rating(int cylinders, double flywheel) {
		Dynamometer.Measurement measurement = new Dynamometer.Measurement(build(cylinders, flywheel), 256);
		while (!measurement.advance()) {
		}
		return Dynamometer.ratedTorque(measurement.curve(), measurement.ripples(), 256);
	}

	@Test
	void lossStartsAboveAFreeRippleAndIsCapped() {
		assertEquals(0, Roughness.loss(0));
		assertEquals(0, Roughness.loss(Roughness.FREE_RIPPLE));
		assertTrue(Roughness.loss(0.1) > 0);
		assertTrue(Roughness.loss(0.2) > Roughness.loss(0.1));
		assertEquals(Roughness.MAX_LOSS, Roughness.loss(50), 1e-12);
		assertEquals(1 - Roughness.MAX_LOSS, Roughness.smoothness(50), 1e-12);
	}

	@Test
	void aFlywheelTakesTheSurgeOutOfTheSpeed() {
		double bare = rippleAt(1, 0, 64);
		double heavy = rippleAt(1, 2, 64);
		assertTrue(bare > 0.15, "a lone cylinder surges: " + bare);
		assertTrue(heavy < bare / 2, heavy + " should be well under " + bare);
	}

	@Test
	void moreCylindersFiringEvenlyAreSmoother() {
		assertTrue(rippleAt(4, 0, 64) < rippleAt(1, 0, 64) / 2);
		assertTrue(rippleAt(8, 0, 64) < rippleAt(4, 0, 64));
	}

	@Test
	void aFasterEngineSurgesLess() {
		assertTrue(rippleAt(2, 0, 128) < rippleAt(2, 0, 64));
	}

	@Test
	void aFlywheelRaisesTheRatingOfASmallEngine() {
		double bare = rating(1, 0);
		double flywheel = rating(1, 1);
		assertTrue(flywheel > bare * 1.1, "flywheel " + flywheel + " vs bare " + bare);
	}

	@Test
	void aSmoothEngineIsRatedForItsFullTorque() {
		EngineSimulation engine = build(8, 2);
		Dynamometer.Measurement measurement = new Dynamometer.Measurement(engine, 256);
		while (!measurement.advance()) {
		}
		double peak = Dynamometer.peakTorque(measurement.curve(), 256);
		double rated = Dynamometer.ratedTorque(measurement.curve(), measurement.ripples(), 256);
		assertTrue(rated >= peak * 0.85, "rated " + rated + " vs peak " + peak);
		assertTrue(Dynamometer.roughnessLoss(measurement.curve(), measurement.ripples(), 256) < 0.15);
	}

	@Test
	void aRoughEngineReportsWhatRoughnessCostsIt() {
		EngineSimulation engine = build(1, 0);
		Dynamometer.Measurement measurement = new Dynamometer.Measurement(engine, 256);
		while (!measurement.advance()) {
		}
		assertTrue(Dynamometer.roughnessLoss(measurement.curve(), measurement.ripples(), 256) > 0.1);
	}
}
