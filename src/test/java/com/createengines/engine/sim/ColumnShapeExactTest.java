package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.ColumnShape.Fault;
import com.createengines.engine.sim.ColumnShape.Joint;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.ColumnShape.Result;

/**
 * The exact geometry of {@link ColumnShape}: offsets, joints, containment, the scan's edges. Added after mutation testing
 * showed the earlier tests only ever built columns at the origin and one block wide, where many wrong sums come out right.
 */
class ColumnShapeExactTest {

	private final Map<String, Part> parts = new HashMap<>();

	private static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	private void put(Part part, int x, int y, int z) {
		parts.put(key(x, y, z), part);
	}

	private void layer(Part part, int x0, int y, int z0, int width) {
		for (int x = x0; x < x0 + width; x++)
			for (int z = z0; z < z0 + width; z++)
				put(part, x, y, z);
	}

	private Result scan(int x, int y, int z) {
		return ColumnShape.scan((px, py, pz) -> parts.getOrDefault(key(px, py, pz), Part.OTHER), x, y, z);
	}

	/** A reboiler base at (bx, by, bz), then a section per entry of {@code layerX} (relative to bx), then the condenser. */
	private void build(int bx, int by, int bz, int width, int... layerX) {
		layer(Part.REBOILER, bx, by, bz, width);
		for (int i = 0; i < layerX.length; i++)
			layer(Part.SECTION, bx + layerX[i], by + i + 1, bz, width);
		layer(Part.CONDENSER, bx + layerX[layerX.length - 1], by + layerX.length + 1, bz, width);
	}

	@Test
	void theDefaultClearTestAcceptsOnlyEmptySpace() {
		ColumnShape.World world = (x, y, z) -> x == 0 ? Part.OTHER : Part.SECTION;
		assertTrue(world.clear(0, 0, 0));
		assertFalse(world.clear(1, 0, 0));
		ColumnShape.World reboilers = (x, y, z) -> Part.REBOILER;
		assertFalse(reboilers.clear(0, 0, 0));
	}

	@Test
	void layerOffsetsAreReadFromTheLeanListAndAreZeroOutsideIt() {
		Result r = new Result(Fault.NONE, 10, 0, 20, 1, 1, new int[] {5, 7, 6, 8}, 0, 0, 0);
		assertEquals(5, r.offsetX(0));
		assertEquals(7, r.offsetZ(0));
		assertEquals(6, r.offsetX(1));
		assertEquals(8, r.offsetZ(1));
		// Just past the end, and before the start, are zero rather than a read past the array
		assertEquals(0, r.offsetX(2));
		assertEquals(0, r.offsetZ(2));
		assertEquals(0, r.offsetX(-1));
		assertEquals(0, r.offsetZ(-1));
		assertEquals(15, r.layerX(0));
		assertEquals(27, r.layerZ(0));
		assertEquals(16, r.layerX(1));
		assertEquals(28, r.layerZ(1));
		assertTrue(r.leans());
		assertFalse(new Result(Fault.NONE, 0, 0, 0, 1, 1, new int[] {0, 0, 0, 0}, 0, 0, 0).leans());
		assertFalse(new Result(Fault.NONE, 0, 0, 0, 1, 1).leans());
	}

