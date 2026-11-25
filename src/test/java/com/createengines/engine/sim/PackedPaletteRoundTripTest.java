package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The saved-chunk reader against its own writer, for every palette size a section can have, and against damaged data. */
class PackedPaletteRoundTripTest {

	/** Packs the way the game does: no cell split across two longs. */
	private static long[] pack(int[] cells, int bits) {
		int perLong = 64 / bits;
		long[] data = new long[(cells.length + perLong - 1) / perLong];
		for (int i = 0; i < cells.length; i++)
			data[i / perLong] |= (long) cells[i] << (i % perLong * bits);
		return data;
	}

	@Test
	void everyPaletteSizeReadsBackWhatWasPacked() {
		Random random = new Random(4096);
		for (int size = 2; size <= PackedPalette.CELLS; size += size < 40 ? 1 : 37) {
			int[] cells = new int[PackedPalette.CELLS];
			for (int i = 0; i < cells.length; i++)
				cells[i] = random.nextInt(size);
			assertArrayEquals(cells, PackedPalette.unpack(pack(cells, PackedPalette.bits(size)), size), "palette of " + size);
		}
	}

	@Test
	void aCellIsAlwaysWideEnoughForTheLargestIndex() {
		for (int size = 2; size <= PackedPalette.CELLS; size++) {
			int bits = PackedPalette.bits(size);
			assertTrue(bits >= 4);
			assertTrue((1 << bits) >= size, size + " does not fit " + bits + " bits");
			if (bits > 4)
				assertTrue((1 << (bits - 1)) < size, size + " wastes a bit");
		}
	}

	@Test
	void dataOneLongShortIsRefusedAndLongerDataIsRead() {
		for (int size : new int[] {2, 16, 17, 33, 65, 257, 4096}) {
			long[] exact = pack(new int[PackedPalette.CELLS], PackedPalette.bits(size));
			assertNotNull(PackedPalette.unpack(exact, size));
			assertNull(PackedPalette.unpack(java.util.Arrays.copyOf(exact, exact.length - 1), size), "short for " + size);
			assertNotNull(PackedPalette.unpack(java.util.Arrays.copyOf(exact, exact.length + 3), size));
		}
	}

	@Test
	void damagedPaletteSizesAndMissingDataAreRefusedWithoutThrowing() {
		assertNull(PackedPalette.unpack(null, 16));
		assertNull(PackedPalette.unpack(new long[0], 16));
		assertNull(PackedPalette.unpack(null, 0));
		assertNull(PackedPalette.unpack(null, -3));
		assertNotNull(PackedPalette.unpack(null, 1), "a one-entry section stores nothing");
	}

	@Test
	void everyCellHasItsOwnPlaceInTheSection() {
		Set<Integer> places = new HashSet<>();
		for (int cell = 0; cell < PackedPalette.CELLS; cell++) {
			int x = PackedPalette.x(cell), y = PackedPalette.y(cell), z = PackedPalette.z(cell);
			assertEquals(cell, y << 8 | z << 4 | x);
			places.add(cell);
		}
		assertEquals(PackedPalette.CELLS, places.size());
	}
}
