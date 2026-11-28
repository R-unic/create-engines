package com.createengines.content;

/**
 * The boxes the outline and collision shapes of the oddly shaped parts are made of, in pixels, with no Minecraft in
 * sight so a plain unit test can hold them against the models ({@code HitboxFitTest}). {@link PartShapes} turns them into
 * shapes. Each is a list of {@code {x1, y1, z1, x2, y2, z2}}, drawn for the model as it is in its json: facing up, or
 * along Y.
 */
public final class PartBoxes {

	private PartBoxes() {
	}

	/** Facings in the order of {@code net.minecraft.core.Direction}, so an index is the direction's ordinal. */
	public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

	/** Alternator: the drum, its end plates and posts with the shaft through, and the terminals on one side. */
	public static final double[][] ALTERNATOR = {
		{1.5, 0, 1.5, 14.5, 16, 14.5},
		{0.5, 4.5, 7, 2, 6, 9},
		{14, 4.5, 7, 15.5, 6, 9},
		{14.25, 9.75, 5.5, 15.75, 12.25, 10.5}};

	/** Black box: the case with its lid, and the carrying handle on top. */
	public static final double[][] BLACK_BOX = {
		{0.5, 0, 0.2, 15.5, 10, 15.5},
		{5, 10, 7, 11, 12.5, 9}};

	/** Governor: the weighted body with its gear ornaments, and the thin spindle that stands out of it. */
	public static final double[][] GOVERNOR = {
		{1, 0, 1, 15, 12.6, 15},
		{6.4, 12.6, 6.4, 9.6, 15.6, 9.6}};

	/** Flare stack: the base plate and the stack, which is much narrower. */
	public static final double[][] FLARE_STACK = {
		{3, 0, 3, 13, 2, 13},
		{5, 2, 5, 11, 16, 11}};

	/** Starter: the base and motor, the flanged drive plate and mounting lug, and the drive stub. Output up. */
	public static final double[][] STARTER = {
		{2.75, 0, 2.75, 13.25, 10, 13.25},
		{5, 10, 5, 11, 12, 11},
		{1, 10, 6.5, 15, 12, 9.5},
		{12, 3, 5.5, 15.5, 9, 10.5},
		{5, 12, 5, 11, 16, 11}};

	/** Air intake: the plate it stands on, the neck, the bell's two steps and the rim. Mouth up. */
	public static final double[][] AIR_INTAKE = {
		{3, 0, 3, 13, 2, 13},
		{4, 2, 4, 12, 8, 12},
		{3, 8, 3, 13, 10, 13},
		{2.25, 10, 2.25, 13.75, 12, 13.75},
		{1.5, 12, 1.5, 14.5, 14, 14.5}};

	/** Exhaust outlet: the plate, the pipe with the band round it, the collar and the flared rim. Mouth up. */
	public static final double[][] EXHAUST_OUTLET = {
		{3, 0, 3, 13, 1.5, 13},
		{5.5, 1.5, 5.5, 10.5, 12, 10.5},
		{5, 3, 5, 11, 8, 11},
		{4, 12, 4, 12, 15, 12},
		{3.5, 15, 3.5, 12.5, 16, 12.5}};

	/** Turbocharger: the two housings, the narrow waist between them, and the stub that stands out of the waist. */
	public static final double[][] TURBOCHARGER = {
		{1, 0, 1, 15, 6, 15},
		{3.5, 6, 3.5, 12.5, 10, 12.5},
		{11.5, 7, 7, 13.5, 10.5, 9},
		{1, 10, 1, 15, 16, 15}};

	/** Variable turbocharger: the turbocharger, with the vane actuator on the side of the lower housing. */
	public static final double[][] VARIABLE_TURBOCHARGER = {
		{1, 0, 1, 15, 6, 15},
		{3.5, 6, 3.5, 12.5, 10, 12.5},
		{11.5, 7, 7, 13.5, 10.5, 9},
		{1, 10, 1, 15, 16, 15},
		{13.5, 1.5, 6, 16, 4.5, 10}};

	/** Supercharger: the case, and the pipe flange on each of the four faces round the shaft. Shaft along Y. */
	public static final double[][] SUPERCHARGER = {
		{1, 0, 1, 15, 16, 15},
		{6, 4, 0, 10, 12, 16},
		{0, 4, 6, 16, 12, 10}};

	/** Variable supercharger: the supercharger, with its drive actuator beside one flange. */
	public static final double[][] VARIABLE_SUPERCHARGER = {
		{1, 0, 1, 15, 16, 15},
		{6, 4, 0, 10, 12, 16},
		{0, 4, 6, 16, 12, 10},
		{1, 6.5, 14.5, 4.5, 9.5, 16}};

