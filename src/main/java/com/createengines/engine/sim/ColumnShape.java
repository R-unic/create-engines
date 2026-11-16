package com.createengines.engine.sim;

/**
 * Finds the refining column in a block of parts: a square base of Reboilers, one or more layers of Column Sections of
 * the same square on it, and a layer of Condensers of that square straight on the last of them. The first layer of
 * sections sits straight on the Reboilers. After that a layer may lean: it may sit one block to the side (along x or z)
 * of the layer below, which joins the two with a slanted piece. A lean fills the blocks beside the two layers with joint
 * pieces (the sections draw the arms themselves, so those blocks must be empty; if one is not, the column does not form and the fault names
 * the block. Nothing here knows about blocks: the caller says what part sits at a position, so the rules are tested
 * as plain Java.
 */
public final class ColumnShape {

	/** What a block is, as far as a column cares. */
	public enum Part {
		OTHER, REBOILER, SECTION, CONDENSER
	}

	/** Why a column did not form, in the order goggles look for it. */
	public enum Fault {
		/** It formed. */
		NONE,
		/** The Reboilers are not one square 1 to 3 blocks wide (an L, a row, a gap, or four wide). */
		BASE,
		/** Nothing sits on the Reboilers. */
		NO_SECTION,
		/** A layer above the base is only part of the square, or part of it is another kind of part. */
		RAGGED,
		/** The sections stop without a Condenser on top. */
		NO_CONDENSER,
		/** Column height is above the limit. */
		TOO_TALL,
		/** Two layers lean, but a block is in the way of the joint pieces; the result names the block. */
		LEAN_BLOCKED
	}

	/** Tallest body a column can have, in section layers. */
	public static final int MAX_HEIGHT = 32;

	/** Looks up the part at a position. */
	@FunctionalInterface
	public interface World {
		Part at(int x, int y, int z);

		/** Whether the slanted brace of a lean may take this block: nothing but air or something a block can replace. */
		default boolean clear(int x, int y, int z) {
			return at(x, y, z) == Part.OTHER;
		}
	}

	/** Sideways steps of a lean: +x, -x, +z, -z. */
	public static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/**
	 * A block space that a lean fills with the slanted arm of a section next to it. The sections draw the arms
	 * themselves (there is no block there), so the space must stay empty.
	 *
	 * @param dir   the way the column leans there, an index into {@link #STEPS}
	 * @param upper false for the space under the upper layer overhang, true for the one above the lower layer
	 */
	public record Joint(int x, int y, int z, int dir, boolean upper) {
	}

