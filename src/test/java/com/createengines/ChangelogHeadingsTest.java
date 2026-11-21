package com.createengines;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The publish workflow finds a release's text by an exact line, {@code ## Changelog for X.Y.Z}, in {@code docs/LISTING.md}, and falls
 * back to a one-line "Release X.Y.Z" when none matches. A heading with a title after the number (as 2.0.0 first had) matched nothing
 * and the release went out with no changelog, so every heading is held to the exact form.
 */
class ChangelogHeadingsTest {

	private static final Path LISTING = Path.of("")
		.toAbsolutePath()
		.resolve("docs/LISTING.md");
	private static final Pattern EXACT = Pattern.compile("## Changelog for \\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?");

	@Test
	void everyChangelogHeadingIsTheExactLineThePublishWorkflowLooksFor() throws Exception {
		List<String> headings = Files.readAllLines(LISTING)
			.stream()
			.filter(line -> line.startsWith("## Changelog for"))
			.toList();
		assertTrue(headings.size() >= 10, "the listing holds a changelog for every release: " + headings.size());
		for (String heading : headings)
			assertTrue(EXACT.matcher(heading)
				.matches(), "the publish workflow matches '## Changelog for X.Y.Z' exactly, so this finds no text: '" + heading + "'");
	}

	@Test
	void everyChangelogHasTextUnderIt() throws Exception {
		List<String> lines = Files.readAllLines(LISTING);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.startsWith("## Changelog for "))
				continue;
			int words = 0;
			for (int j = i + 1; j < lines.size() && !lines.get(j)
				.startsWith("## "); j++)
				words += lines.get(j)
					.isBlank() ? 0 : 1;
			assertTrue(words > 0, line + " has no text under it");
		}
	}
}
