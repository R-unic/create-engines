package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The lookup tables and shortcuts the simulation uses to stay cheap at speed must agree with the maths they replace. */
class SimTablesTest {

	private static final double TOLERANCE = 1e-5;

	private static final class Tank implements FuelSupply {
		@Override
		public double draw(double mb) {
			return mb;
		}

		@Override
		public FuelSpec fuel() {
			return FuelSpec.PLANT_OIL;
		}
	}

	@Test
	void volumeTableMatchesTheSliderCrank() {
		for (CylinderSpec spec : new CylinderSpec[] {CylinderSpec.CRUDE, CylinderSpec.ROTARY,
			CylinderSpec.build(true, true, true, true)}) {
			CylinderSpec.Geometry geometry = spec.geometry();
			for (double angle = -400; angle < 1500; angle += 0.37) {
				double exact = spec.volumeAt(angle);
				assertEquals(exact, geometry.volume(angle), TOLERANCE * exact, "volume at " + angle);
				double heat = Math.pow(exact, CylinderModel.GAMMA - 1);
				assertEquals(heat, geometry.heat(angle), TOLERANCE * heat, "heat at " + angle);
			}
		}
	}

	@Test
	void liftTableMatchesTheHalfSine() {
		for (CamProfile profile : new CamProfile[] {CamProfile.BASIC, CamProfile.ROTARY}) {
			for (double angle = -700; angle < 1500; angle += 0.41) {
				assertEquals(profile.intakeLift(angle), profile.intakeCurve().at(angle), TOLERANCE, "intake at " + angle);
				assertEquals(profile.exhaustLift(angle), profile.exhaustCurve().at(angle), TOLERANCE,
					"exhaust at " + angle);
			}
		}
	}

	@Test
	void tablesAreSharedBetweenCylindersOfOneShape() {
		assertTrue(CylinderSpec.CRUDE.geometry() == CylinderSpec.CRUDE.geometry());
		assertTrue(CamProfile.BASIC.intakeCurve() == CamProfile.BASIC.intakeCurve());
	}

	@Test
	void wrapKeepsAnglesInRangeWhereverTheyStart() {
		for (double angle = -3000; angle < 3000; angle += 7.3) {
			for (double period : new double[] {360, 720, 1080}) {
				double wrapped = SimMath.wrap(angle, period);
				double expected = ((angle % period) + period) % period;
				assertEquals(expected, wrapped, 1e-9, angle + " mod " + period);
				assertTrue(wrapped >= 0 && wrapped <= period, wrapped + " is outside the period " + period);
			}
		}
		assertEquals(0, SimMath.wrap(720), 0);
		assertEquals(0, SimMath.wrap(1440), 0);
		assertEquals(10, SimMath.wrap(730), 0);
	}

	@Test
	void approachIsOneMinusDecayAtAnyRate() {
		for (double x = 0; x < 20; x += 0.013)
			assertEquals(1 - Math.exp(-x), SimMath.approach(x), 1e-8, "rate " + x);
	}

	@Test
	void measurementOneSpeedAtATimeIsTheSameCurveAsAllAtOnce() {
		EngineSimulation engine = engine();
		double[] whole = Dynamometer.torqueCurve(engine, 640);
		Dynamometer.Measurement piece = new Dynamometer.Measurement(engine, 640);
		int steps = 0;
		while (!piece.advance())
			steps++;
		assertTrue(steps > 0, "a curve is more than one speed");
		assertEquals(whole.length, piece.curve().length);
		for (int i = 0; i < whole.length; i++)
			assertEquals(whole[i], piece.curve()[i], 1e-12, "point " + i);
	}

	@Test
	void aRaisedSpeedLimitIsMeasuredAndAnUnraisedOneIsNot() {
		EngineSimulation engine = engine();
		double[] usual = Dynamometer.torqueCurve(engine);
		double[] raised = Dynamometer.torqueCurve(engine, 1024);
		assertEquals(Dynamometer.SPEEDS.length, usual.length);
		assertTrue(raised.length > usual.length, "a higher limit takes more points");
		for (int i = 0; i < usual.length; i++)
			assertEquals(usual[i], raised[i], 1e-12, "the first points are the same measurement");
		for (int i = usual.length; i < raised.length; i++)
			assertTrue(Double.isFinite(raised[i]), "point " + i);
		assertTrue(Dynamometer.peakTorque(raised, 1024) >= Dynamometer.peakTorque(raised, 256));
	}

	@Test
	void anEngineAllowedToRevHighStaysStableAndStopsAtTheLimit() {
		Camshaft cam = new Camshaft();
		List<CylinderModel> cylinders = new ArrayList<>();
		for (double tdc : new double[] {0, 180, 360, 540}) {
			CylinderModel model = new CylinderModel(CylinderSpec.build(true, true, true, true), CamProfile.BASIC, tdc, cam,
				tdc / 2);
			model.fuelSupply = new Tank();
			model.ignition = IgnitionSource.UNLIMITED;
			cylinders.add(model);
		}
		EngineSimulation engine = new EngineSimulation(cylinders, 1, 0.02);
		double limit = EngineSimulation.rpmToOmega(2048);
		for (int i = 0; i < 20 * 60; i++) {
			engine.tickCranked(0, limit, EngineSimulation.rpmToOmega(300), 1);
			assertFalse(Double.isNaN(engine.omega()), "omega went NaN at tick " + i);
			assertTrue(engine.omega() <= limit + 1e-9, "went past the limit at tick " + i);
		}
		assertTrue(engine.rpm() > 300, "should have pulled away from the starter, got " + engine.rpm());
	}

	private static EngineSimulation engine() {
		Camshaft cam = new Camshaft();
		List<CylinderModel> cylinders = new ArrayList<>();
		for (double tdc : new double[] {0, 180, 360, 540}) {
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2);
			model.fuelSupply = new Tank();
			cylinders.add(model);
		}
		return new EngineSimulation(cylinders, 1, 0.02);
	}
}
