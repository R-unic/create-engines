package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
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

/**
 * Tests written to kill mutants that the earlier suites let live (the mutation run, `./gradlew pitest`). Each one says
 * which line it pins. Every expected value is worked out from the code's documentation or by hand, not read off a run.
 * Fast and exact on purpose: nothing here depends on timing or on the order threads run in.
 */
class SurvivorEdgesTest {

	// ---- ColumnShape.Result.joints: the last layer's joints, and the direction of the step into a layer --------------------

	/** East one block, then back west one: two layers of a width-2 column, so a joint pair on each side of each step. */
	@Test
	void aColumnThatLeansEastThenBackWestNeedsEightJointsAtTheExactPlacesFacingTheRightWays() {
		// Layers 0, 1, 2 sit at x offsets 0, 1, 0 (so the height is 1: layers 0 to height + 1)
		Result r = new Result(Fault.NONE, 0, 0, 0, 2, 1, new int[] {0, 0, 1, 0, 0, 0}, 0, 0, 0);
		List<Joint> joints = r.joints();
		// Step 0 to 1 is east (dir 0). Layer 0 covers x 0..1, layer 1 covers x 1..2: x 2 is only in the upper layer (a joint
		// under it, one up from the lower layer's floor, not upper), x 0 is only in the lower layer (a joint above it)
		// Step 1 to 2 is west (dir 1): x 0 is only in the new layer, x 2 only in the old one
		Set<Joint> expected = new HashSet<>();
		for (int z = 0; z < 2; z++) {
			expected.add(new Joint(2, 0, z, 0, false));
			expected.add(new Joint(0, 1, z, 0, true));
			expected.add(new Joint(0, 1, z, 1, false));
			expected.add(new Joint(2, 2, z, 1, true));
		}
		assertEquals(8, joints.size(), "four joints for each of the two steps: " + joints);
		assertEquals(expected, new HashSet<>(joints));
	}

	// ---- ColumnShape.key: no two different joint places may be taken for one ------------------------------------------

	private final Map<String, Part> parts = new HashMap<>();

	private static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	private void layer(Part part, int x0, int y, int z0, int width) {
		for (int x = x0; x < x0 + width; x++)
			for (int z = z0; z < z0 + width; z++)
				parts.put(key(x, y, z), part);
	}

	/**
	 * A column is refused for leaning only when two of its joints want the same block. Built at places where each of x, y and z
	 * is far from zero and negative, and with every kind of staircase, a column is refused exactly when two of its joints
	 * share a place by their coordinates, and not otherwise: a place key that lost a coordinate (or mixed two together)
	 * would refuse columns that have room, or pass ones that do not.
	 */
	@Test
	void aLeaningColumnIsRefusedExactlyWhenTwoOfItsJointsWantTheSameBlock() {
		Random random = new Random(41);
		int formed = 0, refused = 0;
		int[][] bases = {{0, 0, 0}, {-37, 5, -90}, {1_000_000, 200, 70_000}, {-2_000_000, 100, 3}, {64, 4000, -64}};
		for (int round = 0; round < 1500; round++) {
			parts.clear();
			int[] base = bases[round % bases.length];
			int bx = base[0], by = base[1], bz = base[2];
			int width = 1 + random.nextInt(3);
			int sections = 2 + random.nextInt(5);
			layer(Part.REBOILER, bx, by, bz, width);
			int x = bx, z = bz;
			// One x, z pair for each layer from the base (0) to the condenser (sections + 1), as offsets from the base corner
			int[] lean = new int[2 * (sections + 2)];
			for (int i = 1; i <= sections; i++) {
				if (i > 1 && random.nextInt(2) == 0) {
					int[] step = ColumnShape.STEPS[random.nextInt(4)];
					x += step[0];
					z += step[1];
				}
				layer(Part.SECTION, x, by + i, z, width);
				lean[2 * i] = x - bx;
				lean[2 * i + 1] = z - bz;
			}
			layer(Part.CONDENSER, x, by + sections + 1, z, width);
			lean[2 * (sections + 1)] = x - bx;
			lean[2 * (sections + 1) + 1] = z - bz;
			Result scanned = ColumnShape.scan((px, py, pz) -> parts.getOrDefault(key(px, py, pz), Part.OTHER), bx, by, bz);
			assertTrue(scanned.fault() == Fault.NONE || scanned.fault() == Fault.LEAN_BLOCKED,
				"round " + round + ": the only way these fail is leaning back: " + scanned.fault());
			Result built = new Result(Fault.NONE, bx, by, bz, width, sections, lean, 0, 0, 0);
			Set<String> places = new HashSet<>();
			boolean shared = false;
			for (Joint joint : built.joints())
				shared |= !places.add(key(joint.x(), joint.y(), joint.z()));
			assertEquals(shared ? Fault.LEAN_BLOCKED : Fault.NONE, scanned.fault(), "round " + round);
			if (shared)
				refused++;
			else
				formed++;
		}
		assertTrue(formed > 100 && refused > 100, "a test that never refuses, or always does, proves nothing: " + formed + "/" + refused);
	}

