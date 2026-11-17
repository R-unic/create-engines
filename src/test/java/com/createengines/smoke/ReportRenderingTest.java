package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** What a person reads at the end of a smoke run: every kind of line has to appear, in order, and a metric has to survive. */
class ReportRenderingTest {

	@Test
	void aRenderedReportListsFailuresThenWarningsThenSkipsAndThenTheMetrics() {
		SmokeReport report = new SmokeReport("client");
		report.pass("models", "block a");
		report.skip("render", "no gpu", "headless");
		report.warn("perf", "slow frame", "p99 high");
		report.fail("lang", "block b has a translation", "no entry");
		report.metric("frame time median", 8.345, "ms");
		report.markCompleted();
		String text = report.render();
		assertTrue(text.startsWith("SMOKE REPORT client"));
		int fail = text.indexOf("FAIL:");
		int warn = text.indexOf("WARN:");
		int skip = text.indexOf("SKIP:");
		assertTrue(fail > 0 && fail < warn && warn < skip, text);
		assertTrue(text.contains("  [lang] block b has a translation - no entry"));
		assertTrue(text.contains("  [render] no gpu - headless"));
		assertTrue(text.contains("METRICS:\n  frame time median = 8.35 ms"), text);
		assertTrue(text.contains("RESULT: FAIL"));
		assertTrue(text.contains("run completed"));
	}

	@Test
	void aRunThatNeverCompletedSaysSoAndACleanOneSaysPass() {
		SmokeReport report = new SmokeReport("x");
		report.pass("a", "b");
		assertTrue(report.render().contains("RUN DID NOT COMPLETE"));
		report.markCompleted();
		assertTrue(report.render().contains("RESULT: PASS"));
		assertFalse(report.render().contains("METRICS"));
	}

	@Test
	void metricsKeepTheirLatestValueAndComeBackSorted() {
		SmokeReport report = new SmokeReport("x");
		report.metric("b", 1, "MB");
		report.metric("a", 2, "ms");
		report.metric("b", 3, "MB");
		assertEquals(List.of("a", "b"), List.copyOf(report.metrics().keySet()));
		assertEquals("3.00 MB", report.metrics().get("b"));
	}

	@Test
	void aSeriesKnowsItsHighestReadingAndAnEmptyOneIsZero() {
		PerfSampler.Series series = new PerfSampler.Series();
		assertEquals(0, series.max());
		series.add(3);
		series.add(11.5);
		series.add(7);
		assertEquals(11.5, series.max());
		assertEquals(7, series.last());
		series.add(-2);
		assertEquals(11.5, series.max());
	}

	@Test
	void anAllowlistGivenAsAListIgnoresTheLinesItNames() {
		LogScanner scanner = new LogScanner().allowAll(List.of("harmless noise \\d+", "another known line"));
		List<LogScanner.Finding> found = scanner.scan(List.of(
			"[10:00:00] [main/ERROR] [x/]: harmless noise 42",
			"[10:00:01] [main/ERROR] [x/]: another known line",
			"[10:00:02] [main/INFO] [x/]: all fine"));
		assertTrue(found.isEmpty(), found.toString());
		List<LogScanner.Finding> unknown = scanner.scan(List.of("[10:00:00] [main/ERROR] [x/]: something nobody explained"));
		assertFalse(unknown.isEmpty());
	}
}
