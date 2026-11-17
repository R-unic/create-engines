package com.createengines.engine.blueprint;

import java.util.Locale;

/** Names derived from a blueprint's own, in plain Java so the rules are tested without a game. */
public final class BlueprintNames {

	private BlueprintNames() {
	}

	/**
	 * A file name stem for a blueprint: lower case letters and digits, runs of anything else closed up to one underscore,
	 * no underscore at either end, and {@code engine} if nothing is left. It can hold no path character, so a blueprint's
	 * name can never place a file anywhere but where it is meant to go.
	 */
	public static String fileName(String name) {
		StringBuilder clean = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT)
			.toCharArray())
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9')
				clean.append(c);
			else if (clean.length() > 0 && clean.charAt(clean.length() - 1) != '_')
				clean.append('_');
		int end = clean.length();
		while (end > 0 && clean.charAt(end - 1) == '_')
			end--;
		return end == 0 ? "engine" : clean.substring(0, end);
	}
}
