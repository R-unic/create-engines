package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Old progress is credited on the new rows, silently, and every 1.x ID has an entry. */
class AdvancementMigrationTest {

	private static final Path DIRECTORY = Path.of("src/generated/resources/data/create_engines/advancement");

	/** The generated advancement files by path (builder/vee_engine), with their text. */
	private static final Map<String, String> FILES = new HashMap<>();

	@BeforeAll
	static void load() throws IOException {
		try (Stream<Path> paths = Files.walk(DIRECTORY)) {
			for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList())
				FILES.put(DIRECTORY.relativize(path).toString().replace('\\', '/').replace(".json", ""),
					Files.readString(path));
		}
	}

	private static List<String> oldIds() throws IOException {
		return Files.readAllLines(Path.of("src/test/resources/compat/advancements-1x.txt"))
			.stream()
			.filter(line -> !line.isBlank())
			.toList();
	}

	private static Set<String> criteria(String advancement) {
		String json = FILES.get(advancement);
		Set<String> names = new HashSet<>();
		Matcher match = Pattern.compile("\"([a-z_/]+)\"").matcher(json.substring(json.indexOf("\"requirements\"")));
		while (match.find())
			names.add(match.group(1));
		return names;
	}

	@Test
	void everyIdTheFirstVersionDefinedHasAnEntryAndNothingElseDoes() throws IOException {
		assertEquals(110, oldIds().size());
		assertEquals(new HashSet<>(oldIds()), AdvancementMigration.legacyIds());
	}

	@Test
	void everyCreditLandsOnARowAndACriterionThatExist() throws IOException {
		for (String old : oldIds())
			for (AdvancementMigration.Credit credit : AdvancementMigration.creditsFor(old)) {
				assertTrue(FILES.containsKey(credit.advancement()), old + " -> " + credit.advancement() + " is not defined");
				if (credit.criterion() != null)
					assertTrue(criteria(credit.advancement()).contains(credit.criterion()),
						old + " -> " + credit.advancement() + " has no criterion " + credit.criterion());
			}
	}

	@Test
	void everyRowThatStayedInTheTabKeptItsProgress() throws IOException {
		int kept = 0;
		for (String old : oldIds())
			if (AdvancementMigration.creditsFor(old)
				.stream()
				.anyMatch(c -> c.criterion() == null || c.criterion().equals(c.advancement())))
				kept++;
		assertTrue(kept >= 64, "the old shown rows and the capstones: " + kept);
	}

	@Test
	void anOldRowCreditsItsNewOneUnderTheNewCriterionName() {
		Map<String, Set<String>> grants = AdvancementMigration.migrate(Map.of("vee_engine", Set.of("vee_engine")));
		assertTrue(grants.get("builder/vee_engine").contains("builder/vee_engine"));
	}

	@Test
	void aMilestoneRowAlsoCreditsTheCapstonesThatWaitForIt() {
		Map<String, Set<String>> grants = AdvancementMigration.migrate(Map.of("vee_engine", Set.of("vee_engine")));
		assertTrue(grants.get("builder/master_builder").contains("vee_engine"));
		assertTrue(grants.get("chief_engineer").contains("vee_engine"));
	}

	@Test
	void aHiddenRowThatOnlyFedACapstoneStillCountsForIt() {
		Map<String, Set<String>> grants = AdvancementMigration.migrate(Map.of("maf_trim", Set.of("maf_trim")));
		assertTrue(grants.get("tuning/fine_tuner").contains("maf_trim"));
		assertFalse(grants.containsKey("maf_trim"));
	}

	@Test
	void aCapstoneKeepsEveryMilestoneItHad() {
		Map<String, Set<String>> grants = AdvancementMigration.migrate(
			Map.of("power_plant", Set.of("redline", "big_boost")));
		assertEquals(Set.of("redline", "big_boost"), grants.get("power/power_plant"));
	}

	@Test
	void aHiddenRowWithNothingToMoveToCreditsNothing() {
		assertTrue(AdvancementMigration.migrate(Map.of("cylinder_head", Set.of("cylinder_head"))).isEmpty());
		assertTrue(AdvancementMigration.creditsFor("throttle").isEmpty());
	}

	@Test
	void unknownAndEmptyProgressIsIgnoredAndNothingIsInvented() {
		assertTrue(AdvancementMigration.migrate(Map.of("not_ours", Set.of("x"))).isEmpty());
		assertTrue(AdvancementMigration.migrate(Map.of("alive", Set.<String>of())).isEmpty());
		assertTrue(AdvancementMigration.migrate(Map.of()).isEmpty());
	}

	@Test
	void theRootMovesAndNothingElseComesWithIt() {
		Map<String, Set<String>> grants = AdvancementMigration.migrate(Map.of("engineer", Set.of("engineer")));
		assertEquals(Set.of("root"), grants.keySet());
	}

	@Test
	void theOneIdThatSurvivedMapsOntoItself() {
		assertEquals("chief_engineer", AdvancementMigration.creditsFor("chief_engineer").get(0).advancement());
		assertTrue(FILES.containsKey("chief_engineer"));
	}
}
