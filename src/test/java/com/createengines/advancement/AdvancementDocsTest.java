package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The track table of docs/ADVANCEMENTS.md is held to the generated advancement files: the rows of each track are the files
 * of that folder, a row marked as announcing in chat does, a toast-only row does not, a row marked as a goal or challenge
 * is one (and no other row is), and the counts the page quotes are the counts of the files. An advancement added, moved,
 * renamed or re-framed without the table fails here.
 */
class AdvancementDocsTest {

	private static final Path DOC = Path.of("docs/ADVANCEMENTS.md");
	private static final Path DIRECTORY = Path.of("src/generated/resources/data/create_engines/advancement");

	private record Row(String id, boolean announces, boolean sting, boolean mistake) {
	}

	private static String text(Path path) throws IOException {
		return Files.readString(path)
			.replace("\r\n", "\n");
	}

	/** Every row of the table, keyed by advancement ID (track/row, or the bare name for the top row). */
	private static Map<String, Row> table() throws IOException {
		Map<String, Row> rows = new TreeMap<>();
		Pattern line = Pattern.compile("^\\| \\*\\*([A-Za-z ]+)\\*\\*(?: \\(root `\\w+`\\))? \\| (.*) \\|$");
		Pattern item = Pattern.compile("^(?:\\*\\*)?([a-z_]+)(?:\\*\\*)? ([●○])(.*)$");
		for (String text : text(DOC).split("\n")) {
			Matcher match = line.matcher(text);
			if (!match.matches())
				continue;
			String track = match.group(1)
				.strip()
				.toLowerCase()
				.replace(' ', '_');
			for (String cell : match.group(2)
				.split(", | \\+ ")) {
				Matcher row = item.matcher(cell.strip());
				if (!row.matches())
					continue;
				String id = track.equals("top") ? row.group(1) : track + "/" + row.group(1);
				String marks = row.group(3);
				rows.put(id, new Row(id, row.group(2)
					.equals("●"), marks.contains("✦"), marks.contains("😬")));
			}
		}
		return rows;
	}

	private static Map<String, String> files() throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(DIRECTORY)) {
			for (Path path : paths.filter(p -> p.toString()
				.endsWith(".json"))
				.toList())
				files.put(DIRECTORY.relativize(path)
					.toString()
					.replace('\\', '/')
					.replace(".json", ""), text(path));
		}
		files.remove("root");
		return files;
	}

	@Test
	void theTableNamesExactlyTheAdvancementsThatExist() throws IOException {
		assertEquals(new TreeSet<>(files().keySet()), new TreeSet<>(table().keySet()),
			"docs/ADVANCEMENTS.md and the generated advancement files list different rows");
	}

	@Test
	void aRowAnnouncesInChatExactlyWhenTheTableSaysSo() throws IOException {
		Map<String, String> files = files();
		List<String> wrong = new ArrayList<>();
		for (Row row : table().values()) {
			boolean quiet = Pattern.compile("\"announce_to_chat\"\\s*:\\s*false")
				.matcher(files.get(row.id()))
				.find();
			if (row.announces() == quiet)
				wrong.add(row.id() + ": table says " + (row.announces() ? "chat" : "toast only") + ", file says "
					+ (quiet ? "toast only" : "chat"));
		}
		assertEquals(List.of(), wrong);
	}

	@Test
	void aRowHasTheStingExactlyWhenTheTableMarksItAGoalOrChallenge() throws IOException {
		Map<String, String> files = files();
		List<String> wrong = new ArrayList<>();
		for (Row row : table().values()) {
			boolean goal = files.get(row.id())
				.matches("(?s).*\"frame\"\\s*:\\s*\"(goal|challenge)\".*");
			if (row.sting() != goal)
				wrong.add(row.id() + ": table says " + (row.sting() ? "goal or challenge" : "task") + ", file says "
					+ (goal ? "goal or challenge" : "task"));
		}
		assertEquals(List.of(), wrong);
	}

	@Test
	void theCountsThePageQuotesAreTheCountsOfTheFiles() throws IOException {
		Map<String, String> files = files();
		long quiet = table().values()
			.stream()
			.filter(row -> !row.announces())
			.count();
		String doc = text(DOC);
		assertTrue(doc.contains(files.size() + " rows shown"), "the page does not say " + files.size() + " rows shown");
		assertTrue(doc.contains("holds the " + files.size() + " rows"), "step 1 does not say it holds " + files.size() + " rows");
		assertTrue(doc.contains("`QUIET` the " + quiet + " that are toast only"),
			"the page does not say " + quiet + " rows are toast only");
		Matcher listed = Pattern.compile("`QUIET` the \\d+ that are toast only \\(([^)]*)\\)")
			.matcher(doc);
		assertTrue(listed.find(), "the list of quiet rows is missing");
		assertEquals(quiet, listed.group(1)
			.split(",")
			.length, "the quiet rows named in the page are not the quiet rows of the table");
	}
}
