package com.createengines.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * An addon that stays working between versions names nothing of this mod outside the public API. The example addons are held to it:
 * if one needs an internal class, the API is missing something, and the fix is a new public surface, not an import.
 */
class ExampleAddonUsesOnlyTheApiTest {

	private static final Set<String> ALLOWED_ENGINE_TYPES = Set.of("TorqueSource", "AbstractTorqueSource", "Breather", "StatusLine");
	private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?(com\\.createengines\\.[\\w.]+);");

	@Test
	void theExampleAddonImportsOnlyThePublicApi() throws IOException {
		Path root = Path.of("examples/racing-addon/src");
		assertTrue(Files.isDirectory(root), "the example addon is missing");
		Set<String> outside = new TreeSet<>();
		int files = 0;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
				files++;
				Matcher m = IMPORT.matcher(Files.readString(file));
				while (m.find()) {
					String name = m.group(1);
					boolean api = name.startsWith("com.createengines.api.");
					boolean engineType = name.startsWith("com.createengines.engine.sim.")
						&& ALLOWED_ENGINE_TYPES.contains(name.substring(name.lastIndexOf('.') + 1));
					if (!api && !engineType)
						outside.add(file.getFileName() + " imports " + name);
				}
			}
		}
		assertTrue(files >= 2, "found only " + files + " example source files");
		assertTrue(outside.isEmpty(), "the example addon uses the mod outside its public API: " + List.copyOf(outside));
	}
}
