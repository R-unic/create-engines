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

/**
 * The corners of the verifier that decide whether a release gate can be fooled: a GameTest run with no log, a summary line
 * that names no failing test, the allow-list applied to a GameTest log, and a report that lists fewer failures than its
 * header counts (truncated or garbled). Each must fail the run, never pass it quietly.
 */
class SmokeVerifyEdgesTest {

	@TempDir
	Path dir;

	@Test
	void aGameTestRunWithNoLogIsAFailureThatSaysSo() throws IOException {
		SmokeReport report = SmokeVerify.verifyGameTests("gt", dir.resolve("never-written.log"), null);
		assertFalse(report.passed());
		assertEquals(1, report.count("gametest", SmokeReport.Status.FAIL));
		assertTrue(report.problems()
			.stream()
			.anyMatch(p -> p.name()
				.equals("no game log")));
		assertTrue(report.completed(), "a verdict was reached, so the report is complete and still red");
	}

	@Test
	void aFailureSummaryThatNamesNoTestStillFailsTheRunWithTheCount() throws IOException {
		Path log = dir.resolve("gt.log");
		Files.write(log, List.of("[1] [Server thread/INFO] [minecraft/GameTestServer]: 3 required tests failed :("));
		SmokeReport report = SmokeVerify.verifyGameTests("gt", log, null);
		assertFalse(report.passed());
		assertTrue(report.problems()
			.stream()
			.anyMatch(p -> p.name()
				.equals("game tests failed") && p.detail()
					.contains("3 required tests failed")), report.problems()
						.toString());
	}

	@Test
	void theAllowListAppliesToAGameTestLogToo() throws IOException {
		Path log = dir.resolve("gt.log");
		Files.write(log, List.of("[1] [Server thread/INFO] [x/]: All 5 required tests passed :)",
			"[2] [Server thread/ERROR] [x/]: Failed to load properties from file: server.properties"));
		assertFalse(SmokeVerify.verifyGameTests("gt", log, null)
			.passed(), "the error fails the run with no allow-list");
		Path allow = dir.resolve("allow.txt");
		Files.writeString(allow, "# fixture: the dedicated server writes this file itself\nFailed to load properties\n");
		assertTrue(SmokeVerify.verifyGameTests("gt", log, allow)
			.passed());
		assertFalse(SmokeVerify.verifyGameTests("gt", log, dir.resolve("missing-allow.txt"))
			.passed(), "a missing allow-list allows nothing");
	}

	private SmokeReport verifiedReport(int declaredFailures, String reportText) throws IOException {
		Files.writeString(dir.resolve("result.properties"),
			"completed=true\nfailures=" + declaredFailures + "\nwarnings=1\npasses=3\n");
		Files.writeString(dir.resolve("report.txt"), reportText);
		return SmokeVerify.verify("t", dir, null, null, false);
	}

	private static final String LISTING = "FAIL:\n  [models] block sump - has a face with the missing texture\n\nWARN:\n"
		+ "  [sound] event hum - never started\n\nSKIP:\n  [gui] a screen - no mouse\n\nRESULT: FAIL\n";

	@Test
	void theGamesCountsAndLinesAreCarriedIntoTheVerifiedReport() throws IOException {
		SmokeReport report = verifiedReport(1, LISTING);
		assertFalse(report.passed(), "one failure was listed");
		assertEquals(3, report.count("game", SmokeReport.Status.PASS));
		assertEquals(1, report.count("models", SmokeReport.Status.FAIL));
		assertEquals(1, report.count("sound", SmokeReport.Status.WARN));
		assertEquals(1, report.count("gui", SmokeReport.Status.SKIP));
		assertEquals(0, report.count("run", SmokeReport.Status.FAIL), "the count matches the listing");
	}

	@Test
	void aReportThatCountsMoreFailuresThanItListsIsRejectedAsTruncated() throws IOException {
		SmokeReport report = verifiedReport(2, LISTING);
		assertFalse(report.passed());
		assertTrue(report.problems()
			.stream()
			.anyMatch(p -> p.name()
				.equals("failure count mismatch") && p.detail()
					.contains("counts 2 failures but report.txt lists 1")), report.problems()
						.toString());
	}

	@Test
	void aReportWithNoFailuresAndNoneCountedPassesCleanly() throws IOException {
		SmokeReport report = verifiedReport(0, "WARN:\n  [sound] event hum - never started\n\nRESULT: PASS\n");
		assertTrue(report.passed());
		assertEquals(1, report.count("sound", SmokeReport.Status.WARN));
	}
}
