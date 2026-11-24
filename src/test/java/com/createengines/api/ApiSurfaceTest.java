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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The public API is frozen at 2.0. Its surface (every public declaration of {@code com.createengines.api}, plus the two
 * engine types a provider returns) is read from the source and compared with {@code compat/api-surface.txt}. Removing or
 * changing a line there is a major change and fails; adding one is a minor change, recorded with {@code API_REGEN=1} and a
 * bump of {@link ApiVersion#MINOR}.
 */
class ApiSurfaceTest {

	private static final Path ROOT = Path.of("src/main/java/com/createengines");
	/** The simulation lives in its own module (sim/), so its files are read from there. */
	private static final Path SIM_ROOT = Path.of("sim/src/main/java/com/createengines");
	private static final Path FILE = Path.of("src/test/resources/compat/api-surface.txt");

	private static List<Path> files() throws IOException {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> paths = Files.list(ROOT.resolve("api"))) {
			paths.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("ApiVersion.java"))
				.sorted().forEach(out::add);
		}
		out.add(SIM_ROOT.resolve("engine/sim/TorqueSource.java"));
		out.add(SIM_ROOT.resolve("engine/sim/Breather.java"));
		out.add(SIM_ROOT.resolve("engine/sim/AbstractTorqueSource.java"));
		out.add(SIM_ROOT.resolve("engine/sim/StatusLine.java"));
		return out;
	}

	/** Source without comments and string contents, with annotations and annotation arguments removed. */
	static String clean(String source) {
		String s = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
		s = s.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
		s = s.replaceAll("@[A-Za-z_.]+(\\([^)]*\\))?", " ");
		return s;
	}

	/** The public declarations of one file, as normalised single lines such as {@code Foo.bar(int) -> double}. */
	static Set<String> surface(Path file) throws IOException {
		String name = file.getFileName().toString().replace(".java", "");
		String s = clean(Files.readString(file));
		Set<String> out = new TreeSet<>();
		// package and imports are not API
		s = s.replaceAll("(?m)^\\s*(package|import)[^;]*;", " ");
		walk(name, s, 0, new ArrayList<>(List.of(name)), out, true, file);
		return out;
	}

	private record Scope(String name, boolean implicitPublic) {
	}

	/** A small scan: finds each declaration at the member level of a type, and descends into nested types. */
	private static void walk(String owner, String text, int from, List<String> path, Set<String> out, boolean top, Path file) {
		int i = 0;
		// Top level: one type declaration whose body is the rest
		Matcher type = Pattern.compile("(?s)\\b((?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
			+ "(class|interface|enum|record)\\s+(\\w+)([^{;]*)\\{").matcher(text);
		if (!type.find())
			return;
		String kind = type.group(2);
		String header = text.substring(type.start(), type.end() - 1).trim().replaceAll("\\s+", " ");
		if (!header.contains("private"))
			out.add(owner + " :: " + header);
		int open = type.end() - 1;
		int close = match(text, open);
		member(owner, kind, text.substring(open + 1, close), out, kind.equals("interface"));
	}

	private static int match(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '{')
				depth++;
			else if (c == '}' && --depth == 0)
				return i;
		}
		throw new IllegalStateException("unbalanced braces");
	}

	/** Records the members of a type body; nested types are walked with their own names. */
	private static void member(String owner, String kind, String body, Set<String> out, boolean implicitPublic) {
		int i = 0;
		StringBuilder statement = new StringBuilder();
		int paren = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '(')
				paren++;
			else if (c == ')')
				paren--;
			if (paren == 0 && (c == ';' || c == '{')) {
				String decl = statement.toString().trim().replaceAll("\\s+", " ");
				statement.setLength(0);
				Matcher nested = Pattern.compile("^((?:public|protected|private|static|final|abstract|sealed)\\s+)*"
					+ "(class|interface|enum|record)\\s+(\\w+)").matcher(decl);
				boolean block = c == '{';
				int end = block ? match(body, i) : i;
				if (!decl.isEmpty()) {
					boolean isPrivate = decl.matches("^(.*\\s)?private\\s.*");
					boolean isPublic = decl.matches("^(.*\\s)?(public|protected)\\s.*");
					boolean visible = !isPrivate && (isPublic || implicitPublic || kind.equals("enum"));
					if (nested.find()) {
						if (visible) {
							out.add(owner + " :: " + decl);
							member(owner + "." + nested.group(3), nested.group(2), body.substring(i + 1, end), out,
								nested.group(2).equals("interface"));
						}
					} else if (visible && !decl.startsWith("static {") && !decl.equals("static"))
						out.add(owner + " :: " + decl);
				}
				i = end + 1;
				continue;
			}
			statement.append(c);
			i++;
		}
	}

	private static Set<String> now() throws IOException {
		Set<String> all = new TreeSet<>();
		for (Path file : files())
			all.addAll(surface(file));
		return all;
	}

	private static Set<String> shipped() throws IOException {
		Set<String> out = new TreeSet<>();
		for (String line : Files.readAllLines(FILE))
			if (!line.isBlank() && !line.startsWith("#"))
				out.add(line);
		return out;
	}

	@Test
	void theSurfaceScanSeesTheTypesAndTheirMembers() throws IOException {
		Set<String> now = now();
		assertTrue(now.stream().anyMatch(l -> l.startsWith("EngineExtensions :: public static void register(")), "" + now);
		assertTrue(now.stream().anyMatch(l -> l.startsWith("EngineSourceProvider :: ") && l.contains("sourcesFor(")), "" + now);
		assertTrue(now.stream().anyMatch(l -> l.startsWith("TorqueSource :: ") && l.contains("advance(")), "" + now);
		assertTrue(now.size() > 40, "scanned only " + now.size() + " declarations");
	}

	@Test
	void theFrozenApiStillHasEverythingItShippedWith() throws IOException {
		Set<String> shipped = shipped();
		Set<String> now = now();
		Set<String> gone = new TreeSet<>(shipped);
		gone.removeAll(now);
		Set<String> added = new TreeSet<>(now);
		added.removeAll(shipped);
		if (System.getenv("API_REGEN") != null && !added.isEmpty()) {
			Set<String> merged = new TreeSet<>(shipped);
			merged.addAll(added);
			List<String> lines = new ArrayList<>();
			lines.add("# version " + ApiVersion.VERSION);
			lines.add("# The frozen public API, as ApiSurfaceTest reads it from the source. Never edit a line: removing or changing one");
			lines.add("# is a major change. A new line is a minor change (bump ApiVersion.MINOR) and is added with API_REGEN=1.");
			lines.addAll(merged);
			Files.write(FILE, lines);
			System.out.println("api surface: appended " + added.size() + " declarations");
			added.clear();
		}
		assertTrue(gone.isEmpty(), "The public API lost or changed these declarations, which is a major change:\n"
			+ String.join("\n", gone));
	}

	@Test
	void newDeclarationsAreRecordedAndTheMinorVersionMovesWithThem() throws IOException {
		Set<String> added = new TreeSet<>(now());
		added.removeAll(shipped());
		assertEquals(Set.of(), added, "new public API; record it with API_REGEN=1 and bump ApiVersion.MINOR");
	}

	@Test
	void theVersionInTheFileMatchesTheVersionInTheSource() throws IOException {
		String header = Files.readAllLines(FILE).stream().filter(l -> l.startsWith("# version ")).findFirst().orElse("");
		assertEquals("# version " + ApiVersion.VERSION, header, "the surface file records the API version it was taken at");
	}
}
