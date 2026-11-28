package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The Petrochem row's own ignore list covers the recipes Petrochem's jar cannot load, and nothing wider. */
class PetrochemLogAllowlistTest {

	private static final String ERROR = "[27Nov2026 18:42:41.691] [main/ERROR] [net.minecraft.world.item.crafting.RecipeManager/]: "
		+ "Parsing error loading recipe ";
	private static final String UNKNOWN = "com.google.gson.JsonParseException: Unknown registry key in ResourceKey"
		+ "[minecraft:root / minecraft:recipe_serializer]: ";

	private static List<LogScanner.Finding> scan(String... lines) throws IOException {
		return new LogScanner()
			.configure(Files.readString(Path.of("src/smoke/log-allowlist-petrochem.txt"), StandardCharsets.UTF_8))
			.scan(List.of(lines));
	}

	@Test
	void theLeftoverRecipesAreIgnored() throws IOException {
		assertTrue(scan(ERROR + "gearbox:compressing/test", UNKNOWN + "gearbox:compressing",
			"\tat com.mojang.serialization.DataResult$Error.getOrThrow(DataResult.java:287)",
			ERROR + "gearbox:sequenced_assembly/test_assembly",
			"com.google.gson.JsonParseException: Failed to parse either. First: No key id in MapLike[{\"item\":\"create:track\"}]; "
				+ "Second: Compat cannot be deserialized; Unknown registry key in ResourceKey"
				+ "[minecraft:root / minecraft:recipe_serializer]: gearbox:mechanizing; Failed to parse either.")
			.isEmpty());
	}

	@Test
	void anyOtherRecipeThatFailsToLoadStillFails() throws IOException {
		for (String recipe : new String[] {"create_engines:mixing/diesel", "petrochem:distilling/basic_atmospheric",
			"gearbox:compressing/another", "gearbox:compressing/test3"})
			assertFalse(scan(ERROR + recipe).isEmpty(), recipe);
		assertFalse(scan(UNKNOWN + "petrochem:distilling").isEmpty());
		assertFalse(scan(UNKNOWN + "create_engines:refining").isEmpty());
		assertFalse(scan("[12:00:00] [Server thread/ERROR] [gearbox/]: Something else broke").isEmpty());
	}

	@Test
	void theSharedListDoesNotIgnoreThem() throws IOException {
		var shared = new LogScanner()
			.configure(Files.readString(Path.of("src/smoke/log-allowlist.txt"), StandardCharsets.UTF_8));
		assertFalse(shared.scan(List.of(ERROR + "gearbox:compressing/test")).isEmpty());
	}
}
