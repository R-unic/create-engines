package com.createengines.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * docs/CONFIG.md lists every setting of the two config files with its default, its range and what the config's own comment
 * says, and what each shipped preset sets. Those tables are generated from the source and the preset files and held to them
 * here, so the reference can never drift from the mod: a setting added, renamed, re-ranged or re-worded without the doc
 * fails until {@code DOCS_REGEN=1 ./gradlew test --tests "*ConfigDocsTest"} rewrites the three generated blocks (the text
 * around them is written by hand and never touched).
 */
class ConfigDocsTest {

	private static final Path ROOT = Path.of("src/main/java/com/createengines/config");
	private static final Path DOC = Path.of("docs/CONFIG.md");
	private static final Path PRESETS = Path.of("src/main/resources/config_presets");
	private static final List<String> PRESET_NAMES = List.of("casual", "standard", "expert");

	private static final Pattern SETTING = Pattern.compile(
		"value\\(\"([\\w.]+)\",\\s*builder\\.comment\\((.*?)\\)\\s*\\.(defineInRange|define)\\(\"(\\w+)\",\\s*([^;]*?)\\)\\);",
		Pattern.DOTALL);
	private static final Pattern SECTION = Pattern.compile("builder\\.comment\\(([^;]*?)\\)\\s*\\.push\\(\"(\\w+)\"\\)",
		Pattern.DOTALL);
	private static final Pattern STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

	private static String source(String file) throws IOException {
		return Files.readString(ROOT.resolve(file))
			.replace("\r\n", "\n");
	}

	private static String join(String commentArguments) {
		List<String> parts = new ArrayList<>();
		Matcher m = STRING.matcher(commentArguments);
		while (m.find())
			parts.add(m.group(1)
				.replace("\\\"", "\""));
		return String.join(" ", parts)
			.replace("|", "\\|");
	}

	private static String settingsBlock(String source, String file) {
		Map<String, String> intro = new LinkedHashMap<>();
		Matcher sections = SECTION.matcher(source);
		while (sections.find())
			intro.put(sections.group(2), join(sections.group(1)));
		StringBuilder out = new StringBuilder();
		String current = null;
		Matcher m = SETTING.matcher(source);
		int found = 0;
		while (m.find()) {
			found++;
			String path = m.group(1);
			String section = path.substring(0, path.indexOf('.'));
			if (!section.equals(current)) {
				if (current != null)
					out.append('\n');
				current = section;
				out.append("### `[").append(section).append("]`\n\n");
				if (intro.containsKey(section))
					out.append(intro.get(section)).append("\n\n");
				out.append("| Setting | Default | Range | What it does |\n|---|---|---|---|\n");
			}
			String[] args = m.group(5).split(",");
			boolean ranged = m.group(3).equals("defineInRange");
			out.append("| `").append(m.group(4)).append("` | `").append(args[0].trim()).append("` | ")
				.append(ranged ? args[1].trim() + " to " + args[2].trim() : "on or off").append(" | ")
				.append(join(m.group(2))).append(" |\n");
		}
		long declared = Pattern.compile("value\\(\"").matcher(source).results().count();
		assertEquals(declared, found, "the scan of " + file + " skipped a setting it should have read");
		assertTrue(found > 0, file + ": no settings found");
		return out.toString().stripTrailing();
	}

	private static Map<String, Map<String, String>> preset(String name) throws IOException {
		Map<String, Map<String, String>> tables = new LinkedHashMap<>();
		String table = null;
		for (String line : Files.readAllLines(PRESETS.resolve(name + ".toml"))) {
			String t = line.strip();
			if (t.startsWith("#") || t.isEmpty())
				continue;
			if (t.startsWith("[")) {
				table = t.substring(1, t.length() - 1);
				tables.put(table, new LinkedHashMap<>());
			} else {
				String[] kv = t.split("=", 2);
				tables.get(table).put(kv[0].strip(), kv[1].strip());
			}
		}
		return tables;
	}

	private static String presetsBlock() throws IOException {
		Map<String, Map<String, Map<String, String>>> all = new LinkedHashMap<>();
		for (String name : PRESET_NAMES)
			all.put(name, preset(name));
		StringBuilder out = new StringBuilder();
		for (String table : List.of("balance", "features")) {
			if (out.length() > 0)
				out.append('\n');
			out.append("| `[").append(table).append("]` setting | casual | standard | expert |\n|---|---|---|---|\n");
			for (String key : all.get("standard").get(table).keySet()) {
				out.append("| `").append(key).append("`");
				for (String name : PRESET_NAMES)
					out.append(" | `").append(all.get(name).get(table).get(key)).append("`");
				out.append(" |\n");
			}
		}
		return out.toString().stripTrailing();
	}

	private static Map<String, String> generated() throws IOException {
		Map<String, String> blocks = new LinkedHashMap<>();
		blocks.put("server-settings", settingsBlock(source("CEConfig.java"), "CEConfig.java"));
		blocks.put("client-settings", settingsBlock(source("CEClientConfig.java"), "CEClientConfig.java"));
		blocks.put("presets", presetsBlock());
		return blocks;
	}

	private static String begin(String name) {
		return "<!-- generated:" + name + " -->";
	}

	private static String end(String name) {
		return "<!-- /generated:" + name + " -->";
	}

	@Test
	void theScanReadsTheSettingsItShould() throws IOException {
		Map<String, String> blocks = generated();
		assertTrue(blocks.get("server-settings").contains("| `stressPerTorque` | `78.7` | 1.0 to 10000.0 |"), blocks.get("server-settings"));
		assertTrue(blocks.get("server-settings").contains("| `forcedInduction` | `true` | on or off |"), blocks.get("server-settings"));
		assertTrue(blocks.get("client-settings").contains("| `density` | `1.0` | 0.1 to 4.0 |"), blocks.get("client-settings"));
		assertTrue(blocks.get("presets").contains("| `fuelConsumption` | `0.6`"), blocks.get("presets"));
	}

	@Test
	void theConfigReferenceHoldsExactlyWhatTheSourceSays() throws IOException {
		String doc = Files.readString(DOC);
		boolean crlf = doc.contains("\r\n");
		String text = doc.replace("\r\n", "\n");
		List<String> stale = new ArrayList<>();
		for (Map.Entry<String, String> block : generated().entrySet()) {
			String name = block.getKey();
			int from = text.indexOf(begin(name));
			int to = text.indexOf(end(name));
			assertTrue(from >= 0 && to > from, "docs/CONFIG.md has no " + begin(name) + " ... " + end(name) + " pair");
			String body = "\n" + block.getValue() + "\n";
			if (!text.substring(from + begin(name).length(), to).equals(body)) {
				stale.add(name);
				text = text.substring(0, from + begin(name).length()) + body + text.substring(to);
			}
		}
		if (!stale.isEmpty() && System.getenv("DOCS_REGEN") != null) {
			Files.writeString(DOC, crlf ? text.replace("\n", "\r\n") : text);
			stale.clear();
		}
		assertEquals(List.of(), stale, "docs/CONFIG.md is out of date with the source in these blocks; regenerate with DOCS_REGEN=1");
	}

	@Test
	void everyPresetNamesTheSameSettingsAsTheStandardOne() throws IOException {
		Map<String, Map<String, String>> standard = preset("standard");
		for (String name : PRESET_NAMES)
			for (String table : standard.keySet())
				assertEquals(standard.get(table).keySet(), preset(name).get(table).keySet(), name + " [" + table + "]");
	}
}
