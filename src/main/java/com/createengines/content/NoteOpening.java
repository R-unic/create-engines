package com.createengines.content;

/**
 * Which form of an engine's tone is heard at its present speed ({@link NoteTimbre#opened}). It moves in steps, so the
 * resonances are retuned only now and then as the speed does, and a new one is made only then. Pure Java; the sound
 * thread alone uses it.
 */
final class NoteOpening {

	/** Firings a second from which a tone begins to open, by which it has, and in how many steps. */
	private static final float FROM = 16;
	private static final float BY = 42;
	private static final int STEPS = 12;

	private NoteTimbre from;
	private NoteTimbre heard;
	private float open = -1;

	/** The speed by which an engine with this many firings has opened. */
	static float openedBy(int firings) {
		return BY * 120 / Math.max(1, firings);
	}

	/** The tone {@code timbre} is heard as with firings coming this often. */
	NoteTimbre at(NoteTimbre timbre, float firingsASecond) {
		float now = Math.round(Math.clamp((firingsASecond - FROM) / (BY - FROM), 0f, 1f) * STEPS) / (float) STEPS;
		if (now != open || timbre != from) {
			open = now;
			from = timbre;
			heard = timbre.opened(now);
		}
		return heard;
	}
}
