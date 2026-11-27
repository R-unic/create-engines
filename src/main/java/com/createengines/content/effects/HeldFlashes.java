package com.createengines.content.effects;

import java.util.ArrayList;
import java.util.List;

import com.createengines.content.NoteCue;

/**
 * The flame and sparks of the bangs an engine note is playing, held until each is heard: shown with the exhaust stroke
 * that set it off they were seen well before their bang. One tick's list, on the game thread. Pure Java, so it is
 * tested.
 */
public final class HeldFlashes {

	/** Shown this long before the bang is heard, half a tick, so it lands on the nearer one. */
	static final long EARLY = 25_000_000L;
	/** The longest a flash waits: a note that was lost before its bang went off must not lose the flash too. */
	static final long LONGEST = 500_000_000L;
	/** Past this many waiting, the oldest is shown at once. */
	static final int MOST = 32;

	private record Held(NoteCue cue, long askedAt, Runnable flash) {
	}

	private final List<Held> waiting = new ArrayList<>();

	void add(NoteCue cue, long nowNanos, Runnable flash) {
		if (waiting.size() >= MOST)
			waiting.removeFirst().flash.run();
		waiting.add(new Held(cue, nowNanos, flash));
	}

	/** Shows every flash whose bang is heard by now. */
	void tick(long nowNanos) {
		for (int i = 0; i < waiting.size(); i++) {
			Held held = waiting.get(i);
			if (held.cue.heardBy(nowNanos + EARLY) || nowNanos - held.askedAt >= LONGEST) {
				waiting.remove(i--);
				held.flash.run();
			}
		}
	}

	void clear() {
		waiting.clear();
	}

	int size() {
		return waiting.size();
	}
}
