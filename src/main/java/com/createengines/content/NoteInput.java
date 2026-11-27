package com.createengines.content;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;

/**
 * What an engine note is willing to follow, and what follows from it that the game thread can work out ahead, so the
 * sound thread allocates nothing. Pure Java, so it is tested through the synthesizer.
 */
final class NoteInput {

	private static final double CYCLE = 720;

	private NoteInput() {
	}

	/**
	 * What the synthesizer is willing to follow. A NaN or infinity from the engine would poison the filters and the phase
	 * for good, leaving that engine mute until the world reloads, so anything unusable becomes silence or a sane limit.
	 */
	static Params clean(Params p) {
		float[] angles = p.firingAngles() == null ? new float[0] : p.firingAngles();
		for (float angle : angles)
			if (!Float.isFinite(angle) || angle < 0 || angle >= CYCLE)
				return Params.SILENT;
		return new Params(finite(p.rpm(), 0, 20000), angles, finite(p.firingShare(), 0, 1), finite(p.load(), 0, 1.3f),
			finite(p.combustion(), 0, 1), finite(p.size(), 0, 1), finite(p.sharpness(), 0, 1),
			finite(p.roughness(), 0, 1), clean(p.voice(), angles.length));
	}

	/** A voice the synthesizer can follow: an outlet for every firing, each within range, and sane lengths. */
	private static Voice clean(Voice voice, int firings) {
		if (voice == null)
			return Voice.PLAIN;
		int most = EngineNoteSynth.MOST_OUTLETS;
		int[] outlet = new int[firings];
		if (voice.outlet() != null && voice.outlet().length == firings)
			for (int i = 0; i < firings; i++)
				outlet[i] = Math.clamp(voice.outlet()[i], 0, most - 1);
		float[] pipes = new float[most];
		for (int o = 0; o < most; o++)
			pipes[o] = voice.pipeLength() != null && o < voice.pipeLength().length ? finite(voice.pipeLength()[o], 0, 1)
				: 0.5f;
		return new Voice(outlet, pipes, voice.ignition() == null ? Ignition.SPARK : voice.ignition(),
			finite(voice.precision(), 0, 1), finite(voice.enclosure(), 0, 1), muffles(voice.muffle()), voice.rotary());
	}

	private static float[] muffles(float[] wanted) {
		if (wanted == null || wanted.length == 0)
			return new float[0];
		float[] muffles = new float[EngineNoteSynth.MOST_OUTLETS];
		for (int o = 0; o < muffles.length; o++)
			muffles[o] = o < wanted.length ? finite(wanted[o], 0, 1) : 0;
		return muffles;
	}

	static float finite(float value, float min, float max) {
		return Float.isFinite(value) ? Math.clamp(value, min, max) : 0;
	}

	/** The fewest degrees between two firings, going round the cycle. */
	static float closest(float[] angles) {
		if (angles.length < 2)
			return (float) CYCLE;
		float least = (float) CYCLE;
		for (int i = 0; i < angles.length; i++) {
			float next = i + 1 < angles.length ? angles[i + 1] : angles[0] + (float) CYCLE;
			least = Math.min(least, next - angles[i]);
		}
		return least;
	}

	/** How far apart the longest and the shortest of those runs are, 0 to 1. */
	static float unequalRuns(Voice voice, int outlets) {
		float shortest = 1;
		float longest = 0;
		for (int o = 0; o < outlets; o++) {
			shortest = Math.min(shortest, voice.pipe(o));
			longest = Math.max(longest, voice.pipe(o));
		}
		return Math.max(0, longest - shortest);
	}

	/** The mean length of the runs a voice's outlets ring, 0 to 1. */
	static float meanRun(Voice voice, int outlets) {
		float sum = 0;
		for (int o = 0; o < outlets; o++)
			sum += voice.pipe(o);
		return sum / outlets;
	}
}
