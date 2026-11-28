package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createengines.smoke.PrecisionHeadZFightTest.Box;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The cast flywheel's wheel model ({@code tools/models/flywheel.py}, {@code cast_flywheel()}): a spoked wheel that turns
 * with the shaft. Its look changed in 2.0 while the block, its mass and its collision shape stayed as they were, so these
 * pin the parts of the model that the rest of the block depends on. Fix a failure in the generator, never by widening a
 * bound here.
 */
class CastFlywheelModelTest {

	private static final Path DIR = Path.of("src/main/resources/assets/create_engines/models/block/cast_flywheel");
	/** The block's collision shape across the axis ({@code CastFlywheelBlock.SHAPES}): the wheel stays within it. */
	private static final double THICK_LOW = 4, THICK_HIGH = 12;
	private static final double EPS = 1e-6;

	private static List<Box> wheel() throws IOException {
		return PrecisionHeadZFightTest.read(DIR.resolve("disc.json"));
	}

	private static boolean isShaft(Box b) {
		return b.from()[0] == 6 && b.from()[1] == 0 && b.from()[2] == 6 && b.to()[0] == 10 && b.to()[1] == 16 && b.to()[2] == 10;
	}

	/** The wheel without its shaft, which runs the full length of the block and is Create's own. */
	private static List<Box> body() throws IOException {
		List<Box> out = new ArrayList<>(wheel());
		out.removeIf(CastFlywheelModelTest::isShaft);
		return out;
	}

	@Test
	void theWheelCarriesAFullLengthShaftSoItTurnsWithTheAxle() throws IOException {
		assertEquals(1, wheel().stream().filter(CastFlywheelModelTest::isShaft).count());
	}

	@Test
	void noFacesOnTheWheelOrItsCollarsShareAPlane() throws IOException {
		for (String name : new String[] {"disc", "block"}) {
			List<String> found = PrecisionHeadZFightTest.fights(PrecisionHeadZFightTest.read(DIR.resolve(name + ".json")));
			assertTrue(found.isEmpty(), name + " has z-fighting faces: " + found);
		}
	}

	/**
	 * The block model (the collars) and the wheel model (which turns with the shaft) are drawn together, so faces that share
	 * a plane across the two fight just as faces within one do. The shaft's end caps once lay in the collars' outer planes.
	 * The check counts only the faces an element really has, since the shaft has no caps.
	 */
	@Test
	void theCollarsAndTheWheelDoNotShareAPlaneBetweenThem() throws IOException {
		{
			List<String> found = new ArrayList<>();
			var collars = elementsWithFaces("block");
			var disc = elementsWithFaces("disc");
			String[] faces = {"north", "south", "east", "west", "up", "down"};
			for (String face : faces) {
				int a = face.equals("east") || face.equals("west") ? 0 : face.equals("up") || face.equals("down") ? 1 : 2;
				boolean positive = face.equals("east") || face.equals("up") || face.equals("south");
				int u = (a + 1) % 3, v = (a + 2) % 3;
				for (var p : collars)
					for (var q : disc) {
						if (!p.faces.contains(face) || !q.faces.contains(face))
							continue;
						double pp = positive ? p.box.to()[a] : p.box.from()[a];
						double qp = positive ? q.box.to()[a] : q.box.from()[a];
						if (Math.abs(pp - qp) > EPS)
							continue;
						double ou = Math.min(p.box.to()[u], q.box.to()[u]) - Math.max(p.box.from()[u], q.box.from()[u]);
						double ov = Math.min(p.box.to()[v], q.box.to()[v]) - Math.max(p.box.from()[v], q.box.from()[v]);
						if (ou > EPS && ov > EPS)
							found.add(face + " at " + pp);
					}
			}
			assertTrue(found.isEmpty(), "the collars and the wheel model share planes: " + found);
		}
		// The inventory model is the two put together, so it is free of them too
		assertEquals(elementsWithFaces("block").size() + elementsWithFaces("disc").size(), elementsWithFaces("item").size());
	}

	private record Faced(Box box, java.util.Set<String> faces) {
	}

