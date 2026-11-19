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
 * The cast flywheel's wheel model ({@code tools/generate_models.py}, {@code cast_flywheel()}): a spoked wheel that turns
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
