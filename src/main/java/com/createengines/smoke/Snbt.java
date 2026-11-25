package com.createengines.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough SNBT to turn a saved fixture into {@code setblock} commands: splits a compound into its keys and a list into
 * its elements, and leaves every value as the text it was, so nothing is re-typed or rounded on the way through.
 */
public final class Snbt {

	private Snbt() {
	}

	/** The entries of {@code {key:value,...}}, in order, each value as written. */
	public static Map<String, String> compound(String text) {
		Map<String, String> entries = new LinkedHashMap<>();
		for (String part : split(inside(text, '{', '}'))) {
			int colon = topLevel(part, ':', 0);
			if (colon < 0)
				throw new IllegalArgumentException("not a key and a value: " + part);
			String key = part.substring(0, colon).trim();
			if (key.length() > 1 && key.startsWith("\""))
				key = key.substring(1, key.length() - 1);
			entries.put(key, part.substring(colon + 1).trim());
		}
		return entries;
	}

	/** The elements of {@code [a,b,...]}, each as written. A typed array's {@code I;} prefix is not an element. */
	public static List<String> list(String text) {
		String body = inside(text, '[', ']');
		if (body.length() > 1 && body.charAt(1) == ';')
			body = body.substring(2);
		return split(body);
	}

	/** A string value without its quotes; anything else as it is. */
	public static String unquote(String value) {
		if (value.length() > 1 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'")))
			return value.substring(1, value.length() - 1);
		return value;
	}

	private static String inside(String text, char open, char close) {
		String trimmed = text.trim();
		if (trimmed.length() < 2 || trimmed.charAt(0) != open || trimmed.charAt(trimmed.length() - 1) != close)
			throw new IllegalArgumentException("expected " + open + "..." + close + ", got " + trimmed);
		return trimmed.substring(1, trimmed.length() - 1);
	}

	private static List<String> split(String body) {
		List<String> parts = new ArrayList<>();
		int from = 0;
		while (from < body.length()) {
			int comma = topLevel(body, ',', from);
			int end = comma < 0 ? body.length() : comma;
			String part = body.substring(from, end).trim();
			if (!part.isEmpty())
				parts.add(part);
			from = end + 1;
		}
		return parts;
	}

	/** The next {@code wanted} outside every bracket and quote, or -1. */
	private static int topLevel(String text, char wanted, int from) {
		int depth = 0;
		char quote = 0;
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == '\\')
					i++;
				else if (c == quote)
					quote = 0;
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '{' || c == '[') {
				depth++;
			} else if (c == '}' || c == ']') {
				depth--;
			} else if (c == wanted && depth == 0) {
				return i;
			}
		}
		return -1;
	}
}
