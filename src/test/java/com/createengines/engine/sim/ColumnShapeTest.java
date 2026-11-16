package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.ColumnShape.Fault;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.ColumnShape.Result;

class ColumnShapeTest {

	private final Map<String, Part> parts = new HashMap<>();

	private void put(Part part, int x, int y, int z) {
		parts.put(x + "," + y + "," + z, part);
	}

	private void layer(Part part, int x0, int y, int z0, int width) {
		for (int x = x0; x < x0 + width; x++)
			for (int z = z0; z < z0 + width; z++)
				put(part, x, y, z);
	}

	private Result scan(int x, int y, int z) {
		return ColumnShape.scan((px, py, pz) -> parts.getOrDefault(px + "," + py + "," + pz, Part.OTHER), x, y, z);
	}

	/** A column {@code width} wide with {@code sections} layers of body, its base at y 0. */
	private void column(int width, int sections) {
		layer(Part.REBOILER, 0, 0, 0, width);
		for (int y = 1; y <= sections; y++)
			layer(Part.SECTION, 0, y, 0, width);
		layer(Part.CONDENSER, 0, sections + 1, 0, width);
	}

	@Test
	void everyWidthFormsAndKeepsItsSize() {
		for (int width = 1; width <= 3; width++) {
			parts.clear();
			column(width, 4);
			Result r = scan(0, 0, 0);
			assertTrue(r.formed(), "width " + width);
			assertEquals(width, r.width());
			assertEquals(4, r.height());
			assertEquals(5, r.topY());
		}
	}

	@Test
	void anyPartFindsTheSameColumn() {
		column(2, 3);
		Result base = scan(0, 0, 0);
		assertTrue(base.formed());
		assertEquals(base, scan(1, 0, 1));
		assertEquals(base, scan(1, 2, 0));
		assertEquals(base, scan(0, 4, 1));
	}

	@Test
	void oneSectionIsEnough() {
		column(1, 1);
		assertEquals(1, scan(0, 0, 0).height());
	}

	@Test
	void aBlockThatIsNotAPartHasNoColumn() {
		column(1, 2);
		assertFalse(scan(5, 5, 5).formed());
		assertFalse(scan(0, 9, 0).formed());
	}

	@Test
	void aCondenserWithNoSectionIsNotAColumn() {
		put(Part.REBOILER, 0, 0, 0);
		put(Part.CONDENSER, 0, 1, 0);
		assertEquals(Fault.NO_SECTION, scan(0, 0, 0).fault());
	}

	@Test
	void aReboilerAloneHasNoSection() {
		put(Part.REBOILER, 0, 0, 0);
		assertEquals(Fault.NO_SECTION, scan(0, 0, 0).fault());
	}

	@Test
	void sectionsWithoutACondenserSayWhatIsMissing() {
		put(Part.REBOILER, 0, 0, 0);
		put(Part.SECTION, 0, 1, 0);
		put(Part.SECTION, 0, 2, 0);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.NO_CONDENSER, r.fault());
		assertEquals(2, r.height());
	}

	@Test
	void aBaseThatIsNotASquareDoesNotForm() {
		put(Part.REBOILER, 0, 0, 0);
		put(Part.REBOILER, 1, 0, 0);
		put(Part.REBOILER, 0, 0, 1);
		put(Part.SECTION, 0, 1, 0);
		put(Part.CONDENSER, 0, 2, 0);
		assertEquals(Fault.BASE, scan(0, 0, 0).fault());
	}

	@Test
	void aRowOfReboilersIsNotASquare() {
		layer(Part.REBOILER, 0, 0, 0, 1);
		put(Part.REBOILER, 1, 0, 0);
		assertEquals(Fault.BASE, scan(0, 0, 0).fault());
	}

	@Test
	void aBaseFourWideIsTooWide() {
		layer(Part.REBOILER, 0, 0, 0, 4);
		assertEquals(Fault.BASE, scan(0, 0, 0).fault());
	}

	@Test
	void diagonalReboilersDoNotJoin() {
		column(1, 2);
		put(Part.REBOILER, 1, 0, 1);
		assertTrue(scan(0, 0, 0).formed());
		assertEquals(1, scan(0, 0, 0).width());
	}

	@Test
	void aSectionLayerMissingABlockIsRagged() {
		column(2, 3);
		put(Part.OTHER, 1, 2, 1);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.RAGGED, r.fault());
		assertEquals(1, r.height());
	}

	@Test
	void aSectionAmongTheCondensersIsRagged() {
		column(2, 2);
		put(Part.SECTION, 1, 3, 1);
		assertEquals(Fault.RAGGED, scan(0, 0, 0).fault());
	}

	@Test
	void aNarrowerBodyOnAWideBaseIsRagged() {
		layer(Part.REBOILER, 0, 0, 0, 2);
		put(Part.SECTION, 0, 1, 0);
		assertEquals(Fault.RAGGED, scan(0, 0, 0).fault());
	}

	@Test
	void aTallerThanLimitBodyIsRefused() {
		column(1, ColumnShape.MAX_HEIGHT + 1);
		assertEquals(Fault.TOO_TALL, scan(0, 0, 0).fault());
	}

	@Test
	void theTallestAllowedBodyForms() {
		column(1, ColumnShape.MAX_HEIGHT);
		assertTrue(scan(0, 0, 0).formed());
		assertTrue(scan(0, ColumnShape.MAX_HEIGHT + 1, 0).formed());
	}

	@Test
	void stuffAboveTheCondenserIsLeftAlone() {
		column(1, 2);
		put(Part.SECTION, 0, 4, 0);
		assertTrue(scan(0, 0, 0).formed());
		assertEquals(2, scan(0, 0, 0).height());
	}

	@Test
	void containsTakesInTheWholeColumnAndNothingBeside() {
		column(2, 3);
		Result r = scan(0, 0, 0);
		assertTrue(r.contains(1, 4, 1));
		assertTrue(r.contains(0, 0, 0));
		assertFalse(r.contains(2, 0, 0));
		assertFalse(r.contains(0, 5, 0));
		assertFalse(r.contains(0, -1, 0));
	}

	@Test
	void twoColumnsSideBySideRunTogetherIntoOneBase() {
		column(1, 2);
		layer(Part.REBOILER, 1, 0, 0, 1);
		layer(Part.SECTION, 1, 1, 0, 1);
		layer(Part.SECTION, 1, 2, 0, 1);
		layer(Part.CONDENSER, 1, 3, 0, 1);
		// Side by side they read as one base, and a row of two is not a square
		assertEquals(Fault.BASE, scan(0, 0, 0).fault());
	}

	@Test
	void aHugeFloorOfReboilersIsRefusedQuickly() {
		layer(Part.REBOILER, 0, 0, 0, 200);
		long start = System.nanoTime();
		assertEquals(Fault.BASE, scan(100, 0, 100).fault());
		assertTrue(System.nanoTime() - start < 200_000_000L, "scanning a big floor must not take long");
	}

	@Test
	void aScanFromAnywhereInAHugeSolidBlockTerminates() {
		for (int y = 0; y < 40; y++)
			layer(Part.SECTION, 0, y, 0, 3);
		assertFalse(scan(1, 20, 1).formed());
	}
}
