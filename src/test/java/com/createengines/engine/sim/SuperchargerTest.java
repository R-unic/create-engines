package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SuperchargerTest {

	private static Supercharger blower() {
		return new Supercharger(List.of());
	}

	private static void settle(Supercharger blower) {
		for (int i = 0; i < 200; i++)
			blower.tick(0.1);
	}

	@Test
	void aStillBlowerMakesNoBoost() {
		Supercharger blower = blower();
		assertEquals(0, blower.speed(), 1e-12);
		assertEquals(0, blower.potential(), 1e-12);
		settle(blower);
		assertEquals(0, blower.boost(), 1e-9);
	}

	@Test
	void boostGoesWithAPowerOfTheSpeedAndIsFullAtTheDesignSpeed() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM;
		assertEquals(1, blower.speed(), 1e-12);
		assertEquals(Supercharger.FULL_BOOST, blower.potential(), 1e-12);
		blower.rpm = Supercharger.DESIGN_RPM / 2;
		assertEquals(Supercharger.FULL_BOOST * Math.pow(0.5, Supercharger.SPEED_EXPONENT), blower.potential(), 1e-12);
		assertTrue(blower.potential() < Supercharger.FULL_BOOST / 2, "slower than linear at low speed");
	}

	@Test
	void aPrecisionBlowerOverspeedsItsRotorsWhileTheShaftIsSlow() {
		Supercharger plain = blower();
		Supercharger precision = blower();
		precision.variableDrive = true;
		plain.rpm = precision.rpm = Supercharger.DESIGN_RPM / 2;
		assertEquals(0, plain.overdrive(), 0);
		assertTrue(precision.overdrive() > 0);
		assertTrue(precision.rotorSpeed() > plain.rotorSpeed());
		assertTrue(precision.potential() > plain.potential());

		plain.rpm = precision.rpm = Supercharger.DESIGN_RPM;
		assertEquals(0, precision.overdrive(), 1e-12);
		assertEquals(plain.potential(), precision.potential(), 1e-12);
	}

	@Test
	void aPrecisionBlowerTakesLessPowerAndLeavesTheAirCooler() {
		Supercharger plain = blower();
		Supercharger precision = blower();
		precision.variableDrive = true;
		assertEquals(1, plain.dragFactor(), 0);
		assertTrue(precision.dragFactor() < 1);

		plain.rpm = precision.rpm = Supercharger.DESIGN_RPM;
		settle(plain);
		settle(precision);
		assertEquals(plain.boost(), precision.boost(), 1e-6);
		assertTrue(precision.chargeTemperature() < plain.chargeTemperature());
	}

	@Test
	void theBypassValveHoldsTheBoostAtItsLimit() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM * 2;
		settle(blower);
		assertTrue(blower.boost() >= blower.limit - 1e-6, "it reaches the limit");
		assertTrue(blower.boost() < blower.limit + 0.1, "and the valve holds it near there");
		assertTrue(blower.bypass() > 0.5, "most of it blown off at twice the design speed");
	}

	@Test
	void aBlowerBelowItsLimitBleedsNothing() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM / 4;
		assertEquals(0, blower.bypass(), 1e-12);
		settle(blower);
		assertEquals(blower.potential(), blower.boost(), 1e-6);
	}

	@Test
	void bypassBleedsOffTheShareOfPotentialThatTheValveDoesNotHold() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = 0.45;
		double bypass = blower.bypass();
		assertTrue(bypass > 0 && bypass < 1);
		double held = blower.potential() * (1 - bypass);
		assertTrue(held >= blower.limit && held < blower.limit + 0.1);
		blower.limit = 0.3;
		assertTrue(blower.bypass() > bypass, "a lower limit bleeds off more");
	}

	@Test
	void dumpingOpensTheValveFullyAndTheBoostDrains() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM;
		settle(blower);
		assertTrue(blower.boost() > 0.4);
		blower.dump = true;
		assertEquals(1, blower.bypass(), 1e-12);
		settle(blower);
		assertEquals(0, blower.boost(), 1e-6);
	}

	@Test
	void boostFollowsTheShaftWithALagNotAJump() {
		Supercharger blower = blower();
		blower.rpm = Supercharger.DESIGN_RPM;
		Supercharger settled = blower();
		settled.rpm = Supercharger.DESIGN_RPM;
		settle(settled);
		blower.tick(0.2);
		assertEquals(settled.boost() * (1 - Math.exp(-1)), blower.boost(), 1e-6);
		assertTrue(blower.boost() < settled.boost());
	}

	@Test
	void compressionHeatsTheAirMoreTheMoreBoostThereIs() {
		Supercharger blower = blower();
		assertEquals(1, blower.chargeTemperature(), 1e-12);
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = 0.3;
		settle(blower);
		double mild = blower.chargeTemperature();
		blower.limit = Supercharger.MAX_LIMIT;
		settle(blower);
		assertTrue(mild > 1);
		assertTrue(blower.chargeTemperature() > mild);
	}

	@Test
	void aCopyKeepsTheSettingsAndTurnsWithTheCrank() {
		Supercharger blower = blower();
		blower.limit = 0.7;
		blower.driveRatio = 2.5;
		blower.parallel = true;
		blower.variableDrive = true;
		blower.rpm = 90;
		assertFalse(blower.followsCrank());

		Supercharger copy = blower.copyOnto(Map.of());
		assertEquals(0.7, copy.limit, 1e-12);
		assertEquals(2.5, copy.driveRatio, 1e-12);
		assertTrue(copy.parallel);
		assertTrue(copy.variableDrive);
		assertTrue(copy.followsCrank());
		assertEquals(0, copy.boost(), 1e-12, "a copy starts with no boost");
	}

	@Test
	void aCopyKeepsOnlyTheCylindersThatWereCopied() {
		CylinderModel kept = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		CylinderModel dropped = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 180, new Camshaft(), 0);
		CylinderModel copyOfKept = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);

		Supercharger blower = new Supercharger(List.of(kept, dropped));
		Supercharger copy = blower.copyOnto(Map.of(kept, copyOfKept));
		assertEquals(List.of(copyOfKept), copy.compressor());
		assertEquals(2, blower.compressor().size());
	}

	@Test
	void theDefaultsAreWithinTheTurbochargerLimit() {
		assertTrue(Supercharger.DEFAULT_LIMIT <= Supercharger.MAX_LIMIT);
		assertEquals(Turbocharger.MAX_LIMIT, Supercharger.MAX_LIMIT, 0);
	}
}
