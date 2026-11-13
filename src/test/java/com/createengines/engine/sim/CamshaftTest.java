package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CamshaftTest {

	@Test
	void aNewCamshaftIsGearedForAFourStroke() {
		Camshaft cam = new Camshaft();
		assertEquals(Camshaft.FOUR_STROKE_RATIO, cam.ratio, 0);
		assertTrue(cam.isPhaseLocked());
		assertEquals(0, cam.angle(), 0);
	}

	@Test
	void aRatioNearlyRightIsStillLockedButOneClearlyOffIsNot() {
		Camshaft cam = new Camshaft();
		cam.ratio = 0.503;
		assertTrue(cam.isPhaseLocked());
		cam.ratio = 0.497;
		assertTrue(cam.isPhaseLocked());
		cam.ratio = 0.51;
		assertFalse(cam.isPhaseLocked());
		cam.ratio = 1;
		assertFalse(cam.isPhaseLocked());
		cam.ratio = 0;
		assertFalse(cam.isPhaseLocked());
	}

	@Test
	void aLockedCamFollowsTheCrankAtHalfItsAngle() {
		Camshaft cam = new Camshaft();
		cam.sync(500);
		assertEquals(250, cam.angle(), 1e-12);
		cam.sync(30);
		assertEquals(15, cam.angle(), 1e-12);
	}

	@Test
	void anUnlockedCamIgnoresTheSync() {
		Camshaft cam = new Camshaft();
		cam.ratio = 1;
		cam.angle = 42;
		cam.sync(500);
		assertEquals(42, cam.angle(), 0);
	}

	@Test
	void advancingTurnsTheCamByTheRatio() {
		Camshaft cam = new Camshaft();
		cam.advance(100);
		assertEquals(50, cam.angle(), 1e-12);
		cam.ratio = 1;
		cam.advance(10);
		assertEquals(60, cam.angle(), 1e-12);
	}

	@Test
	void theCamAngleWrapsAtATurn() {
		Camshaft cam = new Camshaft();
		cam.advance(720);
		assertEquals(0, cam.angle(), 1e-12);
		cam.advance(750);
		assertEquals(15, cam.angle(), 1e-12);
	}

	@Test
	void aDriftingCamMovesAwayFromTheLockedPhase() {
		Camshaft locked = new Camshaft();
		Camshaft drifting = new Camshaft();
		drifting.ratio = 0.6;
		double crank = 0;
		for (int i = 0; i < 100; i++) {
			crank += 5;
			locked.advance(5);
			drifting.advance(5);
		}
		assertEquals(crank / 2, locked.angle(), 1e-9);
		assertEquals((crank * 0.6) % 360, drifting.angle(), 1e-9);
		assertTrue(Math.abs(drifting.angle() - locked.angle()) > 1);
	}

	@Test
	void aReverseRunningCamStillWrapsIntoAFullTurn() {
		Camshaft cam = new Camshaft();
		cam.ratio = 1;
		cam.advance(-30);
		assertEquals(330, cam.angle(), 1e-12);
	}
}
