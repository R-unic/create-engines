package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@code ./gradlew jarServer} works out before it starts a server: the blocks to place and the engines to build. */
class JarServerTest {

	@Test
	void aCompoundKeepsItsValuesAsWritten() {
		var read = Snbt.compound("{a:1.5d,b:\"x,y}\",\"c d\":{e:[1,2],f:'}'},g:[I;1,-2,3]}");
		assertEquals(List.of("a", "b", "c d", "g"), List.copyOf(read.keySet()));
		assertEquals("1.5d", read.get("a"));
		assertEquals("\"x,y}\"", read.get("b"));
		assertEquals("{e:[1,2],f:'}'}", read.get("c d"));
		assertEquals(List.of("1", "-2", "3"), Snbt.list(read.get("g")));
		assertEquals("x,y}", Snbt.unquote(read.get("b")));
		assertEquals(List.of(), Snbt.list("[]"));
		assertTrue(Snbt.compound("{}").isEmpty());
		assertEquals("\"a\\\"},b\"", Snbt.compound("{k:\"a\\\"},b\"}").get("k"), "an escaped quote does not end a string");
	}

	@Test
	void textThatIsNotWhatItShouldBeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> Snbt.compound("[1]"));
		assertThrows(IllegalArgumentException.class, () -> Snbt.compound("{novalue}"));
		assertThrows(IllegalArgumentException.class, () -> Snbt.list(""));
	}

	@Test
	void everyShippedEngineFixtureBecomesCommandsWithOneRunningCrankshaft() throws IOException {
		for (String kind : List.of("diesel", "spark", "rotary")) {
			String snbt = Files.readString(Path.of("src/test/resources/compat/world/engine_v2_0_0_" + kind + ".snbt"),
				StandardCharsets.UTF_8);
			List<JarServer.Placed> cranks = new ArrayList<>();
			List<String> commands = JarServer.fixtureCommands(snbt, 100, -60, 200, cranks);
			assertTrue(commands.size() > 8, kind + ": " + commands.size() + " commands");
			assertFalse(cranks.isEmpty(), kind + " has no crankshaft that records a running engine");
			for (String command : commands) {
				assertTrue(command.startsWith("setblock "), command);
				assertFalse(command.contains("structure_block"), command);
				assertFalse(command.contains("minecraft:air"), command);
				// Nothing that points into the world the fixture was recorded in
				assertFalse(command.matches(".*[{,](x|Source|Primary|Network):.*"), command);
				assertFalse(command.contains("\n"), command);
			}
		}
	}

	@Test
	void aFixtureIsMovedToWhereItIsAskedFor() {
		List<JarServer.Placed> cranks = new ArrayList<>();
		List<String> commands = JarServer.fixtureCommands("{Blocks:[{Pos:[I;1,2,3],State:{Name:\"create_engines:crankshaft\","
			+ "Properties:{axis:\"x\"}},Entity:{id:\"a\",x:5,y:6,z:7,EngineSpeed:109.0f,Sense:1}}]}", 10, -60, 20, cranks);
		assertEquals(List.of("setblock 11 -58 23 create_engines:crankshaft[axis=x]{EngineSpeed:109.0f,Sense:1}"), commands);
		assertEquals(List.of(new JarServer.Placed("crankshaft", 11, -58, 23)), cranks);
	}

	@Test
	void theBlocksAreTheOnesTheJarCarries(@TempDir Path folder) throws IOException {
		Path jar = folder.resolve("mod.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
			for (String name : List.of("assets/create_engines/blockstates/sump.json", "assets/create_engines/blockstates/battery.json",
				"assets/create_engines/models/block/sump.json", "assets/other/blockstates/nope.json")) {
				zip.putNextEntry(new ZipEntry(name));
				zip.closeEntry();
			}
		}
		assertEquals(List.of("battery", "sump"), JarServer.blockIds(jar));
	}

	@Test
	void noTwoPlacedBlocksTouch() {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 80; i++)
			ids.add("b" + i);
		List<JarServer.Placed> grid = JarServer.grid(ids);
		for (int i = 0; i < grid.size(); i++)
			for (int j = i + 1; j < grid.size(); j++)
				assertTrue(Math.max(Math.abs(grid.get(i).x() - grid.get(j).x()), Math.abs(grid.get(i).z() - grid.get(j).z())) >= 8);
	}

	/** A server task that cannot find its install says so and fails, never passing quietly. */
	@Test
	void aRunWithNoServerInstallFails(@TempDir Path folder) throws Exception {
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
			System.getProperty("java.class.path"), JarServer.class.getName(), "--install", folder.resolve("none").toString(),
			"--jar", "x.jar", "--create", "1", "--work", folder.resolve("work").toString(), "--fixtures", folder.toString())
			.redirectErrorStream(true).start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(1, process.waitFor(), out);
		assertTrue(out.contains("no NeoForge server install"), out);
	}
}
