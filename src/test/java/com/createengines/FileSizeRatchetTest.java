package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * The size ratchet for the files that grew too long (docs/ROADMAP-3.0.md, "Splitting the giant files"). Each is held to
 * a line cap that only goes down. A file that has been split away must not come back under its old name.
 */
class FileSizeRatchetTest {

	/** Path to the most lines it may have. Zero means the file was split up and is gone. */
	private static final Map<String, Integer> CAPS = new TreeMap<>(Map.of(
		"src/main/java/com/createengines/gametest/EngineGameTests.java", 768,
		"src/main/java/com/createengines/ponder/EngineScenes.java", 0));

	@Test
	void noListedFileHasGrown() throws IOException {
		List<String> over = new ArrayList<>();
		for (var cap : CAPS.entrySet()) {
			Path path = Path.of(cap.getKey());
			long lines = 0;
			if (Files.exists(path))
				try (var read = Files.lines(path)) {
					lines = read.count();
				}
			if (lines > cap.getValue())
				over.add(cap.getKey() + " has " + lines + " lines, cap " + cap.getValue());
		}
		assertEquals(List.of(), over, "put new work in a new file split by responsibility; do not raise a cap");
	}
}
