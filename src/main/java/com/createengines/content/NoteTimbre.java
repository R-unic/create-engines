package com.createengines.content;

/**
 * What kind of engine a note is heard as, apart from its rhythm and pitch: where its exhaust system resonates (two
 * fixed bands that do not move with the engine's speed, as a real silencer, collector and tailpipe do not), how much of
 * the rush of gas is heard, and how much thump is under it. Without these every build was the same pipe and the same
 * thump, only faster or slower. Worked out from the firing order, the outlets, the fuel and the runs. Pure Java, so it
 * is tested.
 *
 * @param lowHz   the lower resonance
 * @param lowGain how much of it is heard
 * @param highHz  the upper resonance
 * @param highGain how much of that is heard
 * @param dry     how much of what leaves the pipes is heard as it is, beside the resonances
 * @param flow    how much rush of gas goes down the pipes with each firing
 * @param thump   how much of the thump under each firing, 0 to 1
 * @param level   what the whole is turned up or down by, so no kind of engine is louder than another for its resonances
 * @param roar    how much of the roar of gas fills in between the firings ({@link NoteSystem})
 * @param topHz   where the exhaust system rolls off the top
 * @param thin    the share of the deepest bass taken off, 0 to 1
 * @param burbles whether its banks fire unevenly, so that it opens up with speed ({@link #opened})
 */
