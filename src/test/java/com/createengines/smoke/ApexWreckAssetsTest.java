package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What a failed set of apex seals looks and sounds like. The wreck (from {@code tools/generate_models.py}) is laid over a rotor housing
 * whose shaft may still be turned by the other rotors on it, so it must stay out of the rotor's sweep; and it is on all four sides,
 * so a pipe on one face cannot hide it. A failure is fixed in the generator, never by loosening a check.
 */
class ApexWreckAssetsTest {

	private static final Path ASSETS = Path.of("src/main/resources/assets/create_engines");
	/** How far from the shaft the rotor reaches, in model pixels: its eccentricity (1.5) and the far corner of a seal (5.25). */
	private static final double SWEEP = 6.75;

	private static JsonObject json(Path file) throws IOException {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	private static double at(JsonArray a, int i) {
		return a.get(i).getAsDouble();
	}

	/** The element's corners as the game draws them: turned about its rotation origin if it has one. */
	private static List<double[]> corners(JsonObject element) {
		JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			double[] p = {at((i & 1) == 0 ? from : to, 0), at((i & 2) == 0 ? from : to, 1), at((i & 4) == 0 ? from : to, 2)};
			if (element.has("rotation")) {
				JsonObject rotation = element.getAsJsonObject("rotation");
				JsonArray origin = rotation.getAsJsonArray("origin");
				int axis = "xyz".indexOf(rotation.get("axis").getAsString());
				int u = (axis + 1) % 3, v = (axis + 2) % 3;
				double angle = Math.toRadians(rotation.get("angle").getAsDouble());
				double a = p[u] - at(origin, u), b = p[v] - at(origin, v);
				p[u] = at(origin, u) + a * Math.cos(angle) - b * Math.sin(angle);
				p[v] = at(origin, v) + a * Math.sin(angle) + b * Math.cos(angle);
			}
			out.add(p);
		}
		return out;
	}

	@Test
	void theWreckStaysOutOfTheRotorsSweepAndShowsOnEverySide() throws IOException {
		Set<String> sides = new HashSet<>();
		for (JsonElement e : json(ASSETS.resolve("models/block/rotor_housing/wreck.json")).getAsJsonArray("elements")) {
			List<double[]> corners = corners(e.getAsJsonObject());
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (double[] c : corners) {
				minX = Math.min(minX, c[0]);
				maxX = Math.max(maxX, c[0]);
				minZ = Math.min(minZ, c[2]);
				maxZ = Math.max(maxZ, c[2]);
			}
			double dx = Math.max(0, Math.max(minX - 8, 8 - maxX)), dz = Math.max(0, Math.max(minZ - 8, 8 - maxZ));
			assertTrue(Math.hypot(dx, dz) > SWEEP, "A wreck element is in the rotor's way: " + e);
			if (maxZ <= 1)
				sides.add("north");
			if (minZ >= 15)
				sides.add("south");
			if (maxX <= 1)
				sides.add("west");
			if (minX >= 15)
				sides.add("east");
		}
		assertEquals(Set.of("north", "south", "east", "west"), sides);
	}

	@Test
	void theFailureHasTwoRecordingsOfItsOwn() throws IOException {
		JsonArray sounds = json(ASSETS.resolve("sounds.json")).getAsJsonObject("apex_fail").getAsJsonArray("sounds");
		assertEquals(2, sounds.size());
		for (JsonElement sound : sounds) {
			String name = sound.getAsJsonObject().get("name").getAsString().replace("create_engines:", "");
			assertTrue(name.startsWith("apex_fail"), "Not a borrowed recording: " + name);
			assertTrue(Files.size(ASSETS.resolve("sounds/" + name + ".ogg")) > 2000, name);
		}
	}
}
