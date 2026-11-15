package com.createengines.content;

/**
 * The numbers behind the pushrods drawn beside a cylinder block that a cam works from the side, in pixels (sixteenths of
 * a block) as the models are drawn. Free of the game so they can be tested. The pushrod and rocker models are in the
 * frame of the block: +Y along the bore, +Z towards the cam, centred across the cam.
 */
public final class PushrodGeometry {

	/** How far a pushrod rises at full lift, in pixels. */
	public static final double LIFT_PX = 1.5;
	/** How far from the rocker's pivot the pushrod pushes it, in pixels: the lever the lift works. */
	public static final double CONTACT_PX = 3.1;
	/** Where the intake and the exhaust lobes lie along the cam's axis, in pixels from the block's corner (see the cam model). */
	public static final double INTAKE_AT = 5;
	public static final double EXHAUST_AT = 8;
	/** Where a block's middle is, in pixels, which the models are centred on. */
	private static final double MIDDLE = 8;

	private PushrodGeometry() {
	}

	/** How far, in blocks, a pushrod has risen at {@code lift}, 0 to 1. */
	public static float rise(double lift) {
		return (float) (clamp(lift) * LIFT_PX / 16);
	}

	/**
	 * The angle, in degrees, a rocker is tipped about its pivot when the pushrod under it has risen by {@code lift}, 0 to
	 * 1: the arm's outer end goes up, which about the frame's X axis is a negative turn.
	 */
	public static float rockerAngle(double lift) {
		return (float) -Math.toDegrees(Math.asin(clamp(lift) * LIFT_PX / CONTACT_PX));
	}

	/**
	 * How far, in blocks, to slide a pushrod and its rocker across the block from the middle so they sit over the lobe at
	 * {@code lobeAt} pixels along the cam's axis. {@code alignment} is +1 if the frame's X axis runs along that axis's
	 * positive direction, -1 if against it.
	 */
	public static float across(double lobeAt, int alignment) {
		return (float) (alignment * (lobeAt - MIDDLE) / 16);
	}

	private static double clamp(double lift) {
		return Math.max(0, Math.min(1, lift));
	}
}
