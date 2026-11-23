package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The ways to mix engine oil, read from the data files that ship. The slime and glycerin recipes must stay as they were. */
class EngineOilSourcesTest {

	private static final Path DIR = Path.of("src/main/resources/data/create_engines/recipe/mixing");

	private static JsonObject read(String file) throws IOException {
		try (Reader in = Files.newBufferedReader(DIR.resolve(file))) {
			return JsonParser.parseReader(in).getAsJsonObject();
		}
	}

	private static int out(JsonObject r) {
		JsonObject o = r.getAsJsonArray("results").get(0).getAsJsonObject();
		assertEquals("create_engines:engine_oil", o.get("id").getAsString());
		return o.get("amount").getAsInt();
	}

	@Test
	void slimeAndGlycerinRecipesAreUnchanged() throws IOException {
		assertEquals(250, out(read("engine_oil.json")));
		assertEquals(300, out(read("engine_oil_glycerin.json")));
	}

	@Test
	void magmaCreamAndClayAlsoMakeEngineOilOverHeat() throws IOException {
		JsonObject magma = read("engine_oil_magma_cream.json");
		assertEquals("heated", magma.get("heat_requirement").getAsString());
		assertEquals(250, out(magma));
		assertEquals("minecraft:magma_cream", magma.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());

		JsonObject clay = read("engine_oil_clay.json");
		assertEquals("heated", clay.get("heat_requirement").getAsString());
		assertEquals(3, clay.getAsJsonArray("ingredients").size());
		assertTrue(out(clay) < out(read("engine_oil.json")), "clay is the poorer route");
	}
}
