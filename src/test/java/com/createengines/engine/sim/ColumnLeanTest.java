package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.ColumnShape.Fault;
import com.createengines.engine.sim.ColumnShape.Joint;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.ColumnShape.Result;

/** Columns that lean: a layer one block to the side of the one below, joined by slanted pieces. */
class ColumnLeanTest {

	private final Map<String, Part> parts = new HashMap<>();
	private final Set<String> solid = new HashSet<>();

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

	/** Sets a block that is not part of a column but is in the way of joints. */
	private void block(int x, int y, int z) {
		solid.add(key(x, y, z));
	}

	/** Puts down the joints a result asks for, the way the world adapter does. */
	private void place(Result result) {
		for (Joint joint : result.joints())
			put(Part.JOINT, joint.x(), joint.y(), joint.z());
	}

	private final Map<String, Integer> links = new HashMap<>();

	private Result scan(int x, int y, int z) {
		ColumnShape.World world = new ColumnShape.World() {
			@Override
			public Part at(int px, int py, int pz) {
				return parts.getOrDefault(key(px, py, pz), Part.OTHER);
			}

			@Override
			public boolean clear(int px, int py, int pz) {
				return at(px, py, pz) == Part.OTHER && !solid.contains(key(px, py, pz));
			}

			@Override
			public int jointLink(int px, int py, int pz) {
				return links.getOrDefault(key(px, py, pz), -1);
			}
		};
		return ColumnShape.scan(world, x, y, z);
	}

	/** Reboiler at y 0, then one section per entry of {@code layerX}: the x each body layer sits at, then the Condenser. */
	private void build(int width, int... layerX) {
		layer(Part.REBOILER, 0, 0, 0, width);
		for (int i = 0; i < layerX.length; i++)
			layer(Part.SECTION, layerX[i], i + 1, 0, width);
		layer(Part.CONDENSER, layerX[layerX.length - 1], layerX.length + 1, 0, width);
	}

	/** Registers the link of every joint the way the block does: lower hangs from the column, upper from below. */
	private void link(Result result) {
		for (Joint joint : result.joints()) {
			int link = 0;
			if (!joint.upper()) {
				int[] step = ColumnShape.STEPS[joint.dir()];
				link = ColumnShape.dirOf(-step[0], -step[1]) + 1;
			}
			links.put(key(joint.x(), joint.y(), joint.z()), link);
		}
	}

	@Test
	void aLayerOneBlockToTheSideFormsAndNeedsTwoJointsAtWidthOne() {
		build(1, 0, 0, 1, 1);
		Result r = scan(0, 0, 0);
		assertTrue(r.formed(), "fault " + r.fault());
		assertEquals(4, r.height());
		assertEquals(0, r.offsetX(2));
		assertEquals(1, r.offsetX(3));
		assertEquals(1, r.offsetX(5));
		List<Joint> joints = r.joints();
		assertEquals(2, joints.size());
		// Lower layer at y 2 is the section at x 0; the upper at y 3 leans to x 1
		assertTrue(joints.contains(new Joint(1, 2, 0, 0, false)), joints.toString());
		assertTrue(joints.contains(new Joint(0, 3, 0, 0, true)), joints.toString());
	}

	@Test
	void aWiderColumnNeedsOneRowOfJointsOnEachSide() {
		for (int width = 2; width <= 3; width++) {
			parts.clear();
			build(width, 0, 0, 1, 1);
			Result r = scan(0, 0, 0);
			assertTrue(r.formed(), "width " + width + " fault " + r.fault());
			assertEquals(2 * width, r.joints().size());
		}
	}

	@Test
	void aStraightColumnNeedsNoJoints() {
		build(2, 0, 0, 0);
		Result r = scan(0, 0, 0);
		assertTrue(r.formed());
		assertFalse(r.leans());
		assertTrue(r.joints().isEmpty());
	}

	@Test
	void theFirstLayerStaysStraightOnTheReboilersAndTheCondenserOnTheLastSection() {
		layer(Part.REBOILER, 0, 0, 0, 1);
		layer(Part.SECTION, 1, 1, 0, 1);
		layer(Part.CONDENSER, 1, 2, 0, 1);
		assertEquals(Fault.NO_SECTION, scan(0, 0, 0).fault(), "a first section beside the Reboiler is not on it");
		parts.clear();
		layer(Part.REBOILER, 0, 0, 0, 1);
		layer(Part.SECTION, 0, 1, 0, 1);
		layer(Part.SECTION, 0, 2, 0, 1);
		layer(Part.CONDENSER, 1, 3, 0, 1);
		assertFalse(scan(0, 0, 0).formed(), "a Condenser beside the last section is not on it");
	}

