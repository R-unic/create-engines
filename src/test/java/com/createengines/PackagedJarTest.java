package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 5, the packaged-jar half. These read the jar the build makes, not the classes folder the
 * development run uses, which is what a player's game and a dedicated server load. They catch what only shows up
 * packaged: a class that refers to client code from common code (read from the bytecode, so it also sees fully
 * qualified uses that no import line shows), a mixin config naming a class that is not in the jar, and metadata or
 * resources that did not make it in or were not filled in.
 * <p>
 * Booting a dedicated server from the jar needs a NeoForge server install, which the build does not make. The
 * GameTest server is a server-only JVM running the mod's common code, and covers what actually executes.
 */
class PackagedJarTest {

	private static Path jar;

	@BeforeAll
	static void findTheJar() {
		String path = System.getProperty("ce.jar");
		Assumptions.assumeTrue(path != null && Files.exists(Path.of(path)),
			"no jar to inspect (the Gradle test task builds it and passes -Dce.jar)");
		jar = Path.of(path);
	}

	/**
	 * Registrate's {@code .renderer(() -> Factory::new)} compiles to a lambda whose descriptor names the client
	 * {@code BlockEntityRendererProvider.Context}. The lambda is only ever run by the client; a server loads the class and
	 * never links it (the GameTest server, a server-only JVM, loads this class on every run). Create does the same.
	 */
	private static final Set<String> REGISTRATE_RENDERER_LAMBDAS = Set.of("com/createengines/registry/CEBlockEntities");

	/** The common classes that are allowed to name client classes; see ArchitectureRulesTest, which keeps this in step. */
	private static final Set<String> CLIENT_TOUCHING = Set.of(
		"com/createengines/CreateEnginesClient",
		"com/createengines/content/EngineNoteSound",
		"com/createengines/content/EnginePartRenderers",
		"com/createengines/content/GoggleLines",
		"com/createengines/content/MachineLoopSound",
		"com/createengines/content/StingSound",
		"com/createengines/content/effects/EngineEffects",
		"com/createengines/content/plate/MakerPlateRenderer",
		"com/createengines/content/raincap/RainCapBlockEntity",
		"com/createengines/content/raincap/RainCapRenderer",
		"com/createengines/content/starter/StarterRenderer",
		"com/createengines/content/starter/StarterWhirSound",
		"com/createengines/content/turbo/TurbochargerRenderer",
		"com/createengines/ponder/EngineScenes");

	/** Class names in a class file's constant pool: every CONSTANT_Class and every type named in a descriptor-ish string. */
	static List<String> referencedClasses(InputStream in) throws IOException {
		DataInputStream data = new DataInputStream(in);
		assertEquals(0xCAFEBABE, data.readInt(), "not a class file");
		data.readUnsignedShort();
		data.readUnsignedShort();
		int count = data.readUnsignedShort();
		String[] utf = new String[count];
		int[] classNames = new int[count];
		for (int i = 1; i < count; i++) {
			int tag = data.readUnsignedByte();
			switch (tag) {
				case 1 -> utf[i] = data.readUTF();
				case 3, 4 -> data.readInt();
				case 5, 6 -> {
					data.readLong();
					i++;
				}
				case 7 -> classNames[i] = data.readUnsignedShort();
				case 8, 16, 19, 20 -> data.readUnsignedShort();
				case 9, 10, 11, 12, 17, 18 -> data.readInt();
				case 15 -> {
					data.readUnsignedByte();
					data.readUnsignedShort();
				}
				default -> throw new IOException("unknown constant pool tag " + tag);
			}
		}
		List<String> names = new ArrayList<>();
		for (int i = 1; i < count; i++)
			if (classNames[i] != 0 && utf[classNames[i]] != null)
				names.add(utf[classNames[i]]);
		// Descriptors and signatures hold class names too ("Lnet/minecraft/client/Minecraft;"); take every Utf8 that has one
		for (String s : utf)
			if (s != null && s.contains("Lnet/minecraft/client/") || s != null && s.contains("Lcom/createengines/client/"))
				names.add(s);
		return names;
	}

	private static boolean isClient(String name) {
		return name.startsWith("net/minecraft/client/") || name.contains("Lnet/minecraft/client/")
			|| name.startsWith("com/createengines/client/") || name.contains("Lcom/createengines/client/");
	}

