package com.createengines.content;

import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineSummary;

/**
 * Hands one {@link EngineNoteSynth} what its engine is doing each tick, aimed ahead by the time the audio waits to be
 * heard ({@link NoteLookahead}), and where its cam was seen. One for each synthesizer, a note's or the room voice's.
 * Client thread only.
 */
final class EngineNoteDrive {

	/** About how long the synthesizer takes to follow what it is told, in seconds: on top of the queue. */
	private static final double FOLLOWS = 0.09;
	private static final double AHEAD = EngineNoteSynth.OUTPUT_LATENCY + FOLLOWS;
	/** The furthest the speed (as a share of itself) and the load are carried past what the engine says. */
	private static final float MOST_RPM = 0.15f;
	private static final float MOST_LOAD = 0.15f;

	private final NoteLookahead rpm = new NoteLookahead();
	private final NoteLookahead load = new NoteLookahead();

	/** What the synthesizer should be told for {@code now}, were it heard {@link #AHEAD} later. */
	EngineNoteSynth.Params ahead(EngineNoteSynth.Params now, long nowNanos) {
		if (now.combustion() <= 0 || now.rpm() <= 1)
			return now;
		float speed = rpm.ahead(now.rpm(), nowNanos, AHEAD, MOST_RPM * now.rpm());
		float worked = load.ahead(now.load(), nowNanos, AHEAD, MOST_LOAD);
		return now.atRpm(Math.max(1, speed))
			.atLoad(Math.clamp(worked, 0, 1.3f));
	}

	/** What the synthesizer is to play for this engine now, and where its cam was seen, if the note can keep step with it. */
	void drive(EngineNoteSynth synth, CrankshaftBlockEntity crank, EngineSummary summary, double phase, long seenAt) {
		EngineNoteSynth.Params wanted = EngineNoteSound.params(summary);
		float drawn = crank.getTheoreticalSpeed();
		synth.set(ahead(wanted.atRpm(EngineChorus.noteRpm(summary.rpm(), drawn)), System.nanoTime()));
		synth.setMuffling(summary.muffling());
		if (!Double.isNaN(phase) && EngineChorus.followsDrawing(summary.rpm(), drawn))
			synth.sight(phase, seenAt);
	}
}
