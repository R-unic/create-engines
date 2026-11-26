package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A blown gasket and failed apex seals have to be told from across a factory. Both break the block's outline on all four sides in a
 * colour the sound part does not wear: a red gasket, pale ceramic seals. A failure is fixed in {@code tools/generate_models.py}.
 */
class DamageReadsAfarAssetsTest {

	private static final Path MODELS = Path.of("src/main/resources/assets/create_engines/models/block");
	/** How far past the block, in model pixels, the loud part has to reach to change the outline seen at range. */
	private static final double REACH = 3;

	private static JsonObject model(String name) throws IOException {
		try (Reader r = Files.newBufferedReader(MODELS.resolve(name + ".json"), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	/** The sides of the block past which an element wearing {@code key} reaches, going by its box before any tilt. */
	private static Set<String> sidesReached(JsonObject model, String key) {
		Set<String> sides = new HashSet<>();
		for (JsonElement each : model.getAsJsonArray("elements")) {
			JsonObject element = each.getAsJsonObject();
			if (!element.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals("#" + key))
				continue;
			var from = element.getAsJsonArray("from");
			var to = element.getAsJsonArray("to");
			if (from.get(2).getAsDouble() <= -REACH)
				sides.add("north");
			if (to.get(2).getAsDouble() >= 16 + REACH)
				sides.add("south");
			if (from.get(0).getAsDouble() <= -REACH)
				sides.add("west");
			if (to.get(0).getAsDouble() >= 16 + REACH)
				sides.add("east");
		}
		return sides;
	}

	/** The textures the model's faces really draw with: a model may name one that no face uses. */
	private static Set<String> worn(String name) throws IOException {
		JsonObject model = model(name);
		Set<String> textures = new HashSet<>();
		for (JsonElement element : model.getAsJsonArray("elements"))
			for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
				String key = face.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
				textures.add(model.getAsJsonObject("textures").get(key).getAsString());
			}
		return textures;
	}

	private static void readsAfar(String damage, String sound, String key) throws IOException {
		JsonObject broken = model(damage);
		assertEquals(Set.of("north", "south", "east", "west"), sidesReached(broken, key), damage);
		String colour = broken.getAsJsonObject("textures").get(key).getAsString();
		assertTrue(!worn(sound).contains(colour), sound + " already wears " + colour);
	}

	@Test
	void aBlownGasketBreaksTheHeadsOutlineInItsOwnColour() throws IOException {
		readsAfar("cylinder_head/blown_gasket", "cylinder_head/block", "gasket");
	}

	@Test
	void failedApexSealsBreakTheHousingsOutlineInTheirOwnColour() throws IOException {
		readsAfar("rotor_housing/wreck", "rotor_housing/block", "seal");
		String seal = model("rotor_housing/wreck").getAsJsonObject("textures").get("seal").getAsString();
		assertTrue(!worn("rotor_housing/rotor").contains(seal), "The rotor already wears " + seal);
	}
}
