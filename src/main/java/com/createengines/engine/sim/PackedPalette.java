package com.createengines.engine.sim;

/**
 * Reads the packed block-state indices of one saved chunk section, the way the game writes them, without the game: a section
 * is 4096 cells, each holding an index into the section's palette, packed into longs with no value split across two longs.
 * Plain Java, so the layout is tested directly.
 * <p>
 * A section with a palette of one entry stores no cells at all. Otherwise a cell is {@code max(4, ceil(log2(palette size)))}
 * bits wide, and cell {@code i} sits at {@code (y << 8) | (z << 4) | x} within the section.
 */
public final class PackedPalette {

	/** Cells in a section: 16 x 16 x 16. */
	public static final int CELLS = 4096;

	private PackedPalette() {
	}

	/** Width in bits of one stored cell for a palette of the given size; 0 when the palette has one entry and nothing is stored. */
	public static int bits(int paletteSize) {
		if (paletteSize <= 1)
			return 0;
		int log = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
		return Math.max(4, log);
	}

	/**
	 * The palette index of every cell, or null when the data is not the right size for the palette (a damaged or foreign
	 * section), in which case the caller leaves the section alone. An index beyond the palette is returned as it is; the caller
	 * checks it.
	 */
	public static int[] unpack(long[] data, int paletteSize) {
		int[] cells = new int[CELLS];
		int bits = bits(paletteSize);
		if (bits == 0)
			return paletteSize == 1 ? cells : null;
		if (data == null)
			return null;
		int perLong = 64 / bits;
		int longs = (CELLS + perLong - 1) / perLong;
		if (data.length < longs)
			return null;
		long mask = (1L << bits) - 1;
		for (int i = 0; i < CELLS; i++)
			cells[i] = (int) (data[i / perLong] >>> (i % perLong * bits) & mask);
		return cells;
	}

	/** The x (0..15) of a cell. */
	public static int x(int cell) {
		return cell & 15;
	}

	/** The z (0..15) of a cell. */
	public static int z(int cell) {
		return cell >> 4 & 15;
	}

	/** The y (0..15) of a cell. */
	public static int y(int cell) {
		return cell >> 8 & 15;
	}
}
