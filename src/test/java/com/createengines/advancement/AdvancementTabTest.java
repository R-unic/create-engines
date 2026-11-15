package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The generated advancement files: which ones are in the tab, and that the tab is still one tree. */
class AdvancementTabTest {

	private static final Path DIRECTORY = Path.of("src/generated/resources/data/create_engines/advancement");

	private static final Pattern PARENT = Pattern.compile("\"parent\"\s*:\s*\"create_engines:([a-z_]+)\"");

	private static final Map<String, String> FILES = new HashMap<>();

	@BeforeAll
	static void load() throws IOException {
		try (Stream<Path> paths = Files.list(DIRECTORY)) {
			for (Path path : paths.toList()) {
				String name = path.getFileName()
					.toString();
				FILES.put(name.substring(0, name.length() - ".json".length()),
					Files.readString(path));
			}
		}
	}

	private static boolean shown(String id) {
		return FILES.get(id)
			.contains("\"display\"");
	}

	private static String parentOf(String id) {
		Matcher match = PARENT.matcher(FILES.get(id));
		return match.find() ? match.group(1) : null;
	}

	@Test
	void everyAdvancementKeepsItsIdWhetherOrNotItIsInTheTab() {
		assertEquals(110, FILES.size(), "IDs are deleted in 2.0, not in 1.x");
		for (String file : FILES.values())
			assertTrue(file.contains("\"trigger\""), "a row left out of the tab still earns its progress");
	}

	@Test
	void theTabIsATidyListOfWhatIsWorthBeingProudOf() {
		long shown = FILES.keySet()
			.stream()
			.filter(AdvancementTabTest::shown)
			.count();
		assertTrue(shown > 30 && shown < 70, "a tab, not a checklist: " + shown);
		assertTrue(shown("engineer") && shown("alive") && shown("chief_engineer"));
		assertFalse(shown("cylinder_head"));
	}

	@Test
	void everyAdvancementInTheTabHangsFromTheRootThroughOthersInTheTab() {
		for (String id : FILES.keySet()) {
			if (!shown(id))
				continue;
			String at = id;
			for (int steps = 0; !at.equals("engineer"); steps++) {
				assertTrue(steps < 30, id + " never reaches the root");
				String parent = parentOf(at);
				assertTrue(parent != null, at + " is cut off from the root");
				assertTrue(shown(parent), at + " hangs from " + parent + ", which is not in the tab");
				at = parent;
			}
		}
	}

	@Test
	void aRowLeftOutOfTheTabIsReparentedToTheNearestOneInIt() {
		assertEquals("cylinder", parentOf("camshaft"));
	}
}
