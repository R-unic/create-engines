package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The layout of a saved chunk section's block states, which the casing-dye migration reads without the game. The packer here
 * is written from the format's definition (values packed low bits first, none split across two longs), so a mistake in the
 * reader cannot hide behind the same mistake in the test.
 */
class PackedPaletteTest {

	/** Packs cells the way a chunk is saved. */
	private static long[] pack(int[] cells, int bits) {
		int perLong = 64 / bits;
		long[] out = new long[(cells.length + perLong - 1) / perLong];
		for (int i = 0; i < cells.length; i++)
			out[i / perLong] |= (long) cells[i] << (i % perLong * bits);
		return out;
	}

	@Test
	void cellWidthIsAtLeastFourBitsAndGrowsWithThePalette() {
		assertEquals(0, PackedPalette.bits(0));
		assertEquals(0, PackedPalette.bits(1));
		assertEquals(4, PackedPalette.bits(2));
		assertEquals(4, PackedPalette.bits(16));
		assertEquals(5, PackedPalette.bits(17));
		assertEquals(5, PackedPalette.bits(32));
		assertEquals(6, PackedPalette.bits(33));
		assertEquals(8, PackedPalette.bits(256));
		assertEquals(9, PackedPalette.bits(257));
		assertEquals(12, PackedPalette.bits(4096));
	}

	@Test
	void aSinglePaletteEntrySectionHasNoCellsAndEveryCellIsThatEntry() {
		assertArrayEquals(new int[PackedPalette.CELLS], PackedPalette.unpack(null, 1));
		assertArrayEquals(new int[PackedPalette.CELLS], PackedPalette.unpack(new long[0], 1));
	}

	@Test
	void anEmptyPaletteIsNotASection() {
		assertNull(PackedPalette.unpack(new long[0], 0));
		assertNull(PackedPalette.unpack(null, 0));
	}

	@Test
	void sixteenCellsFitInOneLongAtFourBits() {
		// 0x...FEDCBA9876543210: cell i holds the value i for the first sixteen cells
		long[] data = new long[256];
		data[0] = 0xFEDCBA9876543210L;
		int[] cells = PackedPalette.unpack(data, 16);
		for (int i = 0; i < 16; i++)
			assertEquals(i, cells[i], "cell " + i);
		assertEquals(0, cells[16]);
	}

	@Test
	void everyWidthRoundTripsRandomCells() {
		Random random = new Random(11);
		for (int palette : new int[] { 2, 3, 15, 16, 17, 31, 32, 33, 100, 255, 256, 257, 1000, 4096 }) {
			int bits = PackedPalette.bits(palette);
			int[] cells = new int[PackedPalette.CELLS];
			for (int i = 0; i < cells.length; i++)
				cells[i] = random.nextInt(palette);
			assertArrayEquals(cells, PackedPalette.unpack(pack(cells, bits), palette), "palette of " + palette);
		}
	}

	@Test
	void aValueNeverCrossesFromOneLongToTheNext() {
		// 5-bit cells: 12 per long (60 bits), so cell 12 starts the second long, not the top four bits of the first
		int[] cells = new int[PackedPalette.CELLS];
		cells[11] = 31;
		cells[12] = 21;
		long[] data = pack(cells, 5);
		assertEquals(31L << 55, data[0]);
		assertEquals(21L, data[1]);
		int[] read = PackedPalette.unpack(data, 17);
		assertEquals(31, read[11]);
		assertEquals(21, read[12]);
	}

	@Test
	void dataTooShortForThePaletteIsRefusedNotGuessed() {
		long[] full = pack(new int[PackedPalette.CELLS], 4);
		assertNotNull(PackedPalette.unpack(full, 5));
		assertNull(PackedPalette.unpack(java.util.Arrays.copyOf(full, full.length - 1), 5));
		assertNull(PackedPalette.unpack(new long[0], 5));
		assertNull(PackedPalette.unpack(null, 5));
	}

	@Test
	void extraDataIsIgnored() {
		long[] data = java.util.Arrays.copyOf(pack(new int[PackedPalette.CELLS], 4), 400);
		data[399] = -1;
		assertArrayEquals(new int[PackedPalette.CELLS], PackedPalette.unpack(data, 5));
	}

	@Test
	void anIndexBeyondThePaletteIsReturnedForTheCallerToCheck() {
		int[] cells = new int[PackedPalette.CELLS];
		cells[7] = 15;
		assertEquals(15, PackedPalette.unpack(pack(cells, 4), 3)[7]);
	}

	@Test
	void aCellIsYThenZThenXWithinTheSection() {
		assertEquals(0, PackedPalette.x(0) + PackedPalette.y(0) + PackedPalette.z(0));
		assertEquals(1, PackedPalette.x(1));
		assertEquals(0, PackedPalette.z(1));
		assertEquals(1, PackedPalette.z(16));
		assertEquals(0, PackedPalette.x(16));
		assertEquals(1, PackedPalette.y(256));
		assertEquals(0, PackedPalette.z(256));
		int cell = 9 << 8 | 14 << 4 | 3;
		assertEquals(3, PackedPalette.x(cell));
		assertEquals(14, PackedPalette.z(cell));
		assertEquals(9, PackedPalette.y(cell));
		assertEquals(15, PackedPalette.y(PackedPalette.CELLS - 1));
		assertEquals(15, PackedPalette.x(PackedPalette.CELLS - 1));
		assertEquals(15, PackedPalette.z(PackedPalette.CELLS - 1));
	}
}
