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
 */
record NoteTimbre(float lowHz, float lowGain, float highHz, float highGain, float dry, float flow, float thump,
	float level) {

	/** An engine of a kind: from the fewest firings it covers, up to the next kind's. */
	private record Kind(int firings, NoteTimbre timbre) {
	}

	private static final Kind[] BY_FIRINGS = {
		// A single barks
		new Kind(1, new NoteTimbre(340, 1.6f, 950, 0.7f, 0.7f, 0.8f, 1f, 1f)),
		// A twin is throaty, lower than a single
		new Kind(2, new NoteTimbre(230, 1.7f, 640, 1.2f, 0.65f, 0.8f, 0.9f, 1f)),
		// A triple howls
		new Kind(3, new NoteTimbre(420, 1.6f, 1250, 0.9f, 0.6f, 0.9f, 0.5f, 1.15f)),
		// A four rasps: a hollow low middle and an edge above it
		new Kind(4, new NoteTimbre(200, 1.4f, 1250, 0.85f, 0.75f, 1f, 0.6f, 1.15f)),
		// A five warbles
		new Kind(5, new NoteTimbre(300, 1.9f, 820, 1.4f, 0.6f, 0.9f, 0.45f, 1.15f)),
		// A six sings, smooth, with little rush
		new Kind(6, new NoteTimbre(520, 2.4f, 1050, 0.8f, 0.55f, 0.55f, 0.4f, 1.25f)),
		// Eight firing evenly in each bank bark hard and bright
		new Kind(8, new NoteTimbre(350, 1.8f, 1750, 0.85f, 0.65f, 0.9f, 0.4f, 1.15f)),
		new Kind(10, new NoteTimbre(450, 2f, 1900, 0.8f, 0.55f, 0.75f, 0.3f, 1.2f)),
		// Twelve wail, high and silken
		new Kind(12, new NoteTimbre(740, 2.5f, 2200, 0.7f, 0.5f, 0.45f, 0.25f, 1.3f)),
		new Kind(16, new NoteTimbre(580, 2f, 1300, 1.2f, 0.55f, 0.45f, 0.25f, 1.2f)),
	};

	/** Banks that fire unevenly burble: deep and thick, with the top off. */
	private static final NoteTimbre BURBLE = new NoteTimbre(92, 1.9f, 420, 0.75f, 0.65f, 0.6f, 0.65f, 1f);
	/** A rotary buzzes and tears, with hardly any thump. */
	private static final NoteTimbre ROTARY = new NoteTimbre(850, 2.4f, 2100, 1.3f, 0.45f, 1.4f, 0.12f, 1.5f);
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
		float low = kind.lowHz * moved * (1 - UNEQUAL_LOWERS * unequal) * (compression ? DIESEL_LOW : 1);
		float high = kind.highHz * moved * (1 - UNEQUAL_LOWERS * unequal) * (compression ? DIESEL_HIGH : 1);
		return new NoteTimbre(low, kind.lowGain, high, kind.highGain * (1 - UNEQUAL_DULLS * unequal), kind.dry,
			kind.flow * (compression ? DIESEL_FLOW : 1), kind.thump, kind.level);
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
