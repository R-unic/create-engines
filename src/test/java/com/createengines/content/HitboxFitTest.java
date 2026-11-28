package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The outline of an oddly shaped part is the part. Each shape in {@link PartBoxes} is held against the boxes of its
 * model: every solid piece of the model sits inside the shape (so nothing you can see is unclickable), and the shape
 * reaches no further than the model does (so no empty air is clickable). Pieces under 1.5 pixels thick (rivets, a
 * pointer, the rungs of a handle) are left out of the first check, and the second allows half a pixel, which is how
 * the shapes are rounded.
 */
class HitboxFitTest {

	private static final Path MODELS = Path.of("src/main/resources/assets/create_engines/models/block");

	/** Thinner than this, a piece of a model is detail, not something to aim at. */
	private static final double DETAIL = 1.5;
	private static final double SLACK = 0.5;
	private static final double EDGE = 0.01;

	private static List<double[]> elements(String model) throws IOException {
		JsonObject json = JsonParser.parseString(Files.readString(MODELS.resolve(model + ".json")))
			.getAsJsonObject();
		List<double[]> out = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("elements")) {
			JsonArray from = e.getAsJsonObject().getAsJsonArray("from");
			JsonArray to = e.getAsJsonObject().getAsJsonArray("to");
			out.add(new double[] {from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
				to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()});
		}
		return out;
	}

	private static boolean inAnyBox(double[][] shape, double x, double y, double z) {
		for (double[] b : shape)
			if (x >= b[0] - EDGE && x <= b[3] + EDGE && y >= b[1] - EDGE && y <= b[4] + EDGE && z >= b[2] - EDGE
				&& z <= b[5] + EDGE)
				return true;
		return false;
	}

	private static boolean solid(double[] p) {
		return Math.min(p[3] - p[0], Math.min(p[4] - p[1], p[5] - p[2])) >= DETAIL;
	}

	/** Every solid piece's corners and middle lie in the shape. */
	private static void assertCovers(String name, double[][] shape, List<double[]> pieces) {
		for (double[] piece : pieces) {
			// A hitbox stays in its block, so a flame or a flange that sticks out of it counts only as far as the block
			double[] p = {Math.max(0, piece[0]), Math.max(0, piece[1]), Math.max(0, piece[2]), Math.min(16, piece[3]),
				Math.min(16, piece[4]), Math.min(16, piece[5])};
			if (!solid(p))
				continue;
			// Look just inside the piece so a face lying exactly on the shape's face counts
			double[] lo = {p[0] + EDGE * 2, p[1] + EDGE * 2, p[2] + EDGE * 2};
			double[] hi = {p[3] - EDGE * 2, p[4] - EDGE * 2, p[5] - EDGE * 2};
			for (int i = 0; i < 9; i++) {
				double[] at = i == 8 ? new double[] {(p[0] + p[3]) / 2, (p[1] + p[4]) / 2, (p[2] + p[5]) / 2}
					: new double[] {(i & 1) == 0 ? lo[0] : hi[0], (i & 2) == 0 ? lo[1] : hi[1], (i & 4) == 0 ? lo[2] : hi[2]};
				assertTrue(inAnyBox(shape, at[0], at[1], at[2]), name + ": model piece " + Arrays.toString(p)
					+ " is not inside its hitbox at " + Arrays.toString(at));
			}
		}
	}

	/** No box of the shape pokes out of the model's own outline (clipped to the block) by more than the slack. */
	private static void assertNoPhantom(String name, double[][] shape, List<double[]> pieces) {
		double[] hull = PartBoxes.hull(pieces.toArray(new double[0][]));
		for (int i = 0; i < 3; i++) {
			hull[i] = Math.max(0, hull[i]);
			hull[i + 3] = Math.min(16, hull[i + 3]);
		}
		for (double[] b : shape)
			for (int i = 0; i < 3; i++) {
				assertTrue(b[i] >= hull[i] - SLACK, name + ": hitbox reaches past the model on the low side of axis " + i
					+ ": " + b[i] + " against " + hull[i]);
				assertTrue(b[i + 3] <= hull[i + 3] + SLACK, name + ": hitbox reaches past the model on the high side of axis "
					+ i + ": " + b[i + 3] + " against " + hull[i + 3]);
			}
	}

	/** The shaft Create draws through a kinetic part: 6 pixels across, the whole block long, along Y. */
	private static final double[] SHAFT = {5, 0, 5, 11, 16, 11};

	private static void fit(String name, double[][] shape, String... models) throws IOException {
		fit(name, shape, List.of(), models);
	}

	private static void fit(String name, double[][] shape, List<double[]> drawnInCode, String... models)
		throws IOException {
		List<double[]> pieces = new ArrayList<>(drawnInCode);
		for (String model : models)
			pieces.addAll(elements(model));
		assertCovers(name, shape, pieces);
		assertNoPhantom(name, shape, pieces);
	}

	@Test
	void flatShapesFitTheirModels() throws IOException {
		fit("black_box", PartBoxes.BLACK_BOX, "black_box/block");
		fit("governor", PartBoxes.GOVERNOR, "governor/block");
		fit("flare_stack", PartBoxes.FLARE_STACK, "flare_stack/block", "flare_stack/lit");
		fit("alternator", PartBoxes.ALTERNATOR, List.of(SHAFT), "alternator/block");
	}

	@Test
	void directionalShapesFitTheirModelsFacingUp() throws IOException {
		fit("starter", PartBoxes.STARTER, "starter/block");
		fit("air_intake", PartBoxes.AIR_INTAKE, "air_intake/block");
		fit("exhaust_outlet", PartBoxes.EXHAUST_OUTLET, "exhaust_outlet/block");
		fit("turbocharger", PartBoxes.TURBOCHARGER, "turbocharger/block");
		fit("variable_turbocharger", PartBoxes.VARIABLE_TURBOCHARGER, "variable_turbocharger/block");
	}

	@Test
	void inlineShapesFitTheirModels() throws IOException {
		fit("supercharger", PartBoxes.SUPERCHARGER, "supercharger/block");
		fit("variable_supercharger", PartBoxes.VARIABLE_SUPERCHARGER, "variable_supercharger/block");
		fit("air_filter", PartBoxes.AIR_FILTER, "air_filter/block");
		fit("intercooler", PartBoxes.INTERCOOLER, "intercooler/block");
		fit("muffler", PartBoxes.EXHAUST_CANISTER, "muffler/block");
		fit("soot_trap", PartBoxes.EXHAUST_CANISTER, "soot_trap/block");
	}

	/** The outline's hull alone does not show empty air beside a narrow part of a model, so some of it is named here. */
	@Test
	void theAirBesideNarrowPartsIsNotClickable() {
		assertEmpty("air filter drum", PartBoxes.AIR_FILTER, 2.5, 8, 8);
		assertEmpty("intercooler core", PartBoxes.INTERCOOLER, 2.5, 8, 8);
		assertEmpty("muffler flange", PartBoxes.EXHAUST_CANISTER, 2.5, 0.5, 8);
		assertEmpty("turbocharger waist", PartBoxes.TURBOCHARGER, 2, 8, 8);
		assertEmpty("exhaust pipe", PartBoxes.EXHAUST_OUTLET, 5.2, 10, 8);
		assertEmpty("throttle side", PartBoxes.THROTTLE_BODY, 3.7, 8, 8);
		assertEmpty("sensor side", PartBoxes.MAF_BODY, 3.7, 8, 8);
		assertEmpty("between exchanger fins and cap", PartBoxes.EXCHANGER_BODY, 2.7, 11, 8);
	}

	private static void assertEmpty(String name, double[][] shape, double x, double y, double z) {
		assertTrue(!inAnyBox(shape, x, y, z), name + ": clickable where there is nothing");
	}

	@Test
	void pipeBodiesFitTheirModels() throws IOException {
		fit("throttle", PartBoxes.THROTTLE_BODY, "throttle/core");
		fit("maf_sensor", PartBoxes.MAF_BODY, "maf_sensor/core");
		fit("heat_exchanger", PartBoxes.EXCHANGER_BODY, "heat_exchanger/core");
	}

	/** A turning part is drawn at rest, so only the first check applies: the swept shape must hold the lobes and the web. */
	@Test
	void turningPartsHoldTheirModelAtRest() throws IOException {
		// The shaft through the block is the block's own pole; here the lobes (y 4 to 12) and the web (y 3 to 13)
		List<double[]> lobes = elements("cam_lobe/block").stream().filter(p -> p[1] >= 3.5 && p[4] <= 12.5).toList();
		assertCovers("cam_lobe", PartBoxes.CAM_LOBE, lobes);
		List<double[]> web = elements("crank_throw/block").stream().filter(p -> p[1] >= 3 && p[4] <= 13).toList();
		assertCovers("crank_throw", PartBoxes.CRANK_THROW, web);
	}

	@Test
	void directionsLieOnTheSideTheModelPointsAwayFrom() {
		// A plate drawn on the floor (y 0) is behind the part: opposite the way it faces
		double[][] plate = {{0, 0, 0, 16, 2, 16}};
		assertArrayEquals(new double[] {0, 0, 0, 16, 2, 16}, PartBoxes.directional(PartBoxes.UP, plate)[0]);
		assertArrayEquals(new double[] {0, 14, 0, 16, 16, 16}, PartBoxes.directional(PartBoxes.DOWN, plate)[0]);
		assertArrayEquals(new double[] {0, 0, 14, 16, 16, 16}, PartBoxes.directional(PartBoxes.NORTH, plate)[0]);
		assertArrayEquals(new double[] {0, 0, 0, 16, 16, 2}, PartBoxes.directional(PartBoxes.SOUTH, plate)[0]);
		assertArrayEquals(new double[] {0, 0, 0, 2, 16, 16}, PartBoxes.directional(PartBoxes.EAST, plate)[0]);
		assertArrayEquals(new double[] {14, 0, 0, 16, 16, 16}, PartBoxes.directional(PartBoxes.WEST, plate)[0]);
	}

	@Test
	void asymmetricPartsTurnAsTheirBlockstateTurnsThem() {
		// A lug on +x when the part faces up. East is x=90 then y=90, which takes +x round to +z (south); south is
		// x=90 then y=180, which takes it to -x; west takes it to -z.
		double[][] lug = {{12, 6, 6, 16, 10, 10}};
		assertArrayEquals(new double[] {6, 6, 12, 10, 10, 16}, PartBoxes.directional(PartBoxes.EAST, lug)[0], 0.0001);
		assertArrayEquals(new double[] {0, 6, 6, 4, 10, 10}, PartBoxes.directional(PartBoxes.SOUTH, lug)[0], 0.0001);
		assertArrayEquals(new double[] {6, 6, 0, 10, 10, 4}, PartBoxes.directional(PartBoxes.WEST, lug)[0], 0.0001);
	}

	@Test
	void everyShapeStaysInsideItsBlock() {
		double[][][] all = {PartBoxes.ALTERNATOR, PartBoxes.BLACK_BOX, PartBoxes.GOVERNOR, PartBoxes.FLARE_STACK,
			PartBoxes.STARTER, PartBoxes.AIR_INTAKE, PartBoxes.EXHAUST_OUTLET, PartBoxes.TURBOCHARGER,
			PartBoxes.CAM_LOBE, PartBoxes.CRANK_THROW, PartBoxes.THROTTLE_BODY, PartBoxes.MAF_BODY,
			PartBoxes.EXCHANGER_BODY, PartBoxes.VARIABLE_TURBOCHARGER, PartBoxes.SUPERCHARGER,
			PartBoxes.VARIABLE_SUPERCHARGER, PartBoxes.AIR_FILTER, PartBoxes.INTERCOOLER, PartBoxes.EXHAUST_CANISTER};
		for (double[][] boxes : all)
			for (int facing = 0; facing < 6; facing++)
				for (double[] b : PartBoxes.directional(facing, boxes))
					for (int i = 0; i < 3; i++) {
						assertTrue(b[i] >= 0 && b[i + 3] <= 16, "box leaves the block: " + Arrays.toString(b));
						assertTrue(b[i + 3] > b[i], "empty box: " + Arrays.toString(b));
					}
		for (double[][] boxes : all)
			for (int axis = 0; axis < 3; axis++)
				for (double[] b : PartBoxes.axial(axis, boxes))
					for (int i = 0; i < 3; i++)
						assertTrue(b[i] >= 0 && b[i + 3] <= 16, "box leaves the block: " + Arrays.toString(b));
	}
}