	@Test
	void commonClassesInTheJarDoNotReferToClientClasses() throws IOException {
		Set<String> offenders = new TreeSet<>();
		int checked = 0;
		try (JarFile file = new JarFile(jar.toFile())) {
			for (var entries = file.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("com/createengines/") || !name.endsWith(".class"))
					continue;
				String cls = name.substring(0, name.length() - ".class".length());
				// Inner classes belong to their outer class
				String outer = cls.contains("$") ? cls.substring(0, cls.indexOf('$')) : cls;
				if (outer.startsWith("com/createengines/client/") || CLIENT_TOUCHING.contains(outer) || REGISTRATE_RENDERER_LAMBDAS.contains(outer)
					|| outer.startsWith("com/createengines/mixin/"))
					continue;
				checked++;
				try (InputStream in = file.getInputStream(entry)) {
					for (String referenced : referencedClasses(in))
						if (isClient(referenced)) {
							offenders.add(cls + " -> " + referenced);
							break;
						}
				}
			}
		}
		assertTrue(checked > 300, "only " + checked + " classes were checked; is this the right jar?");
		assertEquals(Set.of(), offenders, "common classes that refer to client classes: a dedicated server that loads them crashes");
	}

	@Test
	void everyMixinNamedInTheConfigIsInTheJar() throws IOException {
		try (JarFile file = new JarFile(jar.toFile())) {
			var config = file.getEntry("create_engines.mixins.json");
			assertNotNull(config, "the mixin config is not in the jar");
			String text = new String(file.getInputStream(config).readAllBytes(), StandardCharsets.UTF_8);
			var json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
			String pkg = json.get("package").getAsString().replace('.', '/');
			List<String> names = new ArrayList<>();
			for (String key : List.of("mixins", "client", "server"))
				if (json.has(key))
					json.getAsJsonArray(key).forEach(e -> names.add(e.getAsString()));
			assertTrue(!names.isEmpty(), "the mixin config names no mixins");
			for (String name : names)
				assertNotNull(file.getEntry(pkg + "/" + name + ".class"), "mixin " + name + " is named in the config but not in the jar");
		}
	}

	/** What only development uses: the GameTests, the smoke audits and the picture-taking runs (build.gradle keeps them out). */
	private static boolean isDevelopmentHarness(String name) {
		return name.startsWith("com/createengines/gametest/") || name.startsWith("com/createengines/smoke/")
			|| name.startsWith("com/createengines/client/smoke/") || name.startsWith("com/createengines/client/VisualSmokeTest")
			|| name.startsWith("com/createengines/client/ShowcaseRun");
	}

	@Test
	void theJarHasNoTestOrSmokeHarnessClasses() throws IOException {
		Set<String> leftovers = new TreeSet<>();
		int classes = 0;
		try (JarFile file = new JarFile(jar.toFile())) {
			for (var entries = file.entries(); entries.hasMoreElements();) {
				String name = entries.nextElement().getName();
				if (name.startsWith("com/createengines/") && name.endsWith(".class"))
					classes++;
				if (isDevelopmentHarness(name))
					leftovers.add(name);
			}
		}
		assertTrue(classes > 200, "the jar should hold the mod's classes, found " + classes);
		assertTrue(leftovers.isEmpty(), "development harness classes were packaged: " + leftovers);
	}

	/** If the jar lacks the harnesses, no class left in it may name one outside the by-name registration that tolerates that. */
	@Test
	void nothingInTheJarNeedsAHarnessThatIsNotThere() throws IOException {
		Set<String> offenders = new TreeSet<>();
		try (JarFile file = new JarFile(jar.toFile())) {
			for (var entries = file.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("com/createengines/") || !name.endsWith(".class"))
					continue;
				try (InputStream in = file.getInputStream(entry)) {
					for (String referenced : referencedClasses(in))
						if (isDevelopmentHarness(referenced))
							offenders.add(name + " -> " + referenced);
				}
			}
		}
		assertTrue(offenders.isEmpty(), "classes in the jar link to harness classes the jar leaves out: " + offenders);
	}

	@Test
	void theModMetadataIsFilledInAndTheResourcesAreThere() throws IOException {
		try (JarFile file = new JarFile(jar.toFile())) {
			var toml = file.getEntry("META-INF/neoforge.mods.toml");
			assertNotNull(toml, "neoforge.mods.toml is not in the jar");
			String text = new String(file.getInputStream(toml).readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(!text.contains("${"), "neoforge.mods.toml still has an unfilled ${placeholder}: " + text);
			assertTrue(text.contains("modId=\"create_engines\""), "the toml does not declare the mod id");
			for (String required : List.of("assets/create_engines/lang/en_us.json", "assets/create_engines/sounds.json",
				"logo.png"))
				assertNotNull(file.getEntry(required), required + " is not in the jar");
			boolean anyData = false, anyModel = false, strayModelSource = false, cache = false;
			for (var entries = file.entries(); entries.hasMoreElements();) {
				String name = entries.nextElement().getName();
				anyData |= name.startsWith("data/create_engines/recipe/");
				anyModel |= name.startsWith("assets/create_engines/models/block/");
				strayModelSource |= name.endsWith(".bbmodel");
				cache |= name.contains("/.cache/");
			}
			assertTrue(anyData, "no recipes in the jar");
			assertTrue(anyModel, "no block models in the jar");
			assertTrue(!strayModelSource, "an editor source (.bbmodel) was packaged");
			assertTrue(!cache, "datagen's .cache folder was packaged");
		}
	}
}
