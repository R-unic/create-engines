package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The battery's models and the blockstate that picks them: a model for each way its neighbours can join it, and a lamp strip
 * over each side that is not joined. The blockstate is read from {@code src/generated} (written by {@code runData}) and the
 * models from {@code tools/generate_models.py}'s output, and every one of the 160 states (32 joins by 5 charges) is put
 * together the way the game does and checked. A failure is fixed in the generator, never by loosening a check.
 */
class BatteryModelsTest {

	private static final Path DIR = Path.of("src/main/resources/assets/create_engines/models/block/battery");
	private static final Path STATE = Path.of("src/generated/resources/assets/create_engines/blockstates/battery.json");
	private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};
	private static final double EPS = 1e-6;
	private static final String[] JOINS = {"north", "east", "south", "west", "up"};

	/** An element with only the faces it draws. */
	record Element(double[] from, double[] to, Set<String> faces) {
	}

	private static JsonObject json(Path file) throws IOException {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	private static double[] numbers(JsonArray a) {
		double[] out = new double[a.size()];
		for (int i = 0; i < out.length; i++)
			out[i] = a.get(i).getAsDouble();
		return out;
	}

	private static List<Element> elements(String model) throws IOException {
		List<Element> out = new ArrayList<>();
		for (JsonElement e : json(DIR.resolve(model + ".json")).getAsJsonArray("elements")) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Element(numbers(o.getAsJsonArray("from")), numbers(o.getAsJsonArray("to")),
				new HashSet<>(o.getAsJsonObject("faces").keySet())));
		}
		return out;
	}

	/** The state as the blockstate file spells it: join properties "true" or "false", charge "0" to "4". */
	private static Map<String, String> state(int joins, int charge) {
		Map<String, String> state = new LinkedHashMap<>();
		for (int i = 0; i < JOINS.length; i++)
			state.put(JOINS[i], String.valueOf((joins >> i & 1) != 0));
		state.put("charge", String.valueOf(charge));
		return state;
	}

	private static boolean applies(JsonObject part, Map<String, String> state) {
		if (!part.has("when"))
			return true;
		for (Map.Entry<String, JsonElement> condition : part.getAsJsonObject("when").entrySet()) {
			String wanted = condition.getValue().getAsString();
			// A charge of 0 matches nothing: the lamps are lit from 1 up, so a plain "when" never names 0
			if (!java.util.Arrays.asList(wanted.split("\\|")).contains(state.get(condition.getKey())))
				return false;
		}
		return true;
	}

	/** The model names (without the namespace and folder) the blockstate lays one over another for this state. */
	private static List<String> modelsFor(Map<String, String> state) throws IOException {
		List<String> out = new ArrayList<>();
		for (JsonElement part : json(STATE).getAsJsonArray("multipart")) {
			JsonObject p = part.getAsJsonObject();
			if (applies(p, state))
				out.add(p.getAsJsonObject("apply").get("model").getAsString().replace("create_engines:block/battery/", ""));
		}
		return out;
	}

	private static List<Element> assembled(Map<String, String> state) throws IOException {
		List<Element> all = new ArrayList<>();
		for (String model : modelsFor(state))
			all.addAll(elements(model));
		return all;
	}

	private static int axis(String face) {
		return switch (face) {
			case "east", "west" -> 0;
			case "up", "down" -> 1;
			default -> 2;
		};
	}

	private static boolean positive(String face) {
		return face.equals("east") || face.equals("up") || face.equals("south");
	}

	/** Faces that point the same way, lie in one plane and overlap, counting only the faces an element really has. */
	private static List<String> fights(List<Element> elements) {
		List<String> out = new ArrayList<>();
		for (String face : FACES) {
			int a = axis(face), u = (a + 1) % 3, v = (a + 2) % 3;
			for (int i = 0; i < elements.size(); i++)
				for (int j = i + 1; j < elements.size(); j++) {
					Element p = elements.get(i), q = elements.get(j);
					if (!p.faces().contains(face) || !q.faces().contains(face))
						continue;
					double pp = positive(face) ? p.to()[a] : p.from()[a];
					double qp = positive(face) ? q.to()[a] : q.from()[a];
					if (Math.abs(pp - qp) > EPS)
						continue;
					double ou = Math.min(p.to()[u], q.to()[u]) - Math.max(p.from()[u], q.from()[u]);
					double ov = Math.min(p.to()[v], q.to()[v]) - Math.max(p.from()[v], q.from()[v]);
					if (ou > EPS && ov > EPS)
						out.add(face + " faces " + i + " and " + j + " at " + pp);
				}
		}
		return out;
	}

	private static List<Path> models() throws IOException {
		try (Stream<Path> files = Files.list(DIR)) {
			return files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
		}
	}

	/** A face that names a texture the model does not define shows the purple and black of a missing texture. */
	@Test
	void everyTextureAFaceNamesIsDefinedByItsModel() throws IOException {
		for (Path file : models()) {
			JsonObject model = json(file);
			// The item model only names its parent
			if (!model.has("elements"))
				continue;
			Set<String> defined = model.getAsJsonObject("textures").keySet();
			for (JsonElement e : model.getAsJsonArray("elements"))
				for (Map.Entry<String, JsonElement> face : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
					String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
					assertTrue(texture.startsWith("#") && defined.contains(texture.substring(1)),
						file.getFileName() + " has a " + face.getKey() + " face with the texture " + texture + ", which it does not define");
				}
		}
	}

	/**
	 * A model with an element that has no faces fails to load ("expected between 1 and 6 unique faces"), and the game then draws the
	 * missing model, a full block of purple and black, for every state that uses it. The all-sides-joined shells once did.
	 */
	@Test
	void everyElementOfEveryModelHasAtLeastOneFace() throws IOException {
		for (Path file : models()) {
			JsonObject model = json(file);
			if (!model.has("elements"))
				continue;
			int i = 0;
			for (JsonElement e : model.getAsJsonArray("elements")) {
				int faces = e.getAsJsonObject().getAsJsonObject("faces").size();
				assertTrue(faces >= 1 && faces <= 6, file.getFileName() + " element " + i + " has " + faces + " faces");
				i++;
			}
		}
	}

	@Test
	void everyModelTheBlockstateNamesExistsAndEveryModelIsUsed() throws IOException {
		Set<String> named = new HashSet<>();
		for (JsonElement part : json(STATE).getAsJsonArray("multipart"))
			named.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString()
				.replace("create_engines:block/battery/", ""));
		for (String model : named)
			assertTrue(Files.exists(DIR.resolve(model + ".json")), "the blockstate names a model that does not exist: " + model);
		// The inventory model (block) and the item (item) are not in the blockstate; every other file is
		for (Path file : models()) {
			String name = file.getFileName().toString().replace(".json", "");
			if (!name.equals("block") && !name.equals("item"))
				assertTrue(named.contains(name), name + " is not used by the blockstate");
		}
	}

	@Test
	void everyCombinationOfNeighboursAndChargeIsAssembledFromModelsWithoutZFighting() throws IOException {
		for (int joins = 0; joins < 32; joins++)
			for (int charge = 0; charge <= 4; charge++) {
				Map<String, String> state = state(joins, charge);
				List<String> found = fights(assembled(state));
				assertTrue(found.isEmpty(), state + " has z-fighting faces: " + found);
			}
	}

	/** Exactly one shell is chosen for each state, so batteries never show two bodies or none. */
	@Test
	void everyStateHasExactlyOneShell() throws IOException {
		for (int joins = 0; joins < 32; joins++) {
			long shells = modelsFor(state(joins, 0)).stream().filter(m -> m.startsWith("block_j")).count();
			assertEquals(1, shells, "joins " + joins);
		}
	}

	/**
	 * The middle of a joined side, which every neighbour covers with its own body, has no face looking into the neighbour, and the
	 * body reaches the neighbour's body, so no seam shows. Only the margin that a narrower or lower neighbour may leave open keeps
	 * a thin face (see {@link #aJoinedSideLeavesNoSlotIntoTheBodyWhateverTheNeighbourIs}).
	 */
	@Test
	void aJoinedSideRunsToTheEdgeOfTheBlockWithNoFaceLookingIntoTheNeighbourInTheMiddle() throws IOException {
		for (int joins = 0; joins < 32; joins++) {
			List<Element> all = assembled(state(joins, 0));
			for (int i = 0; i < 5; i++) {
				boolean joined = (joins >> i & 1) != 0;
				String face = JOINS[i];
				int a = axis(face);
				double edge = positive(face) ? 16 : 0;
				boolean reaches = false;
				for (Element e : all) {
					double at = positive(face) ? e.to()[a] : e.from()[a];
					if (Math.abs(at - edge) < EPS) {
						reaches = true;
						if (joined && e.faces().contains(face))
							assertFalse(coversTheMiddle(e, face), JOINS[i] + " joined, but a face looks into the middle of the neighbour (joins " + joins + ")");
					}
				}
				if (joined && i < 4)
					assertTrue(reaches, JOINS[i] + " joined, but nothing reaches that edge (joins " + joins + ")");
				else if (!joined && i < 4)
					assertFalse(reaches, JOINS[i] + " not joined, but something reaches that edge (joins " + joins + ")");
				if (!joined)
					for (Element e : all)
						if (Math.abs((positive(face) ? e.to()[a] : e.from()[a]) - edge) < EPS && i == 4)
							assertFalse(e.faces().contains(face) && e.to()[1] - e.from()[1] < EPS, "a strip on the top of a battery that has nothing joined there");
			}
		}
	}

	/** Whether the face of this element at the block's edge lies over the middle (the part inside 2 units of each edge, up to the lid) of that side. */
	private static boolean coversTheMiddle(Element e, String face) {
		int a = axis(face);
		int u = (a + 1) % 3, v = (a + 2) % 3;
		double ou = Math.min(e.to()[u], 14) - Math.max(e.from()[u], 2);
		double ov = Math.min(e.to()[v], 14) - Math.max(e.from()[v], 2);
		if (a == 1)
			return ou > EPS && ov > EPS;
		// A side: the middle runs from the ground to the top of the lid, and across the face between the margins
		double along = a == 0 ? Math.min(e.to()[2], 14) - Math.max(e.from()[2], 2) : Math.min(e.to()[0], 14) - Math.max(e.from()[0], 2);
		double high = Math.min(e.to()[1], 13.5) - Math.max(e.from()[1], 0);
		return along > EPS && high > EPS;
	}

	/** The solid of a shell at one plane of its block, as rectangles in the plane's two other axes, and the faces it draws looking out through it. */
	private record Plane(List<double[]> solid, List<double[]> faces) {
	}

	private static Plane plane(List<Element> shell, String face) {
		int a = axis(face), u = (a + 1) % 3, v = (a + 2) % 3;
		double at = positive(face) ? 16 : 0;
		List<double[]> solid = new ArrayList<>(), faces = new ArrayList<>();
		for (Element e : shell) {
			double[] rect = {e.from()[u], e.to()[u], e.from()[v], e.to()[v]};
			double edge = positive(face) ? e.to()[a] : e.from()[a];
			if (Math.abs(edge - at) > EPS)
				continue;
			if (e.to()[a] - e.from()[a] > EPS)
				solid.add(rect);
			if (e.faces().contains(face))
				faces.add(rect);
		}
		return new Plane(solid, faces);
	}

	/**
	 * What a shell fills at one plane of its block, worked out from its joins and not from its elements: a piece whose every face
	 * is left out (the middle of a body joined on all four sides) is not in the file, but the space is still the battery's.
	 * Side planes are rectangles in (up, along) or (along, up) as {@link #plane} orders them; the top and the bottom in (z, x).
	 */
	private static List<double[]> filled(int joins, String face) {
		boolean n = (joins & 1) != 0, e = (joins & 2) != 0, s = (joins & 4) != 0, w = (joins & 8) != 0, up = (joins & 16) != 0;
		double x0 = w ? 0 : 2, x1 = e ? 16 : 14, z0 = n ? 0 : 2, z1 = s ? 16 : 14;
		double tx0 = w ? 0 : 1.5, tx1 = e ? 16 : 14.5, tz0 = n ? 0 : 1.5, tz1 = s ? 16 : 14.5;
		List<double[]> out = new ArrayList<>();
		if (face.equals("up")) {
			if (up)
				out.add(new double[] {z0, z1, x0, x1});
			return out;
		}
		if (face.equals("down")) {
			out.add(new double[] {z0, z1, x0, x1});
			return out;
		}
		boolean sideOfX = face.equals("east") || face.equals("west");
		double[][] bands = {{0, 5, 0}, {5, 8, 1}, {8, 12, 0}, {12, 13.5, 1}, {13.5, 16, up ? 0 : -1}};
		for (double[] band : bands) {
			if (band[2] < 0)
				continue;
			boolean wide = band[2] == 1;
			double lo = sideOfX ? (wide ? tz0 : z0) : (wide ? tx0 : x0), hi = sideOfX ? (wide ? tz1 : z1) : (wide ? tx1 : x1);
			out.add(sideOfX ? new double[] {band[0], band[1], lo, hi} : new double[] {lo, hi, band[0], band[1]});
		}
		return out;
	}

	private static boolean within(List<double[]> rects, double p, double q) {
		for (double[] r : rects)
			if (p > r[0] + EPS && p < r[1] - EPS && q > r[2] + EPS && q < r[3] - EPS)
				return true;
		return false;
	}

	private static String shell(int joins) {
		return "block_j" + (joins & 1) + (joins >> 1 & 1) + (joins >> 2 & 1) + (joins >> 3 & 1) + (joins >> 4 & 1);
	}

	/**
	 * Where two batteries touch, one may be joined to something the other is not (an L's inside corner, a stacked battery beside one
	 * with nothing on top), so its body is wider or taller at the plane they share. Whatever of it sticks out past the other must
	 * still show a face there, or the player looks through a slot into the empty inside of the body. Checked for every shell against
	 * every shell it can touch, side to side and on top of each other, by sampling the shared plane.
	 */
	@Test
	void aJoinedSideLeavesNoSlotIntoTheBodyWhateverTheNeighbourIs() throws IOException {
		// Bits in a shell's name: north 1, east 2, south 4, west 8, up 16
		String[][] pairs = {{"east", "west"}, {"south", "north"}, {"up", "down"}};
		int[] sideBits = {2, 4, 16};
		int[] backBits = {8, 1, 0};
		for (int p = 0; p < pairs.length; p++)
			for (int first = 0; first < 32; first++) {
				if ((first & sideBits[p]) == 0)
					continue;
				for (int second = 0; second < 32; second++) {
					if (backBits[p] != 0 && (second & backBits[p]) == 0)
						continue;
					Plane near = new Plane(filled(first, pairs[p][0]), plane(elements(shell(first)), pairs[p][0]).faces());
					List<Element> other = elements(shell(second));
					Plane far = new Plane(filled(second, pairs[p][1]),
						pairs[p][1].equals("down") ? downPlane(other).faces() : plane(other, pairs[p][1]).faces());
					for (double s = 0.125; s < 16; s += 0.25)
						for (double t = 0.125; t < 16; t += 0.25) {
							if (within(near.solid(), s, t) && !within(far.solid(), s, t))
								assertTrue(within(near.faces(), s, t), "shell " + shell(first) + " has a slot on its " + pairs[p][0] + " side at (" + s
									+ ", " + t + ") where " + shell(second) + " does not reach");
							if (within(far.solid(), s, t) && !within(near.solid(), s, t))
								assertTrue(within(far.faces(), s, t), "shell " + shell(second) + " has a slot on its " + pairs[p][1] + " side at (" + s
									+ ", " + t + ") where " + shell(first) + " does not reach");
						}
				}
			}
	}

	/** The bottom of a shell: a battery has no join below, so its bottom is solid and drawn wherever its body is. */
	private static Plane downPlane(List<Element> shell) {
		List<double[]> solid = new ArrayList<>(), faces = new ArrayList<>();
		for (Element e : shell)
			if (Math.abs(e.from()[1]) < EPS && e.to()[1] - e.from()[1] > EPS) {
				double[] rect = {e.from()[2], e.to()[2], e.from()[0], e.to()[0]};
				solid.add(rect);
				if (e.faces().contains("down"))
					faces.add(rect);
			}
		return new Plane(solid, faces);
	}

	/** A battery with another on top has no terminals, caps or vents, only the body running up to the next one; one with nothing on top has them. */
	@Test
	void theTerminalsAreOnlyOnTheTopOfAStack() throws IOException {
		assertEquals(1, elements("block_j00001").stream().filter(e -> e.to()[1] > 13.5 + EPS).count(),
			"above the lid of a battery with another on top there should be only the body");
		assertTrue(elements("block_j00000").stream().filter(e -> e.to()[1] > 13.5 + EPS).count() > 1,
			"a lone battery has caps and terminals above its lid");
	}

	/** Lamps are laid on the lid of a side that is free, and only there. */
	@Test
	void theLampStripIsOnEverySideThatIsNotJoined() throws IOException {
		for (int joins = 0; joins < 32; joins++)
			for (int charge = 0; charge <= 4; charge++) {
				List<String> models = modelsFor(state(joins, charge));
				for (int i = 0; i < 4; i++) {
					boolean joined = (joins >> i & 1) != 0;
					assertEquals(!joined, models.contains("block_dark_" + JOINS[i]), "dark lamps, joins " + joins);
					for (int stage = 1; stage <= 4; stage++)
						assertEquals(!joined && stage == charge, models.contains("block_gauge_" + stage + "_" + JOINS[i]),
							"lit lamps of stage " + stage + ", joins " + joins + ", charge " + charge);
				}
			}
	}

	/** The inventory model is the lone battery with its lamps dark. */
	@Test
	void theInventoryModelIsTheLoneBatteryWithDarkLamps() throws IOException {
		int expected = elements("block_j00000").size();
		for (String side : new String[] {"north", "east", "south", "west"})
			expected += elements("block_dark_" + side).size();
		assertEquals(expected, elements("block").size());
		assertTrue(fights(elements("block")).isEmpty(), "the inventory model has z-fighting faces: " + fights(elements("block")));
	}
}
