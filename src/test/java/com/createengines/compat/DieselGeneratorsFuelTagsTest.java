package com.createengines.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Create: Diesel Generators' fluids burn in our engines because the common fuel tags list them. Every entry must be
 * optional, so the tags still load when that mod is absent, and our own fluids must stay in the tags.
 */
class DieselGeneratorsFuelTagsTest {
	private static final Path TAGS = Path.of("src/main/resources/data/c/tags/fluid");

	private static final Map<String, String[]> EXPECTED = Map.of(
		"diesel", new String[] { "diesel", "biodiesel" },
		"gasoline", new String[] { "gasoline" },
		"ethanol", new String[] { "ethanol" },
		"plantoil", new String[] { "plant_oil" });

	@Test
	void dieselGeneratorsFluidsAreOptionalMembersOfTheFuelTags() throws IOException {
		for (var entry : EXPECTED.entrySet()) {
			String json = Files.readString(TAGS.resolve(entry.getKey() + ".json"), StandardCharsets.UTF_8)
				.replaceAll("\\s+", "");
			assertTrue(json.contains("\"create_engines:"), entry.getKey() + " keeps our own fluid");
			for (String name : entry.getValue())
				for (String id : new String[] { name, "flowing_" + name })
					assertTrue(json.contains("{\"id\":\"createdieselgenerators:" + id + "\",\"required\":false}"),
						entry.getKey() + " lists createdieselgenerators:" + id + " as optional");
		}
	}
}
