package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Rotary apex seal wear (opt-in, 2.3): the curves, how they differ from the piston's, and what a worn set costs a rotor on the dyno. The
 * housing's own behaviour (nothing runs with the mode off, saves, the failure and its replacement) is covered by the GameTests.
 */
class ApexSealsTest {

	@Test
	void soundSealsChangeNothing() {
		assertEquals(1, ApexSeals.seal(0), 0);
		assertEquals(1, ApexSeals.state(0).seal(), 0);
		assertEquals(1, ApexSeals.state(0).drag(), 0);
		assertEquals(1, ApexSeals.state(1).drag(), 0, "leaking seals do not bind: only the burn is lost");
	}

	@Test
	void wearGrowsWithSpeedHeatAndAboveAllNoOil() {
		double base = ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, 1);
		assertTrue(ApexSeals.wear(2 * ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, 1) > base, "speed");
		assertTrue(ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL + 0.3, 1, 1) > base, "heat");
		assertTrue(ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 0, 1) > base, "no oil");
	}

	@Test
	void theRotarysCurveIsItsOwnNotThePistons() {
		// A set is hurt by running dry and by heat more than a piston is: the seals live on the film and sit in a hot housing
		assertTrue(ApexSeals.dryFactor(0) > Wear.dryFactor(0));
		assertTrue(ApexSeals.heatFactor(Thermal.NORMAL + 0.3) > Wear.heatFactor(Thermal.NORMAL + 0.3));
		// And it is measured against a faster shaft, so the same speed is gentler on a rotary's seals than on a piston
		assertTrue(ApexSeals.REFERENCE_RPM > Wear.REFERENCE_RPM);
		assertTrue(ApexSeals.wear(Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 100) < Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 100));
	}

	@Test
	void aSetAtItsReferenceSpeedWearsOutInItsStatedLife() {
		assertEquals(1, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, ApexSeals.LIFE), 1e-9);
	}

	@Test
	void aStoppedHousingWearsNothingAndASlowOneStillWears() {
		assertEquals(0, ApexSeals.wear(0, Thermal.NORMAL, 1, 100), 0);
		assertTrue(ApexSeals.wear(1, Thermal.NORMAL, 1, 100) > 0);
	}

	@Test
	void anInputThatIsNotASpanOfTimeOrAReadingWearsNothingOrReadsAsNormal() {
		assertEquals(0, ApexSeals.wear(100, Thermal.NORMAL, 1, Double.NaN), 0);
		assertEquals(0, ApexSeals.wear(100, Thermal.NORMAL, 1, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, ApexSeals.wear(100, Thermal.NORMAL, 1, -1), 0);
		assertEquals(1, ApexSeals.heatFactor(Double.NaN), 0);
		assertEquals(1, ApexSeals.dryFactor(Double.NaN), 0);
		assertEquals(1 + ApexSeals.DRY_STRESS, ApexSeals.dryFactor(-3), 1e-12);
		assertTrue(ApexSeals.wear(1e9, 5, -4, 1e9) <= 1);
	}

	@Test
	void theSealLossFallsWithTheSquareOfWearAndNeverPastItsLimit() {
		assertEquals(1 - ApexSeals.SEAL_LOSS, ApexSeals.seal(1), 1e-12);
		assertEquals(1 - ApexSeals.SEAL_LOSS * 0.25, ApexSeals.seal(0.5), 1e-12);
		assertEquals(1, ApexSeals.seal(Double.NaN), 0);
		assertEquals(1 - ApexSeals.SEAL_LOSS, ApexSeals.seal(9), 1e-12);
	}

	@Test
	void nothingFailsBelowTheThresholdAndTheChanceRisesWithWearAndSpeedButStaysAProbability() {
		assertEquals(0, ApexSeals.failureChance(Wear.FAILURE_FROM, 500, 10), 0);
		assertEquals(0, ApexSeals.failureChance(0.2, 500, 10), 0);
		double half = ApexSeals.failureChance(0.75, ApexSeals.REFERENCE_RPM, 1);
		double more = ApexSeals.failureChance(0.9, ApexSeals.REFERENCE_RPM, 1);
		double fast = ApexSeals.failureChance(0.9, 3 * ApexSeals.REFERENCE_RPM, 1);
		assertTrue(0 < half && half < more && more < fast);
		for (double wear : new double[] {Double.NaN, -1, 1, 5})
			for (double rpm : new double[] {Double.NaN, 0, 1e9})
				for (double dt : new double[] {Double.NaN, -1, 0, 1, 1e12}) {
					double chance = ApexSeals.failureChance(wear, rpm, dt);
					assertTrue(chance >= 0 && chance <= 1, wear + " " + rpm + " " + dt + " -> " + chance);
				}
	}

	// On the dyno: a rotor with its seals worn

	private static double peak(Wear.State wear) {
		List<CylinderModel> models = new ArrayList<>();
		for (int chamber = 0; chamber < 3; chamber++) {
			CylinderModel model = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, chamber);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			model.wear = wear;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 1.2, 0.04);
		sim.restore(0, 0, 1);
		for (CylinderModel model : models)
			model.resetGas(model.pistonAngle(0, 1));
		double[] curve = Dynamometer.torqueCurve(sim);
		double best = 0;
		for (double t : curve)
			best = Math.max(best, t);
		return best;
	}

	@Test
	void soundSealsAreBitForBitTheRotorWithNoWear() {
		assertEquals(Double.doubleToLongBits(peak(Wear.NONE)), Double.doubleToLongBits(peak(ApexSeals.state(0))));
		CylinderModel model = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 0);
		assertSame(Wear.NONE, model.wear);
	}

	@Test
	void moreWornSealsMeanLessTorqueButNeverNone() {
		double sound = peak(Wear.NONE);
		double light = peak(ApexSeals.state(0.3));
		double heavy = peak(ApexSeals.state(0.7));
		double spent = peak(ApexSeals.state(1));
		assertTrue(sound > light && light > heavy && heavy > spent, sound + " > " + light + " > " + heavy + " > " + spent);
		assertTrue(spent > 0.4 * sound, "a spent set is weak, not dead: " + spent + " of " + sound);
		assertTrue(light > 0.93 * sound, "light wear is felt a little: " + light + " of " + sound);
		// Measured on the dyno, 2026-11-24 (share of a sound rotor's peak torque): 0.3 wear 96%, 0.5 wear 90%, 0.7 wear 80%, spent 59%
		assertEquals(0.963, light / sound, 0.02, "light");
		assertEquals(0.897, peak(ApexSeals.state(Wear.FAILURE_FROM)) / sound, 0.02, "at the failure threshold");
		assertEquals(0.797, heavy / sound, 0.02, "heavy");
		assertEquals(0.587, spent / sound, 0.02, "spent");
	}
}
