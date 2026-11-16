package com.createengines.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads a game log for things that mean something is broken even though nothing crashed: errors, exceptions and the
 * messages Minecraft prints when a model, texture, sound, recipe or loot table fails to load (it carries on and
 * draws a purple checkerboard). Identical messages are folded into one finding with a count. Known harmless noise
 * from the game, Create or the development environment is taken out by {@link #BASELINE_NOISE} and any extra
 * patterns a caller adds.
 *
 * <p>Pure Java: it works on lines of text, so the rules can be unit tested without a game.
 */
public final class LogScanner {

	public enum Severity {
		FAIL, WARN
	}

	public record Finding(Severity severity, String rule, String message, int count) {
	}

	/** Lines that always mean a defect, whatever their log level. */
	private static final Map<String, Pattern> FATAL = new LinkedHashMap<>();

	/** Log text that is only interesting when it comes from our mod or names one of our resources. */
	private static final Map<String, Pattern> SUSPICIOUS = new LinkedHashMap<>();

	static {
		FATAL.put("exception on a thread", Pattern.compile("Exception in thread"));
		FATAL.put("out of memory", Pattern.compile("OutOfMemoryError"));
		FATAL.put("stale class", Pattern.compile("NoSuchMethodError|NoClassDefFoundError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError"));
		FATAL.put("crash report", Pattern.compile("Crash report saved|This crash report|---- Minecraft Crash Report"));
		FATAL.put("reported exception", Pattern.compile("Reported exception thrown"));
		FATAL.put("mixin failure", Pattern.compile("InvalidInjectionException|MixinApplyError|Mixin apply failed|InjectionError"));
		FATAL.put("our own smoke check", Pattern.compile("(SMOKE|AUDIT)[A-Za-z0-9 ]* FAIL\\b"));
		FATAL.put("missing model or texture", Pattern.compile(
			"Missing (model|texture|sprite)|Unable to (bake|resolve) (model|texture|parent)|Unable to load model|Missing textures in model|Unable to resolve texture|Unable to find (model|blockstate)|Exception loading (blockstate|model)|Couldn't load (blockstate|model)|Failed to load (blockstate|model)",
			Pattern.CASE_INSENSITIVE));
		FATAL.put("resource failed to parse", Pattern.compile(
			"Parsing error loading (recipe|loot|advancement|tag|data)|Couldn't parse (data file|element|loot)|Failed to parse|Couldn't load (recipe|loot table|advancement|tag)|Error loading (recipe|loot|advancement)|Failed to load (recipe|loot|advancement|particle|sound)|Invalid (sound|recipe)",
			Pattern.CASE_INSENSITIVE));
		FATAL.put("unknown id", Pattern.compile("Unknown (registry key|item|block|fluid|entity|sound event|particle)|Unbound values in registry|Missing registry",
			Pattern.CASE_INSENSITIVE));

		SUSPICIOUS.put("mod error", Pattern.compile("create_engines|com\\.createengines|Engine Engineer", Pattern.CASE_INSENSITIVE));
		SUSPICIOUS.put("lang key", Pattern.compile("missing translation|Missing translation|ponder\\.create_engines", Pattern.CASE_INSENSITIVE));
	}

	/**
	 * Known noise, matched against the whole line. Each entry names why it is safe, so the list stays honest: when
	 * the environment changes, an entry whose reason no longer holds is deleted rather than left to hide a real
	 * problem.
	 */
	public static final List<Pattern> BASELINE_NOISE = List.of(
		// Mixin cannot find a refmap in a development environment, and says so
		Pattern.compile("Reference map '.*refmap.json' for .* could not be read"),
		Pattern.compile("Error loading class: org/jetbrains/annotations/ApiStatus"),
		// The vanilla pack builder warns about the union filesystem the dev environment mounts the game jar on
		Pattern.compile("VanillaPackResourcesBuilder.*Assets URL"),
		// Create ships a handful of deliberately empty or Ponder-only entries
		Pattern.compile("Skipping .* because it has no recipe"),
		// NeoForge prints these while building the dev environment's mod list
		Pattern.compile("Mod file .* is missing a license|has no license"),
		// Unrelated debug logging, which this repo's run configs switch to DEBUG level
		Pattern.compile("^\\[[^\\]]*/DEBUG\\]"));

	private final List<Pattern> allowed = new ArrayList<>(BASELINE_NOISE);

	/** Adds a pattern of lines to ignore (matched with {@code find}). */
	public LogScanner allow(String regex) {
		allowed.add(Pattern.compile(regex));
		return this;
	}

	public LogScanner allowAll(Iterable<String> regexes) {
		for (String regex : regexes)
			allow(regex);
		return this;
	}

	/**
	 * Parses allowlist file text: one regex per line, blank lines and lines starting with {@code #} are ignored. Each
	 * entry should be preceded by a comment saying why that noise is harmless.
	 */
	public static List<String> parseAllowlist(String text) {
		List<String> regexes = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.strip();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
				regexes.add(trimmed);
		}
		return regexes;
	}

	private boolean isAllowed(String line) {
		for (Pattern pattern : allowed)
			if (pattern.matcher(line)
				.find())
				return true;
		return false;
	}

	/** Strips the timestamp, thread and ids that change from line to line so repeats fold together. */
	static String normalise(String line) {
		String text = line.replaceAll("^\\[[0-9:.]+\\]\\s*", "")
			.replaceAll("^\\[[^\\]]*/(INFO|WARN|ERROR|FATAL|DEBUG)\\]\\s*", "$1 ")
			.replaceAll("@[0-9a-f]{4,}", "@id")
			.replaceAll("IO-Worker-\\d+", "IO-Worker")
			.replaceAll("\\b\\d{4,}\\b", "N");
		return text.length() > 220 ? text.substring(0, 220) : text;
	}

	private static boolean level(String line, String level) {
		return line.contains("/" + level + "]");
	}

	public List<Finding> scan(Iterable<String> lines) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, String[]> meta = new LinkedHashMap<>();
		boolean inTrace = false;
		boolean traceAllowed = false;
		for (String line : lines) {
			if (line == null)
				continue;
			boolean traceLine = line.startsWith("\tat ") || line.startsWith("Caused by:") || line.startsWith("\t... ")
				|| line.startsWith("java.") && line.contains("Exception") || line.startsWith("\tSuppressed:");
			if (traceLine && inTrace) {
				// A stack trace belongs to the line that began it; an allowed line's trace is allowed too
				if (!traceAllowed && line.startsWith("Caused by:"))
					note(counts, meta, Severity.FAIL, "caused by", line);
				continue;
			}
			inTrace = false;
			if (isAllowed(line)) {
				inTrace = true;
				traceAllowed = true;
				continue;
			}
			boolean error = level(line, "ERROR") || level(line, "FATAL");
			boolean warn = level(line, "WARN");
			String fatalRule = null;
			for (var rule : FATAL.entrySet())
				if (rule.getValue()
					.matcher(line)
					.find()) {
					fatalRule = rule.getKey();
					break;
				}
			if (fatalRule != null) {
				note(counts, meta, Severity.FAIL, fatalRule, line);
				inTrace = true;
				traceAllowed = false;
			} else if (error) {
				note(counts, meta, Severity.FAIL, "error logged", line);
				inTrace = true;
				traceAllowed = false;
			} else if (warn) {
				String why = null;
				for (var rule : SUSPICIOUS.entrySet())
					if (rule.getValue()
						.matcher(line)
						.find()) {
						why = rule.getKey();
						break;
					}
				if (why != null)
					note(counts, meta, Severity.WARN, "warning: " + why, line);
				inTrace = true;
				traceAllowed = why == null;
			}
		}
		List<Finding> findings = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			String[] info = meta.get(entry.getKey());
			findings.add(new Finding(Severity.valueOf(info[0]), info[1], info[2], entry.getValue()[0]));
		}
		return findings;
	}

	private static void note(Map<String, int[]> counts, Map<String, String[]> meta, Severity severity, String rule,
		String line) {
		String key = severity + "|" + rule + "|" + normalise(line);
		counts.computeIfAbsent(key, k -> new int[1])[0]++;
		meta.putIfAbsent(key, new String[] {severity.name(), rule, line.strip()
			.length() > 300 ? line.strip()
				.substring(0, 300) : line.strip()});
	}

	/** Findings as report checks, in category {@code log}. */
	public static void report(List<Finding> findings, SmokeReport report) {
		if (findings.isEmpty())
			report.pass("log", "no errors or suspicious warnings in the game log");
		for (Finding finding : findings) {
			String detail = (finding.count() > 1 ? finding.count() + "x " : "") + finding.message();
			if (finding.severity() == Severity.FAIL)
				report.fail("log", finding.rule(), detail);
			else
				report.warn("log", finding.rule(), detail);
		}
	}
}