	private static List<Faced> elementsWithFaces(String model) throws IOException {
		List<Faced> out = new ArrayList<>();
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve(model + ".json"))) {
			for (var e : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
				var o = e.getAsJsonObject();
				double[] from = new double[3], to = new double[3];
				for (int i = 0; i < 3; i++) {
					from[i] = o.getAsJsonArray("from").get(i).getAsDouble();
					to[i] = o.getAsJsonArray("to").get(i).getAsDouble();
				}
				out.add(new Faced(new Box(from, to), new java.util.HashSet<>(o.getAsJsonObject("faces").keySet())));
			}
		}
		return out;
	}

	@Test
	void theWheelStaysInsideTheBlockAndItsCollisionThickness() throws IOException {
		for (Box b : body()) {
			for (int a = 0; a < 3; a++) {
				assertTrue(b.from()[a] >= -EPS && b.to()[a] <= 16 + EPS, "an element leaves the block on axis " + a);
			}
			assertTrue(b.from()[1] >= THICK_LOW - EPS && b.to()[1] <= THICK_HIGH + EPS,
				"an element is thicker than the collision shape: " + b.from()[1] + " to " + b.to()[1]);
		}
	}

	/** The element set is the same turned end for end, left for right and front for back, so the wheel does not wobble as it spins. */
	@Test
	void theWheelIsSymmetricAboutItsAxis() throws IOException {
		List<Box> boxes = body();
		for (int axis = 0; axis < 3; axis++) {
			final int a = axis;
			for (Box b : boxes) {
				double lo = 16 - b.to()[a], hi = 16 - b.from()[a];
				boolean found = boxes.stream().anyMatch(o -> matches(o, b, a, lo, hi));
				assertTrue(found, "no mirror image on axis " + a + " of the element " + java.util.Arrays.toString(b.from())
					+ " to " + java.util.Arrays.toString(b.to()));
			}
		}
	}

	private static boolean matches(Box o, Box b, int mirrored, double lo, double hi) {
		for (int a = 0; a < 3; a++) {
			double f = a == mirrored ? lo : b.from()[a];
			double t = a == mirrored ? hi : b.to()[a];
			if (Math.abs(o.from()[a] - f) > EPS || Math.abs(o.to()[a] - t) > EPS)
				return false;
		}
		return true;
	}

	/** Still a full-size wheel: the rim reaches near each edge of the block, so the silhouette is not smaller than before. */
	@Test
	void theRimReachesNearTheEdgesOfTheBlock() throws IOException {
		double min = 16, max = 0;
		for (Box b : body()) {
			min = Math.min(min, Math.min(b.from()[0], b.from()[2]));
			max = Math.max(max, Math.max(b.to()[0], b.to()[2]));
		}
		assertTrue(min <= 1, "the rim starts at " + min);
		assertTrue(max >= 15, "the rim ends at " + max);
	}

	/** The point of the redesign: the wheel is open between its spokes, so it reads as a wheel and not as a slab. */
	@Test
	void thereIsAnOpeningBetweenEachPairOfSpokes() throws IOException {
		double[][] windows = {{4.75, 4.75}, {11.25, 4.75}, {4.75, 11.25}, {11.25, 11.25}};
		for (double[] w : windows) {
			boolean covered = false;
			for (Box b : body())
				if (w[0] > b.from()[0] && w[0] < b.to()[0] && w[1] > b.from()[2] && w[1] < b.to()[2])
					covered = true;
			assertFalse(covered, "something fills the opening at " + w[0] + ", " + w[1]);
		}
	}

	/**
	 * Ponder, and a world with Flywheel off, draw a block entity through its renderer, not its visual. Create's plain
	 * kinetic renderer draws only a shaft, so the wheel was missing from the Ponder page and only the collars showed.
	 */
	@Test
	void theWheelIsDrawnByItsOwnRendererWhereFlywheelIsNotUsed() throws IOException {
		String source = java.nio.file.Files.readString(
			Path.of("src/main/java/com/createengines/registry/CEBlockEntities.java"));
		int at = source.indexOf("CAST_FLYWHEEL = REGISTRATE");
		assertTrue(at >= 0, "the cast flywheel registration moved");
		String registration = source.substring(at, source.indexOf(".register();", at));
		assertTrue(registration.contains("EnginePartRenderers.CastFlywheel"), registration);
		assertFalse(registration.contains("KineticBlockEntityRenderer"), registration);
	}

	/** The item is drawn from above-left, so it is turned to put the wheel's face, not its shaft cap, toward the viewer. */
	@Test
	void theInventoryItemIsTurnedToShowTheWheelFace() throws IOException {
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve("item.json"))) {
			var rotation = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("display")
				.getAsJsonObject("gui").getAsJsonArray("rotation");
			assertEquals(90, rotation.get(2).getAsInt());
		}
	}
}
