package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every page of the documentation (README.md and everything under docs/) is held to the tree it describes: a relative link
 * points at a file that exists (and at a heading that exists, when it names one), a path written in backticks that starts
 * with {@code docs/}, {@code src/}, {@code scripts/} or {@code .github/} exists, and a test class named in backticks
 * ({@code FooTest}, {@code FooGameTests}) is a class in the source. Prose that is history rather than a pointer is not
 * checked: only these three shapes are. A path that is a template rather than a file ({@code vX.Y.Z}, {@code ...}, a
 * wildcard or a {@code <placeholder>}) is skipped.
 */
class DocsReferencesTest {

	private static final Path ROOT = Path.of("").toAbsolutePath();

	private static List<Path> pages() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
			return Stream.concat(Stream.of(Path.of("README.md")), paths.filter(p -> p.toString().endsWith(".md")))
				.toList();
		}
	}

	private static String text(Path path) throws IOException {
		return Files.readString(path)
			.replace("\r\n", "\n");
	}

	/** The anchor GitHub gives a heading: lower case, punctuation dropped, spaces to hyphens. */
	private static String slug(String heading) {
		return heading.strip()
			.toLowerCase()
			.replaceAll("[`*_]", "")
			.replaceAll("[^\\p{L}\\p{N} \\-]", "")
			.replace(' ', '-');
	}

	private static Set<String> anchors(Path page) throws IOException {
		Set<String> out = new HashSet<>();
		boolean fence = false;
		for (String line : text(page).split("\n")) {
			if (line.startsWith("```"))
				fence = !fence;
			if (!fence && line.matches("#{1,6} .*"))
				out.add(slug(line.replaceFirst("#+ ", "")));
		}
		return out;
	}

	private static String prose(Path page) throws IOException {
		return text(page).replaceAll("(?s)```.*?```", "");
	}

	@Test
	void everyRelativeLinkPointsAtAFileAndAHeadingThatExist() throws IOException {
		Set<String> broken = new TreeSet<>();
		for (Path page : pages()) {
			Matcher link = Pattern.compile("\\]\\(([^)\\s]+)\\)")
				.matcher(prose(page));
			while (link.find()) {
				String target = link.group(1);
				if (target.startsWith("http") || target.startsWith("mailto:"))
					continue;
				String file = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
				String anchor = target.contains("#") ? target.substring(target.indexOf('#') + 1) : null;
				Path resolved = file.isEmpty() ? page : page.toAbsolutePath()
					.getParent()
					.resolve(file)
					.normalize();
				if (!Files.exists(resolved)) {
					broken.add(page + " -> " + target + " (no such file)");
				} else if (anchor != null && resolved.toString()
					.endsWith(".md") && !anchors(resolved).contains(anchor)) {
					broken.add(page + " -> " + target + " (no such heading)");
				}
			}
		}
		assertEquals(Set.of(), broken);
	}

	@Test
	void everyPathNamedInBackticksExists() throws IOException {
		Set<String> missing = new TreeSet<>();
		Pattern path = Pattern.compile("`((?:docs|src|scripts|\\.github)/[\\w./\\-]+)`");
		int checked = 0;
		for (Path page : pages()) {
			Matcher m = path.matcher(prose(page));
			while (m.find()) {
				String named = m.group(1);
				if (named.endsWith("/") || named.contains("<") || named.contains("*") || named.contains("...")
					|| named.contains("vX.Y.Z"))
					continue;
				checked++;
				if (!Files.exists(ROOT.resolve(named)))
					missing.add(page + " names " + named);
			}
		}
		assertTrue(checked > 50, "only " + checked + " paths were found; the scan is broken");
		assertEquals(Set.of(), missing);
	}

	@Test
	void everyTestClassNamedInBackticksIsInTheSource() throws IOException {
		Set<String> classes = new HashSet<>();
		for (String root : new String[] { "src/test/java", "src/main/java" })
			try (Stream<Path> paths = Files.walk(Path.of(root))) {
				paths.map(p -> p.getFileName()
					.toString())
					.filter(n -> n.endsWith(".java"))
					.forEach(n -> classes.add(n.substring(0, n.length() - 5)));
			}
		Set<String> missing = new TreeSet<>();
		Pattern name = Pattern.compile("`([A-Z]\\w*(?:Test|Tests))(?:#\\w+)?`");
		int checked = 0;
		for (Path page : pages()) {
			Matcher m = name.matcher(prose(page));
			while (m.find()) {
				checked++;
				if (!classes.contains(m.group(1)))
					missing.add(page + " names " + m.group(1));
			}
		}
		assertTrue(checked > 30, "only " + checked + " test classes were found; the scan is broken");
		assertEquals(Set.of(), missing);
	}
}
