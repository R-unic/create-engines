package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The generated advancement files, 2.0 shape: every row is in the tab, its ID is its track and name, and the tab is one tree
 * rooted at {@code root}. The 1.x IDs and the hidden rows are gone; {@link AdvancementMigrationTest} holds the migration.
 */
class AdvancementTabTest {

	private static final Path DIRECTORY = Path.of("src/generated/resources/data/create_engines/advancement");

	private static final Pattern PARENT = Pattern.compile("\"parent\"\\s*:\\s*\"create_engines:([a-z_/]+)\"");

	private static final Map<String, String> FILES = new HashMap<>();

	private static final List<String> TRACKS = List.of("first_light", "builder", "fuel", "power", "tuning", "driveline",
		"service");

	@BeforeAll
	static void load() throws IOException {
		try (Stream<Path> paths = Files.walk(DIRECTORY)) {
			for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList())
				FILES.put(DIRECTORY.relativize(path).toString().replace('\\', '/').replace(".json", ""),
					Files.readString(path));
		}
	}

	private static String parentOf(String id) {
		Matcher match = PARENT.matcher(FILES.get(id));
		return match.find() ? match.group(1) : null;
	}

	@Test
	void everyAdvancementIsInTheTabAndHasSomethingToEarn() {
		assertEquals(65, FILES.size(), "64 rows and the root; the 46 hidden rows are deleted");
		for (Map.Entry<String, String> file : FILES.entrySet()) {
			assertTrue(file.getValue().contains("\"display\""), file.getKey() + " is in the tab");
			assertTrue(file.getValue().contains("\"trigger\""), file.getKey() + " can be earned");
		}
	}

	@Test
	void theIdIsTheTrackAndTheRow() {
		for (String id : FILES.keySet()) {
			if (id.equals("root") || id.equals("chief_engineer"))
				continue;
			assertTrue(TRACKS.contains(id.substring(0, id.indexOf('/'))), id + " is under no track");
		}
		for (String track : TRACKS)
			assertTrue(FILES.keySet().stream().anyMatch(id -> id.startsWith(track + "/")), track + " is empty");
	}

	@Test
	void theHiddenRowsAndTheOldNamesAreGone() {
		for (String gone : new String[] { "cylinder_head", "throttle", "muffler", "high_revs", "engineer", "alive", "starter" })
			assertFalse(FILES.containsKey(gone), gone + " no longer exists under that ID");
	}

	@Test
	void everyAdvancementHangsFromTheRoot() {
		for (String id : FILES.keySet()) {
			String at = id;
			for (int steps = 0; !at.equals("root"); steps++) {
				assertTrue(steps < 30, id + " never reaches the root");
				String parent = parentOf(at);
				assertNotNull(parent, at + " is cut off from the root");
				assertTrue(FILES.containsKey(parent), at + " hangs from " + parent + ", which does not exist");
				at = parent;
			}
		}
	}

	private static boolean announces(String id) {
		return !Pattern.compile("\"announce_to_chat\"\\s*:\\s*false")
			.matcher(FILES.get(id))
			.find();
	}

	@Test
	void mostRowsAnnounceInChatAndOnlyTheInsignificantOnesDoNot() {
		long quiet = FILES.keySet().stream().filter(id -> !id.equals("root")).filter(id -> !announces(id)).count();
		long loud = FILES.keySet().stream().filter(id -> !id.equals("root")).filter(AdvancementTabTest::announces).count();
		assertEquals(18, quiet, "the toast-only rows");
		assertTrue(loud > quiet * 2, "most announce: " + loud + " loud, " + quiet + " quiet");
		for (String milestone : new String[] { "first_light/alive", "builder/twelve_cylinders", "power/big_boost",
			"builder/master_builder", "chief_engineer", "first_light/stalled", "fuel/smoke_signals", "power/overloaded",
			"driveline/every_gear" })
			assertTrue(announces(milestone), milestone + " is worth a line in chat");
		for (String stone : new String[] { "first_light/crankshaft", "first_light/cylinder", "first_light/starter",
			"first_light/warmed_up", "builder/pipework", "fuel/fouled_plug" })
			assertFalse(announces(stone), stone + " is a stepping stone");
	}

	@Test
	void mistakesAreWorthAnAdvancementToo() {
		for (String id : new String[] { "first_light/stalled", "first_light/flooded", "fuel/smoke_signals", "fuel/fouled_plug",
			"fuel/thinned_oil", "power/overloaded" })
			assertTrue(FILES.containsKey(id), id + " is a discovery worth a toast");
	}

	@Test
	void theTransmissionAndEveryGearAreInTheDrivelineTrack() {
		assertTrue(FILES.containsKey("driveline/transmission") && FILES.containsKey("driveline/every_gear")
			&& FILES.containsKey("driveline/full_driveline"));
	}

	@Test
	void onlyChiefEngineerHandsOutAKeepsake() {
		for (var entry : FILES.entrySet())
			assertEquals(entry.getKey().equals("chief_engineer"), entry.getValue().contains("\"rewards\""), entry.getKey());
		assertTrue(FILES.get("chief_engineer").contains("advancement/chief_engineer"));
	}

	@Test
	void everyCapstoneHasOneCriterionPerMilestoneItWaitsFor() {
		for (String capstone : new String[] { "builder/master_builder", "power/power_plant", "tuning/fine_tuner",
			"driveline/full_driveline", "fuel/closed_loop", "service/service_record" })
			assertTrue(FILES.get(capstone).split("\"trigger\"").length > 4,
				capstone + " shows its progress milestone by milestone");
	}
}