record NoteTimbre(float lowHz, float lowGain, float highHz, float highGain, float dry, float flow, float thump,
	float level, float roar, float topHz, boolean burbles, float thin) {

	/** A tone that stays as it is at any speed. */
	NoteTimbre(float lowHz, float lowGain, float highHz, float highGain, float dry, float flow, float thump, float level,
		float roar, float topHz) {
		this(lowHz, lowGain, highHz, highGain, dry, flow, thump, level, roar, topHz, false, 0);
	}

	/** An engine of a kind: from the fewest firings it covers, up to the next kind's. */
	private record Kind(int firings, NoteTimbre timbre) {
	}

	private static final Kind[] BY_FIRINGS = {
		// A single barks
		new Kind(1, new NoteTimbre(340, 1.6f, 950, 0.7f, 0.7f, 0.8f, 1f, 1f, 1f, 1300)),
		// A twin is throaty, lower than a single
		new Kind(2, new NoteTimbre(230, 1.7f, 640, 1.2f, 0.65f, 0.8f, 0.9f, 1f, 1f, 1150)),
		// A triple howls
		new Kind(3, new NoteTimbre(420, 1.6f, 1250, 0.9f, 0.6f, 0.9f, 0.5f, 1.15f, 0.9f, 1550)),
		// A four rasps: a hollow low middle and an edge above it
		new Kind(4, new NoteTimbre(200, 1.4f, 1250, 0.85f, 0.75f, 1f, 0.6f, 1.15f, 1f, 1300)),
		// A five warbles
		new Kind(5, new NoteTimbre(300, 1.9f, 820, 1.4f, 0.6f, 0.9f, 0.45f, 1.15f, 0.9f, 1300)),
		// A six sings, smooth, with little rush
		new Kind(6, new NoteTimbre(270, 2.6f, 620, 0.5f, 0.55f, 0.5f, 0.35f, 1.2f, 0.65f, 1200)),
		// Eight firing evenly in each bank bark hard and bright
		new Kind(8, new NoteTimbre(350, 1.8f, 1750, 0.85f, 0.65f, 0.9f, 0.4f, 1.15f, 0.9f, 1900)),
		new Kind(10, new NoteTimbre(450, 2f, 1900, 0.8f, 0.55f, 0.75f, 0.3f, 1.2f, 0.85f, 2050)),
		// Twelve wail, high and silken
		new Kind(12, new NoteTimbre(300, 1f, 750, 2.2f, 0.8f, 0.4f, 0.2f, 1.25f, 0.65f, 1250)),
		new Kind(16, new NoteTimbre(580, 2f, 1300, 1.2f, 0.55f, 0.45f, 0.25f, 1.2f, 0.8f, 1450)),
	};

	/** Banks that fire unevenly burble: deep and thick, with the top off. */
	private static final NoteTimbre BURBLE = new NoteTimbre(92, 1.9f, 420, 0.75f, 0.65f, 0.6f, 0.65f, 1f, 0.9f, 1700, true, 0);
	/** A rotary buzzes and tears, with hardly any thump. */
	private static final NoteTimbre ROTARY = new NoteTimbre(380, 2.4f, 1700, 2.2f, 0.6f, 0.55f, 0.2f, 1.5f, 0.45f, 3000);
	/** How much runs of unequal length thicken a note, as unequal headers give a flat four its rumble. */
	private static final float UNEQUAL_LOWERS = 0.5f;
	private static final float UNEQUAL_DULLS = 0.7f;

	/** How far apart the strongest and weakest firings of an engine are before its banks are heard to burble. */
	private static final float BURBLES = 0.25f;
	/** Banks burble from this many firings: with fewer, each firing is heard by itself. */
	private static final int BURBLES_FROM = 6;
	/** Compression ignition sits lower and duller, and rushes less: its knock is the sharp part of it. */
	private static final float DIESEL_LOW = 0.8f;
	private static final float DIESEL_HIGH = 0.72f;
	private static final float DIESEL_FLOW = 0.65f;
	private static final float DIESEL_BODY_HZ = 155;
	private static final float DIESEL_BODY_GAIN = 1.9f;
	private static final float DIESEL_THUMP = 0.45f;
	private static final float DIESEL_ROAR = 1.2f;
	private static final float DIESEL_CLATTER = 1.3f;
	/** Octaves the resonances move, down for a long run and up for a stub, either side of a middling one. */
	private static final double RUN_OCTAVES = 0.7;

	/**
	 * @param lope  each firing's strength for how long its outlet waited ({@link EngineNoteVoicing#lope})
	 * @param run     the mean length of the exhaust runs, 0 (stubs) to 1 (long)
	 * @param unequal how far apart the longest and the shortest run are, 0 to 1
	 */
	static NoteTimbre of(float[] lope, float run, float unequal, boolean compression, boolean rotary) {
		NoteTimbre kind = rotary ? ROTARY : kind(lope);
		float moved = (float) Math.pow(2, (0.5 - run) * RUN_OCTAVES);
		if (compression && !rotary)
			kind = diesel(kind);
		float low = kind.lowHz * moved * (1 - UNEQUAL_LOWERS * unequal) * (compression ? DIESEL_LOW : 1);
		float high = kind.highHz * moved * (1 - UNEQUAL_LOWERS * unequal) * (compression ? DIESEL_HIGH : 1);
		return new NoteTimbre(low, kind.lowGain, high, kind.highGain * (1 - UNEQUAL_DULLS * unequal), kind.dry,
			kind.flow * (compression ? DIESEL_FLOW : 1), kind.thump, kind.level, kind.roar,
			kind.topHz * (compression ? DIESEL_HIGH : 1), kind.burbles, kind.thin);
	}

	/**
	 * A diesel is a big, slow-breathing thing: whatever its layout, much of it is a low body, with a clatter above. As a
	 * petrol engine merely tuned lower it could not be told from one but for a tick.
	 */
	private static NoteTimbre diesel(NoteTimbre kind) {
		return new NoteTimbre(DIESEL_BODY_HZ / DIESEL_LOW, DIESEL_BODY_GAIN, kind.highHz, kind.highGain * DIESEL_CLATTER,
			kind.dry, kind.flow, Math.max(kind.thump, DIESEL_THUMP), kind.level, kind.roar * DIESEL_ROAR, kind.topHz, kind.burbles, kind.thin);
	}

	/** How many times higher uneven banks' low resonance has moved once their firings crowd, and what else changes. */
	private static final float OPEN_LOW = 1.3f;
	private static final float OPEN_HIGH_GAIN = 2.2f;
	private static final float OPEN_DRY = 1.35f;
	private static final float OPEN_THUMP_LOST = 0.8f;
	private static final float OPEN_THIN = 0.65f;

	/**
	 * This tone as heard with its firings {@code open} of the way to crowded, 0 to 1. Only a burble changes. Its deep voice
	 * is right at idle, but held there at speed nearly all of it lay under what most speakers play and it seemed to go
	 * quiet, so as it revs the deepest bass and the thump are thinned and more is heard above them.
	 */
	NoteTimbre opened(float open) {
		if (!burbles || open <= 0)
			return this;
		return new NoteTimbre(lowHz * (1 + (OPEN_LOW - 1) * open), lowGain, highHz,
			highGain * (1 + (OPEN_HIGH_GAIN - 1) * open), dry * (1 + (OPEN_DRY - 1) * open), flow,
			thump * (1 - OPEN_THUMP_LOST * open), level, roar, topHz, burbles, OPEN_THIN * open);
	}

	private static NoteTimbre kind(float[] lope) {
		if (lope.length >= BURBLES_FROM) {
			float weakest = Float.MAX_VALUE;
			float strongest = 0;
			for (float strength : lope) {
				weakest = Math.min(weakest, strength);
				strongest = Math.max(strongest, strength);
			}
			if (strongest - weakest > BURBLES)
				return BURBLE;
		}
		NoteTimbre found = BY_FIRINGS[0].timbre;
		for (Kind kind : BY_FIRINGS)
			if (lope.length >= kind.firings)
				found = kind.timbre;
		return found;
	}
}
