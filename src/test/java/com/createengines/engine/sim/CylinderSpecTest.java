package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CylinderSpecTest {
	private static final double G = CylinderSpec.SPEED_GAIN;


	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void theShorterConstructorsFillInTheUsualDisplacementAndInjector() {
		CylinderSpec spec = new CylinderSpec(12, 4 / 14d, 20 * G, 20 * G, 0.5, 0.02 / G, 0.06 / G, 0.08, 0.01 / G, 0.15);
		assertEquals(1, spec.displacement(), 0);
		assertEquals(1, spec.injectorFlow(), 0);
		assertEquals(CylinderSpec.CRUDE, spec);
		CylinderSpec big = new CylinderSpec(12, 4 / 14d, 20 * G, 20 * G, 0.5, 0.02 / G, 0.06 / G, 0.08, 0.01 / G, 0.15, 2);
		assertEquals(2, big.displacement(), 0);
		assertEquals(1, big.injectorFlow(), 0);
	}

	@Test
	void clearanceIsWhatTheCompressionRatioLeavesOver() {
		assertEquals(1 / 11d, CylinderSpec.CRUDE.clearanceVolume(), 1e-12);
		assertEquals(1, new CylinderSpec(2, 0.2, 1, 1, 1, 1, 1, 1, 1, 1).clearanceVolume(), 1e-12);
	}

	@Test
	void theVolumeSwingsFromClearanceAtTopToTheFullBoreAtBottom() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		assertEquals(spec.clearanceVolume(), spec.volumeAt(0), 1e-12);
		assertEquals(spec.clearanceVolume() + 1, spec.volumeAt(180), 1e-9);
		assertEquals(spec.compressionRatio(), spec.volumeAt(180) / spec.volumeAt(0), 1e-9);
	}

	@Test
	void theVolumeRepeatsEveryRevolutionAndIsSymmetricAboutTheEnds() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		assertEquals(spec.volumeAt(40), spec.volumeAt(400), 1e-9);
		assertEquals(spec.volumeAt(40), spec.volumeAt(-40), 1e-9);
	}

	@Test
	void theVolumeScalesWithTheDisplacement() {
		CylinderSpec one = CylinderSpec.CRUDE;
		CylinderSpec two = new CylinderSpec(12, 4 / 14d, 20 * G, 20 * G, 0.5, 0.02 / G, 0.06 / G, 0.08, 0.01 / G, 0.15, 2);
		for (double angle : new double[] {0, 45, 90, 180, 270})
			assertEquals(2 * one.volumeAt(angle), two.volumeAt(angle), 1e-12);
	}

	@Test
	void aShortRodSkewsTheStrokeTowardsTheTop() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		double midUp = spec.volumeAt(90) - spec.volumeAt(0);
		double midDown = spec.volumeAt(180) - spec.volumeAt(90);
		assertTrue(midUp > midDown, "the piston has moved more than halfway by a quarter turn");
	}

	@Test
	void theTablesAgreeWithTheFormula() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		for (double angle = 0; angle < 360; angle += 0.37) {
			assertEquals(spec.volumeAt(angle), spec.geometry().volume(angle), 1e-5, "volume at " + angle);
			assertEquals(Math.pow(spec.volumeAt(angle), CylinderModel.GAMMA - 1), spec.geometry().heat(angle), 1e-5,
				"heat at " + angle);
		}
	}

	@Test
	void theTablesWrapLikeTheCrankAndAreSharedBetweenEqualShapes() {
		CylinderSpec.Geometry geometry = CylinderSpec.CRUDE.geometry();
		assertEquals(geometry.volume(10), geometry.volume(370), 1e-9);
		assertEquals(geometry.volume(350), geometry.volume(-10), 1e-9);
		CylinderSpec same = new CylinderSpec(12, 4 / 14d, 20 * G, 20 * G, 0.5, 0.02 / G, 0.06 / G, 0.08, 0.01 / G, 0.15);
		assertSame(geometry, same.geometry());
	}

	@Test
	void frictionIsStaticAtRestAndGrowsWithSpeed() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		assertEquals(spec.frictionStatic(), spec.frictionAt(0, 1), 1e-12);
		assertEquals(spec.frictionStatic() + spec.frictionViscous() * 3, spec.frictionAt(3, 1), 1e-12);
		assertTrue(spec.frictionAt(5, 1) > spec.frictionAt(1, 1));
	}

	@Test
	void aDryFilmMultipliesBothKindsOfFriction() {
		CylinderSpec spec = CylinderSpec.CRUDE;
		double expected = spec.frictionStatic() * 2 + spec.frictionViscous() * 2 * 4;
		assertEquals(expected, spec.frictionAt(2, 0), 1e-12);
		assertTrue(spec.frictionAt(2, 0) > spec.frictionAt(2, 0.5));
		assertTrue(spec.frictionAt(2, 0.5) > spec.frictionAt(2, 1));
	}

	@Test
	void buildingWithNoPrecisionPartsGivesTheCrudeCylinder() {
		assertEquals(CylinderSpec.CRUDE, CylinderSpec.build(false, false, false, false));
	}

	@Test
	void eachPrecisionPartImprovesItsOwnAspect() {
		CylinderSpec crude = CylinderSpec.CRUDE;
		CylinderSpec bore = CylinderSpec.build(true, false, false, false);
		assertEquals(crude.compressionRatio() * CylinderSpec.PRECISION_COMPRESSION, bore.compressionRatio(), 1e-12);
		assertTrue(bore.frictionStatic() < crude.frictionStatic());
		assertTrue(bore.frictionViscous() < crude.frictionViscous());

		CylinderSpec head = CylinderSpec.build(false, true, false, false);
		assertEquals(crude.intakeFlow() * CylinderSpec.PRECISION_BREATHING, head.intakeFlow(), 1e-12);
		assertEquals(crude.exhaustFlow() * CylinderSpec.PRECISION_BREATHING, head.exhaustFlow(), 1e-12);
		assertEquals(crude.ignitionDelay() * CylinderSpec.PRECISION_HEAD_DELAY, head.ignitionDelay(), 1e-12);

		CylinderSpec piston = CylinderSpec.build(false, false, true, false);
		assertEquals(crude.inertia() * CylinderSpec.PRECISION_PISTON_INERTIA, piston.inertia(), 1e-12);
		assertTrue(piston.frictionStatic() < crude.frictionStatic());

		CylinderSpec injector = CylinderSpec.build(false, false, false, true);
		assertEquals(crude.ignitionDelay() * CylinderSpec.PRECISION_INJECTOR_DELAY, injector.ignitionDelay(), 1e-12);
		assertEquals(crude.burnDuration() * CylinderSpec.PRECISION_INJECTOR_BURN, injector.burnDuration(), 1e-12);
		assertEquals(CylinderSpec.PRECISION_INJECTOR_FLOW, injector.injectorFlow(), 1e-12);
	}

	@Test
	void precisionPartsAddUp() {
		CylinderSpec head = CylinderSpec.build(false, true, false, false);
		CylinderSpec injector = CylinderSpec.build(false, false, false, true);
		CylinderSpec both = CylinderSpec.build(false, true, false, true);
		assertEquals(CylinderSpec.CRUDE.ignitionDelay() * CylinderSpec.PRECISION_HEAD_DELAY
			* CylinderSpec.PRECISION_INJECTOR_DELAY, both.ignitionDelay(), 1e-12);
		assertTrue(both.ignitionDelay() < head.ignitionDelay());
		assertTrue(both.ignitionDelay() < injector.ignitionDelay());
	}

	@Test
	void theBonusSettingScalesHowMuchThePartsHelp() {
		Tuning.precisionBonus = 0;
		assertEquals(CylinderSpec.CRUDE, CylinderSpec.build(true, true, true, true));
		Tuning.precisionBonus = 0.5;
		CylinderSpec half = CylinderSpec.build(true, false, false, false);
		assertEquals(12 * (1 + 0.2 * 0.5), half.compressionRatio(), 1e-12);
	}

	@Test
	void aPlainRotaryChamberIsTheStockOneAndTheRotaryPowerScalesIt() {
		assertSame(CylinderSpec.ROTARY, CylinderSpec.buildRotary(false));
		Tuning.rotaryPower = 2;
		CylinderSpec strong = CylinderSpec.buildRotary(false);
		assertEquals(2 * CylinderSpec.ROTARY.displacement(), strong.displacement(), 1e-12);
		assertEquals(2 * CylinderSpec.ROTARY.maxFuelPerShot(), strong.maxFuelPerShot(), 1e-12);
		assertEquals(CylinderSpec.ROTARY.burnDuration(), strong.burnDuration(), 0);
	}

	@Test
	void aPrecisionInjectorSpeedsUpARotaryChamberToo() {
		CylinderSpec fitted = CylinderSpec.buildRotary(true);
		assertEquals(CylinderSpec.ROTARY.ignitionDelay() * CylinderSpec.PRECISION_INJECTOR_DELAY, fitted.ignitionDelay(), 1e-12);
		assertEquals(CylinderSpec.ROTARY.burnDuration() * CylinderSpec.PRECISION_INJECTOR_BURN, fitted.burnDuration(), 1e-12);
		assertEquals(CylinderSpec.PRECISION_INJECTOR_FLOW, fitted.injectorFlow(), 1e-12);
		assertEquals(CylinderSpec.ROTARY.displacement(), fitted.displacement(), 0);
	}
}
