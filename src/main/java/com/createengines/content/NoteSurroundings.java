package com.createengines.content;

/**
 * What the world does to an engine's sound on its way to the listener (the sound plan's step 4): how far the air closes
 * the note, how a wall muffles it, how many walls a straight line passes through, and whether the listener's surroundings
 * are a room. The world itself is only asked whether a block cell is solid ({@link Blocks}), so all of it is plain Java
 * and tested; {@code EngineRoomScene} does the asking. These are a player's ears only: nothing here moves a rating.
 */
final class NoteSurroundings {

	/** A cutoff meaning no filter at all. */
	static final float OPEN = Float.POSITIVE_INFINITY;
	/** The first reflection at its loudest, against the engine's own. */
	static final float LOUDEST_ECHO = 0.3f;

	/** Blocks within which the air takes nothing away. */
	static final double CLEAR_AIR = 8;
	private static final float NEAR_HZ = 12000;
	private static final float FAR_HZ = 700;
	/** Where the filter sits behind one, two and three walls, and the share of the level that gets through. */
	private static final float[] WALL_HZ = {OPEN, 1400, 750, 450};
	private static final float[] WALL_LEVEL = {1, 0.7f, 0.55f, 0.45f};

	/** How far from each end of a line solid blocks do not count: the engine's own, and any the listener's head is in. */
	private static final double SOURCE_CLEAR = 1.5;
	private static final double LISTENER_CLEAR = 0.5;

