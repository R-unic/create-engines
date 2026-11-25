package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exact figures and edges for the plug, turbo, knock, apex seal and battery curves. The curves are simple, so each test pins the number the
 * formula gives at a few points (a changed operator or constant shows up as a different figure) and the edge each guard sits on.
 */
class WearEdgesTest {

	private static final double EPS = 1e-12;

	// --- Spark plug ---

	@Test
	void plugWearFigures() {
		assertEquals(3600 / PlugWear.LIFE, PlugWear.wear(Wear.REFERENCE_RPM, false, 0, 3600), EPS);
		assertEquals(3600 / PlugWear.LIFE * PlugWear.COIL_FACTOR, PlugWear.wear(Wear.REFERENCE_RPM, true, 0, 3600), EPS);
		assertEquals(3600 / PlugWear.LIFE * PlugWear.FOULING_FACTOR, PlugWear.wear(Wear.REFERENCE_RPM, false, 1, 3600), EPS);
		assertEquals(3600 / PlugWear.LIFE * (1 + (PlugWear.FOULING_FACTOR - 1) * 0.5), PlugWear.wear(Wear.REFERENCE_RPM, false, 0.5, 3600), EPS);
		// Soot outside 0 to 1, or not a number, counts as the nearest clean or fouled
		assertEquals(PlugWear.wear(Wear.REFERENCE_RPM, false, 1, 100), PlugWear.wear(Wear.REFERENCE_RPM, false, 7, 100), 0);
		assertEquals(PlugWear.wear(Wear.REFERENCE_RPM, false, 0, 100), PlugWear.wear(Wear.REFERENCE_RPM, false, -3, 100), 0);
		assertEquals(PlugWear.wear(Wear.REFERENCE_RPM, false, 0, 100), PlugWear.wear(Wear.REFERENCE_RPM, false, Double.NaN, 100), 0);
		// One step cannot spend more than a whole plug, and a step that is no time wears nothing
		assertEquals(1, PlugWear.wear(Wear.REFERENCE_RPM, true, 1, 1e9), 0);
		assertEquals(0, PlugWear.wear(Wear.REFERENCE_RPM, true, 1, 0), 0);
		assertEquals(0, PlugWear.wear(Wear.REFERENCE_RPM, true, 1, -5), 0);
		assertEquals(0, PlugWear.wear(Wear.REFERENCE_RPM, true, 1, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, PlugWear.wear(Wear.REFERENCE_RPM, true, 1, Double.NaN), 0);
	}

	@Test
	void plugMisfireAndLeanFigures() {
		assertEquals(0, PlugWear.misfireChance(0.2), 0);
		assertEquals(0, PlugWear.misfireChance(PlugWear.MISFIRE_FROM), 0);
		assertEquals(PlugWear.MISFIRE_MAX * 0.25, PlugWear.misfireChance(PlugWear.MISFIRE_FROM + 0.65 / 2), EPS);
		assertEquals(PlugWear.MISFIRE_MAX, PlugWear.misfireChance(1), EPS);
		assertEquals(PlugWear.MISFIRE_MAX, PlugWear.misfireChance(5), EPS);
		assertEquals(0, PlugWear.misfireChance(-1), 0);
		assertEquals(0, PlugWear.misfireChance(Double.NaN), 0);
		assertEquals(1 + (PlugWear.LEAN_LOSS - 1) * 0.25, PlugWear.leanScale(0.5), EPS);
		assertEquals(PlugWear.LEAN_LOSS, PlugWear.leanScale(1), EPS);
		assertEquals(PlugWear.LEAN_LOSS, PlugWear.leanScale(9), EPS);
		assertEquals(1, PlugWear.leanScale(-9), 0);
	}

	// --- Turbo ---

	@Test
	void turboWearFigures() {
		double day = 3600;
		assertEquals(day / TurboWear.LIFE, TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL, 1, day), EPS);
		// No boost, and a little boost, are held at the idle floor; more boost is the square of the load
		assertEquals(day / TurboWear.LIFE * TurboWear.IDLE_FLOOR, TurboWear.wear(0, Thermal.NORMAL, 1, day), EPS);
		assertEquals(day / TurboWear.LIFE * TurboWear.IDLE_FLOOR, TurboWear.wear(0.1, Thermal.NORMAL, 1, day), EPS);
		assertEquals(day / TurboWear.LIFE * 4, TurboWear.wear(2 * TurboWear.REFERENCE_BOOST, Thermal.NORMAL, 1, day), EPS);
		assertEquals(day / TurboWear.LIFE * TurboWear.IDLE_FLOOR, TurboWear.wear(-3, Thermal.NORMAL, 1, day), EPS);
		assertEquals(day / TurboWear.LIFE * TurboWear.IDLE_FLOOR, TurboWear.wear(Double.NaN, Thermal.NORMAL, 1, day), EPS);
		// Heat and dry oil multiply as they do for a piston
		assertEquals(day / TurboWear.LIFE * Wear.heatFactor(Thermal.NORMAL + 0.2), TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL + 0.2, 1, day), EPS);
		assertEquals(day / TurboWear.LIFE * Wear.dryFactor(0), TurboWear.wear(TurboWear.REFERENCE_BOOST, Thermal.NORMAL, 0, day), EPS);
		assertEquals(1, TurboWear.wear(5, Thermal.NORMAL, 0, 1e9), 0);
		assertEquals(0, TurboWear.wear(5, Thermal.NORMAL, 1, 0), 0);
		assertEquals(0, TurboWear.wear(5, Thermal.NORMAL, 1, -1), 0);
		assertEquals(0, TurboWear.wear(5, Thermal.NORMAL, 1, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, TurboWear.wear(5, Thermal.NORMAL, 1, Double.NaN), 0);
	}

