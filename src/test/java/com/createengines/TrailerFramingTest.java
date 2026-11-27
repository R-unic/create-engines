package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.client.ShowcaseRunFraming;
import com.createengines.client.ShowcaseRunFraming.Box;
import com.createengines.client.ShowcaseRunFraming.Placing;

/**
 * The trailer's camera is placed by sum, not by hand: from every side, a subject lies wholly inside the frame's safe area (clear
 * of the letterbox and the caption) and reaches its edge, so no shot crops its subject or leaves it small in the middle.
 */
class TrailerFramingTest {

	private static final double EPSILON = 1e-6;
	private static final Box[] BOXES = { new Box(0, 1, 2, 14, 7.5, 7.5), new Box(5, 0, 2, 10, 9, 6), new Box(8, 1.5, 3.5, 11, 5, 7),
		new Box(-6, 0, 3, 19, 8, 8) };

	/** The box's reach in the frame: {least x, most x, least y, most y}, where 1 is the edge of the screen. */
	private static double[] reach(Box box, Placing placing) {
		double[] f = { placing.aimX() - placing.eyeX(), placing.aimY() - placing.eyeY(), placing.aimZ() - placing.eyeZ() };
		normalise(f);
		double[] r = { -f[2], 0, f[0] };
		normalise(r);
		double[] u = { r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0] };
		double tanUp = Math.tan(Math.toRadians(ShowcaseRunFraming.FOV / 2));
		double[] out = { 9, -9, 9, -9 };
		for (int corner = 0; corner < 8; corner++) {
			double[] q = { ((corner & 1) == 0 ? box.x0() : box.x1()) - placing.eyeX(),
				((corner & 2) == 0 ? box.y0() : box.y1()) - placing.eyeY(), ((corner & 4) == 0 ? box.z0() : box.z1()) - placing.eyeZ() };
			double depth = q[0] * f[0] + q[1] * f[1] + q[2] * f[2];
			assertTrue(depth > 0, "a corner is behind the camera");
			double x = (q[0] * r[0] + q[1] * r[1] + q[2] * r[2]) / (depth * tanUp * ShowcaseRunFraming.ASPECT);
			double y = (q[0] * u[0] + q[1] * u[1] + q[2] * u[2]) / (depth * tanUp);
			out[0] = Math.min(out[0], x);
			out[1] = Math.max(out[1], x);
			out[2] = Math.min(out[2], y);
			out[3] = Math.max(out[3], y);
		}
		return out;
	}

	private static void normalise(double[] v) {
		double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		for (int i = 0; i < 3; i++)
			v[i] /= length;
	}

	@Test
	void theSubjectFillsTheSafeAreaWithoutLeavingIt() {
		double low = ShowcaseRunFraming.SAFE_RAISE - ShowcaseRunFraming.SAFE_HALF_HEIGHT;
		double high = ShowcaseRunFraming.SAFE_RAISE + ShowcaseRunFraming.SAFE_HALF_HEIGHT;
		double wide = ShowcaseRunFraming.SAFE_HALF_WIDTH;
		for (Box box : BOXES)
			for (int yaw = -180; yaw < 180; yaw += 15)
				for (int pitch = -10; pitch <= 40; pitch += 10) {
					double[] got = reach(box, ShowcaseRunFraming.place(box, yaw, pitch, 1));
					String where = box + " from yaw " + yaw + " pitch " + pitch;
					assertTrue(got[0] >= -wide - EPSILON && got[1] <= wide + EPSILON, "cropped at a side: " + where);
					assertTrue(got[2] >= low - EPSILON && got[3] <= high + EPSILON, "cropped top or bottom: " + where);
					assertTrue(got[0] <= -wide + EPSILON || got[1] >= wide - EPSILON || got[2] <= low + EPSILON
						|| got[3] >= high - EPSILON, "stands further back than it needs to: " + where);
				}
	}

	@Test
	void theSafeAreaClearsTheLetterboxAndTheCaption() {
		// post.py: 104 pixels of black at the top and the bottom of 1080, a caption about 80 pixels tall 28 above the lower bar
		double pixel = 2.0 / 1080;
		assertTrue(ShowcaseRunFraming.SAFE_RAISE + ShowcaseRunFraming.SAFE_HALF_HEIGHT < 1 - 104 * pixel);
		assertTrue(ShowcaseRunFraming.SAFE_RAISE - ShowcaseRunFraming.SAFE_HALF_HEIGHT > -1 + (104 + 28 + 80) * pixel);
	}

	@Test
	void aSmallerFillStandsFurtherBack() {
		Box box = BOXES[0];
		Placing near = ShowcaseRunFraming.place(box, -50, 12, 1);
		Placing far = ShowcaseRunFraming.place(box, -50, 12, 0.7);
		assertTrue(distance(far) > distance(near));
		assertEquals(-50, Math.toDegrees(Math.atan2(far.eyeX() - far.aimX(), far.aimZ() - far.eyeZ())), EPSILON);
	}

	@Test
	void aMovingBoxStartsAndEndsOnItsTwoBoxesAndPassesHalfWayBetween() {
		Box from = new Box(7, 5, 6, 8, 6, 7);
		Box to = new Box(0.5, 1, 2.5, 9, 7, 7.5);
		assertEquals(from, ShowcaseRunFraming.between(from, to, 0));
		assertEquals(to, ShowcaseRunFraming.between(from, to, 1));
		assertEquals(new Box(3.75, 3, 4.25, 8.5, 6.5, 7.25), ShowcaseRunFraming.between(from, to, 0.5));
	}

	/** The goggle shot: the camera is as far as asked, from the side asked, and the box's middle is dead ahead. */
	@Test
	void aShotAtReachStandsThatFarOffAndLooksAtTheMiddleOfTheBox() {
		Box block = new Box(1, 3, 5, 2, 4, 6);
		Placing placing = ShowcaseRunFraming.atDistance(block, -34, 4, 3.6);
		assertEquals(3.6, distance(placing), EPSILON);
		assertEquals(1.5, placing.aimX(), EPSILON);
		assertEquals(3.5, placing.aimY(), EPSILON);
		assertEquals(5.5, placing.aimZ(), EPSILON);
		assertEquals(-34, Math.toDegrees(Math.atan2(placing.eyeX() - placing.aimX(), placing.aimZ() - placing.eyeZ())), EPSILON);
		assertTrue(placing.eyeY() > placing.aimY(), "a positive pitch looks down on it");
	}

	private static double distance(Placing p) {
		return Math.hypot(Math.hypot(p.eyeX() - p.aimX(), p.eyeY() - p.aimY()), p.eyeZ() - p.aimZ());
	}
}
