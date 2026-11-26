package com.createengines.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The parts of the public surface that {@link ApiSurfaceTest} cannot see: what a KubeJS script can name (the
 * {@code CreateEngines} global, the two script events and what an event gives a script) and the keys of the two data maps a
 * pack writes ({@code fuel} and {@code part_stats}). Read from the source and compared with
 * {@code compat/script-surface.txt}. A line that disappears or changes is a major change and fails; a new line fails until it
 * is recorded with {@code SURFACE_REGEN=1} and {@link ApiVersion#MINOR} is raised.
 */
class ScriptAndDataSurfaceTest {

	private static final Path ROOT = Path.of("src/main/java/com/createengines");
	private static final Path FILE = Path.of("src/test/resources/compat/script-surface.txt");

	private static String read(String relative) throws IOException {
		return ApiSurfaceTest.clean(Files.readString(ROOT.resolve(relative)));
	}

	/** Source without comments, but with string contents (the data map keys are strings). */
	private static String readKeepingStrings(String relative) throws IOException {
		return Files.readString(ROOT.resolve(relative))
			.replaceAll("(?s)/\\*.*?\\*/", " ")
			.replaceAll("//[^\\n]*", " ");
	}

	/** Every line the source gives now, normalised. */
	static Set<String> now() throws IOException {
		Set<String> out = new TreeSet<>();

		String plugin = Files.readString(ROOT.resolve("compat/kubejs/CreateEnginesKubePlugin.java"));
		Matcher binding = Pattern.compile("bindings\\.add\\(\"(\\w+)\"").matcher(plugin);
		while (binding.find())
			out.add("script global " + binding.group(1));

		String bindingSource = read("compat/kubejs/CreateEnginesBinding.java");
		Matcher method = Pattern.compile("public static ([\\w<>.@ ]+?) (\\w+)\\(([^)]*)\\)").matcher(bindingSource);
		while (method.find())
			out.add("script CreateEngines." + method.group(2) + "(" + method.group(3).replaceAll("\\s+", " ").trim() + ") -> "
				+ method.group(1).replaceAll("@\\w+ ", "").trim());

		String events = Files.readString(ROOT.resolve("compat/kubejs/CreateEnginesEvents.java"));
		Matcher group = Pattern.compile("EventGroup\\.of\\(\"(\\w+)\"\\)").matcher(events);
		while (group.find())
			out.add("script event group " + group.group(1));
		Matcher handler = Pattern.compile("GROUP\\.(server|client|startup)\\(\"(\\w+)\", \\(\\) -> (\\w+)\\.class").matcher(events);
		while (handler.find())
			out.add("script event " + handler.group(2) + " " + handler.group(1) + " " + handler.group(3));

		for (String type : List.of("EngineStateKubeEvent", "EngineMilestoneKubeEvent")) {
			Matcher getter = Pattern.compile("public ([\\w.]+) (get|is)(\\w+)\\(\\)")
				.matcher(read("compat/kubejs/" + type + ".java"));
			while (getter.find())
				out.add("script field " + type + "." + Character.toLowerCase(getter.group(3).charAt(0))
					+ getter.group(3).substring(1) + " : " + getter.group(1));
		}

		String maps = readKeepingStrings("registry/CEDataMaps.java");
		int part = maps.indexOf("record PartStatsProperties");
		String fuel = maps.substring(maps.indexOf("record FuelProperties"), part);
		String stats = maps.substring(part);
		Matcher field = Pattern.compile("Codec\\.DOUBLE\\.(fieldOf|optionalFieldOf)\\(\"(\\w+)\"(?:, ([\\d.]+))?\\)")
			.matcher(fuel);
		while (field.find())
			out.add("data fuel " + field.group(2) + " " + (field.group(1).equals("fieldOf") ? "required"
				: field.group(3) == null ? "optional" : "optional " + field.group(3)));

		String statsSource = readKeepingStrings("engine/sim/PartStats.java");
		String min = number(statsSource, "MIN_SCALE");
		String max = number(statsSource, "MAX_SCALE");
		Matcher scale = Pattern.compile("scale\\(\\)\\.optionalFieldOf\\(\"(\\w+)\", ([\\d.]+)\\)").matcher(stats);
		while (scale.find())
			out.add("data part_stats " + scale.group(1) + " optional " + scale.group(2) + " range " + min + ".." + max);

		// The part_effect data map (an addon block's own effect file), the part_effects files and the cam_profiles files
		Matcher effect = Pattern.compile("ResourceLocation\\.CODEC\\.fieldOf\\(\"(\\w+)\"\\)").matcher(stats);
		while (effect.find())
			out.add("data part_effect " + effect.group(1) + " required");
		for (String key : keys("engine/sim/PartEffects.java"))
			out.add("data part_effects " + key + " optional 1.0 range " + min + ".." + max);
		String camSource = readKeepingStrings("engine/sim/CamProfiles.java");
		for (String key : keys("engine/sim/CamProfiles.java"))
			out.add("data cam_profiles " + key + " optional range 0..720");
		out.add("data cam_profiles valve duration " + number(camSource, "MIN_DURATION") + ".." + number(camSource, "MAX_DURATION"));

		// The flywheel data map (a pack's or an addon's own flywheel, API 1.2): its two masses and their range
		String flywheel = maps.substring(maps.indexOf("record FlywheelProperties"), maps.indexOf("DataMapType<Block, FlywheelProperties>"));
		String limit = number(readKeepingStrings("engine/FlywheelSpec.java"), "MAX_INERTIA");
		Matcher mass = Pattern.compile("mass\\(\\)\\.(fieldOf|optionalFieldOf)\\(\"(\\w+)\"(?:, ([\\d.]+))?\\)").matcher(flywheel);
		while (mass.find())
			out.add("data flywheel " + mass.group(2) + " " + (mass.group(1).equals("fieldOf") ? "required"
				: "optional " + mass.group(3)) + " range 0.0.." + limit);
		return out;
	}

	/** The strings of the {@code KEYS = Set.of(...)} a data file's reader holds, in the order written. */
	private static List<String> keys(String relative) throws IOException {
		String source = readKeepingStrings(relative);
		Matcher set = Pattern.compile("KEYS\\s*=\\s*Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL).matcher(source);
		assertTrue(set.find(), relative + " has no KEYS");
		List<String> keys = new ArrayList<>();
		Matcher key = Pattern.compile("\"(\\w+)\"").matcher(set.group(1));
		while (key.find())
			keys.add(key.group(1));
		assertTrue(keys.size() >= 6, relative + " KEYS read as " + keys);
		return keys;
	}

	private static String number(String source, String constant) {
		Matcher m = Pattern.compile(constant + " = ([\\d.]+)").matcher(source);
		assertTrue(m.find(), constant + " not found");
		return m.group(1);
	}

	private static Set<String> shipped() throws IOException {
		Set<String> out = new TreeSet<>();
		for (String line : Files.readAllLines(FILE))
			if (!line.isBlank() && !line.startsWith("#"))
				out.add(line);
		return out;
	}

	@Test
	void theScanSeesTheScriptAndTheDataSurface() throws IOException {
		Set<String> now = now();
		assertTrue(now.contains("script global CreateEngines"), "" + now);
		assertTrue(now.stream().anyMatch(l -> l.startsWith("script CreateEngines.engineAt(")), "" + now);
		assertTrue(now.contains("script event stateChanged server EngineStateKubeEvent"), "" + now);
		assertTrue(now.contains("script event milestone server EngineMilestoneKubeEvent"), "" + now);
		assertTrue(now.contains("script field EngineStateKubeEvent.stalled : boolean"), "" + now);
		assertTrue(now.contains("data fuel energy_per_mb required"), "" + now);
		assertTrue(now.contains("data fuel sulfur optional"), "" + now);
		assertTrue(now.contains("data part_stats inertia optional 1.0 range 0.1..10"), "" + now);
		assertEquals(11, now.stream().filter(l -> l.startsWith("data part_stats ")).count());
		assertTrue(now.contains("data fuel flammability optional 1.0"), "" + now);
		assertTrue(now.contains("data fuel heat_factor optional 1.0"), "" + now);
		assertEquals(10, now.stream().filter(l -> l.startsWith("data fuel ")).count());
		assertEquals(8, now.stream().filter(l -> l.startsWith("data part_effects ")).count());
		assertEquals(6, now.stream().filter(l -> l.startsWith("data cam_profiles ") && l.contains(" optional ")).count());
		assertTrue(now.contains("data part_effect effect required"), "" + now);
		assertTrue(now.contains("data flywheel primary_inertia required range 0.0..100.0"), "" + now);
		assertTrue(now.contains("data flywheel secondary_inertia optional 0.0 range 0.0..100.0"), "" + now);
		assertEquals(2, now.stream().filter(l -> l.startsWith("data flywheel ")).count());
		assertTrue(now.contains("data cam_profiles valve duration 40..400"), "" + now);
	}

	@Test
	void everythingAScriptOrAPackCouldNameIsStillThere() throws IOException {
		Set<String> gone = new TreeSet<>(shipped());
		gone.removeAll(now());
		assertTrue(gone.isEmpty(), "A script or data map name was removed or changed, which is a major change:\n"
			+ String.join("\n", gone));
	}

	@Test
	void newNamesAreRecordedAndTheMinorVersionMovesWithThem() throws IOException {
		Set<String> added = new TreeSet<>(now());
		added.removeAll(shipped());
		if (System.getenv("SURFACE_REGEN") != null && !added.isEmpty()) {
			Set<String> merged = new TreeSet<>(shipped());
			merged.addAll(added);
			List<String> lines = new ArrayList<>();
			lines.add("# The names a KubeJS script and a pack's data maps use, as ScriptAndDataSurfaceTest reads them from the source.");
			lines.add("# Never edit a line: removing or changing one is a major change. A new line is a minor change (bump");
			lines.add("# ApiVersion.MINOR) and is added with SURFACE_REGEN=1.");
			lines.addAll(merged);
			Files.write(FILE, lines);
			added.clear();
		}
		assertEquals(Set.of(), added, "new script or data names; record them with SURFACE_REGEN=1 and bump ApiVersion.MINOR");
	}
}
