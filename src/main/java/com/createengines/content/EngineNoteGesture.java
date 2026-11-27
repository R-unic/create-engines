package com.createengines.content;

/**
 * A catch or a stall played by the {@link EngineNoteSynth} itself, so its firings come when the engine's do: at its speed,
 * in its firing order. A catch only changes how hard the first firings hit and which of them miss. A stall also takes the
 * speed over, winding it down from what the engine was turning at, so the gaps start as the engine's own and stretch.
 * Immutable but for its counters, which only the audio thread touches. Pure Java so it can be tested.
 */
final class EngineNoteGesture {

	enum Kind {
		CATCH(1.5, 0, 0, 0, new float[] {1.6f, 0.55f, 1.4f, 1.25f, 1.12f}),
		HARD_CATCH(2.5, 0, 0, 0, new float[] {0.6f, MISS, 1.1f, MISS, 0.45f, 1.7f, MISS, 1.45f, 1.2f}),
		STALL(0, 0.9, 1f, 0.11f, new float[] {1f, 0.85f, MISS, 0.7f, 0.5f, MISS}),
		LUGGED_STALL(0, 1.2, 1.9f, 0.05f, new float[] {1.25f, 1.35f, 1.45f, 1.5f, 1.4f, 1.1f}),
		STARVED_STALL(0, 1.7, 0.55f, 0.07f, new float[] {0.7f, MISS, 0.4f, MISS, MISS, 0.65f, MISS, 0.3f, MISS});

		/** For a catch, the longest it may colour the firings, so a slow engine is not left coughing for many turns. */
		private final double colours;
		/** For a stall, how long the crank takes to stop; 0 for a catch. */
		private final double winds;
		/** How hard each firing in turn hits, as a share of its usual strength; {@link #MISS} does not light. */
		private final float[] firings;
		/** For a stall, how hard the stopped crank fetches up against compression, and how much is left to sigh out. */
		final float slam;
		final float sigh;

		Kind(double colours, double winds, float slam, float sigh, float[] firings) {
			this.colours = colours;
			this.winds = winds;
			this.slam = slam;
			this.sigh = sigh;
			this.firings = firings;
		}

		boolean dies() {
			return winds > 0;
		}

		/** Seconds the crank takes to stop; 0 for a catch. */
		double windSeconds() {
			return winds;
		}
	}

	static final float MISS = 0;
	/** Ticks what the note plays now waits behind the audio already queued, before it is heard. */
	private static final int HEARD_AFTER_TICKS = (int) Math.round(EngineNoteSynth.OUTPUT_LATENCY * 20);
	/** A crank turning slower than this is as good as at rest. */
	private static final float NEARLY_STOPPED_RPM = 8;

	/** For a stall from {@code fromRpm}: ticks from now until its crank is heard to come to rest. */
	static int restTicks(Kind kind, float fromRpm) {
		return HEARD_AFTER_TICKS + (fromRpm < NEARLY_STOPPED_RPM ? 0 : (int) Math.round(kind.winds * 20));
	}

	/** The share of its strength the last firing of a stall has lost. */
	private static final float WEAKENS = 0.5f;

	final Kind kind;
	/** For a stall, the speed it winds down from. */
	private final float fromRpm;
	private long samples;
	private int fired;

	EngineNoteGesture(Kind kind, float fromRpm) {
		this.kind = kind;
		this.fromRpm = Float.isFinite(fromRpm) ? Math.min(Math.abs(fromRpm), 20000) : 0;
	}

	/** Moves on by one sample. */
	void advance() {
		samples++;
	}

	private double seconds() {
		return samples / (double) EngineNoteSynth.SAMPLE_RATE;
	}

	/** Whether it has nothing more to change: a catch that has run its course, or a stall whose crank has stopped. */
	boolean over() {
		return kind.dies() ? seconds() >= kind.winds : fired >= kind.firings.length || fired > 0 && seconds() >= kind.colours;
	}

	/** A stall's speed now: an even slowing from where the engine was to a stop. */
	float rpm() {
		return (float) (fromRpm * Math.max(0, 1 - seconds() / kind.winds));
	}

	/**
	 * How hard the firing happening now hits, as a share of its usual strength, or {@link #MISS}. Counts it, so called
	 * once a firing. A stall also weakens as the crank slows, and repeats its pattern if the engine is fast enough to get
	 * through it.
	 */
	float strength() {
		int index = fired++;
		float share = kind.firings[kind.dies() ? index % kind.firings.length : Math.min(index, kind.firings.length - 1)];
		return kind.dies() ? share * (1 - WEAKENS * (float) Math.min(1, seconds() / kind.winds)) : share;
	}
}
