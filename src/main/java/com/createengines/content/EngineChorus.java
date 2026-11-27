package com.createengines.content;

/**
 * The rules that keep a build full of engines bearable to stand in (the 2.4 sound rework, "Easy to live with in a build"). The
 * nearest few engines each have their own note; the next few are mixed into one shared "engine room" voice
 * ({@link EngineRoomMix}); the rest are only counted, as a fuller room. All of them together are never more than a few
 * decibels louder than the loudest alone ({@link #ceiling}), and an engine held steady settles a little quieter ({@link Settle}).
 * Pure Java, so it is unit tested; the client's {@code EngineNoteSound} applies it.
 */
public final class EngineChorus {

	/** Engines that each get a streamed note of their own. */
	public static final int NOTE_VOICES = 3;
	/** Engines mixed into the shared voice past those: each costs about as much as a note to render. */
	public static final int ROOM_ENGINES = 3;
	/** All engines together are at most this many times as loud (in amplitude) as the loudest alone: about 4 dB. */
	public static final float CEILING = 1.6f;
	/** Blocks over which an engine fades to nothing, as the note's own sound does (its attenuation distance). */
	public static final double REACH = 32;
	/** The shared voice grows fuller with the engines counted but not rendered, to at most this many times its level. */
	public static final float MOST_FILL = 2f;

	/** How many blocks nearer another engine must be to take a note from one, so two about as far do not swap back and forth. */
	public static final double YIELD_MARGIN = 6;

	private EngineChorus() {
	}

	/** Whether an engine {@code holding} blocks away gives its own note up to one {@code waiting} blocks away. */
	public static boolean shouldYield(double holding, double waiting) {
		return waiting + YIELD_MARGIN < holding;
	}

	/**
	 * The speed the note runs at: the drawn crank's, so its firings keep step with the flashes drawn from it, unless that is
	 * far from the engine's own (Create caps how fast a shaft turns), when the engine's is heard and nothing to keep step with.
	 */
	static float noteRpm(float engineRpm, float drawnRpm) {
		return followsDrawing(engineRpm, drawnRpm) ? Math.abs(drawnRpm) : Math.abs(engineRpm);
	}

	static boolean followsDrawing(float engineRpm, float drawnRpm) {
		return drawnRpm != 0 && Math.abs(Math.abs(drawnRpm) - Math.abs(engineRpm)) <= 0.1f * Math.abs(engineRpm) + 2;
	}

	/** How loud an engine is heard at {@code distance} blocks, 0 to 1, falling off in a straight line as the game's sounds do. */
	public static float reach(double distance) {
		return Double.isFinite(distance) ? (float) Math.clamp(1 - distance / REACH, 0, 1) : 0;
	}

	/**
	 * The share every engine is played at so that, heard together, they are at most {@link #CEILING} times as loud as the
	 * loudest one alone. Engines are different sounds, so their powers add: sixteen equal engines would be four times (12 dB)
	 * as loud as one; with this they are {@link #CEILING} times. 1 when the engines are few or quiet enough already.
	 *
	 * @param levels each engine's own loudness, 0 to 1 (its reach, its settling, its muffler)
	 * @param count  how many of {@code levels} are in use
	 */
	public static float ceiling(float[] levels, int count) {
		double power = 0;
		float loudest = 0;
		for (int i = 0; i < count; i++) {
			float level = Float.isFinite(levels[i]) ? Math.max(0, levels[i]) : 0;
			power += level * level;
			loudest = Math.max(loudest, level);
		}
		if (power <= 0)
			return 1;
		double allowed = CEILING * loudest;
		return (float) Math.min(1, allowed / Math.sqrt(power));
	}

	/**
	 * How much fuller the shared voice is for engines counted but not rendered: as loud as their powers would add, so a crowd of
	 * faint engines far off adds next to nothing and the total stays under the {@link #ceiling} (which counted them too), but
	 * no further than {@link #MOST_FILL}.
	 *
	 * @param renderedPower the summed squares of the levels of the engines mixed into the shared voice
	 * @param countedPower  the same for the engines past those, in earshot
	 */
	public static float fill(double renderedPower, double countedPower) {
		if (!(renderedPower > 0) || !Double.isFinite(renderedPower))
			return 1;
		double counted = Double.isFinite(countedPower) ? Math.max(0, countedPower) : 0;
		return (float) Math.min(MOST_FILL, Math.sqrt((renderedPower + counted) / renderedPower));
	}

	/**
	 * An engine held at one speed and load drifts a few decibels quieter over the first half minute, and comes straight back
	 * up when its speed, load or firing changes: what draws the ear is change, and a running factory should sit in the
	 * background. One per engine, ticked by the client.
	 */
	public static final class Settle {

		/** Where a steady engine settles: about 3 dB down. */
		public static final float SETTLED = 0.7f;
		/** Seconds over which a steady engine settles most of the way. */
		static final float SETTLING = 10;
		/** Seconds in which a change brings it back up. */
		static final float RETURNING = 0.25f;
		/** Seconds over which what counts as steady follows the engine. */
		static final float FOLLOWING = 2;

		private float rpm;
		private float load;
		private float share;
		private float gain = 1;
		private boolean seen;

		/**
		 * Takes the engine's figures {@code seconds} after the last call and returns the share to play it at, {@link #SETTLED}
		 * to 1.
		 */
		public float update(float rpm, float load, float share, float seconds) {
			if (!Float.isFinite(rpm) || !Float.isFinite(load) || !Float.isFinite(share) || !Float.isFinite(seconds) || seconds < 0)
				return gain;
			if (!seen) {
				seen = true;
				this.rpm = rpm;
				this.load = load;
				this.share = share;
				gain = 1;
				return gain;
			}
			boolean changed = Math.abs(rpm - this.rpm) > Math.max(4, 0.03f * Math.abs(this.rpm))
				|| Math.abs(load - this.load) > 0.06f || Math.abs(share - this.share) > 0.05f;
			float follow = Math.min(1, seconds / FOLLOWING);
			this.rpm += (rpm - this.rpm) * follow;
			this.load += (load - this.load) * follow;
			this.share += (share - this.share) * follow;
			if (changed)
				gain += (1 - gain) * Math.min(1, seconds / RETURNING);
			else
				gain += (SETTLED - gain) * Math.min(1, seconds / SETTLING);
			return gain;
		}

		public float gain() {
			return gain;
		}
	}
}
