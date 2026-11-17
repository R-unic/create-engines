package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.Heat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The JEI and EMI pages for refining routes (client/compat). Both viewers find their plugin by an annotation and load
 * it only when they are installed, so what has to hold is read off the jar a player gets: the plugins are there with
 * their annotations, nothing else in the mod can pull a viewer class in (a game without JEI or EMI must start), and the
 * wording the pages use is in the language file. What the pages look like, and that each viewer accepts them, is checked
 * by the compatibility rows that load the viewer (docs/COMPAT.md).
 */
class RecipeViewerCompatTest {

	private static final String JEI_PLUGIN = "com/createengines/client/compat/jei/CEJeiPlugin";
	private static final String EMI_PLUGIN = "com/createengines/client/compat/emi/CEEmiPlugin";

	private static Path jar;

	@BeforeAll
	static void findTheJar() {
		String path = System.getProperty("ce.jar");
		Assumptions.assumeTrue(path != null && Files.exists(Path.of(path)),
			"no jar to inspect (the Gradle test task builds it and passes -Dce.jar)");
		jar = Path.of(path);
	}

	private static byte[] bytes(JarFile file, String entry) throws IOException {
		JarEntry found = file.getJarEntry(entry);
		assertNotNull(found, entry + " is not in the jar");
		try (InputStream in = file.getInputStream(found)) {
			return in.readAllBytes();
		}
	}

	@Test
	void eachPluginIsInTheJarWithTheAnnotationItsViewerLooksFor() throws IOException {
		try (JarFile file = new JarFile(jar.toFile())) {
			// The annotation's descriptor sits in the class's constant pool as a Utf8
			String jei = new String(bytes(file, JEI_PLUGIN + ".class"), StandardCharsets.ISO_8859_1);
			assertTrue(jei.contains("Lmezz/jei/api/JeiPlugin;"), "the JEI plugin class lacks @JeiPlugin, so JEI never finds it");
			String emi = new String(bytes(file, EMI_PLUGIN + ".class"), StandardCharsets.ISO_8859_1);
			assertTrue(emi.contains("Ldev/emi/emi/api/EmiEntrypoint;"), "the EMI plugin class lacks @EmiEntrypoint, so EMI never finds it");
		}
	}

	/** A viewer's classes may be named only inside the plugin packages, which nothing else loads. */
	@Test
	void noClassOutsideThePluginPackagesNamesAViewer() throws IOException {
		Set<String> offenders = new TreeSet<>();
		int checked = 0;
		try (JarFile file = new JarFile(jar.toFile())) {
			for (var entries = file.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("com/createengines/") || !name.endsWith(".class")
					|| name.startsWith("com/createengines/client/compat/jei/") || name.startsWith("com/createengines/client/compat/emi/"))
					continue;
				checked++;
				String text = new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
				if (text.contains("mezz/jei/") || text.contains("dev/emi/"))
					offenders.add(name);
			}
		}
		assertTrue(checked > 300, "only " + checked + " classes were checked; is this the right jar?");
		assertEquals(Set.of(), offenders, "these classes name JEI or EMI, so a game without that viewer would fail to link them");
	}

	/** Nothing in the mod's own code may name the plugin classes either: only the viewers load them. */
	@Test
	void nothingInTheModLoadsAPluginClass() throws IOException {
		Set<String> offenders = new TreeSet<>();
		try (JarFile file = new JarFile(jar.toFile())) {
			for (var entries = file.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("com/createengines/") || !name.endsWith(".class")
					|| name.startsWith("com/createengines/client/compat/jei/") || name.startsWith("com/createengines/client/compat/emi/"))
					continue;
				try (InputStream in = file.getInputStream(entry)) {
					for (String referenced : PackagedJarTest.referencedClasses(in))
						if (referenced.startsWith("com/createengines/client/compat/jei/") || referenced.startsWith("com/createengines/client/compat/emi/"))
							offenders.add(name + " -> " + referenced);
				}
			}
		}
		assertEquals(Set.of(), offenders, "a plugin class is loaded by the mod itself, which would fail without its viewer");
	}

	@Test
	void theModDoesNotRequireEitherViewer() throws IOException {
		try (JarFile file = new JarFile(jar.toFile())) {
			String toml = new String(bytes(file, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
			assertTrue(!toml.contains("modId=\"jei\"") && !toml.contains("modId=\"emi\""),
				"JEI or EMI is declared as a dependency; both must stay optional");
		}
	}

	/** EMI logs an error for every tag it has no name for; a tag this mod ships must be named in the language file. */
	@Test
	void everyTagTheModShipsHasAName() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(
			Path.of("src/generated/resources/assets/create_engines/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		Path data = Path.of("src/main/resources/data");
		List<String> unnamed = new java.util.ArrayList<>();
		int tags = 0;
		try (var walk = Files.walk(data)) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
				Path relative = data.relativize(file);
				String[] parts = relative.toString().replace('\\', '/').split("/");
				// <namespace>/tags/<registry>/<path...>.json
				if (parts.length < 4 || !parts[1].equals("tags"))
					continue;
				String path = String.join(".", java.util.Arrays.copyOfRange(parts, 3, parts.length));
				path = path.substring(0, path.length() - ".json".length());
				tags++;
				String key = "tag." + parts[2] + "." + parts[0] + "." + path;
				if (!lang.has(key))
					unnamed.add(key);
			}
		}
		assertTrue(tags >= 6, "only " + tags + " tags were found; is the working directory right?");
		assertEquals(List.of(), unnamed, "tags with no name in en_us.json (add them to CELang): EMI reports each as an error");
	}

	@Test
	void theWordingThePagesUseIsInTheLanguageFile() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(
			Path.of("src/generated/resources/assets/create_engines/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		List<String> keys = new java.util.ArrayList<>(List.of(
			"create_engines.recipe.refining.title",
			"create_engines.recipe.refining.speed",
			"create_engines.recipe.refining.ratio",
			"create_engines.recipe.refining.catalyst",
			"create_engines.recipe.refining.catalyst.tooltip",
			// EMI names a category by this exact key
			"emi.category.create_engines.refining"));
		// The heat line reuses the column's goggle wording, one entry for each heat a route can ask for
		for (Heat heat : Heat.values())
			keys.add("create_engines.goggles.column.heat." + heat.serializedName());
		for (String key : keys) {
			assertTrue(lang.has(key), key + " is missing from en_us.json: run ./gradlew runData --offline");
			assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
		}
	}
}
