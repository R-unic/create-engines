package com.createengines.client;

import java.util.Set;

import com.createengines.compat.sable.SableCompat;

/**
 * The sound bench's scene about where an engine is heard: {@code moving} (only with Create Aeronautics loaded) lifts an
 * idling inline four into a moving structure and carries it past the listener.
 */
final class ShowcaseRunSoundBenchPlaces {

	/** The listener stands half this many blocks from the engine. */
	private static final int LISTEN = 12;
	/** Ticks the held engine is listened to. */
	private static final int HOLD = 160;
	/** How far the structure travels either side of where it starts, and how many ticks the whole way takes: 8 blocks a second. */
	private static final int TRAVEL = 24;
	private static final int PASS_TICKS = 120;

	private ShowcaseRunSoundBenchPlaces() {
	}

	/** Adds the wanted scene on pad {@code bay}; returns the next free pad. */
	static int schedule(Set<String> only, int bay, ShowcaseRunSoundBench.Rig rig) {
		// Never by default: without the mod there is no structure to ride, so it has to be asked for by name
		if (only.contains("moving"))
			moving(bay, rig);
		return bay + 1;
	}

	/**
	 * The engine, started on the ground, becomes a moving structure held beside the listener, then is carried away to
	 * one side, past the listener to the other, and back.
	 */
	private static void moving(int bay, ShowcaseRunSoundBench.Rig rig) {
		if (!SableCompat.installed()) {
			ShowcaseRunSoundBench.step("moving", "missing", 20,
				() -> ShowcaseRunSoundBench.failed("the moving scene needs Create Aeronautics loaded"));
			return;
		}
		ShowcaseRunSoundBench.startedEngine("moving", rig, bay, LISTEN / 2);
		ShowcaseRunSoundBench.step("moving", "assemble", 100,
			() -> ShowcaseRunSoundBench.onServer((level, player) -> ShowcaseRunSoundBenchSable.assemble(level, rig)));
		ShowcaseRunSoundBench.during("moving", "held", HOLD, t -> carry(0));
		ShowcaseRunSoundBench.during("moving", "leave", PASS_TICKS / 2, t -> carry(-TRAVEL * share(t, PASS_TICKS / 2)));
		ShowcaseRunSoundBench.during("moving", "pass", PASS_TICKS, t -> carry(-TRAVEL + 2 * TRAVEL * share(t, PASS_TICKS)));
		ShowcaseRunSoundBench.during("moving", "pass_back", PASS_TICKS,
			t -> carry(TRAVEL - 2 * TRAVEL * share(t, PASS_TICKS)));
		// Left where it is: nothing comes after it in the run that has it
	}

	/** The share done by {@code tick} of a stretch whose last tick told is one short of {@code of}. */
	private static double share(int tick, int of) {
		return Math.min(1, tick / (double) (of - 1));
	}

	private static void carry(double along) {
		ShowcaseRunSoundBench.onServer((level, player) -> ShowcaseRunSoundBenchSable.carry(along));
	}
}