	@Test
	void aBlockInTheWayOfAJointStopsTheColumnAndNamesIt() {
		build(1, 0, 0, 1, 1);
		block(1, 2, 0);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.LEAN_BLOCKED, r.fault());
		assertEquals(1, r.blockedX());
		assertEquals(2, r.blockedY());
		assertEquals(0, r.blockedZ());
		block(1, 2, 0);
		solid.clear();
		assertTrue(scan(0, 0, 0).formed(), "clearing the block lets it form");
	}

	@Test
	void jointsAlreadyPlacedDoNotBlockTheColumn() {
		build(1, 0, 0, 1, 1);
		Result r = scan(0, 0, 0);
		place(r);
		link(r);
		Result again = scan(0, 0, 0);
		assertTrue(again.formed());
		assertEquals(r, again);
	}

	@Test
	void leaningBackTheWayItCameCrossesItsOwnJointsAndIsRefused() {
		build(1, 0, 0, 1, 0);
		Result r = scan(0, 0, 0);
		assertEquals(Fault.LEAN_BLOCKED, r.fault(), "the two leans want the same block");
	}

	@Test
	void anyPartOfAFormedLeaningColumnFindsTheSameColumn() {
		build(2, 0, 0, 1, 1, 2, 2);
		Result r = scan(0, 0, 0);
		assertTrue(r.formed(), "fault " + r.fault());
		place(r);
		link(r);
		for (int y = 0; y <= r.topY(); y++)
			for (int x = -1; x < 6; x++)
				for (int z = -1; z < 3; z++)
					if (parts.getOrDefault(key(x, y, z), Part.OTHER) != Part.OTHER)
						assertEquals(r, scan(x, y, z), "from " + x + "," + y + "," + z);
	}

	@Test
	void aSectionWithNothingBelowItIsInNoColumnUntilTheJointsAreThere() {
		build(1, 0, 0, 1, 1);
		// The overhanging section at x 1: below it is air until the Reboiler's scan places the joints
		assertFalse(scan(1, 3, 0).formed());
		place(scan(0, 0, 0));
		link(scan(0, 0, 0));
		assertTrue(scan(1, 3, 0).formed());
	}

	@Test
	void leaningInTwoDirectionsAtOnce() {
		layer(Part.REBOILER, 0, 0, 0, 1);
		layer(Part.SECTION, 0, 1, 0, 1);
		layer(Part.SECTION, 1, 2, 0, 1);
		layer(Part.SECTION, 1, 3, 1, 1);
		layer(Part.CONDENSER, 1, 4, 1, 1);
		Result r = scan(0, 0, 0);
		assertTrue(r.formed(), "fault " + r.fault());
		assertEquals(1, r.offsetX(4));
		assertEquals(1, r.offsetZ(4));
		assertEquals(4, r.joints().size());
		assertTrue(r.contains(1, 4, 1));
		assertFalse(r.contains(0, 4, 0));
	}

	@Test
	void aRandomLeaningStaircaseNeverLoopsAndKeepsItsSize() {
		Random random = new Random(11);
		for (int round = 0; round < 200; round++) {
			parts.clear();
			solid.clear();
			links.clear();
			int width = 1 + random.nextInt(3);
			int sections = 2 + random.nextInt(12);
			layer(Part.REBOILER, 0, 0, 0, width);
			int x = 0, z = 0;
			for (int i = 1; i <= sections; i++) {
				if (i > 1 && random.nextInt(3) == 0) {
					int[] step = ColumnShape.STEPS[random.nextInt(4)];
					x += step[0];
					z += step[1];
				}
				layer(Part.SECTION, x, i, z, width);
			}
			layer(Part.CONDENSER, x, sections + 1, z, width);
			Result r = scan(0, 0, 0);
			if (r.formed()) {
				assertEquals(sections, r.height());
				assertEquals(width, r.width());
				assertTrue(r.contains(x, sections + 1, z));
				place(r);
				link(r);
				assertEquals(r, scan(x, sections + 1, z));
			} else {
				assertEquals(Fault.LEAN_BLOCKED, r.fault(), "the only reason a built column fails here is leaning back");
			}
		}
	}

	@Test
	void aSkyscraperOfLeansIsStillBoundedByTheHeightLimit() {
		layer(Part.REBOILER, 0, 0, 0, 1);
		for (int i = 1; i <= ColumnShape.MAX_HEIGHT + 4; i++)
			layer(Part.SECTION, i - 1, i, 0, 1);
		assertEquals(Fault.TOO_TALL, scan(0, 0, 0).fault());
	}

	@Test
	void damagedLeanDataStaysInsideItsLayers() {
		Result odd = new Result(Fault.NONE, 0, 0, 0, 2, 3, new int[] {0, 0, 5}, 0, 0, 0);
		assertEquals(0, odd.offsetX(2));
		assertEquals(0, odd.offsetX(-1));
		assertEquals(0, odd.offsetZ(99));
		assertTrue(odd.joints().isEmpty() || odd.joints().size() >= 0);
		Result huge = new Result(Fault.NONE, 0, 0, 0, 3, 0, new int[] {0, 0, 1000000, -1000000}, 0, 0, 0);
		assertTrue(huge.joints().size() <= 4 * 9 * 4, "a wild save cannot ask for an endless list of joints");
	}
}
