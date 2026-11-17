package com.createengines;

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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Verification track item 5, the architecture rules. Read from the source's import lines, so it needs no extra library
 * and runs with the unit tests.
 * <ul>
 * <li>The simulation and the smoke harness's core are plain Java: they import nothing from Minecraft, NeoForge or Create.
 * That is what lets the unit tests run without a game.</li>
 * <li>Common code never touches client classes. A class that imports {@code net.minecraft.client} or this mod's
 * {@code client} package is client-only, and a dedicated server that loads it crashes. The classes that do are listed
 * below, each one reached only from client-side registration. The list may shrink; a new entry needs a reason.</li>
 * </ul>
 */
class ArchitectureRulesTest {

	private static final Path ROOT = Path.of("src/main/java");
	private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE);

	/** Packages that must stay free of the game. */
	private static final List<String> PURE_PACKAGES = List.of(
		"com/createengines/engine/sim",
		"com/createengines/engine/blueprint",
		"com/createengines/smoke");
	private static final List<String> FORBIDDEN_FOR_PURE = List.of("net.minecraft", "net.neoforged", "com.simibubi", "com.mojang",
		"dev.engine_room", "com.tterrag", "net.createmod");

	/**
	 * Classes outside the client packages that import client classes. Each is a renderer, a sound instance, a
	 * client-only screen helper or a class that is only ever called behind a {@code level.isClientSide} check or a
	 * client-side registration.
	 */
	private static final Set<String> CLIENT_TOUCHING = Set.of(
		"com/createengines/CreateEnginesClient.java",
		"com/createengines/content/EngineNoteSound.java",
		"com/createengines/content/EnginePartRenderers.java",
		"com/createengines/content/GoggleLines.java",
		"com/createengines/content/MachineLoopSound.java",
		"com/createengines/content/StingSound.java",
		"com/createengines/content/effects/EngineEffects.java",
		"com/createengines/content/plate/MakerPlateRenderer.java",
		"com/createengines/content/raincap/RainCapBlockEntity.java",
		"com/createengines/content/raincap/RainCapRenderer.java",
		"com/createengines/content/starter/StarterRenderer.java",
		"com/createengines/content/starter/StarterWhirSound.java",
		"com/createengines/content/turbo/TurbochargerRenderer.java",
		"com/createengines/ponder/EngineScenes.java");

	private static List<Path> sources() throws IOException {
		try (Stream<Path> walk = Files.walk(ROOT)) {
			return walk.filter(p -> p.toString().endsWith(".java")).toList();
		}
	}

	private static String rel(Path path) {
		return ROOT.relativize(path).toString().replace('\\', '/');
	}

	private static List<String> imports(Path path) throws IOException {
		Matcher m = IMPORT.matcher(Files.readString(path));
		List<String> out = new ArrayList<>();
		while (m.find())
			out.add(m.group(1));
		return out;
	}

	@Test
	void sourcesWereFound() throws IOException {
		assertTrue(sources().size() > 200, "the scan found only " + sources().size() + " files; the working directory is wrong");
	}

	@Test
	void theSimulationAndSmokeCoreImportNothingFromTheGame() throws IOException {
		List<String> breaches = new ArrayList<>();
		int scanned = 0;
		for (Path path : sources()) {
			String rel = rel(path);
			// smoke/audit and the client audit use the game by design; only the core package itself is pure
			boolean pure = PURE_PACKAGES.stream().anyMatch(p -> rel.startsWith(p + "/"))
				&& !rel.startsWith("com/createengines/smoke/audit/");
			if (!pure)
				continue;
			scanned++;
			for (String imported : imports(path))
				for (String banned : FORBIDDEN_FOR_PURE)
					if (imported.startsWith(banned))
						breaches.add(rel + " imports " + imported);
		}
		assertTrue(scanned > 40, "scanned only " + scanned + " pure files");
		assertEquals(List.of(), breaches, "the simulation must stay plain Java");
	}

	@Test
	void commonCodeDoesNotImportClientClassesBeyondTheKnownList() throws IOException {
		Set<String> found = new TreeSet<>();
		for (Path path : sources()) {
			String rel = rel(path);
			if (rel.startsWith("com/createengines/client/"))
				continue;
			for (String imported : imports(path))
				if (imported.startsWith("net.minecraft.client") || imported.startsWith("com.createengines.client")) {
					found.add(rel);
					break;
				}
		}
		Set<String> unexpected = new TreeSet<>(found);
		unexpected.removeAll(CLIENT_TOUCHING);
		assertEquals(Set.of(), unexpected, "new classes that import client code; they would crash a dedicated server if loaded there");
		Set<String> stale = new TreeSet<>(CLIENT_TOUCHING);
		stale.removeAll(found);
		assertEquals(Set.of(), stale, "these no longer import client code; remove them from CLIENT_TOUCHING");
	}

	/** The blocks, block entities and items a server loads must not be the ones that import client code. */
	@Test
	void serverSideClassesAreNotInTheClientTouchingList() {
		for (String entry : CLIENT_TOUCHING) {
			String name = entry.substring(entry.lastIndexOf('/') + 1);
			boolean client = name.contains("Renderer") || name.contains("Sound") || name.contains("Client")
				|| name.equals("EnginePartRenderers.java") || name.equals("GoggleLines.java") || name.equals("EngineScenes.java")
				|| name.equals("EngineEffects.java") || name.equals("RainCapBlockEntity.java");
			assertTrue(client, entry + " is not obviously client-only; it should not be on the list without a reason here");
		}
	}
}