	/** How far the room is felt for, in blocks. */
	static final double ROOM_REACH = 12;
	/** Straight up and four ways steeply up: what tells a roof from open sky between walls. */
	private static final double[][] ROOF = {{0, 1, 0}, {1, 2, 0}, {-1, 2, 0}, {0, 2, 1}, {0, 2, -1}};
	private static final double[][] REST = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0},
		{1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1}};
	private static final double SPEED_OF_SOUND = 343;

	private NoteSurroundings() {
	}

	/** Whether a block cell stops sound. */
	@FunctionalInterface
	interface Blocks {
		boolean solid(int x, int y, int z);
	}

	/**
	 * The room round the listener.
	 *
	 * @param echo    the first reflection's level, 0 out of doors
	 * @param seconds how long after the sound it arrives
	 */
	record Room(float echo, float seconds) {
		static final Room OUTDOORS = new Room(0, 0.02f);
	}

	/** Where the air puts the filter for an engine {@code blocks} away: open near by, only the boom far off. */
	static float airCutoff(double blocks) {
		if (!(blocks > CLEAR_AIR))
			return OPEN;
		double share = Math.min(1, (blocks - CLEAR_AIR) / (EngineChorus.REACH - CLEAR_AIR));
		return (float) (NEAR_HZ * Math.pow(FAR_HZ / NEAR_HZ, share));
	}

	/** The filter for an engine {@code blocks} away behind {@code walls} blocks: the duller of what the air and walls ask. */
	static float cutoff(double blocks, int walls) {
		return Math.min(airCutoff(blocks), WALL_HZ[Math.clamp(walls, 0, WALL_HZ.length - 1)]);
	}

	static float wallLevel(int walls) {
		return WALL_LEVEL[Math.clamp(walls, 0, WALL_LEVEL.length - 1)];
	}

	/**
	 * The walls on the straight line from the listener to a source: how many blocks' length of it runs through solid, to the
	 * nearest whole one, up to as many as make a difference. A one-block wall crossed at a slant is still one, and a line
	 * that only clips a corner meets none.
	 */
	static int walls(Blocks blocks, double x, double y, double z, double toX, double toY, double toZ) {
		double dx = toX - x;
		double dy = toY - y;
		double dz = toZ - z;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		// Nothing beyond earshot is worth walking to (and a position that is not a number never gets there)
		if (!(length > 0) || length > 2 * EngineChorus.REACH)
			return 0;
		Walk walk = new Walk(x, y, z, dx / length, dy / length, dz / length);
		int most = WALL_HZ.length - 1;
		double through = 0;
		double end = length - SOURCE_CLEAR;
		while (walk.entered < end && through < most) {
			// Only what is between the two clear ends counts
			double inside = Math.min(walk.leaves(), end) - Math.max(walk.entered, LISTENER_CLEAR);
			if (inside > 0 && blocks.solid(walk.x, walk.y, walk.z))
				through += inside;
			walk.step();
		}
		return (int) Math.min(most, Math.round(through));
	}

	/** Feels round the listener for walls and a roof. */
	static Room room(Blocks blocks, double x, double y, double z) {
		if (!Double.isFinite(x + y + z))
			return Room.OUTDOORS;
		int hits = 0;
		int roofHits = 0;
		double total = 0;
		for (int i = 0; i < ROOF.length + REST.length; i++) {
			boolean roof = i < ROOF.length;
			double[] way = roof ? ROOF[i] : REST[i - ROOF.length];
			double hit = distanceToSolid(blocks, x, y, z, way);
			if (hit < 0)
				continue;
			hits++;
			total += hit;
			if (roof)
				roofHits++;
		}
		if (hits == 0)
			return Room.OUTDOORS;
		float enclosed = ramp(hits / (float) (ROOF.length + REST.length), 0.55f, 0.9f);
		float roofed = ramp(roofHits / (float) ROOF.length, 0.35f, 0.8f);
		float there = (float) (2 * (total / hits) / SPEED_OF_SOUND);
		return new Room(LOUDEST_ECHO * enclosed * roofed, Math.clamp(there, 0.008f, NoteEcho.LONGEST));
	}

	/** How far along {@code way} the first solid block is, or -1 if none is within reach. */
	private static double distanceToSolid(Blocks blocks, double x, double y, double z, double[] way) {
		double length = Math.sqrt(way[0] * way[0] + way[1] * way[1] + way[2] * way[2]);
		Walk walk = new Walk(x, y, z, way[0] / length, way[1] / length, way[2] / length);
		while (walk.entered <= ROOM_REACH) {
			if (blocks.solid(walk.x, walk.y, walk.z))
				return walk.entered;
			walk.step();
		}
		return -1;
	}

	private static float ramp(float value, float from, float to) {
		return Math.clamp((value - from) / (to - from), 0f, 1f);
	}

	/** Every block cell a straight line passes through, in order, from where it starts. */
	private static final class Walk {
		int x;
		int y;
		int z;
		/** How far along the line the current cell was entered. */
		double entered;
		private final int stepX;
		private final int stepY;
		private final int stepZ;
		private final double perX;
		private final double perY;
		private final double perZ;
		private double nextX;
		private double nextY;
		private double nextZ;

		Walk(double fromX, double fromY, double fromZ, double dx, double dy, double dz) {
			x = (int) Math.floor(fromX);
			y = (int) Math.floor(fromY);
			z = (int) Math.floor(fromZ);
			stepX = dx < 0 ? -1 : 1;
			stepY = dy < 0 ? -1 : 1;
			stepZ = dz < 0 ? -1 : 1;
			perX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
			perY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
			perZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
			nextX = first(fromX, dx, perX);
			nextY = first(fromY, dy, perY);
			nextZ = first(fromZ, dz, perZ);
		}

		private static double first(double from, double way, double per) {
			if (way == 0)
				return Double.POSITIVE_INFINITY;
			double part = from - Math.floor(from);
			return (way < 0 ? part : 1 - part) * per;
		}

		double leaves() {
			return Math.min(nextX, Math.min(nextY, nextZ));
		}

		void step() {
			entered = leaves();
			if (nextX <= nextY && nextX <= nextZ) {
				x += stepX;
				nextX += perX;
			} else if (nextY <= nextZ) {
				y += stepY;
				nextY += perY;
			} else {
				z += stepZ;
				nextZ += perZ;
			}
		}
	}
}
