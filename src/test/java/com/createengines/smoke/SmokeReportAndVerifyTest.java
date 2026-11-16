package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The harness's own bookkeeping: a harness that can mislay a failure is worse than none. */
class SmokeReportAndVerifyTest {

	@TempDir
	Path dir;

	@Test
	void anIncompleteRunNeverPasses() {
		SmokeReport report = new SmokeReport("t");
		report.pass("a", "b");
		assertFalse(report.passed());
		report.markCompleted();
		assertTrue(report.passed());
	}

	@Test
	void aSingleFailureFailsTheRunButAWarningDoesNot() {
		SmokeReport report = new SmokeReport("t");
		report.markCompleted();
		report.warn("a", "meh", "");
		assertTrue(report.passed());
		report.fail("a", "bad", "why");
		assertFalse(report.passed());
	}

	@Test
	void checkAndExpectChooseTheirSeverity() {
		SmokeReport report = new SmokeReport("t");
		assertFalse(report.check("a", "x", false, "d"));
		assertFalse(report.expect("a", "y", false, "d"));
		assertTrue(report.check("a", "z", true, "d"));
		assertEquals(1, report.count(SmokeReport.Status.FAIL));
		assertEquals(1, report.count(SmokeReport.Status.WARN));
		assertEquals(1, report.count("a", SmokeReport.Status.PASS));
	}

	@Test
	void problemsStayOnOneLine() {
		SmokeReport report = new SmokeReport("t");
		report.fail("a", "two\nlines", "detail\r\nwith breaks");
		assertFalse(report.problems()
			.get(0)
			.detail()
			.contains("\n"));
	}

	@Test
	void mergeAddsCountsAndProblems() {
		SmokeReport a = new SmokeReport("a"), b = new SmokeReport("b");
		a.pass("c", "x");
		b.pass("c", "y");
		b.fail("c", "z", "");
		a.merge(b);
		assertEquals(2, a.count(SmokeReport.Status.PASS));
		assertEquals(1, a.count(SmokeReport.Status.FAIL));
	}

	@Test
	void writtenReportsReadBackThroughVerify() throws IOException {
		SmokeReport report = new SmokeReport("t");
		report.pass("content", "ok");
		report.fail("content", "block foo has no lang entry", "key block.create_engines.foo");
		report.warn("perf", "slow frame", "p99 120 ms");
		report.markCompleted();
		report.write(dir);
		SmokeReport verified = SmokeVerify.verify("t", dir, null, null, false);
		assertFalse(verified.passed());
		assertEquals(1, verified.count("content", SmokeReport.Status.FAIL));
		assertEquals(1, verified.count("perf", SmokeReport.Status.WARN));
	}

	@Test
	void aMissingResultIsAFailureNotASilentPass() throws IOException {
		SmokeReport verified = SmokeVerify.verify("t", dir.resolve("nothing-here"), null, null, false);
		assertFalse(verified.passed());
		assertEquals(1, verified.count("run", SmokeReport.Status.FAIL));
	}

	@Test
	void aRunThatDidNotCompleteFailsEvenWithNoRecordedFailures() throws IOException {
		SmokeReport report = new SmokeReport("t");
		report.pass("content", "ok");
		report.write(dir); // never marked completed
		assertFalse(SmokeVerify.verify("t", dir, null, null, false)
			.passed());
	}

	@Test
	void theLogScanFailsAnOtherwiseCleanRun() throws IOException {
		SmokeReport report = new SmokeReport("t");
		report.markCompleted();
		report.write(dir);
		Path log = dir.resolve("latest.log");
		Files.write(log, List.of("[1] [Render thread/INFO] [x/]: fine", "[2] [Render thread/ERROR] [x/]: boom"));
		assertFalse(SmokeVerify.verify("t", dir, log, null, false)
			.passed());
		// The same line allowed by a reasoned allowlist entry passes
		Path allow = dir.resolve("allow.txt");
		Files.writeString(allow, "# test fixture noise\nERROR.*boom\n");
		assertTrue(SmokeVerify.verify("t", dir, log, allow, false)
			.passed());
	}

	@Test
	void lenientLogDowngradesLogFailuresToWarnings() throws IOException {
		SmokeReport report = new SmokeReport("t");
		report.markCompleted();
		report.write(dir);
		Path log = dir.resolve("latest.log");
		Files.write(log, List.of("[2] [Render thread/ERROR] [x/]: boom"));
		SmokeReport verified = SmokeVerify.verify("t", dir, log, null, true);
		assertTrue(verified.passed());
		assertEquals(1, verified.count("log", SmokeReport.Status.WARN));
	}

	private SmokeReport gameTestLog(String... lines) throws IOException {
		Path log = dir.resolve("gt.log");
		Files.write(log, List.of(lines));
		return SmokeVerify.verifyGameTests("gt", log, null);
	}

	@Test
	void aGameTestRunThatPassedEverythingPasses() throws IOException {
		assertTrue(gameTestLog("[1] [Server thread/INFO] [minecraft/GameTestServer]: All 183 required tests passed :)")
			.passed());
	}

	@Test
	void aGameTestRunWithAFailedTestFailsAndNamesIt() throws IOException {
		SmokeReport report = gameTestLog("[1] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(",
			"[1] [Server thread/INFO] [minecraft/GameTestServer]:    - everyblockisfine",
			"[1] [Server thread/INFO] [minecraft/GameTestServer]: ======");
		assertFalse(report.passed());
		assertTrue(report.problems()
			.stream()
			.anyMatch(p -> p.name()
				.equals("everyblockisfine")));
	}

	/** The server exits 0 without testing anything when another server holds the world lock; that must not look like a pass. */
	@Test
	void aGameTestServerThatNeverFinishedIsNotAPass() throws IOException {
		assertFalse(gameTestLog("[1] [main/INFO] [x/]: Loading", "[2] [main/INFO] [x/]: Done")
			.passed());
	}

	@Test
	void anErrorInAPassingGameTestRunStillFails() throws IOException {
		assertFalse(gameTestLog("[1] [Server thread/INFO] [x/]: All 5 required tests passed :)",
			"[2] [Server thread/ERROR] [x/]: Exception ticking world")
			.passed());
	}

	@Test
	void aLogOnlyRunNeedsNoReportButStillFailsOnAnError() throws IOException {
		Path log = dir.resolve("visual.log");
		Files.write(log, List.of("[1] [Render thread/INFO] [x/]: fine"));
		assertTrue(SmokeVerify.verify("visual", null, log, null, false)
			.passed());
		Files.write(log, List.of("[1] [Render thread/ERROR] [x/]: boom"));
		assertFalse(SmokeVerify.verify("visual", null, log, null, false)
			.passed());
		assertTrue(SmokeVerify.verify("visual", null, log, null, true)
			.passed());
	}

	@Test
	void aMissingLogFileIsAFailure() throws IOException {
		SmokeReport report = new SmokeReport("t");
		report.markCompleted();
		report.write(dir);
		assertFalse(SmokeVerify.verify("t", dir, dir.resolve("absent.log"), null, false)
			.passed());
	}
}
