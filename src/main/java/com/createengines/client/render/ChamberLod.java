package com.createengines.client.render;

import org.jetbrains.annotations.Nullable;

/**
 * How much of a cylinder's gas body is worth drawing at a given distance from the camera. The body is a few hundred
 * vertices per cylinder, twice (once added, once blended), rebuilt every frame, so a big engine in view costs most of its
 * frame time here. Up close it is drawn in full; further off the same shape is drawn from fewer sides and rings, and
 * past {@link #FAR} blocks, where it is a smudge inside a bore, not at all.
 *
 * <p>Pure arithmetic, no game classes, so the thresholds and vertex counts are unit tested.
 */
public final class ChamberLod {

	/** Closer than this many blocks (to the block's corner) the gas is drawn in full detail. */
	public static final float NEAR = 16;
	/** Further than this many blocks the gas is not drawn. */
	public static final float FAR = 40;

	private ChamberLod() {
	}

	/** How finely the shape is cut: sides round the bore, rings along it, bands over the flame front, flame tongues. */
	public enum Level {
		DETAILED(12, 7, 6, 5), COARSE(8, 3, 4, 3);

		public final int sides;
		public final int rings;
		public final int bands;
		public final int tongues;

		Level(int sides, int rings, int bands, int tongues) {
			this.sides = sides;
			this.rings = rings;
			this.bands = bands;
			this.tongues = tongues;
		}

		/** Vertices in the gas shell, which is drawn once for haze and once for glow. */
		public int shellVertices() {
			return rings * sides * 4 + 2 * sides * 4;
		}

		/** Vertices in the flame front and the tongues, which are drawn once. */
		public int flameVertices() {
			return bands * sides * 4 + tongues * 2 * 4;
		}

		/** The most vertices one cylinder can send in a frame. */
		public int worstCase() {
			return 2 * shellVertices() + flameVertices();
		}
	}

	/** The level for a block whose corner is {@code distanceSquared} blocks squared from the camera; null to draw nothing. */
	public static @Nullable Level at(double distanceSquared) {
		if (!(distanceSquared <= (double) FAR * FAR))
			return null;
		return distanceSquared <= (double) NEAR * NEAR ? Level.DETAILED : Level.COARSE;
	}
}
