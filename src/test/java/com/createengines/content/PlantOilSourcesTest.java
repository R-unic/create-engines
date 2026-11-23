package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The ways to press plant oil, read from the data files that ship. The older two (seeds, saplings) must stay as they were. */
class PlantOilSourcesTest {

	private static final Path DIR = Path.of("src/main/resources/data/create_engines/recipe/compacting");

	private static JsonObject read(String file) throws IOException {
		try (Reader in = Files.newBufferedReader(DIR.resolve(file))) {
			return JsonParser.parseReader(in).getAsJsonObject();
		}
	}

	private static int amount(JsonObject recipe) {
		JsonObject r = recipe.getAsJsonArray("results").get(0).getAsJsonObject();
		assertEquals("create_engines:plant_oil", r.get("id").getAsString());
		return r.get("amount").getAsInt();
	}

	private static int count(JsonObject recipe) {
		return recipe.getAsJsonArray("ingredients").size();
	}

	private static void check(String file, String item, int items, int mb) throws IOException {
		JsonObject recipe = read(file);
		assertEquals("create:compacting", recipe.get("type").getAsString(), file);
		assertFalse(recipe.has("heat_requirement"), file + " is pressed cold like seeds");
		assertEquals(items, count(recipe), file);
		for (JsonElement e : recipe.getAsJsonArray("ingredients"))
			assertEquals(item, e.getAsJsonObject().get("item").getAsString(), file);
		assertEquals(mb, amount(recipe), file);
	}

	@Test
	void seedsAndSaplingsKeepTheirOldYield() throws IOException {
		assertEquals(100, amount(read("plant_oil.json")));
		assertEquals(6, count(read("plant_oil.json")));
		assertEquals(50, amount(read("plant_oil_saplings.json")));
		assertEquals(2, count(read("plant_oil_saplings.json")));
	}

	@Test
	void sunflowersCocoaAndKelpAlsoPressToOil() throws IOException {
		check("plant_oil_sunflowers.json", "minecraft:sunflower", 2, 50);
		check("plant_oil_cocoa.json", "minecraft:cocoa_beans", 4, 50);
		check("plant_oil_kelp.json", "minecraft:dried_kelp", 6, 50);
	}

	@Test
	void noNewSourceBeatsSaplingsPerItem() throws IOException {
		double ceiling = 50.0 / 2;
		for (String f : new String[] { "plant_oil_sunflowers.json", "plant_oil_cocoa.json", "plant_oil_kelp.json" }) {
			JsonObject r = read(f);
			double perItem = (double) amount(r) / count(r);
			assertTrue(perItem <= ceiling, f + " gives " + perItem + " mB per item");
		}
	}
}
