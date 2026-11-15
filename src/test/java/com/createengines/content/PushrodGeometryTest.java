package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A pushrod rises with its valve's lift, and the rocker it pushes tips the way that raises its outer end. */
class PushrodGeometryTest {

	@Test
	void aPushrodRestsAtNoLiftAndRisesByTheCamsStrokeAtFull() {
		assertEquals(0, PushrodGeometry.rise(0));
		assertEquals(PushrodGeometry.LIFT_PX / 16, PushrodGeometry.rise(1), 1e-6);
		assertEquals(PushrodGeometry.rise(1), PushrodGeometry.rise(7), "a lift past full is clamped");
		assertEquals(0, PushrodGeometry.rise(-1), "and so is one below none");
	}

	@Test
	void theRockerTipsFartherTheMoreThePushrodRisesAndRaisesItsOuterEnd() {
		assertEquals(0, PushrodGeometry.rockerAngle(0), 1e-9);
		float half = PushrodGeometry.rockerAngle(0.5);
		float full = PushrodGeometry.rockerAngle(1);
		assertTrue(half < 0 && full < half, "negative about the frame's X axis, and more so at full lift");
		// The arm's outer end, one contact distance out from the pivot, is raised by exactly what the pushrod rose
		double raised = PushrodGeometry.CONTACT_PX * Math.sin(Math.toRadians(-full));
		assertEquals(PushrodGeometry.LIFT_PX, raised, 1e-4);
		assertTrue(-full < 45, "never so far that the arm looks broken");
	}

	@Test
	void eachPushrodSitsOverItsOwnLobeWhicheverWayTheFrameFaces() {
		assertEquals(0, PushrodGeometry.across(PushrodGeometry.EXHAUST_AT, 1) + PushrodGeometry.across(PushrodGeometry.EXHAUST_AT, -1));
		assertEquals(-PushrodGeometry.across(PushrodGeometry.INTAKE_AT, 1), PushrodGeometry.across(PushrodGeometry.INTAKE_AT, -1));
		assertEquals(-3f / 16, PushrodGeometry.across(PushrodGeometry.INTAKE_AT, 1), 1e-6);
		assertTrue(Math.abs(PushrodGeometry.across(PushrodGeometry.INTAKE_AT, 1)
			- PushrodGeometry.across(PushrodGeometry.EXHAUST_AT, 1)) >= 2.5f / 16, "the two rods do not touch");
	}
}
