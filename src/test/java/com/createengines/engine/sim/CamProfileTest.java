package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CamProfileTest {

	@Test
	void aValveIsShutOutsideItsWindowAndOpenInsideIt() {
		CamProfile basic = CamProfile.BASIC;
		assertEquals(0, basic.intakeLift(basic.intakeOpen()), 1e-9);
		assertEquals(0, basic.intakeLift(300), 1e-12);
		assertTrue(basic.intakeLift(780) > 0.5);
		assertEquals(0, basic.exhaustLift(200), 1e-12);
		assertTrue(basic.exhaustLift(615) > 0.99);
	}

	@Test
	void liftPeaksAtOneHalfwayThroughTheWindow() {
		CamProfile basic = CamProfile.BASIC;
		double intakeMid = SimMath.wrap(basic.intakeOpen() + 115);
		double exhaustMid = SimMath.wrap(basic.exhaustOpen() + 115);
		assertEquals(1, basic.intakeLift(intakeMid), 1e-9);
		assertEquals(1, basic.exhaustLift(exhaustMid), 1e-9);
	}

	@Test
	void liftIsNeverOutsideZeroToOne() {
		for (CamProfile profile : new CamProfile[] {CamProfile.BASIC, CamProfile.ROTARY}) {
			for (double angle = -720; angle < 1440; angle += 0.37) {
				double intake = profile.intakeLift(angle);
				double exhaust = profile.exhaustLift(angle);
				assertTrue(intake >= 0 && intake <= 1 + 1e-12, "intake at " + angle);
				assertTrue(exhaust >= 0 && exhaust <= 1 + 1e-12, "exhaust at " + angle);
			}
		}
	}

	@Test
	void anAngleAMultipleOfTheCycleAwayReadsTheSame() {
		CamProfile basic = CamProfile.BASIC;
		assertEquals(basic.intakeLift(800), basic.intakeLift(800 + 720), 1e-9);
		assertEquals(basic.exhaustLift(600), basic.exhaustLift(600 - 720), 1e-9);
	}

	@Test
	void theBasicValvesOnlyOverlapAroundTopDeadCentre() {
		CamProfile basic = CamProfile.BASIC;
		boolean overlapAtTdc = basic.intakeLift(5) > 0 && basic.exhaustLift(5) > 0;
		assertTrue(overlapAtTdc, "a short overlap at the end of exhaust");
		assertFalse(basic.intakeLift(300) > 0 && basic.exhaustLift(300) > 0);
		assertFalse(basic.intakeLift(540) > 0 && basic.exhaustLift(540) > 0);
	}

	@Test
	void theRotaryPortsStayOpenLongerThanTheBasicIntake() {
		assertTrue(openDegrees(CamProfile.ROTARY, true) >= openDegrees(CamProfile.BASIC, true));
	}

	private static int openDegrees(CamProfile profile, boolean intake) {
		int open = 0;
		for (int degree = 0; degree < 720; degree++) {
			double lift = intake ? profile.intakeLift(degree + 0.5) : profile.exhaustLift(degree + 0.5);
			if (lift > 0)
				open++;
		}
		return open;
	}

	@Test
	void theSampledCurveMatchesTheFormula() {
		for (CamProfile profile : new CamProfile[] {CamProfile.BASIC, CamProfile.ROTARY}) {
			for (double angle = 0; angle < 720; angle += 0.173) {
				assertEquals(profile.intakeLift(angle), profile.intakeCurve().at(angle), 1e-5, "intake at " + angle);
				assertEquals(profile.exhaustLift(angle), profile.exhaustCurve().at(angle), 1e-5, "exhaust at " + angle);
			}
		}
	}

	@Test
	void theSampledCurveWrapsLikeTheCycle() {
		CamProfile.Curve curve = CamProfile.BASIC.intakeCurve();
		assertEquals(curve.at(800), curve.at(800 + 720), 1e-9);
		assertEquals(curve.at(710.5), curve.at(-9.5), 1e-9);
		assertEquals(curve.at(0), curve.at(720), 1e-9);
	}

	@Test
	void theCurvesAreBuiltOncePerProfile() {
		assertSame(CamProfile.BASIC.intakeCurve(), CamProfile.BASIC.intakeCurve());
		CamProfile same = new CamProfile(710, 220, 500, 10, 345, 350);
		assertSame(CamProfile.BASIC.exhaustCurve(), same.exhaustCurve());
	}

	@Test
	void profilesAreComparedByTheirAngles() {
		assertEquals(CamProfile.BASIC, new CamProfile(710, 220, 500, 10, 345, 350));
		assertFalse(CamProfile.BASIC.equals(CamProfile.ROTARY));
	}

	@Test
	void sparkAndInjectionComeBeforeFiringTopDeadCentre() {
		for (CamProfile profile : new CamProfile[] {CamProfile.BASIC, CamProfile.ROTARY}) {
			assertTrue(profile.injection() < 360);
			assertTrue(profile.spark() < 360);
			assertTrue(profile.injection() <= profile.spark(), "the fuel goes in before the spark");
		}
	}
}
