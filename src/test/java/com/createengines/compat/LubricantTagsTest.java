package com.createengines.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The shipped full-strength lubricant tag: another mod's fluids only, each optional, so the tag loads without that mod. */
class LubricantTagsTest {

	@Test
	void itNamesPetrochemsLubricantAndNothingIsRequired() throws IOException {
		JsonObject tag = JsonParser
			.parseString(Files.readString(Path.of("src/main/resources/data/create_engines/tags/fluid/lubricants.json"),
				StandardCharsets.UTF_8))
			.getAsJsonObject();
		assertFalse(tag.has("replace"), "a pack's own entries must be kept");
		List<String> ids = new ArrayList<>();
		for (JsonElement value : tag.getAsJsonArray("values")) {
			JsonObject entry = value.getAsJsonObject();
			assertFalse(entry.get("required")
				.getAsBoolean(), () -> "required: " + entry);
			ids.add(entry.get("id")
				.getAsString());
		}
		assertEquals(List.of("petrochem:lubricant", "petrochem:flowing_lubricant"), ids);
	}
}
