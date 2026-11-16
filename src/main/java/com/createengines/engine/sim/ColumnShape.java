package com.createengines.engine.sim;

/**
 * Finds the refining column in a block of parts: a square base of Reboilers, one or more layers of Column Sections of
 * the same square straight on it, and a layer of Condensers of that square straight on the last of them. Nothing here
 * knows about blocks: the caller says what part sits at a position, so the rules are tested as plain Java.
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
		TOO_TALL
	}

	/** Tallest body a column can have, in section layers. */
	public static final int MAX_HEIGHT = 32;

	/** Looks up the part at a position. */
	@FunctionalInterface
	public interface World {
		Part at(int x, int y, int z);
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
	 */
	public record Result(Fault fault, int x, int y, int z, int width, int height) {

		public boolean formed() {
			return fault == Fault.NONE;
		}

		/** Whether the column takes in this position, whichever part is there. */
		public boolean contains(int px, int py, int pz) {
			return width > 0 && px >= x && px < x + width && pz >= z && pz < z + width && py >= y && py <= y + height + 1;
		}

		/** Layer above the last section, where the Condenser is. */
		public int topY() {
			return y + height + 1;
		}
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
		// Down the column from here to the Reboilers
		int baseY = y;
		int guard = MAX_HEIGHT + 3;
		while (world.at(x, baseY, z) != Part.REBOILER) {
			Part below = world.at(x, baseY - 1, z);
			if (below == Part.OTHER || --guard < 0)
				return NOTHING;
			baseY--;
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
		for (int py = baseY + 1; ; py++) {
			if (layerIs(world, Part.SECTION, minX, py, minZ, width)) {
				height++;
				if (height > MAX_HEIGHT)
					return new Result(Fault.TOO_TALL, minX, baseY, minZ, width, height);
				continue;
			}
			if (layerIs(world, Part.CONDENSER, minX, py, minZ, width)) {
				if (height == 0)
					return new Result(Fault.NO_SECTION, minX, baseY, minZ, width, 0);
				return new Result(Fault.NONE, minX, baseY, minZ, width, height);
			}
			boolean any = false;
			for (int px = minX; px < minX + width; px++)
				for (int pz = minZ; pz < minZ + width; pz++)
					any |= world.at(px, py, pz) != Part.OTHER;
			if (any)
				return new Result(Fault.RAGGED, minX, baseY, minZ, width, height);
			return new Result(height == 0 ? Fault.NO_SECTION : Fault.NO_CONDENSER, minX, baseY, minZ, width, height);
		}
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
