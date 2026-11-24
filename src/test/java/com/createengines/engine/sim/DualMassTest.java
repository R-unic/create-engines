package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The dual-mass flywheel: a primary mass that is plain crank inertia and a secondary mass that smooths only the ripple the
 * network sees, by a share that grows with speed above the spring pack's resonance. Shipped numbers are the config
 * defaults: primary 0.45, secondary 1.4, resonance 48 RPM, against a Cast Flywheel of 1.0.
 */
class DualMassTest {

	private static final double JOURNAL = 0.15;
	private static final double THROW = 0.2;
	private static final double PRIMARY = 0.45;
	private static final double SECONDARY = 1.4;
	private static final double RESONANCE = 48;
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

	private static EngineSimulation build(int cylinders, double flywheel, double secondary) {
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
		EngineSimulation engine =
			new EngineSimulation(list, 3 * JOURNAL + cylinders * THROW + flywheel, 0.02 * (1 + cylinders));
		engine.setIsolatedMass(secondary, RESONANCE);
		return engine;
	}

	private static double rippleAt(EngineSimulation engine, double rpm) {
		engine.trackSwing();
		engine.restore(0, 0, 1);
		double omega = EngineSimulation.rpmToOmega(rpm);
		for (int t = 0; t < 400; t++)
			engine.tickDriven(omega, 1);
		return engine.predictedRipple();
	}

	private static double rating(EngineSimulation engine) {
		Dynamometer.Measurement measurement = new Dynamometer.Measurement(engine, 256);
		while (!measurement.advance()) {
		}
		return Dynamometer.ratedTorque(measurement.curve(), measurement.ripples(), 256);
	}

	// The isolation curve

	@Test
	void theSecondaryMassDoesNothingAtOrBelowTheResonance() {
		for (double rpm : new double[] {0, 1, 16, 32, RESONANCE})
			assertEquals(0, DualMass.isolation(rpm, RESONANCE), 0, "at " + rpm);
	}

	@Test
	void theSecondaryMassCountsInFullFromThreeTimesTheResonance() {
		assertEquals(1, DualMass.isolation(RESONANCE * DualMass.FULL_AT, RESONANCE), 1e-12);
		assertEquals(1, DualMass.isolation(RESONANCE * 10, RESONANCE), 0);
		assertEquals(1, DualMass.isolation(1e9, RESONANCE), 0);
	}

	@Test
	void theIsolationFollowsTheSmoothstepAtKnownSpeeds() {
		// t is how far between the resonance and three times it the speed is; the share is t * t * (3 - 2t)
		assertEquals(0.15625, DualMass.isolation(RESONANCE * 1.5, RESONANCE), 1e-12, "a quarter of the way");
		assertEquals(0.5, DualMass.isolation(RESONANCE * 2, RESONANCE), 1e-12, "halfway");
		assertEquals(0.84375, DualMass.isolation(RESONANCE * 2.5, RESONANCE), 1e-12, "three quarters of the way");
		assertEquals(0.5, DualMass.isolation(100, 50), 1e-12, "it depends on the ratio, not the speed");
		assertEquals(0, DualMass.isolation(-0.0, RESONANCE), 0);
	}

	@Test
	void theEffectiveInertiaAddsTheIsolatedShareOfTheSecondaryMass() {
		assertEquals(PRIMARY + 0.5 * SECONDARY, DualMass.effectiveInertia(PRIMARY, SECONDARY, RESONANCE * 2, RESONANCE), 1e-12);
		assertEquals(PRIMARY, DualMass.effectiveInertia(PRIMARY, SECONDARY, RESONANCE, RESONANCE), 0);
	}

	@Test
	void theIsolationRisesWithSpeedAndStaysInZeroToOne() {
		double before = 0;
		for (double rpm = 0; rpm <= 400; rpm += 0.5) {
			double now = DualMass.isolation(rpm, RESONANCE);
			assertTrue(now >= 0 && now <= 1, "out of range at " + rpm + ": " + now);
			assertTrue(now >= before, "fell at " + rpm + ": " + before + " to " + now);
			before = now;
		}
		assertTrue(DualMass.isolation(RESONANCE * 2, RESONANCE) > DualMass.isolation(RESONANCE * 1.4, RESONANCE));
		assertTrue(DualMass.isolation(RESONANCE * 1.4, RESONANCE) > 0, "help should begin above the resonance");
		assertTrue(DualMass.isolation(RESONANCE * 1.4, RESONANCE) < 0.25, "just past the resonance it is still weak");
	}

