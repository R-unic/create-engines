package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The guides for pack makers and addon authors (docs/PACKS.md, DATA.md, CONFIG.md, ADDONS.md) are held to the code the way
 * the API is: the example in the addon guide is the file that is built and run by a GameTest, every key a data file takes is
 * named in the data reference, every {@code /ce} subcommand is described, and no link in them points nowhere. The settings
 * tables of CONFIG.md are held by {@code ConfigDocsTest}.
 */
class GuideDocsTest {

	private static final Path ADDONS = Path.of("docs/ADDONS.md");
	private static final Path DATA = Path.of("docs/DATA.md");
	private static final Path PACKS = Path.of("docs/PACKS.md");
	private static final List<Path> GUIDES = List.of(Path.of("docs/PACKS.md"), Path.of("docs/DATA.md"), Path.of("docs/CONFIG.md"),
		Path.of("docs/ADDONS.md"), Path.of("README.md"));

	private static String text(Path path) throws IOException {
		return Files.readString(path)
			.replace("\r\n", "\n");
	}

	/** The lines of a source file between its docs:begin and docs:end lines, one tab of indentation removed, tabs as four spaces. */
	private static String region(Path source) throws IOException {
		List<String> out = new ArrayList<>();
		boolean inside = false;
		for (String line : text(source).split("\n")) {
			if (line.strip().equals("// docs:end"))
				inside = false;
			if (inside)
				out.add((line.startsWith("\t") ? line.substring(1) : line).replace("\t", "    "));
			if (line.strip().equals("// docs:begin"))
				inside = true;
		}
		assertTrue(!out.isEmpty(), source + " has no docs:begin / docs:end region");
		return String.join("\n", out);
	}

	@Test
	void theExampleInTheAddonGuideIsTheFileThatIsBuiltAndRun() throws IOException {
		String doc = text(ADDONS);
		Matcher marker = Pattern.compile("<!-- example:(\\S+) -->\n```java\n(.*?)\n```\n<!-- /example -->", Pattern.DOTALL)
			.matcher(doc);
		int found = 0;
		StringBuilder fresh = new StringBuilder();
		int last = 0;
		boolean stale = false;
		while (marker.find()) {
			found++;
			String expected = region(Path.of(marker.group(1)));
			fresh.append(doc, last, marker.start(2))
				.append(expected);
			last = marker.end(2);
			stale |= !marker.group(2)
				.equals(expected);
		}
		assertTrue(found > 0, "docs/ADDONS.md shows no example from the source");
		fresh.append(doc.substring(last));
		if (stale && System.getenv("DOCS_REGEN") != null) {
			boolean crlf = Files.readString(ADDONS)
				.contains("\r\n");
			Files.writeString(ADDONS, crlf ? fresh.toString().replace("\n", "\r\n") : fresh.toString());
			stale = false;
		}
		assertTrue(!stale, "the example in docs/ADDONS.md differs from its source file; regenerate with DOCS_REGEN=1");
	}

	@Test
	void everyKeyADataFileTakesIsNamedInTheDataReference() throws IOException {
		String doc = text(DATA);
		TreeSet<String> missing = new TreeSet<>();
		for (String line : Files.readAllLines(Path.of("src/test/resources/compat/script-surface.txt"))) {
			Matcher m = Pattern.compile("^data (fuel|part_stats) (\\w+)").matcher(line);
			if (m.find() && !doc.contains("`" + m.group(2) + "`"))
				missing.add(m.group(1) + "." + m.group(2));
		}
		Matcher keys = Pattern.compile("Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL)
			.matcher(text(Path.of("src/main/java/com/createengines/engine/sim/PartEffects.java")).substring(
				text(Path.of("src/main/java/com/createengines/engine/sim/PartEffects.java")).indexOf("KEYS")));
		assertTrue(keys.find(), "PartEffects.KEYS not found");
		Matcher key = Pattern.compile("\"(\\w+)\"")
			.matcher(keys.group(1));
		int count = 0;
		while (key.find()) {
			count++;
			if (!doc.contains("`" + key.group(1) + "`"))
				missing.add("part_effects." + key.group(1));
		}
		assertEquals(8, count, "the scan of the part effect keys read " + count);
		// The cam profile files' keys, and the data map an addon's own part names its effect file by
		Matcher camKeys = Pattern.compile("Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL)
			.matcher(text(Path.of("src/main/java/com/createengines/engine/sim/CamProfiles.java")).substring(
				text(Path.of("src/main/java/com/createengines/engine/sim/CamProfiles.java")).indexOf("KEYS")));
		assertTrue(camKeys.find(), "CamProfiles.KEYS not found");
		Matcher camKey = Pattern.compile("\"(\\w+)\"")
			.matcher(camKeys.group(1));
		int camCount = 0;
		while (camKey.find()) {
			camCount++;
			if (!doc.contains("`" + camKey.group(1) + "`"))
				missing.add("cam_profiles." + camKey.group(1));
		}
		assertEquals(6, camCount, "the scan of the cam profile keys read " + camCount);
		if (!doc.contains("`part_effect`") || !doc.contains("`effect`"))
			missing.add("part_effect.effect");
		assertEquals(new TreeSet<String>(), missing, "docs/DATA.md does not name these keys");
	}

	@Test
	void everyCommandIsDescribedInThePackMakersGuide() throws IOException {
		String doc = text(PACKS);
		Matcher literal = Pattern.compile("Commands\\.literal\\(\"(\\w+)\"\\)")
			.matcher(text(Path.of("src/main/java/com/createengines/command/CeCommand.java")));
		int count = 0;
		while (literal.find()) {
			String name = literal.group(1);
			if (name.equals("ce"))
				continue;
			count++;
			assertTrue(doc.contains("/ce " + name), "docs/PACKS.md does not describe /ce " + name);
		}
		assertTrue(count >= 5, "found only " + count + " subcommands");
	}

	@Test
	void everyShippedFuelIsInTheDataReferencesTable() throws IOException {
		String doc = text(DATA);
		Matcher id = Pattern.compile("\"(create_engines:\\w+)\":\\s*\\{")
			.matcher(text(Path.of("src/main/resources/data/create_engines/data_maps/fluid/fuel.json")));
		int count = 0;
		while (id.find()) {
			count++;
			assertTrue(doc.contains("`" + id.group(1) + "`"), "docs/DATA.md leaves out the shipped fuel " + id.group(1));
		}
		assertTrue(count >= 5, "found only " + count + " shipped fuels");
	}

	@Test
	void noLinkInTheGuidesPointsAtNothing() throws IOException {
		List<String> broken = new ArrayList<>();
		for (Path guide : GUIDES) {
			Matcher link = Pattern.compile("\\]\\(([^)\\s]+)\\)")
				.matcher(text(guide));
			while (link.find()) {
				String target = link.group(1);
				if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto:"))
					continue;
				String file = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
				Path resolved = guide.toAbsolutePath()
					.getParent()
					.resolve(file)
					.normalize();
				if (!Files.exists(resolved))
					broken.add(guide + " -> " + target);
			}
		}
		assertEquals(List.of(), broken);
	}
}
