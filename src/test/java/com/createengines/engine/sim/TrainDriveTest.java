package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrainDriveTest {

	private static final double BASE_TOP = 28;
	private static final double BASE_ACCEL = 3;

	/** An engine rated flat at {@code capacity} SU per RPM from 16 to 256 RPM, its power rising with speed. */
	private static TrainDrive.Spec flat(double capacity) {
		float[] rpm = new float[16];
		float[] torque = new float[16];
		for (int i = 0; i < rpm.length; i++) {
			rpm[i] = 16 * (i + 1);
			torque[i] = (float) capacity;
		}
		return new TrainDrive.Spec(rpm, torque, 12000);
	}

	/** An engine that makes {@code power} SU at every speed from 16 to 256 RPM, so only the gearing moves its output. */
	private static TrainDrive.Spec constantPower(double power) {
		float[] rpm = new float[16];
		float[] torque = new float[16];
		for (int i = 0; i < rpm.length; i++) {
			rpm[i] = 16 * (i + 1);
			torque[i] = (float) (power / rpm[i]);
		}
		return new TrainDrive.Spec(rpm, torque, 12000);
	}

	/** RPM per unit of speed that puts 256 RPM, the end of these curves, at {@code speed}. */
	private static double endAt(double speed) {
		return 256 / speed;
	}

	/** Gearing that puts the end of the curve (256 RPM) at one and a half times the base top speed. */
	private static double gearing(TrainDrive.Spec spec) {
		return TrainDrive.idealGearing(spec, BASE_TOP * 1.5);
	}

	private static TrainDrive.Boost boost(TrainDrive.Spec spec, double gearing, int carriages, double throttle,
										 double speed) {
		return TrainDrive.boost(spec, gearing, carriages, throttle, BASE_TOP, BASE_ACCEL, speed);
	}

	@Test
	void theCurveIsReadBetweenItsPointsAndIsZeroOffItsEnds() {
		TrainDrive.Spec spec = new TrainDrive.Spec(new float[] {16, 32, 64}, new float[] {10, 20, 20}, 1);
		assertEquals(0, spec.capacityAt(8));
		assertEquals(10, spec.capacityAt(16), 1e-9);
		assertEquals(15, spec.capacityAt(24), 1e-9);
		assertEquals(20, spec.capacityAt(48), 1e-9);
		assertEquals(0, spec.capacityAt(65));
		assertEquals(20 * 64, spec.peakPower(), 1e-9);
		assertEquals(64, spec.peakPowerRpm());
	}

	@Test
	void anEngineWithoutThrottleOrWithoutACurveGivesNothing() {
		TrainDrive.Spec spec = flat(100);
		assertEquals(TrainDrive.Boost.NONE, boost(spec, gearing(spec), 2, 0, 20));
		assertEquals(TrainDrive.Boost.NONE, TrainDrive.boost(new TrainDrive.Spec(new float[0], new float[0], 1), 4, 1, 1,
			BASE_TOP, BASE_ACCEL, 10));
		assertEquals(TrainDrive.Boost.NONE, boost(spec, 0, 2, 1, 20));
	}

	@Test
	void moreEnginePowerPerCarriageGivesMoreTopSpeedAndPull() {
		TrainDrive.Spec weak = flat(20);
		TrainDrive.Spec strong = flat(100);
		TrainDrive.Boost small = boost(weak, gearing(weak), 3, 1, 20);
		TrainDrive.Boost big = boost(strong, gearing(strong), 3, 1, 20);
		assertTrue(small.topSpeed() > BASE_TOP, "even a weak engine adds something: " + small);
		assertTrue(big.topSpeed() > small.topSpeed(), small + " against " + big);
		assertTrue(big.acceleration() > small.acceleration());
		assertTrue(big.fuelPerSecond() > small.fuelPerSecond(), "more power burns more fuel");
	}

	@Test
	void theSamePowerSpreadOverMoreCarriagesPushesEachLess() {
		TrainDrive.Spec spec = flat(100);
		TrainDrive.Boost two = boost(spec, gearing(spec), 2, 1, 20);
		TrainDrive.Boost eight = boost(spec, gearing(spec), 8, 1, 20);
		assertTrue(two.topSpeed() > eight.topSpeed() && two.acceleration() > eight.acceleration());
		assertEquals(two.power(), eight.power(), 1e-9, "the engine makes what it makes");
	}

	@Test
	void topSpeedGrowsWithTheCubeRootOfPower() {
		// Power that is eight times larger everywhere on the curve doubles the gain, so long as neither hits the cap
		TrainDrive.Spec one = constantPower(20000);
		TrainDrive.Spec eight = constantPower(160000);
		double gainOne = boost(one, endAt(BASE_TOP * 3), 1, 1, 20).topSpeed() / BASE_TOP - 1;
		double gainEight = boost(eight, endAt(BASE_TOP * 3), 1, 1, 20).topSpeed() / BASE_TOP - 1;
		assertEquals(2, gainEight / gainOne, 0.01, gainOne + " then " + gainEight);
	}

	@Test
	void noEngineIsWorthMoreThanTheCap() {
		TrainDrive.Spec huge = constantPower(1e9);
		// Geared so the curve runs well past the capped top speed, or the engine would redline first
		TrainDrive.Boost boost = boost(huge, endAt(BASE_TOP * 4), 1, 1, 20);
		assertEquals(BASE_TOP * (1 + TrainDrive.MAX_SPEED_BONUS), boost.topSpeed(), 1e-6);
		assertEquals(BASE_ACCEL * TrainDrive.MAX_ACCEL_BONUS, boost.acceleration(), 1e-9);
	}

	@Test
	void anEngineRedlinesAtTheEndOfItsCurveWhateverItsPower() {
		// Geared so the curve ends at one and a half times the base top speed: that is as fast as it can pull the train
		TrainDrive.Spec huge = constantPower(1e9);
		TrainDrive.Boost boost = boost(huge, endAt(BASE_TOP * 1.5), 1, 1, 20);
		assertEquals(BASE_TOP * 1.5, boost.topSpeed(), 1e-6);
	}

	@Test
	void aThrottleScalesThePowerAndTheFuelBurned() {
		TrainDrive.Spec spec = flat(100);
		TrainDrive.Boost full = boost(spec, gearing(spec), 2, 1, 20);
		TrainDrive.Boost half = boost(spec, gearing(spec), 2, 0.5, 20);
		assertEquals(full.power() / 2, half.power(), 1e-9);
		assertEquals(full.fuelPerSecond() / 2, half.fuelPerSecond(), 1e-9);
		assertTrue(half.topSpeed() < full.topSpeed() && half.topSpeed() > BASE_TOP);
	}

	@Test
	void aGearingThatSpinsTheEnginePastItsCurveGivesNoTopSpeedGain() {
		TrainDrive.Spec spec = flat(100);
		// Geared so tall that even the base top speed is beyond the end of the curve
		double tall = 400 / BASE_TOP;
		TrainDrive.Boost off = boost(spec, tall, 2, 1, 20);
		assertEquals(BASE_TOP, off.topSpeed(), 1e-9);
		assertEquals(0, off.power());
		assertEquals(0, off.fuelPerSecond());
	}

	@Test
	void theRightGearingBeatsOneThatIsTooShortOrTooTall() {
		float[] rpm = {32, 64, 96, 128, 160, 192, 224, 256};
		// A peaky engine: all its power in the middle of its range
		float[] torque = {2, 40, 90, 120, 90, 40, 8, 1};
		TrainDrive.Spec spec = new TrainDrive.Spec(rpm, torque, 12000);
		double right = TrainDrive.idealGearing(spec, BASE_TOP * 1.4);
		double top = boost(spec, right, 2, 1, 20).topSpeed();
		assertTrue(top > boost(spec, right * 0.4, 2, 1, 20).topSpeed(), "too short a gear");
		assertTrue(top > boost(spec, right * 2.5, 2, 1, 20).topSpeed(), "too tall a gear");
	}

	@Test
	void pullFadesOnceTheTrainIsAtTheEnginesTopSpeed() {
		TrainDrive.Spec spec = flat(100);
		double top = boost(spec, gearing(spec), 2, 1, 0).topSpeed();
		assertTrue(boost(spec, gearing(spec), 2, 1, top * 0.5).acceleration() > 0);
		assertEquals(0, boost(spec, gearing(spec), 2, 1, top + 1).acceleration(), "at its top speed it adds no pull");
	}

	@Test
	void aStandingTrainIsPulledAsIfAtAQuarterOfItsTopSpeed() {
		TrainDrive.Spec spec = flat(100);
		TrainDrive.Boost standing = boost(spec, gearing(spec), 2, 1, 0);
		assertTrue(standing.power() > 0 && standing.acceleration() > 0, "a stopped train still gets its pull: " + standing);
		assertEquals(TrainDrive.engineRpm(BASE_TOP * 0.25, gearing(spec)), standing.engineRpm(), 1e-9);
	}
}