	@Test
	void turboBoostShareAndFailureFigures() {
		assertEquals(1 - TurboWear.MAX_BOOST_LOSS * 0.25, TurboWear.boostShare(0.5), EPS);
		assertEquals(1 - TurboWear.MAX_BOOST_LOSS, TurboWear.boostShare(1), EPS);
		assertEquals(1 - TurboWear.MAX_BOOST_LOSS, TurboWear.boostShare(4), EPS);
		assertEquals(1, TurboWear.boostShare(-4), 0);
		// At the failure threshold and below nothing fails; just past it something does, however small
		assertEquals(0, TurboWear.failureChance(Wear.FAILURE_FROM, 0.5, 100), 0);
		assertEquals(0, TurboWear.failureChance(0.2, 0.5, 100), 0);
		assertTrue(TurboWear.failureChance(Math.nextUp(Wear.FAILURE_FROM), 0.5, 100) >= 0);
		// Fully worn at the reference boost: the rate itself, per second
		assertEquals(TurboWear.FAILURE_RATE, TurboWear.failureChance(1, TurboWear.REFERENCE_BOOST, 1), EPS);
		assertEquals(TurboWear.FAILURE_RATE * 3, TurboWear.failureChance(1, TurboWear.REFERENCE_BOOST, 3), EPS);
		// Less boost than the reference does not lower the chance, and more raises it in proportion up to the overspeed point
		assertEquals(TurboWear.FAILURE_RATE, TurboWear.failureChance(1, 0, 1), EPS);
		assertEquals(TurboWear.FAILURE_RATE, TurboWear.failureChance(1, 0.25, 1), EPS);
		assertEquals(TurboWear.FAILURE_RATE * 2, TurboWear.failureChance(1, 2 * TurboWear.REFERENCE_BOOST, 1), EPS);
		assertEquals(TurboWear.FAILURE_RATE * TurboWear.OVERSPEED / TurboWear.REFERENCE_BOOST, TurboWear.failureChance(1, TurboWear.OVERSPEED, 1), EPS);
		// Past the overspeed point it doubles for each bar
		assertEquals(TurboWear.FAILURE_RATE * (TurboWear.OVERSPEED + 1) / TurboWear.REFERENCE_BOOST * 2,
			TurboWear.failureChance(1, TurboWear.OVERSPEED + 1, 1), EPS);
		// Cubed past the threshold
		assertEquals(TurboWear.FAILURE_RATE / 8, TurboWear.failureChance(0.75, TurboWear.REFERENCE_BOOST, 1), EPS);
		assertEquals(1, TurboWear.failureChance(1, 100, 100), 0);
		assertEquals(0, TurboWear.failureChance(1, 1, 0), 0);
		assertEquals(0, TurboWear.failureChance(1, 1, -1), 0);
		assertEquals(0, TurboWear.failureChance(1, 1, Double.POSITIVE_INFINITY), 0);
		assertEquals(TurboWear.FAILURE_RATE, TurboWear.failureChance(1, Double.NaN, 1), EPS);
		assertEquals(TurboWear.FAILURE_RATE, TurboWear.failureChance(1, -2, 1), EPS);
		assertEquals(0, TurboWear.failureChance(Double.NaN, 1, 1), 0);
	}

	// --- Knock ---

