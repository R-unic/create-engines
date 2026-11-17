package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** What soot is good for besides black dye, read from the data files that ship. All of it is additive. */
class SootUsesTest {

	private static final Path DATA = Path.of("src/main/resources/data");
	private static final String SOOT = "create_engines:soot";

	private static JsonObject read(String file) throws IOException {
		try (Reader in = Files.newBufferedReader(DATA.resolve(file))) {
			return JsonParser.parseReader(in).getAsJsonObject();
		}
	}

	private static int sootIn(JsonObject recipe) {
		int n = 0;
		for (JsonElement e : recipe.getAsJsonArray("ingredients"))
			if (e.getAsJsonObject().has("item") && SOOT.equals(e.getAsJsonObject().get("item").getAsString()))
				n++;
		return n;
	}

	private static JsonObject result(JsonObject recipe) {
		JsonElement r = recipe.get("result");
		return r != null ? r.getAsJsonObject() : recipe.getAsJsonArray("results").get(0).getAsJsonObject();
	}

	private static void check(String file, int soot, String id, int count) throws IOException {
		JsonObject recipe = read("create_engines/recipe/" + file);
		assertEquals(soot, sootIn(recipe), file);
		assertEquals(id, result(recipe).get("id").getAsString(), file);
		assertEquals(count, result(recipe).get("count").getAsInt(), file);
	}

	@Test
	void sootStillGrindsToBlackDye() throws IOException {
		check("black_dye_from_soot.json", 1, "minecraft:black_dye", 1);
	}

	@Test
	void sootMakesGrayDyeWithBoneMeal() throws IOException {
		check("gray_dye_from_soot.json", 1, "minecraft:gray_dye", 2);
	}

	@Test
	void sootAndSugarMakeGunpowderOnlyOverHeat() throws IOException {
		check("mixing/gunpowder_from_soot.json", 2, "minecraft:gunpowder", 1);
		assertEquals("heated", read("create_engines/recipe/mixing/gunpowder_from_soot.json").get("heat_requirement").getAsString());
	}

	@Test
	void sootPressesBackToCoalOnlyOverHeatAndAtALoss() throws IOException {
		check("compacting/coal_from_soot.json", 4, "minecraft:coal", 1);
		assertEquals("heated", read("create_engines/recipe/compacting/coal_from_soot.json").get("heat_requirement").getAsString());
	}

	@Test
	void sootBurnsInAFurnaceButWorseThanCoal() throws IOException {
		int burn = read("neoforge/data_maps/item/furnace_fuels.json").getAsJsonObject("values")
			.getAsJsonObject(SOOT).get("burn_time").getAsInt();
		assertTrue(burn > 0 && burn < 1600 / 4, "four soot must not out-burn the coal they press into: " + burn);
	}
}
