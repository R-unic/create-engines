package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.smoke.LogScanner.Finding;
import com.createengines.smoke.LogScanner.Severity;

/** The scanner is only worth trusting if it flags what breaks a mod and stays quiet about what does not. */
class LogScannerTest {

	private static List<Finding> scan(String... lines) {
		return new LogScanner().scan(List.of(lines));
	}

	@Test
	void aCleanLogHasNoFindings() {
		assertTrue(scan("[12:00:00] [main/INFO] [create_engines/]: Visual smoke test enabled",
			"[12:00:01] [Render thread/INFO] [minecraft/Minecraft]: Stopping!").isEmpty());
	}

	@Test
	void anyErrorLineFails() {
		var findings = scan("[12:00:00] [Server thread/ERROR] [create_engines/]: Engine exploded");
		assertEquals(1, findings.size());
		assertEquals(Severity.FAIL, findings.get(0)
			.severity());
	}

	@Test
	void repeatsFoldIntoOneFindingWithACount() {
		var findings = scan("[12:00:00] [IO-Worker-1486/ERROR] [x/]: Could not save data Foo@1fbcf7f3",
			"[12:00:01] [IO-Worker-1487/ERROR] [x/]: Could not save data Foo@1b8370dc",
			"[12:00:02] [IO-Worker-1488/ERROR] [x/]: Could not save data Foo@117b1bac");
		assertEquals(1, findings.size());
		assertEquals(3, findings.get(0)
			.count());
	}

	@Test
	void theMessagesMinecraftPrintsForBrokenResourcesAreCaught() {
		for (String line : List.of(
			"[12:00:00] [Worker-Main-3/WARN] [minecraft/ModelBakery]: Unable to resolve texture reference: #side in create_engines:block/foo",
			"[12:00:00] [Worker-Main-3/WARN] [minecraft/ModelBakery]: Missing textures in model create_engines:block/foo",
			"[12:00:00] [Render thread/WARN] [minecraft/ModelBakery]: Unable to bake model: 'create_engines:foo#facing=north'",
			"[12:00:00] [Render thread/INFO] [x/]: Parsing error loading recipe create_engines:foo",
			"[12:00:00] [Render thread/INFO] [x/]: Couldn't parse element create_engines:loot/foo",
			"[12:00:00] [Sound/WARN] [x/]: Failed to load sound create_engines:foo"))
			assertEquals(Severity.FAIL, scan(line).get(0)
				.severity(), line);
	}

	@Test
	void staleClassesAndOutOfMemoryAreFatalAtAnyLogLevel() {
		for (String line : List.of("[1] [Sound engine/INFO] [x/]: java.lang.OutOfMemoryError: Direct buffer memory",
			"[1] [Render thread/INFO] [x/]: java.lang.NoSuchMethodError: com.createengines.Foo.bar()V",
			"Exception in thread \"Sound engine\" java.lang.IllegalStateException"))
			assertFalse(scan(line).isEmpty(), line);
	}

	@Test
	void ourOwnSmokeFailLinesFail() {
		assertFalse(scan("[1] [Render thread/ERROR] [create_engines/]: SOUND SMOKE FAIL create_engines:foo: no entry").isEmpty());
		assertFalse(scan("[1] [Render thread/INFO] [create_engines/]: AUDIT FAIL model for foo").isEmpty());
	}

	@Test
	void aWarningOnlyMattersWhenItNamesUs() {
		assertTrue(scan("[1] [main/WARN] [minecraft/Foo]: Something about vanilla").isEmpty());
		var ours = scan("[1] [main/WARN] [create_engines/]: Something about create_engines");
		assertEquals(Severity.WARN, ours.get(0)
			.severity());
	}

	@Test
	void baselineNoiseIsIgnoredAndSoIsItsStackTrace() {
		assertTrue(scan("[1] [main/WARN] [mixin/]: Reference map 'ponder.refmap.json' for ponder-common.mixins.json could not be read. If this is a development environment you can ignore this message",
			"\tat net.foo.Bar.baz(Bar.java:1)", "Caused by: java.lang.RuntimeException: whatever").isEmpty());
	}

	@Test
	void anAllowlistEntryHidesExactlyWhatItMatches() {
		var scanner = new LogScanner().allow("Failed to save level \\.\\\\oldtest");
		assertTrue(scanner.scan(List.of("[1] [Server thread/ERROR] [x/]: Failed to save level .\\oldtest2"))
			.isEmpty());
		assertFalse(scanner.scan(List.of("[1] [Server thread/ERROR] [x/]: Failed to save level .\\realworld"))
			.isEmpty());
	}

	@Test
	void aWarnPrefixedAllowlistEntryDowngradesInsteadOfHiding() {
		var scanner = new LogScanner().configure("# why\nwarn: OpenAlUtil.*Invalid name\nignored line here\n");
		var findings = scanner.scan(List.of("[1] [Sound engine/ERROR] [OpenAlUtil/]: Stop: Invalid name parameter.",
			"[2] [Sound engine/INFO] [x/]: ignored line here is quiet", "[3] [Render thread/ERROR] [x/]: a different problem"));
		assertEquals(2, findings.size());
		assertEquals(Severity.WARN, findings.get(0)
			.severity());
		assertEquals(Severity.FAIL, findings.get(1)
			.severity());
	}

	@Test
	void theSimulatedRopeConnectorEntryHidesOnlyThatModel() throws Exception {
		var scanner = new LogScanner().configure(java.nio.file.Files.readString(java.nio.file.Path.of("src/smoke/log-allowlist.txt")));
		String line = "[18Nov2026 01:03:40.025] [Worker-Main-1/WARN] [net.minecraft.client.resources.model.ModelBakery/]: "
			+ "Unable to load model: 'minecraft:block' referenced from: simulated:block/rope_connector/block_horizontal: "
			+ "java.io.FileNotFoundException: minecraft:models/block.json";
		assertTrue(scanner.scan(List.of(line))
			.isEmpty());
		// the same missing parent on another model, a different missing parent, and one of ours all still fail
		assertFalse(scanner.scan(List.of(line.replace("block_horizontal", "block_vertical")))
			.isEmpty());
		assertFalse(scanner.scan(List.of(line.replace("'minecraft:block'", "'minecraft:item/generated'")))
			.isEmpty());
		assertFalse(scanner.scan(List.of(line.replace("simulated:block/rope_connector/block_horizontal",
			"create_engines:block/piston")))
			.isEmpty());
	}

	@Test
	void aCausedByUnderAnErrorIsItsOwnFinding() {
		var findings = scan("[1] [Render thread/ERROR] [x/]: Failed to render", "\tat net.foo.Bar.baz(Bar.java:1)",
			"Caused by: java.lang.NullPointerException: engine was null");
		assertEquals(2, findings.size());
	}

	@Test
	void allowlistFilesIgnoreCommentsAndBlankLines() {
		assertEquals(List.of("one", "two"), LogScanner.parseAllowlist("# why\none\n\n  # another\ntwo\n"));
	}

	@Test
	void findingsBecomeReportChecks() {
		var report = new SmokeReport("t");
		LogScanner.report(scan("[1] [Server thread/ERROR] [x/]: boom"), report);
		assertEquals(1, report.count(SmokeReport.Status.FAIL));
		var clean = new SmokeReport("t");
		LogScanner.report(List.of(), clean);
		assertEquals(1, clean.count(SmokeReport.Status.PASS));
	}
}
