package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createengines.smoke.PrecisionHeadZFightTest.Box;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The dual-mass flywheel's wheel model ({@code tools/models/flywheel.py}, {@code dual_mass_flywheel()}). It shares the Cast
 * Flywheel's block shape, so the model must stay inside the same collision thickness, carry a full-length shaft so it turns
 * with the axle, and not fight itself or its collars for pixels. Fix a failure in the generator, never by widening a bound.
 */
class DualMassFlywheelModelTest {

	private static final Path DIR = Path.of("src/main/resources/assets/create_engines/models/block/dual_mass_flywheel");
	/** The wheel runs a pixel past the block's collision shape (4 to 12, from the Cast Flywheel) either way, as Create's own does. */
	private static final double THICK_LOW = 2.5, THICK_HIGH = 13;
	private static final double EPS = 1e-6;

	/** The wheel's straight pieces. The ones turned 45 degrees about the shaft are checked by their turned extent, below. */
	private static List<Box> disc() throws IOException {
		List<Box> out = new ArrayList<>();
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve("disc.json"))) {
			for (var e : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
				var o = e.getAsJsonObject();
				if (o.has("rotation"))
					continue;
				out.add(new Box(numbers(o.getAsJsonArray("from")), numbers(o.getAsJsonArray("to"))));
			}
		}
		return out;
	}

	private static double[] numbers(com.google.gson.JsonArray array) {
		double[] out = new double[3];
		for (int i = 0; i < 3; i++)
			out[i] = array.get(i).getAsDouble();
		return out;
	}

	/** Across-axis extent {low, high} of every piece of the wheel, with the turned ones rotated about the shaft. */
	private static double[] reach() throws IOException {
		double low = 99, high = -99;
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve("disc.json"))) {
			for (var e : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
				var o = e.getAsJsonObject();
				double[] from = numbers(o.getAsJsonArray("from")), to = numbers(o.getAsJsonArray("to"));
				double angle = 0, ox = 8, oz = 8;
				if (o.has("rotation")) {
					var r = o.getAsJsonObject("rotation");
					assertEquals("y", r.get("axis").getAsString(), "turned pieces turn about the shaft");
					angle = Math.toRadians(r.get("angle").getAsDouble());
					ox = r.getAsJsonArray("origin").get(0).getAsDouble();
					oz = r.getAsJsonArray("origin").get(2).getAsDouble();
				}
				for (double x : new double[] {from[0], to[0]})
					for (double z : new double[] {from[2], to[2]}) {
						double dx = x - ox, dz = z - oz;
						double rx = ox + dx * Math.cos(angle) - dz * Math.sin(angle);
						double rz = oz + dx * Math.sin(angle) + dz * Math.cos(angle);
						low = Math.min(low, Math.min(rx, rz));
						high = Math.max(high, Math.max(rx, rz));
					}
			}
		}
		return new double[] {low, high};
	}

	private static boolean isShaft(Box b) {
		return b.from()[0] == 6 && b.from()[1] == 0 && b.from()[2] == 6 && b.to()[0] == 10 && b.to()[1] == 16 && b.to()[2] == 10;
	}

	private static List<Box> body() throws IOException {
		List<Box> out = new ArrayList<>(disc());
		out.removeIf(DualMassFlywheelModelTest::isShaft);
		return out;
	}

	/**
	 * Minecraft refuses a model with an element turned by anything but -45, -22.5, 0, 22.5 or 45 degrees, and a model it refuses
	 * is drawn as the purple and black missing texture. So no model of the mod, the wheel's included, may turn an element any
	 * other angle: a quarter turn is the same box with its ranges swapped.
	 */
	@Test
	void noModelTurnsAnElementByAnAngleTheGameRefuses() throws IOException {
		List<String> bad = new ArrayList<>();
		try (var files = java.nio.file.Files.walk(DIR.getParent())) {
			for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
				try (var reader = java.nio.file.Files.newBufferedReader(file)) {
					var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
					if (!root.has("elements"))
						continue;
					for (var e : root.getAsJsonArray("elements")) {
						var o = e.getAsJsonObject();
						if (!o.has("rotation"))
							continue;
						double angle = o.getAsJsonObject("rotation").get("angle").getAsDouble();
						if (angle != 0 && Math.abs(angle) != 22.5 && Math.abs(angle) != 45)
							bad.add(DIR.getParent().relativize(file) + " turns an element " + angle + " degrees");
					}
				}
			}
		}
		assertTrue(bad.isEmpty(), "the game would not load these models: " + bad.stream().distinct().toList());
	}

	@Test
	void theWheelCarriesAFullLengthShaftSoItTurnsWithTheAxle() throws IOException {
		assertEquals(1, disc().stream().filter(DualMassFlywheelModelTest::isShaft).count());
	}

	private record Piece(Box box, double angle, String texture) {
	}

	/** Every piece of the wheel, with a turn of a quarter or a half folded into an axis-aligned box. */
	private static List<Piece> pieces() throws IOException {
		List<Piece> out = new ArrayList<>();
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve("disc.json"))) {
			for (var e : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
				var o = e.getAsJsonObject();
				double[] from = numbers(o.getAsJsonArray("from")), to = numbers(o.getAsJsonArray("to"));
				double angle = o.has("rotation") ? o.getAsJsonObject("rotation").get("angle").getAsDouble() : 0;
				if (Math.abs(angle) == 90) {
					// about (8, 8): a quarter turn one way takes (x, z) to (16 - z, x), the other way to (z, 16 - x)
					boolean first = angle > 0;
					double[] f = first ? new double[] {16 - to[2], from[1], from[0]} : new double[] {from[2], from[1], 16 - to[0]};
					double[] t = first ? new double[] {16 - from[2], to[1], to[0]} : new double[] {to[2], to[1], 16 - from[0]};
					from = f;
					to = t;
					angle = 0;
				}
				String texture = o.getAsJsonObject("faces").entrySet().iterator().next().getValue().getAsJsonObject()
					.get("texture").getAsString();
				out.add(new Piece(new Box(from, to), angle, texture));
			}
		}
		return out;
	}

	@Test
	void noFacesShareAPlaneWithinAnyOfTheModels() throws IOException {
		// Pieces turned the same way share a frame, so the usual check holds for each group of them
		java.util.Map<Double, List<Box>> groups = new java.util.TreeMap<>();
		for (Piece p : pieces())
			groups.computeIfAbsent(p.angle(), k -> new ArrayList<>()).add(p.box());
		for (var group : groups.entrySet()) {
			List<String> found = PrecisionHeadZFightTest.fights(group.getValue());
			assertTrue(found.isEmpty(), "pieces turned " + group.getKey() + " degrees have z-fighting faces: " + found);
		}
		List<String> found = PrecisionHeadZFightTest.fights(PrecisionHeadZFightTest.read(DIR.resolve("block.json")));
		assertTrue(found.isEmpty(), "block has z-fighting faces: " + found);
	}

	/** Whether the turned piece's footprint (a rectangle turned about the shaft) and the straight piece's overlap, by a separating-axis test. */
	private static boolean footprintsOverlap(Piece turned, Piece straight) {
		double angle = Math.toRadians(turned.angle());
		double cos = Math.cos(angle), sin = Math.sin(angle);
		double[][] a = new double[4][2];
		double[] f = turned.box().from(), t = turned.box().to();
		double[][] corners = {{f[0], f[2]}, {t[0], f[2]}, {t[0], t[2]}, {f[0], t[2]}};
		for (int i = 0; i < 4; i++) {
			double dx = corners[i][0] - 8, dz = corners[i][1] - 8;
			a[i] = new double[] {8 + dx * cos - dz * sin, 8 + dx * sin + dz * cos};
		}
		double[] g = straight.box().from(), h = straight.box().to();
		double[][] b = {{g[0], g[2]}, {h[0], g[2]}, {h[0], h[2]}, {g[0], h[2]}};
		double[][] axes = {{1, 0}, {0, 1}, {cos, sin}, {-sin, cos}};
		for (double[] axis : axes) {
			double minA = 1e9, maxA = -1e9, minB = 1e9, maxB = -1e9;
			for (double[] p : a) {
				double d = p[0] * axis[0] + p[1] * axis[1];
				minA = Math.min(minA, d);
				maxA = Math.max(maxA, d);
			}
			for (double[] p : b) {
				double d = p[0] * axis[0] + p[1] * axis[1];
				minB = Math.min(minB, d);
				maxB = Math.max(maxB, d);
			}
			if (maxA <= minB + EPS || maxB <= minA + EPS)
				return false;
		}
		return true;
	}

	/**
	 * Across turned frames two faces can only share a plane where it is horizontal, and then only if they are at the same
	 * height. A piece turned to the diagonals is trimmed a little in height for exactly that, so none of its top or bottom
	 * faces is at the height of a straight piece's that it crosses.
	 */
	@Test
	void turnedPiecesNeverShareAHeightWithAStraightPieceTheyCross() throws IOException {
		List<Piece> all = pieces();
		for (Piece turned : all) {
			if (turned.angle() == 0 || turned.texture().endsWith("axis"))
				continue;
			for (Piece straight : all) {
				if (straight.angle() != 0 || straight.box() == turned.box())
					continue;
				if (!footprintsOverlap(turned, straight))
					continue;
				double[] t = turned.box().from(), tt = turned.box().to(), s2 = straight.box().from(), st = straight.box().to();
				for (int face : new int[] {0, 1}) {
					double a = face == 0 ? t[1] : tt[1], b = face == 0 ? s2[1] : st[1];
					assertTrue(Math.abs(a - b) > EPS, "a piece turned " + turned.angle() + " has a " + (face == 0 ? "bottom" : "top")
						+ " at " + a + ", the height of a straight piece's it crosses");
				}
			}
		}
	}

	/**
	 * The collars and the wheel are drawn together, so faces that share a plane across the two fight just as faces within
	 * one do. Counts only the faces an element really has, since the shaft has no caps.
	 */
	@Test
	void theCollarsAndTheWheelDoNotShareAPlaneBetweenThem() throws IOException {
		var collars = elementsWithFaces("block");
		var wheel = elementsWithFaces("disc");
		List<String> found = new ArrayList<>();
		for (String face : new String[] {"north", "south", "east", "west", "up", "down"}) {
			int a = face.equals("east") || face.equals("west") ? 0 : face.equals("up") || face.equals("down") ? 1 : 2;
			boolean positive = face.equals("east") || face.equals("up") || face.equals("south");
			int u = (a + 1) % 3, v = (a + 2) % 3;
			for (var p : collars)
				for (var q : wheel) {
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
		// The inventory model is the two put together
		assertEquals(collars.size() + wheel.size(), elementsWithFaces("item").size());
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

	/** Create's own flywheel wheel runs from -14 to 30 across its axis (44 wide); this one is nearly as wide, and no wider. */
	private static final double REACH_LOW = -14, REACH_HIGH = 30;

	@Test
	void theWheelIsAsWideAsCreatesFlywheelAndStaysWithinItsThickness() throws IOException {
		assertTrue(body().size() > 10, "the two masses and the spring ring are many pieces");
		double[] reach = reach();
		assertTrue(reach[0] >= REACH_LOW - EPS && reach[1] <= REACH_HIGH + EPS, "the wheel reaches past Create's: " + reach[0] + " to " + reach[1]);
		assertTrue(reach[1] - reach[0] >= 40, "the wheel should be nearly as wide as Create's 44: " + (reach[1] - reach[0]));
		for (Box b : body())
			assertTrue(b.from()[1] >= THICK_LOW - EPS && b.to()[1] <= THICK_HIGH + EPS,
				"an element is thicker than the collision shape: " + b.from()[1] + " to " + b.to()[1]);
	}

	/** The centre of every piece that is textured with {@code texture}, with turned pieces rotated about the shaft. */
	private static List<double[]> centres(String texture) throws IOException {
		List<double[]> out = new ArrayList<>();
		try (var reader = java.nio.file.Files.newBufferedReader(DIR.resolve("disc.json"))) {
			for (var e : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
				var o = e.getAsJsonObject();
				if (!o.getAsJsonObject("faces").entrySet().iterator().next().getValue().getAsJsonObject().get("texture").getAsString().equals("#" + texture))
					continue;
				double[] from = numbers(o.getAsJsonArray("from")), to = numbers(o.getAsJsonArray("to"));
				double dx = (from[0] + to[0]) / 2 - 8, dz = (from[2] + to[2]) / 2 - 8;
				if (o.has("rotation")) {
					double angle = Math.toRadians(o.getAsJsonObject("rotation").get("angle").getAsDouble());
					double rx = dx * Math.cos(angle) - dz * Math.sin(angle);
					dz = dx * Math.sin(angle) + dz * Math.cos(angle);
					dx = rx;
				}
				out.add(new double[] {dx, dz});
			}
		}
		return out;
	}

	/** Eight packs of four brass coils round the edge, one every eighth of a turn, so the ring is even and the wheel does not wobble as it spins. */
	@Test
	void theSpringRingIsEvenAboutTheAxis() throws IOException {
		List<double[]> coils = centres("coil");
		assertEquals(32, coils.size(), "eight packs of four coils");
		for (double[] c : coils) {
			double radius = Math.hypot(c[0], c[1]);
			assertTrue(radius > 18 && radius < 21, "a coil is not on the primary's rim: " + radius);
			// A quarter turn of the ring lands on another coil
			boolean quarter = coils.stream().anyMatch(o -> Math.abs(o[0] + c[1]) < 1e-6 && Math.abs(o[1] - c[0]) < 1e-6);
			boolean opposite = coils.stream().anyMatch(o -> Math.abs(o[0] + c[0]) < 1e-6 && Math.abs(o[1] + c[1]) < 1e-6);
			assertTrue(quarter && opposite, "a coil at " + c[0] + ", " + c[1] + " has no partner a quarter turn or half turn round");
		}
	}

	/** The rim is eight bars with a red stripe on each, so a high-performance part reads as one and not as a copy of Create's brass wheel. */
	@Test
	void theRimIsSteelWithARedStripeAndTheMassesAreBlackOxide() throws IOException {
		assertEquals(8, centres("stripe").size(), "a red stripe on each of eight rim bars");
		assertEquals(9, centres("steel").size(), "the secondary rim of eight bars and the hub");
		assertEquals(8, centres("primary").size(), "the primary mass has a black oxide rim of eight bars");
		assertEquals(40, centres("tooth").size(), "a ring gear of five teeth on each of eight sides");
		assertEquals(8, centres("lug").size(), "eight drive lugs between the spring packs");
		assertEquals(16, centres("spoke").size(), "eight spokes on each wheel");
	}

}
