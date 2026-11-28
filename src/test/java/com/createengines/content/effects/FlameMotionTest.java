package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlameMotionTest {

	@Test
	void flameAndSparkCoolFromPaleYellowToDarkRed() {
		for (boolean spark : new boolean[] {false, true}) {
			float[] last = null;
			for (int i = 0; i <= 20; i++) {
				float life = i / 20f;
				float[] colour = spark ? FlameMotion.sparkColour(life) : FlameMotion.flameColour(life);
				for (float part : colour)
					assertTrue(part >= 0 && part <= 1);
				assertTrue(colour[0] >= colour[1] && colour[1] >= colour[2], "warm all the way");
				if (last != null)
					assertTrue(colour[1] <= last[1] + 1e-6f, "never brightens again");
				last = colour;
			}
		}
		assertTrue(FlameMotion.flameColour(0)[1] > 0.9f);
		assertTrue(FlameMotion.flameColour(1)[0] < 0.4f, "ends as soot");
		assertArrayEquals(FlameMotion.sparkColour(1), FlameMotion.sparkColour(7), "clamped");
		assertArrayEquals(FlameMotion.sparkColour(0), FlameMotion.sparkColour(-1));
	}

	@Test
	void speedDrawsAStreakOutWithinBounds() {
		assertEquals(0.1, FlameMotion.tongueHalfLength(0.1, 0), 1e-9);
		assertTrue(FlameMotion.tongueHalfLength(0.1, 0.2) > FlameMotion.tongueHalfLength(0.1, 0.1));
		assertEquals(0.3, FlameMotion.tongueHalfLength(0.1, 5), 1e-9);
		assertEquals(0.02, FlameMotion.sparkHalfLength(0.02, 0), 1e-9);
		assertTrue(FlameMotion.sparkHalfLength(0.02, 0.2) > FlameMotion.sparkHalfLength(0.02, 0.1));
		assertEquals(0.14, FlameMotion.sparkHalfLength(0.02, 5), 1e-9);
	}

	@Test
	void aStreakLiesAlongItsMotionAndFacesTheCamera() {
		double[][] axes = FlameMotion.streakAxes(new double[] {0.2, 0, 0}, new double[] {0, 0, 3}, 0.5, 0.1);
		assertNotNull(axes);
		assertArrayEquals(new double[] {0.5, 0, 0}, axes[0], 1e-9);
		assertEquals(0.1, Math.abs(axes[1][1]), 1e-9);
		assertEquals(0, axes[1][0], 1e-9);
		assertEquals(0, axes[1][2], 1e-9);
	}

	@Test
	void aStillOrHeadOnStreakHasNoAxes() {
		assertNull(FlameMotion.streakAxes(new double[] {0, 0, 0}, new double[] {0, 0, 3}, 0.5, 0.1));
		assertNull(FlameMotion.streakAxes(new double[] {0, 0, 0.2}, new double[] {0, 0, 3}, 0.5, 0.1));
		assertNull(FlameMotion.streakAxes(new double[] {0.02, 0, 0.2}, new double[] {0, 0, 3}, 0.5, 0.1));
		assertNull(FlameMotion.streakAxes(new double[] {0.2, 0, 0}, new double[] {0, 0, 0}, 0.5, 0.1), "camera on it");
	}

	@Test
	void aSparkBouncesOffWhatStopsItAndSkidsAlong() {
		double[] free = {0.1, -0.2, 0.05};
		assertArrayEquals(free, FlameMotion.bounce(free, free.clone()), 1e-9);
		double[] landed = FlameMotion.bounce(free, new double[] {0.1, -0.05, 0.05});
		assertTrue(landed[1] > 0 && landed[1] < 0.2, "up again, slower");
		assertTrue(landed[0] > 0 && landed[0] < 0.1, "skids");
	}
}
