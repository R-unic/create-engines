package com.createengines.smoke;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * The last word on a smoke run, run by Gradle after the game has exited: reads what the game wrote, scans the game
 * log for errors the game itself would not call failures, and exits non-zero if either found a problem. Doing the log
 * scan out here, after shutdown, catches errors printed while the world was saving and the sound engine closing,
 * which no in-game check can see.
 *
 * <p>Arguments: {@code --run NAME --dir REPORT_DIR --log LOG_FILE [--allow ALLOWLIST_FILE] [--lenient-log]}.
 * Pure Java with no Minecraft imports, so it can be run by any JVM and unit tested.
 */
public final class SmokeVerify {

	private SmokeVerify() {
	}

	public static void main(String[] args) throws IOException {
		String run = "smoke";
		Path dir = null;
		Path log = null;
		Path allow = null;
		boolean lenientLog = false;
		boolean gameTest = false;
		boolean logOnly = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--log-only" -> logOnly = true;
				case "--run" -> run = args[++i];
				case "--dir" -> dir = Path.of(args[++i]);
				case "--log" -> log = Path.of(args[++i]);
				case "--allow" -> allow = Path.of(args[++i]);
				case "--lenient-log" -> lenientLog = true;
				case "--gametest" -> gameTest = true;
				default -> throw new IllegalArgumentException("unknown argument " + args[i]);
			}
		}
		SmokeReport report;
		if (gameTest) {
			if (log == null)
				throw new IllegalArgumentException("--log is required with --gametest");
			report = verifyGameTests(run, log, allow);
		} else {
			if (dir == null && !logOnly)
				throw new IllegalArgumentException("--dir is required (or --log-only)");
			report = verify(run, dir, log, allow, lenientLog);
		}
		System.out.println(report.render());
		if (dir != null)
			report.write(dir.resolveSibling(dir.getFileName() + "-verified"));
		if (!report.passed())
			System.exit(1);
	}

	/**
	 * The verdict on a GameTest server run, from its log. Gradle's own exit code cannot be trusted for this: the
	 * server finishes and exits 0 whether its tests passed or not, and exits 0 too if it never started (another
	 * process held the world lock), so the only evidence is the summary line the server prints.
	 */
	public static SmokeReport verifyGameTests(String run, Path log, Path allow) throws IOException {
		SmokeReport report = new SmokeReport(run);
		if (!Files.exists(log)) {
			report.fail("gametest", "no game log", "expected " + log);
			report.markCompleted();
			return report;
		}
		List<String> lines = Files.readAllLines(log, StandardCharsets.ISO_8859_1);
		java.util.regex.Pattern passed = java.util.regex.Pattern.compile("All (\\d+) required tests passed");
		java.util.regex.Pattern failed = java.util.regex.Pattern.compile("(\\d+) required tests failed");
		boolean decided = false;
		for (int i = 0; i < lines.size(); i++) {
			var passedMatch = passed.matcher(lines.get(i));
			var failedMatch = failed.matcher(lines.get(i));
			if (passedMatch.find()) {
				decided = true;
				report.pass("gametest", passedMatch.group(1) + " game tests passed");
			} else if (failedMatch.find()) {
				decided = true;
				for (int j = i + 1; j < lines.size() && j < i + 60; j++) {
					var name = java.util.regex.Pattern.compile("\\]:\\s+- (\\S+)")
						.matcher(lines.get(j));
					if (!name.find())
						break;
					report.fail("gametest", name.group(1), "failed (see the log for its message)");
				}
				if (report.count(SmokeReport.Status.FAIL) == 0)
					report.fail("gametest", "game tests failed", failedMatch.group(1) + " required tests failed");
			}
		}
		if (!decided)
			report.fail("gametest", "the game test server never finished",
				"no 'All N required tests passed' line: it crashed, or another server held the world lock");
		LogScanner scanner = new LogScanner();
		if (allow != null && Files.exists(allow))
			scanner.configure(Files.readString(allow, StandardCharsets.UTF_8));
		LogScanner.report(scanner.scan(lines), report);
		report.markCompleted();
		return report;
	}

	/** Builds the final report for a run: the game's own report merged with the log scan. */
	public static SmokeReport verify(String run, Path dir, Path log, Path allow, boolean lenientLog) throws IOException {
		SmokeReport report = new SmokeReport(run);
		// With no report directory the run is judged by its log alone (the older screenshot runs write no report)
		Path result = dir == null ? null : dir.resolve("result.properties");
		if (result == null) {
			report.pass("run", "no report expected");
		} else if (!Files.exists(result)) {
			report.fail("run", "the game wrote no result", "expected " + result + "; it crashed before the audit ended or never started");
		} else {
			Properties properties = new Properties();
			try (var in = Files.newBufferedReader(result)) {
				properties.load(in);
			}
			if (Boolean.parseBoolean(properties.getProperty("completed", "false")))
				report.pass("run", "the run completed");
			else
				report.fail("run", "the run did not complete", "result.properties says completed=false");
			Path text = dir.resolve("report.txt");
			if (Files.exists(text))
				copyChecks(Files.readAllLines(text, StandardCharsets.UTF_8), properties, report);
		}
		if (log != null) {
			if (!Files.exists(log)) {
				report.fail("log", "no game log", "expected " + log);
			} else {
				LogScanner scanner = new LogScanner();
				if (allow != null && Files.exists(allow))
					scanner.configure(Files.readString(allow, StandardCharsets.UTF_8));
				List<String> lines = Files.readAllLines(log, StandardCharsets.ISO_8859_1);
				List<LogScanner.Finding> findings = scanner.scan(lines);
				if (lenientLog)
					findings = findings.stream()
						.map(f -> new LogScanner.Finding(LogScanner.Severity.WARN, f.rule(), f.message(), f.count()))
						.toList();
				LogScanner.report(findings, report);
			}
		}
		report.markCompleted();
		return report;
	}

	/** Brings the game's counts and problem lines into the final report. */
	private static void copyChecks(List<String> lines, Properties properties, SmokeReport report) {
		int failures = Integer.parseInt(properties.getProperty("failures", "0"));
		int passes = Integer.parseInt(properties.getProperty("passes", "0"));
		for (int i = 0; i < passes; i++)
			report.pass("game", "check");
		String section = null;
		int parsedFailures = 0;
		for (String line : lines) {
			if (line.equals("FAIL:") || line.equals("WARN:") || line.equals("SKIP:"))
				section = line.substring(0, 4);
			else if (line.isBlank() || line.startsWith("run ") || line.startsWith("RUN ") || line.startsWith("RESULT"))
				section = null;
			else if (section != null && line.startsWith("  [")) {
				int close = line.indexOf(']');
				String category = line.substring(3, close);
				String rest = line.substring(close + 2);
				int dash = rest.indexOf(" - ");
				String name = dash < 0 ? rest : rest.substring(0, dash);
				String detail = dash < 0 ? "" : rest.substring(dash + 3);
				switch (section) {
					case "FAIL" -> {
						report.fail(category, name, detail);
						parsedFailures++;
					}
					case "WARN" -> report.warn(category, name, detail);
					default -> report.skip(category, name, detail);
				}
			}
		}
		// A report whose own header counts more failures than it lists has been truncated or garbled
		if (parsedFailures != failures)
			report.fail("run", "failure count mismatch", "result.properties counts " + failures
				+ " failures but report.txt lists " + parsedFailures);
	}
}