	/**
	 * What a scan found.
	 *
	 * @param fault   {@link Fault#NONE} when a column formed
	 * @param x       lowest x of the base
	 * @param y       y of the base
	 * @param z       lowest z of the base
	 * @param width   blocks along each side of the square, 0 when the base was not a square
	 * @param height  layers of sections between the base and the Condenser; 0 when none formed
	 * @param lean    for each layer from the base (0) to the Condenser, the x and z the layer sits at from the base
	 *                corner, as pairs; empty for a column that stands straight
	 * @param blockedX x of the block in the way of a lean joints (only with {@link Fault#LEAN_BLOCKED})
	 */
	public record Result(Fault fault, int x, int y, int z, int width, int height, int[] lean, int blockedX, int blockedY,
						 int blockedZ) {

		public Result(Fault fault, int x, int y, int z, int width, int height) {
			this(fault, x, y, z, width, height, new int[0], 0, 0, 0);
		}

		/** How far layer {@code layer} (0 at the base) sits from the base in x. */
		public int offsetX(int layer) {
			return layer >= 0 && 2 * layer + 1 < lean.length ? lean[2 * layer] : 0;
		}

		/** How far layer {@code layer} (0 at the base) sits from the base in z. */
		public int offsetZ(int layer) {
			return layer >= 0 && 2 * layer + 1 < lean.length ? lean[2 * layer + 1] : 0;
		}

		public boolean leans() {
			for (int v : lean)
				if (v != 0)
					return true;
			return false;
		}

		/** Lowest x of the footprint of layer {@code layer} above the base. */
		public int layerX(int layer) {
			return x + offsetX(layer);
		}

		public int layerZ(int layer) {
			return z + offsetZ(layer);
		}

		/** The joint pieces this column leans need, none for a column that stands straight. */
		public java.util.List<Joint> joints() {
			java.util.List<Joint> joints = new java.util.ArrayList<>();
			if (!leans() || width <= 0)
				return joints;
			for (int layer = 1; layer <= height + 1; layer++) {
				int dx = offsetX(layer) - offsetX(layer - 1);
				int dz = offsetZ(layer) - offsetZ(layer - 1);
				int dir = dirOf(dx, dz);
				if (dir < 0)
					continue;
				int ax = layerX(layer - 1), az = layerZ(layer - 1), bx = layerX(layer), bz = layerZ(layer);
				int lowY = y + layer - 1;
				for (int px = Math.min(ax, bx); px < Math.max(ax, bx) + width; px++)
					for (int pz = Math.min(az, bz); pz < Math.max(az, bz) + width; pz++) {
						boolean inA = in(px, pz, ax, az, width);
						boolean inB = in(px, pz, bx, bz, width);
						if (inB && !inA)
							joints.add(new Joint(px, lowY, pz, dir, false));
						else if (inA && !inB)
							joints.add(new Joint(px, lowY + 1, pz, dir, true));
					}
			}
			return joints;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Result r && fault == r.fault && x == r.x && y == r.y && z == r.z && width == r.width
				&& height == r.height && java.util.Arrays.equals(lean, r.lean) && blockedX == r.blockedX
				&& blockedY == r.blockedY && blockedZ == r.blockedZ;
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(fault, x, y, z, width, height, java.util.Arrays.hashCode(lean), blockedX, blockedY,
				blockedZ);
		}

		public boolean formed() {
			return fault == Fault.NONE;
		}

		/** Whether the column takes in this position, whichever part is there. */
		public boolean contains(int px, int py, int pz) {
			int layer = py - y;
			return width > 0 && layer >= 0 && layer <= height + 1 && in(px, pz, layerX(layer), layerZ(layer), width);
		}

		/** Layer above the last section, where the Condenser is. */
		public int topY() {
			return y + height + 1;
		}
	}

	private static boolean in(int px, int pz, int ox, int oz, int width) {
		return px >= ox && px < ox + width && pz >= oz && pz < oz + width;
	}

	/** The index into {@link #STEPS} of a one-block sideways step, or -1 if it is not one. */
	static int dirOf(int dx, int dz) {
		for (int i = 0; i < STEPS.length; i++)
			if (STEPS[i][0] == dx && STEPS[i][1] == dz)
				return i;
		return -1;
	}

	private static final Result NOTHING = new Result(Fault.BASE, 0, 0, 0, 0, 0);

	private ColumnShape() {
	}

