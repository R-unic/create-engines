package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The two ways a crank sets a cylinder's place in its cycle, now one seam instead of a flag tested on every step. */
class CycleDriveTest {

	@Test
	void aPistonFollowsTheCrankAngleFromItsOwnTopDeadCentre() {
		CycleDrive drive = CycleDrive.PISTON;
		assertEquals(SimMath.wrap(100 - 90), drive.cycleAngle(100, 5, 1, 90), 0);
		assertEquals(SimMath.wrap(100 + 90), drive.cycleAngle(100, 5, -1, 90), 0);
		assertEquals(1, drive.scale(), 0);
		assertEquals(SimMath.CYCLE, drive.cycleDegrees(), 0);
		assertFalse(drive.ported());
	}

	@Test
	void aPistonIsTimedOnlyByACamshaftAndLockedOnlyWhenItIsGearedToTheCrank() {
		CycleDrive drive = CycleDrive.PISTON;
		assertFalse(drive.timed(null));
		assertFalse(drive.phaseLocked(null));
		Camshaft cam = new Camshaft();
		assertTrue(drive.timed(cam));
		assertEquals(cam.isPhaseLocked(), drive.phaseLocked(cam));
	}

	@Test
	void aRotaryChamberFollowsTheShaftOverThreeTurnsAtTwoThirdsOfTheAngle() {
		CycleDrive.RotaryDrive drive = new CycleDrive.RotaryDrive(0);
		assertTrue(drive.ported());
		assertEquals(CylinderModel.ROTARY_SCALE, drive.scale(), 0);
		assertEquals(CylinderModel.ROTARY_CYCLE, drive.cycleDegrees(), 0);
		// The ignored plain rotor angle: only the rotary angle matters
		assertEquals(SimMath.wrap(600 * CylinderModel.ROTARY_SCALE), drive.cycleAngle(123, 600, 1, 0), 1e-9);
		assertTrue(drive.timed(null));
		assertTrue(drive.phaseLocked(null));
	}

	@Test
	void theThreeChambersOfARotorAreOneThirdOfTheirCycleApart() {
		CylinderModel a = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 0);
		CylinderModel b = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 1);
		CylinderModel c = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 2);
		double step = CylinderModel.ROTARY_CHAMBER_SPACING * CylinderModel.ROTARY_SCALE;
		assertEquals(SimMath.wrap(a.pistonAngle(10, 10, 1) + step), b.pistonAngle(10, 10, 1), 1e-9);
		assertEquals(SimMath.wrap(a.pistonAngle(10, 10, 1) + 2 * step), c.pistonAngle(10, 10, 1), 1e-9);
	}

	@Test
	void aCopyKeepsHowItsCylinderIsDriven() {
		CylinderModel chamber = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 30, 1);
		CylinderModel copy = chamber.copyOnto(null);
		assertTrue(copy.ported());
		assertEquals(chamber.cycleDegrees(), copy.cycleDegrees(), 0);
		assertEquals(chamber.pistonAngle(200, 700, 1), copy.pistonAngle(200, 700, 1), 0);
		CylinderModel piston = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		assertFalse(piston.copyOnto(null).ported());
	}

	@Test
	void aCylinderReportsItsDrivesScaleAndWhetherItFired() {
		CylinderModel piston = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		CylinderModel chamber = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfile.ROTARY, 0, 0);
		assertEquals(1, piston.angleScale(), 0);
		assertEquals(CylinderModel.ROTARY_SCALE, chamber.angleScale(), 0);
		assertEquals(SimMath.CYCLE, piston.cycleDegrees(), 0);
		assertEquals(CylinderModel.ROTARY_CYCLE, chamber.cycleDegrees(), 0);
		assertFalse(piston.fired(), "a cylinder that has not run has not fired");
		assertFalse(chamber.fired());
	}
}