	// ---- The small pure rules, each on the exact edge the earlier tests stepped over -------------------------------------------

	/** CamProfile.lift: a valve whose angle is exactly where it closes is shut (0), not the 1e-16 that sin(pi) leaves. */
	@Test
	void aValveAtTheExactAngleItClosesIsShutNotAHairOpen() {
		CamProfile cam = CamProfile.BASIC;
		assertEquals(0.0, cam.intakeLift(cam.intakeClose()), 0.0);
		assertEquals(0.0, cam.exhaustLift(cam.exhaustClose()), 0.0);
		// and a quarter of the way through its open time it is at sin(pi / 4)
		double duration = SimMath.wrap(cam.intakeClose() - cam.intakeOpen());
		assertEquals(Math.sin(Math.PI / 4), cam.intakeLift(cam.intakeOpen() + duration / 4), 1e-12);
	}

	/** SimMath.approach: up to 0.25 exactly the series is used (1 - e^-x is used above it), and the two differ by 4e-10 there. */
	@Test
	void approachUsesTheSeriesAtExactlyOneQuarter() {
		double x = 0.25;
		double series = x * (1 - x * (1 / 2d - x * (1 / 6d - x * (1 / 24d - x * (1 / 120d - x * (1 / 720d - x / 5040d))))));
		assertEquals(series, SimMath.approach(x), 1e-15);
		assertEquals(1 - Math.exp(-0.2500001), SimMath.approach(0.2500001), 1e-15);
	}

