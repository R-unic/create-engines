package com.createengines.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Old worlds must keep loading, and factories built on one version must work the same on every later version of the
 * same major release. Both rules are mostly about names and numbers that a saved world has already committed to:
 * registry ids, block state properties, saved-data keys, and the recipes and loot tables a factory was built around.
 * <p>
 * {@code src/test/resources/compat/manifest.txt} lists every one of them as shipped. This test rescans the source and
 * fails if any entry has gone or changed. New entries are fine (they are listed in the output so they can be added);
 * run with the environment variable {@code COMPAT_REGEN=1} to append them. Removing or editing an existing entry by
 * hand is a decision to break old worlds, and belongs in a major release only.
 * <p>
 * The numbers a factory runs on are pinned separately by BehaviourBaselineTest (engine.sim); what a saved block entity
 * reads back is pinned by the {@code LegacyWorldGameTests} GameTests.
 */
class CompatManifestTest {

	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path MANIFEST = ROOT.resolve("src/test/resources/compat/manifest.txt");
	private static final Path JAVA = ROOT.resolve("src/main/java/com/createengines");

	private static final Pattern REGISTRATE = Pattern.compile("REGISTRATE\\s*\\.\\s*(\\w+)\\(\\s*\"([a-z0-9_]+)\"");
	private static final Pattern REGISTER = Pattern.compile("\\bregister\\(\\s*\"([a-z0-9_]+)\"");
	private static final Pattern PROPERTY =
		Pattern.compile("(?:Boolean|Integer|Enum|Direction)Property\\s*\\.\\s*create\\(\\s*\"([a-z0-9_]+)\"");
	private static final Pattern NBT_KEY =
		Pattern.compile("\\.(?:put\\w*|get\\w*|contains|remove)\\(\\s*\"([A-Za-z][A-Za-z0-9_]*)\"");
	private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z0-9_]*)\"");

	/** Data folders whose contents decide what a built factory produces, so their text is pinned, not just their names. */
	private static final List<String> BEHAVIOUR_DATA = List.of(
		"data/create_engines/recipe", "data/create_engines/data_maps", "data/create_engines/loot_table");
	/** Data folders where only the file's existence is pinned (tags may grow, a tag going missing breaks recipes). */
	private static final List<String> PRESENCE_DATA = List.of("data/create_engines/tags", "data/c/tags");

	@Test
	void everyShippedNameAndNumberIsStillThere() throws IOException {
		Map<String, String> shipped = readManifest();
		Map<String, String> now = scan();

		List<String> missing = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, String> e : shipped.entrySet()) {
			String value = now.get(e.getKey());
			if (value == null)
				missing.add(e.getKey());
			else if (!value.equals(e.getValue()))
				changed.add(e.getKey());
		}
		List<String> added = new ArrayList<>();
		for (String key : now.keySet())
			if (!shipped.containsKey(key))
				added.add(key);

		if (System.getenv("COMPAT_REGEN") != null && !added.isEmpty()) {
			Map<String, String> merged = new TreeMap<>(shipped);
			for (String key : added)
				merged.put(key, now.get(key));
			write(merged);
			System.out.println("compat manifest: appended " + added.size() + " new entries");
			added.clear();
		}

		if (!added.isEmpty())
			System.out.println("compat manifest: " + added.size()
				+ " new entries not yet recorded (rerun with COMPAT_REGEN=1 to append): " + added);

		String advice = "\nOld worlds and built factories rely on these. Restore them, or read the old form on load, "
			+ "or move the change to a new block, item or config switch. Only a major release (2.0) may remove or "
			+ "change them, and then the entry is edited out of src/test/resources/compat/manifest.txt by hand.";
		assertTrue(missing.isEmpty(), "Shipped names gone: " + missing + advice);
		assertTrue(changed.isEmpty(), "Shipped behaviour data changed: " + changed + advice);
	}

	private static Map<String, String> scan() throws IOException {
		Map<String, String> out = new TreeMap<>();

		try (Stream<Path> files = Files.walk(JAVA)) {
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				String name = file.getFileName().toString().replace(".java", "");
				boolean registry = file.getParent().getFileName().toString().equals("registry");
				if (registry) {
					Matcher m = REGISTRATE.matcher(text);
					while (m.find())
						out.put("reg:" + m.group(1) + ":" + m.group(2), "");
					m = REGISTER.matcher(text);
					while (m.find())
						out.put("reg:" + name + ":" + m.group(1), "");
				}
				Matcher m = PROPERTY.matcher(text);
				while (m.find())
					out.put("prop:" + name + ":" + m.group(1), "");
				if (text.contains("StringRepresentable") && text.contains("enum ")) {
					m = LITERAL.matcher(text);
					while (m.find())
						out.put("enumvalue:" + name + ":" + m.group(1), "");
				}
				// LegacyWorldGameTests only reads fixtures and mentions keys of its own (an environment variable, the snbt
				// layout); none of them is saved data, and recording them would pin names no world has
				if (text.contains("CompoundTag") && !name.equals("LegacyWorldGameTests")) {
					m = NBT_KEY.matcher(text);
					while (m.find())
						out.put("nbt:" + name + ":" + m.group(1), "");
				}
			}
		}

		for (String root : new String[] {"src/main/resources/", "src/generated/resources/"})
			for (String folder : BEHAVIOUR_DATA)
				scanData(out, ROOT.resolve(root), folder, true);
		for (String folder : PRESENCE_DATA)
			scanData(out, ROOT.resolve("src/main/resources/"), folder, false);
		return out;
	}

	private static void scanData(Map<String, String> out, Path base, String folder, boolean hash) throws IOException {
		Path dir = base.resolve(folder);
		if (!Files.isDirectory(dir))
			return;
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
				String key = "data:" + base.relativize(file).toString().replace('\\', '/');
				out.put(key, hash ? digest(file) : "");
			}
		}
	}

	/** SHA-1 of the file's text with line endings normalised (the checkout is CRLF on Windows and LF elsewhere). */
	private static String digest(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, String> readManifest() throws IOException {
		Map<String, String> out = new TreeMap<>();
		if (!Files.exists(MANIFEST))
			return out;
		for (String line : Files.readAllLines(MANIFEST, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#"))
				continue;
			int tab = line.indexOf('\t');
			out.put(tab < 0 ? line : line.substring(0, tab), tab < 0 ? "" : line.substring(tab + 1));
		}
		return out;
	}

	private static void write(Map<String, String> entries) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("# Names and data a saved world or a built factory depends on. See CompatManifestTest.");
		lines.add("# Append only. Removing or editing a line breaks old worlds: major releases only.");
		for (Map.Entry<String, String> e : entries.entrySet())
			lines.add(e.getValue().isEmpty() ? e.getKey() : e.getKey() + "\t" + e.getValue());
		Files.createDirectories(MANIFEST.getParent());
		Files.write(MANIFEST, lines, StandardCharsets.UTF_8);
	}
}
