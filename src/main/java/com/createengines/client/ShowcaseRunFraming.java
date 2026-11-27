package com.createengines.client;

/**
 * Where the trailer's camera stands to compose a shot: given the box round the subject and the direction it is seen from, the
 * nearest place from which the whole box lies inside the frame's safe area, centred. The safe area is what is left of a 16:9
 * frame once tools/trailer/post.py has drawn its 2.2:1 letterbox and put a caption along the bottom, so the subject sits a
 * little above the middle. Plain arithmetic with no Minecraft in it, so it is tested without the game.
 */
public final class ShowcaseRunFraming {

	/** The vertical field of view the trailer is filmed with, in degrees (the run fixes it, so the sums here hold). */
	public static final double FOV = 60;
	/** The frame's shape. */
	public static final double ASPECT = 16.0 / 9.0;
	/** Half the safe area's width, in the frame's own units (1 is the edge of the screen). */
	public static final double SAFE_HALF_WIDTH = 0.84;
	/** Half the safe area's height, and how far its middle is above the middle of the frame. */
	public static final double SAFE_HALF_HEIGHT = 0.62;
	public static final double SAFE_RAISE = 0.08;

	/** A box round a subject, in blocks. */
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
	}

	/** The camera's eye and the point in the middle of its view. */
	public record Placing(double eyeX, double eyeY, double eyeZ, double aimX, double aimY, double aimZ) {
	}

	private ShowcaseRunFraming() {
	}

	/**
	 * Places the camera.
	 *
	 * @param box   what must be in the frame
	 * @param yaw   the side it is seen from, in degrees: 0 is from the north, 90 from the east
	 * @param pitch how far above it the camera is, in degrees
	 * @param fill  how much of the safe area the box takes up: 1 touches its edge, less stands further back
	 */
	public static Placing place(Box box, double yaw, double pitch, double fill) {
		double a = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		double sin = Math.sin(a);
		double cos = Math.cos(a);
		// From the subject to the camera, and the camera's own right and up
		double[] back = { sin * Math.cos(p), Math.sin(p), -cos * Math.cos(p) };
		double[] right = { -cos, 0, -sin };
		double[] up = { -sin * Math.sin(p), Math.cos(p), cos * Math.sin(p) };
		double tanUp = Math.tan(Math.toRadians(FOV / 2));
		double tanAcross = tanUp * ASPECT;
		double halfWidth = SAFE_HALF_WIDTH * fill;
		double halfHeight = SAFE_HALF_HEIGHT * fill;
		double cx = (box.x0() + box.x1()) / 2;
		double cy = (box.y0() + box.y1()) / 2;
		double cz = (box.z0() + box.z1()) / 2;
		double distance = 0;
		for (int corner = 0; corner < 8; corner++) {
			double[] q = { ((corner & 1) == 0 ? box.x0() : box.x1()) - cx, ((corner & 2) == 0 ? box.y0() : box.y1()) - cy,
				((corner & 4) == 0 ? box.z0() : box.z1()) - cz };
			double across = dot(q, right);
			double high = dot(q, up);
			// How far past the box's middle the corner is, seen from the camera
			double deeper = -dot(q, back);
			distance = Math.max(distance, Math.abs(across) / (halfWidth * tanAcross) - deeper);
			// The camera is dropped by SAFE_RAISE of the half frame at the subject's distance, which lifts the subject in it
			distance = Math.max(distance, (high / tanUp - (SAFE_RAISE + halfHeight) * deeper) / halfHeight);
			distance = Math.max(distance, (-high / tanUp + (SAFE_RAISE - halfHeight) * deeper) / halfHeight);
		}
		double drop = SAFE_RAISE * distance * tanUp;
		double ax = cx - up[0] * drop;
		double ay = cy - up[1] * drop;
		double az = cz - up[2] * drop;
		return new Placing(ax + back[0] * distance, ay + back[1] * distance, az + back[2] * distance, ax, ay, az);
	}

	/** The box {@code t} of the way (0 to 1) from one box to another, corner by corner. */
	public static Box between(Box from, Box to, double t) {
		return new Box(mix(from.x0(), to.x0(), t), mix(from.y0(), to.y0(), t), mix(from.z0(), to.z0(), t),
			mix(from.x1(), to.x1(), t), mix(from.y1(), to.y1(), t), mix(from.z1(), to.z1(), t));
	}

	private static double mix(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/**
	 * Places the camera a set {@code distance} from the middle of the box, looking straight at it, so that point is under the
	 * crosshair: for a shot that reads a block through goggles, which only reach so far.
	 */
	public static Placing atDistance(Box box, double yaw, double pitch, double distance) {
		double a = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		double x = (box.x0() + box.x1()) / 2;
		double y = (box.y0() + box.y1()) / 2;
		double z = (box.z0() + box.z1()) / 2;
		return new Placing(x + Math.sin(a) * Math.cos(p) * distance, y + Math.sin(p) * distance,
			z - Math.cos(a) * Math.cos(p) * distance, x, y, z);
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}
}
