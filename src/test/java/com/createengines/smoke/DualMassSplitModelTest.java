package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.content.flywheel.SpringPackLag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The dual-mass flywheel is drawn as two models, the secondary mass (its wheel, stripe, spokes and the lugs that drive the
 * springs) and the primary (everything else), so the secondary can lag the primary (SpringPackLag). Together they must be
 * exactly the one wheel the model tests, the item and the inventory icon use, or the wheel changes shape the moment it is
 * drawn in two. Fix a failure in the generator, never by widening a bound.
 */
class DualMassSplitModelTest {

	private static final Path DIR = Path.of("src/main/resources/assets/create_engines/models/block/dual_mass_flywheel");

	private static JsonObject read(String name) throws IOException {
		try (var reader = Files.newBufferedReader(DIR.resolve(name + ".json"))) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	private static List<String> pieces(String name) throws IOException {
		List<String> out = new ArrayList<>();
		for (JsonElement e : read(name).getAsJsonArray("elements"))
			out.add(e.toString());
		return out;
	}

	@Test
	void theTwoMassesAreTheWholeWheelAndNothingElse() throws IOException {
		List<String> whole = pieces("disc");
		List<String> both = new ArrayList<>(pieces("primary"));
		both.addAll(pieces("secondary"));
		assertEquals(whole.size(), both.size(), "no piece is lost or drawn twice");
		assertEquals(whole.stream().sorted().toList(), both.stream().sorted().toList(), "the same pieces");
		assertEquals(read("disc").getAsJsonObject("textures"), read("primary").getAsJsonObject("textures"));
		assertEquals(read("disc").getAsJsonObject("textures"), read("secondary").getAsJsonObject("textures"));
	}

	@Test
	void theShaftHubSpringsAndRingGearStayWithThePrimary() throws IOException {
		List<String> primary = pieces("primary");
		assertTrue(primary.stream().anyMatch(p -> p.contains("\"from\":[6,0,6]") && p.contains("\"to\":[10,16,10]")),
			"the full-length shaft turns with the primary mass");
		for (String texture : List.of("#coil", "#stop", "#tooth", "#primary", "#bolt"))
			assertTrue(primary.stream().anyMatch(p -> p.contains("\"texture\":\"" + texture + "\"")), "the primary has " + texture);
		assertFalse(primary.stream().anyMatch(p -> p.contains("\"#lug\"") || p.contains("\"#stripe\"")),
			"the lugs and the red stripe are the secondary's");
	}

	@Test
	void theSecondaryIsItsWheelAndLugsOnly() throws IOException {
		List<String> secondary = pieces("secondary");
		assertTrue(secondary.stream().anyMatch(p -> p.contains("\"texture\":\"#stripe\"")), "the red stripe");
		assertTrue(secondary.stream().anyMatch(p -> p.contains("\"texture\":\"#lug\"")), "the lugs that drive the springs");
		for (String texture : List.of("#coil", "#stop", "#tooth", "#primary", "#bolt", "#axis"))
			assertFalse(secondary.stream().anyMatch(p -> p.contains("\"texture\":\"" + texture + "\"")),
				"nothing of the primary or the springs is in the secondary: " + texture);
	}

	/** How near the shaft any point of a box comes, in the plane across it (centre 8, 8): a turned box keeps its distance. */
	private static double nearest(JsonObject element) {
		var from = element.getAsJsonArray("from");
		var to = element.getAsJsonArray("to");
		double dx = near(from.get(0).getAsDouble(), to.get(0).getAsDouble());
		double dz = near(from.get(2).getAsDouble(), to.get(2).getAsDouble());
		return Math.hypot(dx, dz);
	}

	private static double farthest(JsonObject element) {
		var from = element.getAsJsonArray("from");
		var to = element.getAsJsonArray("to");
		double dx = Math.max(Math.abs(from.get(0).getAsDouble() - 8), Math.abs(to.get(0).getAsDouble() - 8));
		double dz = Math.max(Math.abs(from.get(2).getAsDouble() - 8), Math.abs(to.get(2).getAsDouble() - 8));
		return Math.hypot(dx, dz);
	}

	private static double near(double low, double high) {
		return low <= 8 && high >= 8 ? 0 : Math.min(Math.abs(low - 8), Math.abs(high - 8));
	}

	/**
	 * The secondary swings a good way either side of the primary (SpringPackLag), so its lugs may not reach the ring the spring
	 * packs stand on: at any twist a lug that stops short of the ring passes through nothing. The lugs drive the packs from just
	 * inside it.
	 */
	@Test
	void theLugsStopShortOfTheSpringPacksSoTheSecondaryCanSwingFreely() throws IOException {
		double lugReach = 0, packInner = Double.MAX_VALUE;
		for (JsonElement e : read("secondary").getAsJsonArray("elements"))
			if (e.toString().contains("\"#lug\""))
				lugReach = Math.max(lugReach, farthest(e.getAsJsonObject()));
		for (JsonElement e : read("primary").getAsJsonArray("elements"))
			if (e.toString().contains("\"#coil\"") || e.toString().contains("\"#stop\""))
				packInner = Math.min(packInner, nearest(e.getAsJsonObject()));
		assertTrue(lugReach > 0 && packInner < Double.MAX_VALUE, "found the lugs and the packs");
		assertTrue(lugReach < packInner, "the lugs reach " + lugReach + " from the shaft, the spring packs start at " + packInner);
	}
}