	/**
	 * Scans for the column a part at {@code (x, y, z)} belongs to, or fails with the fault that comes first. A position
	 * that holds no part of a column has no column.
	 */
	public static Result scan(World world, int x, int y, int z) {
		if (world.at(x, y, z) == Part.OTHER)
			return NOTHING;
		// Down the column from here to the Reboilers; a section with nothing under it is the top of a lean, and is
		// held by the section one block to the side and one down
		int baseY = y;
		int guard = 2 * (MAX_HEIGHT + 3);
		while (world.at(x, baseY, z) != Part.REBOILER) {
			if (--guard < 0)
				return NOTHING;
			if (world.at(x, baseY - 1, z) != Part.OTHER) {
				baseY--;
				continue;
			}
			if (world.at(x, baseY, z) != Part.SECTION)
				return NOTHING;
			boolean found = false;
			for (int[] step : STEPS)
				if (world.at(x + step[0], baseY - 1, z + step[1]) == Part.SECTION) {
					x += step[0];
					z += step[1];
					baseY--;
					found = true;
					break;
				}
			if (!found)
				return NOTHING;
		}
		// The square of Reboilers that touch this one on its layer
		int minX = x, maxX = x, minZ = z, maxZ = z;
		boolean grew = true;
		while (grew && maxX - minX < RefiningColumn.MAX_WIDTH && maxZ - minZ < RefiningColumn.MAX_WIDTH) {
			grew = false;
			for (int px = minX - 1; px <= maxX + 1; px++)
				for (int pz = minZ - 1; pz <= maxZ + 1; pz++) {
					if ((px >= minX && px <= maxX && pz >= minZ && pz <= maxZ) || world.at(px, baseY, pz) != Part.REBOILER)
						continue;
					// Only a block that touches the cluster side to side joins it
					if (!touches(world, px, baseY, pz, minX, maxX, minZ, maxZ))
						continue;
					minX = Math.min(minX, px);
					maxX = Math.max(maxX, px);
					minZ = Math.min(minZ, pz);
					maxZ = Math.max(maxZ, pz);
					grew = true;
				}
		}
		int width = maxX - minX + 1;
		if (width != maxZ - minZ + 1 || width < RefiningColumn.MIN_WIDTH || width > RefiningColumn.MAX_WIDTH
			|| !layerIs(world, Part.REBOILER, minX, baseY, minZ, width))
			return new Result(Fault.BASE, minX, baseY, minZ, 0, 0);

		int height = 0;
		int ox = 0, oz = 0;
		java.util.List<Integer> lean = new java.util.ArrayList<>();
		lean.add(0);
		lean.add(0);
		for (int py = baseY + 1; ; py++) {
			if (layerIs(world, Part.SECTION, minX + ox, py, minZ + oz, width)) {
				height++;
				if (height > MAX_HEIGHT)
					return new Result(Fault.TOO_TALL, minX, baseY, minZ, width, height);
				lean.add(ox);
				lean.add(oz);
				continue;
			}
			if (layerIs(world, Part.CONDENSER, minX + ox, py, minZ + oz, width)) {
				if (height == 0)
					return new Result(Fault.NO_SECTION, minX, baseY, minZ, width, 0);
				lean.add(ox);
				lean.add(oz);
				return finish(world, minX, baseY, minZ, width, height, lean);
			}
			// A layer after the first may lean one block to the side
			boolean leaned = false;
			if (height >= 1)
				for (int[] step : STEPS)
					if (layerIs(world, Part.SECTION, minX + ox + step[0], py, minZ + oz + step[1], width)) {
						ox += step[0];
						oz += step[1];
						height++;
						if (height > MAX_HEIGHT)
							return new Result(Fault.TOO_TALL, minX, baseY, minZ, width, height);
						lean.add(ox);
						lean.add(oz);
						leaned = true;
						break;
					}
			if (leaned)
				continue;
			boolean any = false;
			for (int px = minX + ox; px < minX + ox + width; px++)
				for (int pz = minZ + oz; pz < minZ + oz + width; pz++) {
					any |= world.at(px, py, pz) != Part.OTHER;
				}
			if (any)
				return new Result(Fault.RAGGED, minX, baseY, minZ, width, height);
			return new Result(height == 0 ? Fault.NO_SECTION : Fault.NO_CONDENSER, minX, baseY, minZ, width, height);
		}
	}

	/** Checks that the joint pieces a column leans need have room, and gives the column or the block in the way. */
	private static Result finish(World world, int minX, int baseY, int minZ, int width, int height, java.util.List<Integer> lean) {
		int[] offsets = lean.stream()
			.mapToInt(Integer::intValue)
			.toArray();
		Result formed = new Result(Fault.NONE, minX, baseY, minZ, width, height, offsets, 0, 0, 0);
		if (!formed.leans())
			return formed;
		java.util.Set<Long> used = new java.util.HashSet<>();
		for (Joint joint : formed.joints()) {
			boolean taken = !used.add(key(joint.x(), joint.y(), joint.z()));
			if (taken || !world.clear(joint.x(), joint.y(), joint.z()))
				return new Result(Fault.LEAN_BLOCKED, minX, baseY, minZ, width, height, new int[0], joint.x(), joint.y(), joint.z());
		}
		return formed;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private static boolean touches(World world, int px, int py, int pz, int minX, int maxX, int minZ, int maxZ) {
		return inside(px + 1, pz, minX, maxX, minZ, maxZ) || inside(px - 1, pz, minX, maxX, minZ, maxZ)
			|| inside(px, pz + 1, minX, maxX, minZ, maxZ) || inside(px, pz - 1, minX, maxX, minZ, maxZ);
	}

	private static boolean inside(int px, int pz, int minX, int maxX, int minZ, int maxZ) {
		return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
	}

	/** Whether a whole width-by-width layer holds this part. */
	private static boolean layerIs(World world, Part part, int minX, int py, int minZ, int width) {
		for (int px = minX; px < minX + width; px++)
			for (int pz = minZ; pz < minZ + width; pz++)
				if (world.at(px, py, pz) != part)
					return false;
		return true;
	}
}