	@Test
	void knockFigures() {
		double span = FuelSpec.PREIGNITION_MAX_LOSS - Knock.FROM;
		assertEquals(0, Knock.intensity(0), 0);
		assertEquals(0, Knock.intensity(Knock.FROM), 0);
		assertEquals(0.5, Knock.intensity(Knock.FROM + span / 2), EPS);
		assertEquals(1, Knock.intensity(FuelSpec.PREIGNITION_MAX_LOSS), EPS);
		assertEquals(1, Knock.intensity(1), 0);
		assertEquals(0, Knock.intensity(-1), 0);
		assertEquals(0, Knock.intensity(Double.NaN), 0);
		assertEquals(0, Knock.intensity(Double.POSITIVE_INFINITY), 0);
		// Full knock at the reference speed spends a piston in its stated life, and half the knock takes a quarter of the wear
		assertEquals(1, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, Knock.LIFE), EPS);
		assertEquals(0.25, Knock.pistonWear(Knock.FROM + span / 2, Wear.REFERENCE_RPM, Knock.LIFE), EPS);
		assertEquals(60 / Knock.LIFE, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, 60), EPS);
		assertEquals(1, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, 1e9), 0);
		assertEquals(0, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, 0), 0);
		assertEquals(0, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, -1), 0);
		assertEquals(0, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, Knock.pistonWear(FuelSpec.PREIGNITION_MAX_LOSS, Wear.REFERENCE_RPM, Double.NaN), 0);
		assertEquals(0, Knock.pistonWear(0.01, 256, 1000), 0);
	}

	@Test
	void aPrecisionPistonLastsLongerAgainstKnockToo() {
		double loss = FuelSpec.PREIGNITION_MAX_LOSS;
		double standard = Knock.pistonWear(loss, Wear.REFERENCE_RPM, 60, false);
		assertEquals(Knock.pistonWear(loss, Wear.REFERENCE_RPM, 60), standard, 0);
		assertEquals(standard / Wear.PRECISION_LIFE, Knock.pistonWear(loss, Wear.REFERENCE_RPM, 60, true), EPS);
		assertEquals(0, Knock.pistonWear(Knock.FROM, Wear.REFERENCE_RPM, 60, true), 0);
	}

	@Test
	void knockIsAudibleFromItsThresholdExactly() {
		assertTrue(Knock.audible(Knock.AUDIBLE_FROM));
		assertFalse(Knock.audible(Math.nextDown(Knock.AUDIBLE_FROM)));
		assertTrue(Knock.audible(FuelSpec.PREIGNITION_MAX_LOSS));
		assertFalse(Knock.audible(0));
		assertFalse(Knock.audible(Double.NaN));
		assertTrue(Knock.AUDIBLE_FROM > Knock.FROM, "pinging is heard after the engine starts to lose power to it");
	}

	// --- Apex seals ---

	@Test
	void apexSealFigures() {
		double hour = 3600;
		assertEquals(0, ApexSeals.speedFactor(0), 0);
		assertEquals(0, ApexSeals.speedFactor(-5), 0);
		assertEquals(0, ApexSeals.speedFactor(Double.NaN), 0);
		assertEquals(Wear.IDLE_FLOOR, ApexSeals.speedFactor(1), 0);
		assertEquals(1, ApexSeals.speedFactor(ApexSeals.REFERENCE_RPM), EPS);
		assertEquals(4, ApexSeals.speedFactor(2 * ApexSeals.REFERENCE_RPM), EPS);
		assertEquals(Wear.SPEED_CAP * Wear.SPEED_CAP, ApexSeals.speedFactor(1e9), EPS);
		assertEquals(1, ApexSeals.heatFactor(Thermal.NORMAL), 0);
		assertEquals(1, ApexSeals.heatFactor(0), 0);
		assertEquals(1 + ApexSeals.HEAT_STRESS * 0.1, ApexSeals.heatFactor(Thermal.NORMAL + 0.1), EPS);
		assertEquals(1, ApexSeals.heatFactor(Double.NaN), 0);
		assertEquals(1, ApexSeals.dryFactor(1), 0);
		assertEquals(1 + ApexSeals.DRY_STRESS, ApexSeals.dryFactor(0), EPS);
		assertEquals(1 + ApexSeals.DRY_STRESS / 2, ApexSeals.dryFactor(0.5), EPS);
		assertEquals(1 + ApexSeals.DRY_STRESS, ApexSeals.dryFactor(-4), EPS);
		assertEquals(1, ApexSeals.dryFactor(4), 0);
		assertEquals(1, ApexSeals.dryFactor(Double.NaN), 0);
		assertEquals(hour / ApexSeals.LIFE, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, hour), EPS);
		assertEquals(1, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 0, 1e9), 0);
		assertEquals(0, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, 0), 0);
		assertEquals(0, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, -1), 0);
		assertEquals(0, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, ApexSeals.wear(ApexSeals.REFERENCE_RPM, Thermal.NORMAL, 1, Double.NaN), 0);
		assertEquals(1, ApexSeals.seal(0), 0);
		assertEquals(1 - ApexSeals.SEAL_LOSS * 0.25, ApexSeals.seal(0.5), EPS);
		assertEquals(1 - ApexSeals.SEAL_LOSS, ApexSeals.seal(7), EPS);
		assertEquals(1, ApexSeals.state(0.5).drag(), 0);
		assertEquals(ApexSeals.seal(0.5), ApexSeals.state(0.5).seal(), 0);
	}

	@Test
	void apexSealFailureFigures() {
		assertEquals(0, ApexSeals.failureChance(Wear.FAILURE_FROM, 192, 100), 0);
		assertEquals(0, ApexSeals.failureChance(0.1, 192, 100), 0);
		assertEquals(Wear.FAILURE_RATE, ApexSeals.failureChance(1, ApexSeals.REFERENCE_RPM, 1), EPS);
		assertEquals(Wear.FAILURE_RATE, ApexSeals.failureChance(1, 0, 1), EPS);
		assertEquals(Wear.FAILURE_RATE * 4, ApexSeals.failureChance(1, 2 * ApexSeals.REFERENCE_RPM, 1), EPS);
		assertEquals(Wear.FAILURE_RATE / 8, ApexSeals.failureChance(0.75, ApexSeals.REFERENCE_RPM, 1), EPS);
		assertEquals(Wear.FAILURE_RATE * 5, ApexSeals.failureChance(1, ApexSeals.REFERENCE_RPM, 5), EPS);
		assertEquals(1, ApexSeals.failureChance(1, 1e9, 1e9), 0);
		assertEquals(0, ApexSeals.failureChance(1, 192, 0), 0);
		assertEquals(0, ApexSeals.failureChance(1, 192, -1), 0);
		assertEquals(0, ApexSeals.failureChance(1, 192, Double.POSITIVE_INFINITY), 0);
		assertEquals(0, ApexSeals.failureChance(1, 192, Double.NaN), 0);
		assertEquals(0, ApexSeals.failureChance(Double.NaN, 192, 1), 0);
	}

	// --- Battery ---

	@Test
	void batteryWearFigures() {
		for (BatteryTier tier : BatteryTier.values()) {
			BatteryWear.Profile profile = BatteryWear.profile(tier);
			double perSpark = 1 / (tier.capacity() * profile.cycles());
			assertEquals(10 * perSpark, BatteryWear.drawWear(tier, 10, 1, 0), EPS, tier + " warm and well charged");
			assertEquals(10 * perSpark * profile.deepStress(), BatteryWear.drawWear(tier, 10, 0, 0), EPS, tier + " from empty");
			assertEquals(10 * perSpark * (1 + profile.coldStress()), BatteryWear.drawWear(tier, 10, 1, 1), EPS, tier + " in the deepest cold");
			assertEquals(10 * perSpark, BatteryWear.drawWear(tier, 10, BatteryWear.DEEP_BELOW, 0), EPS, tier + " just at the deep edge");
			assertEquals(10 * perSpark * (1 + (profile.deepStress() - 1) / 2), BatteryWear.drawWear(tier, 10, BatteryWear.DEEP_BELOW / 2, 0), EPS);
			assertEquals(0, BatteryWear.drawWear(tier, 0, 0, 1), 0);
			assertEquals(0, BatteryWear.drawWear(tier, -5, 0, 1), 0);
			assertEquals(0, BatteryWear.drawWear(tier, Double.NaN, 0, 1), 0);
			assertEquals(0, BatteryWear.drawWear(tier, Double.POSITIVE_INFINITY, 0, 1), 0);
			assertEquals(1, BatteryWear.drawWear(tier, 1e12, 0, 1), 0);
			assertEquals(BatteryWear.drawWear(tier, 10, 1, 0), BatteryWear.drawWear(tier, 10, Double.NaN, Double.NaN), 0);
			assertEquals(BatteryWear.drawWear(tier, 10, 0, 1), BatteryWear.drawWear(tier, 10, -3, 7), 0);
		}
	}
}