	@Test
	void aCorruptSpeedOrResonanceNeverHelps() {
		double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0};
		for (double value : bad) {
			assertEquals(0, DualMass.isolation(value, RESONANCE), 0, "rpm " + value);
			assertEquals(0, DualMass.isolation(100, value), 0, "resonance " + value);
			double seen = DualMass.effectiveInertia(1, 1, value, RESONANCE);
			assertTrue(Double.isFinite(seen) && seen >= 1, "effective inertia " + seen);
		}
	}

	@Test
	void theEffectiveInertiaIsNeverBelowTheCrankAndNeverAboveTheWholeMass() {
		for (double rpm = 0; rpm <= 500; rpm += 7) {
			double seen = DualMass.effectiveInertia(PRIMARY, SECONDARY, rpm, RESONANCE);
			assertTrue(seen >= PRIMARY && seen <= PRIMARY + SECONDARY + 1e-12, rpm + " RPM: " + seen);
		}
		assertEquals(PRIMARY, DualMass.effectiveInertia(PRIMARY, -5, 300, RESONANCE), 0, "a negative mass is no mass");
	}

	// In the simulation

	@Test
	void noSecondaryMassIsAPlainFlywheel() {
		for (double rpm : new double[] {32, 96, 200}) {
			double plain = rippleAt(build(2, PRIMARY, 0), rpm);
			EngineSimulation other = build(2, PRIMARY, 0);
			other.setIsolatedMass(0, 1);
			assertEquals(Double.doubleToLongBits(plain), Double.doubleToLongBits(rippleAt(other, rpm)), "at " + rpm);
		}
	}

	@Test
	void itIsNeverRoughestThanAPlainFlywheelOfItsPrimaryMass() {
		for (int cylinders : new int[] {1, 2, 4}) {
			for (double rpm = 16; rpm <= 256; rpm += 16) {
				double plain = rippleAt(build(cylinders, PRIMARY, 0), rpm);
				double dual = rippleAt(build(cylinders, PRIMARY, SECONDARY), rpm);
				assertTrue(dual <= plain + 1e-12, cylinders + " cylinders at " + rpm + ": " + dual + " vs " + plain);
			}
		}
	}

	@Test
	void belowTheResonanceItIsJustItsPrimaryMass() {
		double plain = rippleAt(build(1, PRIMARY, 0), 32);
		double dual = rippleAt(build(1, PRIMARY, SECONDARY), 32);
		assertEquals(plain, dual, plain * 1e-12);
	}

	@Test
	void wellAboveTheResonanceItSmoothsLikeTheWholeMass() {
		double whole = rippleAt(build(1, PRIMARY + SECONDARY, 0), 192);
		double dual = rippleAt(build(1, PRIMARY, SECONDARY), 192);
		assertEquals(whole, dual, whole * 1e-9);
	}

	@Test
	void theSecondaryMassDoesNotSlowTheCrank() {
		EngineSimulation dual = build(1, PRIMARY, SECONDARY);
		EngineSimulation primaryOnly = build(1, PRIMARY, 0);
		EngineSimulation whole = build(1, PRIMARY + SECONDARY, 0);
		assertEquals(primaryOnly.inertia(), dual.inertia(), 0);
		assertTrue(whole.inertia() > dual.inertia());
		// Cranked from rest with nothing to burn, it comes up to speed exactly as the primary-only engine does, and the
		// heavier whole-mass engine is slower to
		for (EngineSimulation engine : List.of(dual, primaryOnly, whole))
			for (CylinderModel cylinder : engine.cylinders())
				cylinder.ignition = IgnitionSource.UNLIMITED;
		for (int i = 0; i < 200; i++) {
			dual.tickFree(0, EngineSimulation.rpmToOmega(256));
			primaryOnly.tickFree(0, EngineSimulation.rpmToOmega(256));
			whole.tickFree(0, EngineSimulation.rpmToOmega(256));
		}
		assertEquals(Double.doubleToLongBits(primaryOnly.omega()), Double.doubleToLongBits(dual.omega()));
	}

	@Test
	void theDynamometerRatesItFromThePrimaryMassUpToTheWholeMass() {
		double primaryOnly = rating(build(1, PRIMARY, 0));
		double dual = rating(build(1, PRIMARY, SECONDARY));
		double whole = rating(build(1, PRIMARY + SECONDARY, 0));
		assertTrue(dual >= primaryOnly, dual + " vs " + primaryOnly);
		assertTrue(dual <= whole + 1e-9, dual + " vs " + whole);
		assertTrue(dual > primaryOnly, "on a one-cylinder engine the secondary mass should pay somewhere");
	}

	/** The niche: slow and heavy is the Cast Flywheel's, fast and quick-revving is the dual-mass part's. */
	@Test
	void aCastFlywheelWinsSlowAndTheDualMassPartWinsFastAtTheSameCost() {
		double cast = 1.0;
		double lowDual = rippleAt(build(1, PRIMARY, SECONDARY), 32);
		double lowCast = rippleAt(build(1, cast, 0), 32);
		assertTrue(lowCast < lowDual, "slow: cast " + lowCast + " dual " + lowDual);
		double highDual = rippleAt(build(1, PRIMARY, SECONDARY), 192);
		double highCast = rippleAt(build(1, cast, 0), 192);
		assertTrue(highDual < highCast, "fast: dual " + highDual + " cast " + highCast);
		assertTrue(build(1, PRIMARY, 0).inertia() < build(1, cast, 0).inertia(), "and it revs on less crank mass");
	}
}
