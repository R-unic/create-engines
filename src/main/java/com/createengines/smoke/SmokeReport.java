package com.createengines.smoke;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a smoke run found, as checks that passed, warned or failed, grouped by category. Passing checks are only counted
 * (an audit of every block state makes thousands), so the report stays readable: it lists every problem and says how
 * many things were fine. Pure Java so the reporting itself can be unit tested; thread safe, since the client, server
 * and sound threads all report into it.
 *
 * <p>A run is only valid if it {@link #markCompleted() completed}: a crash, a hang and a client that never reached the
 * world all leave the marker out, which {@link SmokeVerify} treats as a failure of its own.
 */
public final class SmokeReport {

	public enum Status {
		PASS, WARN, FAIL, SKIP
	}

	public record Check(String category, String name, Status status, String detail) {
	}

	private final String run;
	private final List<Check> problems = new ArrayList<>();
	private final Map<String, int[]> counts = new TreeMap<>();
	private boolean completed;

	public SmokeReport(String run) {
		this.run = run;
	}

	public String run() {
		return run;
	}

	private int[] row(String category) {
		return counts.computeIfAbsent(category, c -> new int[Status.values().length]);
	}

	public synchronized void pass(String category, String name) {
		row(category)[Status.PASS.ordinal()]++;
	}

	public synchronized void fail(String category, String name, String detail) {
		add(new Check(category, name, Status.FAIL, detail));
	}

	public synchronized void warn(String category, String name, String detail) {
		add(new Check(category, name, Status.WARN, detail));
	}

	public synchronized void skip(String category, String name, String detail) {
		add(new Check(category, name, Status.SKIP, detail));
	}

	/** A pass when {@code ok}, otherwise a failure carrying {@code detail}. */
	public synchronized boolean check(String category, String name, boolean ok, String detail) {
		if (ok)
			pass(category, name);
		else
			fail(category, name, detail);
		return ok;
	}

	/** As {@link #check} but a miss is only a warning: for things that are probably wrong but may be deliberate. */
	public synchronized boolean expect(String category, String name, boolean ok, String detail) {
		if (ok)
			pass(category, name);
		else
			warn(category, name, detail);
		return ok;
	}

	private final java.util.Map<String, String> metrics = new TreeMap<>();

	/**
	 * Records a measured number (a frame time, a heap size, a tick cost) so runs can be compared over time. A metric
	 * never passes or fails by itself; pair it with a {@link #check} against a limit.
	 */
	public synchronized void metric(String name, double value, String unit) {
		metrics.put(name, String.format(java.util.Locale.ROOT, "%.2f %s", value, unit));
	}

	public synchronized java.util.Map<String, String> metrics() {
		return new TreeMap<>(metrics);
	}

	private void add(Check raw) {
		// One line each, so report.txt can be read back by SmokeVerify
		Check check = new Check(raw.category(), flat(raw.name()), raw.status(), flat(raw.detail()));
		problems.add(check);
		row(check.category())[check.status()
			.ordinal()]++;
	}

	private static String flat(String text) {
		return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ")
			.strip();
	}

	public synchronized void markCompleted() {
		completed = true;
	}

	public synchronized boolean completed() {
		return completed;
	}

	public synchronized int count(Status status) {
		int total = 0;
		for (int[] row : counts.values())
			total += row[status.ordinal()];
		return total;
	}

	public synchronized int count(String category, Status status) {
		int[] row = counts.get(category);
		return row == null ? 0 : row[status.ordinal()];
	}

	public synchronized List<Check> problems() {
		return List.copyOf(problems);
	}

	/** Whether the run completed and nothing failed (warnings do not count against it). */
	public synchronized boolean passed() {
		return completed && count(Status.FAIL) == 0;
	}

	/** Folds another report's checks and counts into this one (the log scan's, say). */
	public synchronized void merge(SmokeReport other) {
		synchronized (other) {
			problems.addAll(other.problems);
			metrics.putAll(other.metrics);
			for (var entry : other.counts.entrySet()) {
				int[] mine = row(entry.getKey());
				for (int i = 0; i < mine.length; i++)
					mine[i] += entry.getValue()[i];
			}
		}
	}

	public synchronized String render() {
		StringBuilder out = new StringBuilder();
		out.append("SMOKE REPORT ")
			.append(run)
			.append('\n');
		out.append(String.format("%-28s %6s %6s %6s %6s%n", "category", "pass", "warn", "fail", "skip"));
		for (var entry : counts.entrySet()) {
			int[] row = entry.getValue();
			out.append(String.format("%-28s %6d %6d %6d %6d%n", entry.getKey(), row[Status.PASS.ordinal()],
				row[Status.WARN.ordinal()], row[Status.FAIL.ordinal()], row[Status.SKIP.ordinal()]));
		}
		out.append(String.format("%-28s %6d %6d %6d %6d%n", "TOTAL", count(Status.PASS), count(Status.WARN),
			count(Status.FAIL), count(Status.SKIP)));
		for (Status status : new Status[] {Status.FAIL, Status.WARN, Status.SKIP}) {
			boolean any = false;
			for (Check check : problems) {
				if (check.status() != status)
					continue;
				if (!any)
					out.append('\n')
						.append(status)
						.append(":\n");
				any = true;
				out.append("  [")
					.append(check.category())
					.append("] ")
					.append(check.name());
				if (!check.detail()
					.isEmpty())
					out.append(" - ")
						.append(check.detail());
				out.append('\n');
			}
		}
		if (!metrics.isEmpty()) {
			out.append("\nMETRICS:\n");
			for (var metric : metrics.entrySet())
				out.append("  ")
					.append(metric.getKey())
					.append(" = ")
					.append(metric.getValue())
					.append('\n');
		}
		out.append('\n')
			.append(completed ? "run completed" : "RUN DID NOT COMPLETE (crash, hang or never reached the world)")
			.append('\n')
			.append(passed() ? "RESULT: PASS" : "RESULT: FAIL")
			.append('\n');
		return out.toString();
	}

	/** Writes {@code report.txt} and the machine-readable {@code result.properties} into {@code dir}. */
	public synchronized void write(Path dir) {
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("report.txt"), render(), StandardCharsets.UTF_8);
			Files.writeString(dir.resolve("result.properties"),
				"run=" + run + "\ncompleted=" + completed + "\nfailures=" + count(Status.FAIL) + "\nwarnings="
					+ count(Status.WARN) + "\npasses=" + count(Status.PASS) + "\n",
				StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write the smoke report to " + dir, e);
		}
	}
}
