package com.createengines.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A recipe with a bad pattern is only logged by the game and left out, so its item cannot be crafted and nothing else
 * fails. 2.3.0 shipped the Copper Gasket that way, with rows of different widths.
 */
class ShapedRecipePatternTest {

	private static final List<Path> ROOTS = List.of(Path.of("src/main/resources/data"),
		Path.of("src/generated/resources/data"));

	/** What is wrong with a recipe that has a pattern; empty when the game would take it. */
	private static List<String> problems(JsonObject recipe) {
		List<String> found = new ArrayList<>();
		List<String> rows = recipe.getAsJsonArray("pattern")
			.asList()
			.stream()
			.map(JsonElement::getAsString)
			.toList();
		if (rows.isEmpty() || rows.get(0).isEmpty())
			return List.of("empty pattern");
		// Create's mechanical crafting goes past the crafting table's three by three
		if (recipe.get("type").getAsString().startsWith("minecraft:")
			&& (rows.size() > 3 || rows.stream().anyMatch(row -> row.length() > 3)))
			found.add("bigger than 3x3: " + rows);
		if (rows.stream().map(String::length).distinct().count() > 1)
			found.add("rows of different widths: " + rows);
		Set<String> used = new HashSet<>();
		for (String row : rows)
			for (char symbol : row.toCharArray())
				if (symbol != ' ')
					used.add(String.valueOf(symbol));
		Set<String> keys = recipe.has("key") ? recipe.getAsJsonObject("key").keySet() : Set.of();
		for (String symbol : used)
			if (!keys.contains(symbol))
				found.add("no key for '" + symbol + "'");
		for (String key : keys)
			if (!used.contains(key))
				found.add("key '" + key + "' is not in the pattern");
		return found;
	}

	private static JsonObject read(Path file) {
		try (Reader in = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(in).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void everyShippedPatternIsOneTheGameLoads() throws IOException {
		int checked = 0;
		List<String> bad = new ArrayList<>();
		for (Path root : ROOTS) {
			if (!Files.isDirectory(root))
				continue;
			List<Path> files;
			try (Stream<Path> walk = Files.walk(root)) {
				files = walk.filter(file -> file.toString().endsWith(".json"))
					.filter(file -> file.toString().replace('\\', '/').contains("/recipe/"))
					.sorted()
					.toList();
			}
			for (Path file : files) {
				JsonObject recipe = read(file);
				if (!recipe.has("pattern"))
					continue;
				checked++;
				for (String problem : problems(recipe))
					bad.add(file + ": " + problem);
			}
		}
		assertTrue(checked > 40, "found only " + checked + " patterns, so the search is looking in the wrong place");
		assertEquals(List.of(), bad);
	}

	@Test
	void theCheckRefusesTheCopperGasketAs230ShippedIt() {
		JsonObject broken = JsonParser.parseString(
			"{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"C\",\"CC\",\"C\"],\"key\":{\"C\":{}}}")
			.getAsJsonObject();
		assertEquals(1, problems(broken).size(), problems(broken).toString());
	}

	@Test
	void theCheckRefusesMissingAndUnusedKeys() {
		JsonObject broken = JsonParser.parseString(
			"{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AB\"],\"key\":{\"A\":{},\"C\":{}}}")
			.getAsJsonObject();
		assertEquals(2, problems(broken).size(), problems(broken).toString());
	}
}
