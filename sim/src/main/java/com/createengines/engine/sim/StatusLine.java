package com.createengines.engine.sim;

import java.util.List;

/**
 * One line a {@link TorqueSource} adds to the engine's goggle readout, such as a turbine's "Spool 80%". {@code key} is a
 * translation key from the pack's or addon's own language files; {@code args} fill its {@code %s} slots. It is plain data so
 * the engine can sync it to clients; the lengths are capped when it is taken (see {@link #LIMIT}).
 *
 * @param key  a translation key; a key nothing translates is shown as it is
 * @param args text for the key's placeholders
 * @param tone how it is coloured
 */
public record StatusLine(String key, List<String> args, Tone tone) {

	/** The most lines one engine shows from its added sources, in total. */
	public static final int LIMIT = 8;
	/** The most placeholder values one line takes, and the longest key or value kept. */
	public static final int MAX_ARGS = 4;
	public static final int MAX_TEXT = 96;

	public enum Tone {
		/** Plain information, grey. */
		NOTE,
		/** Working as it should, green. */
		GOOD,
		/** Something to look at, gold. */
		WARN,
		/** Something is wrong, red. */
		BAD
	}

	public StatusLine {
		key = clip(key == null ? "" : key);
		args = args == null ? List.of() : args.stream()
			.limit(MAX_ARGS)
			.map(arg -> clip(String.valueOf(arg)))
			.toList();
		tone = tone == null ? Tone.NOTE : tone;
	}

	public static StatusLine of(String key, Tone tone, String... args) {
		return new StatusLine(key, List.of(args), tone);
	}

	private static String clip(String text) {
		return text.substring(0, Math.min(text.length(), MAX_TEXT));
	}
}
