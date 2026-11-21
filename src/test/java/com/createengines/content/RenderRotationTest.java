package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A crank is drawn from an accumulated angle so a speed change never makes it jump, but the real Create shafts and cogs
 * joined to it are drawn at time x speed and do jump. {@link RenderRotation#alignedAt} must bring the crank back into
 * step with them, to the nearest quarter turn (a shaft looks the same every 90 degrees), jumping only on the frame the
 * speed changes, which is when the shafts jump too.
 */
class RenderRotationTest {

	private static final float TICK = 1f;

	/** Create's own angle for a shaft at this time and speed. */
	private static double create(float time, float speed) {
		return (time * speed * 3f / 10) % 360;
	}

	/** How far an angle is from agreeing with Create's, to the nearest quarter turn, in degrees (0 to 45). */
	private static double offBy(double drawn, float time, float speed) {
		double gap = ((create(time, speed) - drawn) % 90 + 135) % 90 - 45;
		return Math.abs(gap);
	}

	@Test
	void plainAngleNeverFindsCreatesAfterASpeedChange() {
		RenderRotation rotation = new RenderRotation();
		float time = 100_000;
		rotation.at(time, 20);
		double drawn = 0;
		// The speed changes once, as an engine's does, and Create's shaft jumps to a new angle where the crank does not
		for (int i = 0; i < 600; i++) {
			time += TICK;
			drawn = rotation.at(time, i < 3 ? 20 : 21.7f);
		}
		assertTrue(offBy(drawn, time, 21.7f) > 1, "the unaligned angle is where it is left: " + offBy(drawn, time, 21.7f));
	}

	@Test
	void alignedAngleFindsCreatesQuarterTurnAfterASpeedChange() {
		RenderRotation rotation = new RenderRotation();
		float time = 100_000;
		rotation.alignedAt(time, 20);
		double drawn = 0;
		for (int i = 0; i < 600; i++) {
			time += TICK;
			drawn = rotation.alignedAt(time, i < 3 ? 20 : 21.7f);
		}
		assertTrue(offBy(drawn, time, 21.7f) < 0.05, "off by " + offBy(drawn, time, 21.7f));
	}

	@Test
	void alignedAngleSettlesWhateverTheSpeedAndDirection() {
		for (float speed : new float[] { 1, 8, 64, 256, -8, -64, -256 }) {
			RenderRotation rotation = new RenderRotation();
			float time = 5_000;
			rotation.alignedAt(time, speed / 2);
			double drawn = 0;
			for (int i = 0; i < 900; i++) {
				time += TICK;
				drawn = rotation.alignedAt(time, speed);
			}
			// Float arithmetic on a large time x speed is coarse, so allow what one ulp of Create's own angle is
			double slack = 0.05 + Math.ulp((float) (time * Math.abs(speed) * 0.3)) * 2;
			assertTrue(offBy(drawn, time, speed) < slack, "speed " + speed + " off by " + offBy(drawn, time, speed));
		}
	}

	@Test
	void aStoppedCrankSettlesSquareToTheShaftsAround() {
		RenderRotation rotation = new RenderRotation();
		float time = 1_000;
		rotation.alignedAt(time, 30);
		for (int i = 0; i < 40; i++) {
			time += TICK;
			rotation.alignedAt(time, 30);
		}
		double drawn = 0;
		for (int i = 0; i < 900; i++) {
			time += TICK;
			drawn = rotation.alignedAt(time, 0);
		}
		// Create draws a stopped shaft at no turn at all, which is any multiple of a quarter turn
		assertTrue(offBy(drawn, time, 0) < 0.05, "off by " + offBy(drawn, time, 0));
	}

	@Test
	void theCrankIsSquareToTheShaftsInTheVeryFrameTheirSpeedChanges() {
		RenderRotation rotation = new RenderRotation();
		float time = 100_000;
		float speed = 20;
		rotation.alignedAt(time, speed);
		float[] speeds = { 20, 21.7f, 25, 24, 40, 39, 12, 12, -12, 64 };
		for (int i = 0; i < 400; i++) {
			time += TICK;
			speed = speeds[(i / 5) % speeds.length];
			double drawn = rotation.alignedAt(time, speed);
			// Create jumps its shafts to time x speed on the frame the speed changes, and so must the crank: a gap of any
			// size would show as the two ends of a joint turning out of step until it eased shut
			double slack = 0.05 + Math.ulp((float) (time * Math.abs(speed) * 0.3)) * 2;
			assertTrue(offBy(drawn, time, speed) < slack, "frame " + i + " off by " + offBy(drawn, time, speed));
		}
	}

	@Test
	void aFrameNeverMovesTheAngleMoreThanItsSpeedPlusTheNudgeUnlessTheSpeedChanged() {
		RenderRotation rotation = new RenderRotation();
		float time = 100_000;
		rotation.alignedAt(time, 12);
		double last = rotation.alignedAt(time, 12);
		float[] speeds = { 12, 12, 40, 40, 3, -15, -15, 0, 90, 90, 90 };
		float lastSpeed = 12;
		for (int i = 0; i < 400; i++) {
			float speed = speeds[(i / 7) % speeds.length];
			float before = time;
			time += 0.4f + (i % 5) * 0.35f;
			// The gap the rotation sees is the difference of two coarse floats, not the step added
			float gap = time - before;
			double now = rotation.alignedAt(time, speed);
			double expected = gap * speed * 3d / 10;
			// A speed change jumps the crank with the shafts, by up to an eighth of a turn; every other frame is only a nudge
			double allowed = speed != lastSpeed ? 45 : RenderRotation.ALIGN_MAX_PER_TICK * gap;
			assertTrue(Math.abs(now - last - expected) <= allowed + 1e-9,
				"frame " + i + " jumped " + (now - last - expected) + " beyond its speed");
			last = now;
			lastSpeed = speed;
		}
	}

	@Test
	void callingTwiceInAFrameDoesNotTurnItTwice() {
		RenderRotation rotation = new RenderRotation();
		rotation.alignedAt(1000, 50);
		double first = rotation.alignedAt(1001, 50);
		assertEquals(first, rotation.alignedAt(1001, 50));
		assertEquals(first, rotation.at(1001, 50));
	}

	@Test
	void anAngleAlreadySquareIsLeftAlone() {
		assertEquals(0, RenderRotation.nudge(37, 37 + 90 * 5, 1), 1e-9);
		assertEquals(0, RenderRotation.nudge(-20, 70, 1), 1e-9);
	}

	@Test
	void theNudgeTakesTheShorterWayRoundAndIsBounded() {
		// 10 degrees behind: forward. 10 degrees ahead: back. 80 degrees behind is 10 degrees ahead of the next quarter turn
		assertTrue(RenderRotation.nudge(10, 0, 1) > 0);
		assertTrue(RenderRotation.nudge(0, 10, 1) < 0);
		assertTrue(RenderRotation.nudge(80, 0, 1) < 0);
		assertTrue(RenderRotation.nudge(-80, 0, 1) > 0);
		for (double gap = -400; gap < 400; gap += 3.7)
			assertTrue(Math.abs(RenderRotation.nudge(gap, 0, 1)) <= RenderRotation.ALIGN_MAX_PER_TICK + 1e-9);
		assertTrue(Math.abs(RenderRotation.nudge(45, 0, 20)) <= RenderRotation.ALIGN_MAX_PER_TICK * 20 + 1e-9);
	}
}