	@Test
	void aLeanOfOneNeedsTwoJointsAtTheExactPlaces() {
		build(0, 0, 0, 1, 0, 1);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.NONE, r.fault());
		assertEquals(List.of(new Joint(0, 2, 0, 0, true), new Joint(1, 1, 0, 0, false)), r.joints());
	}

	@Test
	void jointsAreFoundForEveryDirectionAndAtAnOffset() {
		// A column leaning in -x, then +z, then -z, at a base far from the origin; the joint count follows the widths
		build(40, 5, 60, 2, 0, -1);
		Result west = scan(40, 5, 60);
		assertEquals(Fault.NONE, west.fault());
		List<Joint> joints = west.joints();
		assertEquals(4, joints.size());
		for (Joint joint : joints)
			assertEquals(1, joint.dir());
		assertTrue(joints.contains(new Joint(39, 6, 60, 1, false)));
		assertTrue(joints.contains(new Joint(39, 6, 61, 1, false)));
		assertTrue(joints.contains(new Joint(41, 7, 60, 1, true)));
		assertTrue(joints.contains(new Joint(41, 7, 61, 1, true)));
	}

	@Test
	void aTwoWideLeanFormsBecauseItsJointsAreAllDifferentPlaces() {
		build(0, 0, 0, 2, 0, 1);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.NONE, r.fault());
		assertEquals(4, r.joints().size());
		build(100, 3, -50, 3, 0, 0, 1, 1);
		Result wide = scan(100, 3, -50);
		assertEquals(Fault.NONE, wide.fault());
		assertEquals(6, wide.joints().size());
	}

	@Test
	void aStraightColumnHasNoJointsAndAZeroWidthOneHasNone() {
		build(0, 0, 0, 2, 0, 0, 0);
		assertEquals(List.of(), scan(0, 0, 0).joints());
		Result zeroWidth = new Result(Fault.NONE, 0, 0, 0, 0, 1, new int[] {0, 0, 1, 0, 1, 0}, 0, 0, 0);
		assertEquals(List.of(), zeroWidth.joints());
	}

	@Test
	void aColumnTakesInExactlyItsOwnBlocks() {
		build(5, 3, 7, 1, 0, 1);
		Result r = scan(5, 3, 7);
		assertTrue(r.formed());
		assertEquals(3 + 2 + 1, r.topY());
		assertTrue(r.contains(5, 3, 7));
		assertTrue(r.contains(5, 4, 7));
		assertTrue(r.contains(6, 5, 7));
		assertTrue(r.contains(6, 6, 7));
		assertFalse(r.contains(5, 5, 7));
		assertFalse(r.contains(5, 2, 7));
		assertFalse(r.contains(6, 7, 7));
		assertFalse(r.contains(6, 6, 8));
		assertFalse(new Result(Fault.BASE, 5, 3, 7, 0, 0).contains(5, 3, 7));
	}

	@Test
	void resultsAreEqualOnlyWhenEveryFieldIs() {
		Result a = new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7);
		Result same = new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7);
		assertEquals(a, same);
		assertEquals(a.hashCode(), same.hashCode());
		assertNotEquals(0, a.hashCode());
		assertNotEquals(a, new Result(Fault.BASE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 9, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 9, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 9, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 9, 4, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 2, 9, new int[] {0, 0, 1, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 0, 0}, 5, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 9, 6, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 9, 7));
		assertNotEquals(a, new Result(Fault.NONE, 1, 2, 3, 2, 4, new int[] {0, 0, 1, 0}, 5, 6, 9));
		assertNotEquals(a, "not a result");
		assertNotEquals(a.hashCode(), new Result(Fault.NONE, 1, 2, 3, 2, 5, new int[] {0, 0, 1, 0}, 5, 6, 7).hashCode());
	}

	@Test
	void everySidewaysStepHasItsOwnDirection() {
		assertEquals(0, ColumnShape.dirOf(1, 0));
		assertEquals(1, ColumnShape.dirOf(-1, 0));
		assertEquals(2, ColumnShape.dirOf(0, 1));
		assertEquals(3, ColumnShape.dirOf(0, -1));
		assertEquals(-1, ColumnShape.dirOf(0, 0));
		assertEquals(-1, ColumnShape.dirOf(1, 1));
		assertEquals(-1, ColumnShape.dirOf(2, 0));
	}

	@Test
	void aColumnThatNeverReachesItsReboilersIsGivenUpOnQuickly() {
		ColumnShape.World endless = (x, y, z) -> Part.SECTION;
		Result r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> ColumnShape.scan(endless, 0, 0, 0));
		assertNotNull(r);
		assertEquals(Fault.BASE, r.fault());
	}

	@Test
	void aPartWithNothingBelowItAndNotASectionIsNoColumn() {
		put(Part.CONDENSER, 3, 9, 3);
		Result r = scan(3, 9, 3);
		assertNotNull(r);
		assertEquals(Fault.BASE, r.fault());
		assertEquals(0, r.width());
		// A section hanging with nothing to the side to hold it is no column either
		put(Part.SECTION, 8, 9, 8);
		assertNotNull(scan(8, 9, 8));
		assertEquals(Fault.BASE, scan(8, 9, 8).fault());
		assertEquals(Fault.BASE, scan(50, 50, 50).fault());
	}

	@Test
	void theBaseIsMeasuredWhereverItSits() {
		build(5, 3, 7, 2, 0, 0);
		Result r = scan(6, 4, 8);
		assertEquals(Fault.NONE, r.fault());
		assertEquals(2, r.width());
		assertEquals(5, r.x());
		assertEquals(3, r.y());
		assertEquals(7, r.z());
		assertEquals(2, r.height());
		// A base that is not square is not a column, however it is placed
		parts.clear();
		layer(Part.REBOILER, 20, 0, 30, 2);
		put(Part.REBOILER, 22, 0, 30);
		Result lopsided = scan(20, 0, 30);
		assertEquals(Fault.BASE, lopsided.fault());
		assertEquals(0, lopsided.width());
	}

	@Test
	void aBaseFourWideIsRejected() {
		layer(Part.REBOILER, 0, 0, 0, 4);
		assertEquals(Fault.BASE, scan(0, 0, 0).fault());
		parts.clear();
		layer(Part.REBOILER, 0, 0, 0, 3);
		layer(Part.SECTION, 0, 1, 0, 3);
		layer(Part.CONDENSER, 0, 2, 0, 3);
		assertEquals(Fault.NONE, scan(1, 1, 1).fault());
	}

	@Test
	void aStrayBlockAnywhereInAnUnfinishedLayerIsRagged() {
		for (int[] stray : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}, {1, 1}}) {
			parts.clear();
			layer(Part.REBOILER, 10, 0, 20, 3);
			layer(Part.SECTION, 10, 1, 20, 3);
			put(Part.CONDENSER, 10 + stray[0], 2, 20 + stray[1]);
			assertEquals(Fault.RAGGED, scan(10, 0, 20).fault(), "stray at " + stray[0] + "," + stray[1]);
		}
		// A block next to the footprint, not in it, is not the column's business: the column just has no condenser
		parts.clear();
		layer(Part.REBOILER, 10, 0, 20, 3);
		layer(Part.SECTION, 10, 1, 20, 3);
		put(Part.CONDENSER, 13, 2, 20);
		put(Part.CONDENSER, 9, 2, 20);
		put(Part.CONDENSER, 10, 2, 23);
		put(Part.CONDENSER, 10, 2, 19);
		assertEquals(Fault.NO_CONDENSER, scan(10, 0, 20).fault());
		// A raggedness at a leaned layer is found where the layer is, not where the base is
		parts.clear();
		layer(Part.REBOILER, 0, 0, 0, 2);
		layer(Part.SECTION, 0, 1, 0, 2);
		layer(Part.SECTION, 1, 2, 0, 2);
		put(Part.CONDENSER, 2, 3, 1);
		assertEquals(Fault.RAGGED, scan(0, 0, 0).fault());
	}

	@Test
	void aColumnWithNoSectionsSaysSo() {
		layer(Part.REBOILER, 0, 0, 0, 1);
		layer(Part.CONDENSER, 0, 1, 0, 1);
		assertEquals(Fault.NO_SECTION, scan(0, 0, 0).fault());
		parts.clear();
		layer(Part.REBOILER, 0, 0, 0, 1);
		assertEquals(Fault.NO_SECTION, scan(0, 0, 0).fault());
	}

	@Test
	void theHeightLimitIsExactForStraightAndLeaningColumns() {
		int max = ColumnShape.MAX_HEIGHT;
		layer(Part.REBOILER, 0, 0, 0, 1);
		for (int i = 1; i <= max; i++)
			put(Part.SECTION, 0, i, 0);
		put(Part.CONDENSER, 0, max + 1, 0);
		assertEquals(Fault.NONE, scan(0, 0, 0).fault());
		put(Part.SECTION, 0, max + 1, 0);
		put(Part.CONDENSER, 0, max + 2, 0);
		assertEquals(Fault.TOO_TALL, scan(0, 0, 0).fault());

		// Leaning: every layer after the first steps one block east, and the same limit applies
		for (int sections : new int[] {max, max + 1}) {
			parts.clear();
			layer(Part.REBOILER, 0, 0, 0, 1);
			put(Part.SECTION, 0, 1, 0);
			for (int i = 2; i <= sections; i++)
				put(Part.SECTION, i - 1, i, 0);
			put(Part.CONDENSER, sections - 1, sections + 1, 0);
			Fault expected = sections > max ? Fault.TOO_TALL : null;
			Fault actual = scan(0, 0, 0).fault();
			if (expected != null)
				assertEquals(expected, actual, "sections " + sections);
			else
				assertNotEquals(Fault.TOO_TALL, actual, "sections " + sections);
		}
	}
}