	/** Air filter: the two end plates and the drum between them. Pipes along Y. */
	public static final double[][] AIR_FILTER = {
		{2, 0, 2, 14, 2, 14},
		{3, 2, 3, 13, 14, 13},
		{2, 14, 2, 14, 16, 14}};

	/** Intercooler: the two end tanks and the core between them. Pipes along Y. */
	public static final double[][] INTERCOOLER = {
		{2, 0, 2, 14, 3, 14},
		{3, 3, 3, 13, 13, 13},
		{2, 13, 2, 14, 16, 14}};

	/** Muffler and soot trap: the canister, and at each end the neck and the pipe flange. Pipes along Y. */
	public static final double[][] EXHAUST_CANISTER = {
		{3.5, 0, 3.5, 12.5, 1.5, 12.5},
		{5, 1.5, 5, 11, 2.5, 11},
		{2, 2.5, 2, 14, 13.5, 14},
		{5, 13.5, 5, 11, 14.5, 11},
		{3.5, 14.5, 3.5, 12.5, 16, 12.5}};

	/** Cam lobe's swept lobes (the block adds the shaft through them). */
	public static final double[][] CAM_LOBE = {{2.5, 3.5, 2.5, 13.5, 12.5, 13.5}};

	/** Crank throw's swinging web as the disc it sweeps (the block adds the shaft through it). */
	public static final double[][] CRANK_THROW = {{1, 3, 1, 15, 13, 15}};

	/** Throttle body, stood on the manifold's core: the butterfly housing and the linkage above it. */
	public static final double[][] THROTTLE_BODY = {
		{4, 4, 4, 12, 12, 12},
		{6, 12, 7, 12, 15, 9},
		{8.5, 12, 9.5, 11.5, 13.5, 11.5}};

	/** Mass airflow sensor body: the tube, the mount on it and the connector that stands out of that. */
	public static final double[][] MAF_BODY = {
		{4, 4, 4, 12, 12, 12},
		{5, 12, 5, 11, 14, 11},
		{6, 14, 8, 10, 16, 12.5}};

	/** Heat exchanger body: the drum, its two fins and the cap on top. */
	public static final double[][] EXCHANGER_BODY = {
		{4, 4, 4, 12, 12, 12},
		{2.5, 5, 2.5, 13.5, 10, 13.5},
		{2, 12, 2, 14, 15.5, 14}};

	/**
	 * The boxes drawn facing up, turned to {@code facing}. The turns are the ones the part's blockstate gives its model
	 * ({@code x=90} for north, with {@code y} 90, 180 and 270 for east, south and west, {@code x=180} for down), about the
	 * middle of the block, so the shape lies where the model does.
	 */
	public static double[][] directional(int facing, double[][] up) {
		double[][] out = new double[up.length][];
		for (int i = 0; i < up.length; i++) {
			double[] lo = turn(facing, up[i][0], up[i][1], up[i][2]);
			double[] hi = turn(facing, up[i][3], up[i][4], up[i][5]);
			out[i] = new double[] {Math.min(lo[0], hi[0]), Math.min(lo[1], hi[1]), Math.min(lo[2], hi[2]),
				Math.max(lo[0], hi[0]), Math.max(lo[1], hi[1]), Math.max(lo[2], hi[2])};
		}
		return out;
	}

	/**
	 * The boxes drawn along Y, laid along {@code axis} (0 x, 1 y, 2 z) with the turns a rotated-pillar blockstate gives its
	 * model: {@code x=90, y=90} for x and {@code x=90, y=180} for z, the same as facing east and south.
	 */
	public static double[][] axial(int axis, double[][] y) {
		return directional(axis == 0 ? EAST : axis == 2 ? SOUTH : UP, y);
	}

	/** The smallest box around all of these. */
	public static double[] hull(double[][] boxes) {
		double[] hull = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
			-Double.MAX_VALUE};
		for (double[] b : boxes)
			for (int i = 0; i < 3; i++) {
				hull[i] = Math.min(hull[i], b[i]);
				hull[i + 3] = Math.max(hull[i + 3], b[i + 3]);
			}
		return hull;
	}

	/** A point in pixels, drawn for a part facing up, turned to {@code facing} about the middle of the block. */
	private static double[] turn(int facing, double px, double py, double pz) {
		double x = px - 8, y = py - 8, z = pz - 8;
		double[] t = switch (facing) {
			case UP -> new double[] {x, y, z};
			case DOWN -> new double[] {x, -y, -z};
			case NORTH -> new double[] {x, z, -y};
			case SOUTH -> new double[] {-x, z, y};
			case EAST -> new double[] {y, z, x};
			case WEST -> new double[] {-y, z, -x};
			default -> throw new IllegalArgumentException("facing " + facing);
		};
		return new double[] {t[0] + 8, t[1] + 8, t[2] + 8};
	}
}