	/** WindowTint.byOrdinal: one past the last ordinal is out of range, so amber, not a read off the end of the list. */
	@Test
	void aTintOrdinalOnePastTheEndIsAmber() {
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(WindowTint.values().length));
		assertSame(WindowTint.values()[WindowTint.values().length - 1], WindowTint.byOrdinal(WindowTint.values().length - 1));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(-1));
		assertSame(WindowTint.PLANT_OIL, WindowTint.byOrdinal(WindowTint.PLANT_OIL.ordinal()));
	}

	/** PlateFigures.of: a curve with torque but no power at all (every speed zero) makes no plate. */
	@Test
	void aCurveWithTorqueButNoPowerMakesNoPlate() {
		assertNull(PlateFigures.of(new float[] {0}, new float[] {5}));
		assertNull(PlateFigures.of(new float[] {100, 200}, new float[] {0, 0}));
	}

	/** FiringPattern.describe: "all even" needs more than two intervals; two equal ones are listed. */
	@Test
	void twoEqualFiringIntervalsAreListedAndThreeAreCalledEven() {
		assertEquals("360° / 360°", FiringPattern.describe(new float[] {360, 360}));
		assertEquals("240° apart, all even", FiringPattern.describe(new float[] {240, 240, 240}));
	}

	/** ColumnAdvantage.percentMore: a basin that yields nothing is no baseline to be better than, so no claim. */
	@Test
	void aColumnIsNotClaimedBetterThanABasinThatYieldsNothing() {
		assertEquals(0, ColumnAdvantage.percentMore(5, 0));
		assertEquals(0, ColumnAdvantage.percentMore(0, 5));
		assertEquals(25, ColumnAdvantage.percentMore(5, 4));
	}

	/**
	 * RunComparison: the powerband takes in every speed whose power is within 90% of the peak, on both sides, and no speed
	 * outside it. Peak power is 3000 at 300 RPM; 200 and 400 give 2850 (inside), 100 and 500 give 100 and 500 (outside).
	 */
	@Test
	void thePowerbandIsEveryNeighbouringSpeedWithinNinetyPercentOfThePeakPower() {
		float[] rpm = {100, 200, 300, 400, 500};
		float[] torque = {1, 14.25f, 10, 7.125f, 1};
		RunComparison c = RunComparison.between(new float[] {300}, new float[] {1}, rpm, torque);
		assertEquals(200f, c.bandDelta(), 0f);
		assertEquals(13.25f, c.torqueDelta(), 0f);
		assertEquals(2700f, c.powerDelta(), 0f);
		assertEquals(0f, c.peakShift(), 0f);
		assertEquals(1325f, c.torquePercent(), 0f);
		assertEquals(900f, c.powerPercent(), 0f);
	}

	/** SootCollector.share: an engine whose exhaust runs have no heads at all (a share of 1 / 0) gives a trap nothing. */
	@Test
	void aTrapIsGivenNoSootWhenTheEngineHasNoHeadsOnItsRuns() {
		assertEquals(0.0, SootCollector.share(10, 1, 0, 1f, 1), 0.0);
		assertEquals(0.0, SootCollector.share(10, 0, 4, 1f, 1), 0.0);
		assertEquals(0.0, SootCollector.share(10, 1, 4, 1f, 0), 0.0);
		assertEquals(0.0, SootCollector.share(0, 1, 4, 1f, 1), 0.0);
		// and the ordinary case: a quarter of the heads, the whole of it caught, two traps share it
		assertEquals(1.25, SootCollector.share(10, 1, 4, 1f, 2), 1e-12);
	}

	// ---- The lookup tables: built the first time a shape is asked for, and never again, so these use shapes nobody has asked for ----

	/**
	 * A cam profile's lift table, built fresh (a profile no other test uses, so it is not in the cache), reads back what the
	 * formula gives at every sample, the last ones past the end of a cycle included, and between samples to a few millionths.
	 */
	@Test
	void aFreshCamProfileTableHoldsTheFormulaAtEverySample() throws Exception {
		CamProfile cam = new CamProfile(703.5, 213.25, 503.5, 17.75, 341, 352);
		// Taken out of the cache first and after: a mutant run asks this test many times in one JVM, and a table that is already
		// cached is never built again, so the code that builds it would not run
		Map<Object, Object> cache = staticMap(CamProfile.class, "CURVES");
		cache.remove(cam);
		try {
			checkCamTables(cam);
		} finally {
			cache.remove(cam);
		}
	}

	private static void checkCamTables(CamProfile cam) {
		CamProfile.Curve intake = cam.intakeCurve();
		CamProfile.Curve exhaust = cam.exhaustCurve();
		for (int k = 0; k <= 720 * 8; k++) {
			double angle = k / 8d;
			assertEquals(cam.intakeLift(angle), intake.at(angle), 1e-12, "intake at " + angle);
			assertEquals(cam.exhaustLift(angle), exhaust.at(angle), 1e-12, "exhaust at " + angle);
		}
		// Between samples it reads a straight line between them, close to the formula
		for (double angle = 0.0123; angle < 720; angle += 0.3711) {
			assertEquals(cam.intakeLift(angle), intake.at(angle), 2e-5, "intake between samples at " + angle);
			assertEquals(cam.exhaustLift(angle), exhaust.at(angle), 2e-5, "exhaust between samples at " + angle);
		}
		// and it is built once, then shared
		assertSame(intake, cam.intakeCurve());
		assertSame(exhaust, cam.exhaustCurve());
	}

	/** A cylinder shape's volume and heat tables, built fresh, hold the formulas at every sample of a revolution and after it. */
	@Test
	void aFreshCylinderShapeTableHoldsTheFormulaAtEverySample() throws Exception {
		CylinderSpec spec = new CylinderSpec(7.31, 0.2857, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.2);
		Map<Object, Object> cache = staticMap(CylinderSpec.class, "GEOMETRIES");
		cache.remove(spec);
		try {
			checkShapeTables(spec);
		} finally {
			cache.remove(spec);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> staticMap(Class<?> owner, String field) throws Exception {
		Field f = owner.getDeclaredField(field);
		f.setAccessible(true);
		return (Map<Object, Object>) f.get(null);
	}

	private static void checkShapeTables(CylinderSpec spec) {
		CylinderSpec.Geometry geometry = spec.geometry();
		for (int k = 0; k <= 360 * 8 + 1; k++) {
			double angle = k / 8d;
			assertEquals(spec.volumeAt(angle), geometry.volume(angle), 1e-12, "volume at " + angle);
			assertEquals(Math.pow(spec.volumeAt(angle), CylinderModel.GAMMA - 1), geometry.heat(angle), 1e-12, "heat at " + angle);
		}
		for (double angle = 0.0123; angle < 360; angle += 0.2711) {
			assertEquals(spec.volumeAt(angle), geometry.volume(angle), 1e-5 * spec.volumeAt(angle), "volume between samples at " + angle);
		}
		assertSame(geometry, spec.geometry());
	}
}
