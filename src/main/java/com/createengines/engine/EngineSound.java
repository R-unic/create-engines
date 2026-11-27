package com.createengines.engine;

import java.util.Arrays;

/**
 * What an engine is built of, as far as it changes how it sounds: worked out on the server from the layout and synced with the
 * summary, so the client's exhaust note can be the build's own (see {@code EngineNoteSynth.Voice}). Only the sound: nothing
 * here changes how the engine runs.
 *
 * @param outlets     for each of the summary's firing angles, in the same order, which exhaust outlet it goes out of, 0 to
 *                    {@link #MOST_OUTLETS} - 1: cylinders whose heads share an exhaust run share an outlet
 * @param pipes       for each outlet, how long its exhaust run is, 0 (a stub) to 1 (a long run)
 * @param compression the cylinders light their charge by compression (no spark plug), which knocks
 * @param precision   share of the cylinders built with precision parts, 0 to 1
 * @param enclosure   share of the cylinders' sides clad in Engine Casing, 0 to 1: a clad engine is heard dulled
 * @param muffles     for each outlet, how much of its gas goes through a muffler, 0 to 1
 * @param rotary      the engine is rotor housings rather than cylinders
 */
public record EngineSound(int[] outlets, float[] pipes, boolean compression, float precision, float enclosure,
	float[] muffles, boolean rotary) {

	/** A piston engine's shape. */
	public EngineSound(int[] outlets, float[] pipes, boolean compression, float precision, float enclosure,
		float[] muffles) {
		this(outlets, pipes, compression, precision, enclosure, muffles, false);
	}

	/** The same shape, of rotor housings or not. */
	public EngineSound asRotary(boolean rotors) {
		return rotors == rotary ? this
			: new EngineSound(outlets, pipes, compression, precision, enclosure, muffles, rotors);
	}

	/** A shape whose outlets say nothing about mufflers: the engine's share of them is heard on all alike. */
	public EngineSound(int[] outlets, float[] pipes, boolean compression, float precision, float enclosure) {
		this(outlets, pipes, compression, precision, enclosure, new float[0]);
	}

	public static final int MOST_OUTLETS = 4;

	/** Nothing known: a summary from before the sound was synced, or a stopped engine. The note is plain. */
	public static final EngineSound NONE = new EngineSound(new int[0], new float[0], false, 1, 0);

	/** Blocks of exhaust run, the opening included, at which a run counts as long. */
	private static final int LONG_RUN = 13;

	/**
	 * How long a run of {@code blocks} blocks sounds, 0 to 1: a lone opening is a stub, thirteen blocks or more a long run.
	 * By how many times longer the run gets, not by blocks added, as pitch is heard: the first pipes on a short run change it
	 * most.
	 */
	public static float pipeLength(int blocks) {
		// In hundredths, as it is synced, so the server and the client hold the same number
		double length = Math.log(Math.max(1, blocks)) / Math.log(LONG_RUN);
		return Math.round(Math.min(1, length) * 100) / 100f;
	}

	/**
	 * Which outlet each cylinder goes out of.
	 *
	 * @param outletOf for each cylinder, its outlet
	 * @param runs     for each outlet, the exhaust run it stands for
	 */
	public record Outlets(int[] outletOf, int[] runs) {
	}

	/**
	 * Gives each exhaust run an outlet, in the order the cylinders first meet them; a run past the last outlet shares it. A
	 * cylinder with no exhaust (-1) is heard through the first.
	 *
	 * @param runOf for each cylinder, the number of its exhaust run, or -1
	 */
	public static Outlets assignOutlets(int[] runOf) {
		int[] outletOf = new int[runOf.length];
		int[] runs = new int[MOST_OUTLETS];
		int used = 0;
		for (int i = 0; i < runOf.length; i++) {
			if (runOf[i] < 0)
				continue;
			int outlet = 0;
			while (outlet < used && runs[outlet] != runOf[i])
				outlet++;
			if (outlet == used) {
				if (used < MOST_OUTLETS)
					runs[used++] = runOf[i];
				else
					outlet = MOST_OUTLETS - 1;
			}
			outletOf[i] = outlet;
		}
		return new Outlets(outletOf, Arrays.copyOf(runs, used));
	}

	/**
	 * Sorts the first {@code count} firing angles as {@link Float#compare} orders them, each outlet moving with its firing. A
	 * few dozen at most, so an insertion sort; it allocates nothing.
	 */
	public static void sortFirings(float[] angles, int[] outlets, int count) {
		for (int i = 1; i < count; i++) {
			float angle = angles[i];
			int outlet = outlets[i];
			int j = i - 1;
			while (j >= 0 && Float.compare(angles[j], angle) > 0) {
				angles[j + 1] = angles[j];
				outlets[j + 1] = outlets[j];
				j--;
			}
			angles[j + 1] = angle;
			outlets[j + 1] = outlet;
		}
	}

	/** Whether two shapes would sound the same, so a change need not be synced. */
	public boolean sameAs(EngineSound other) {
		return other != null && compression == other.compression && rotary == other.rotary
			&& Math.abs(precision - other.precision) < 0.05f && Math.abs(enclosure - other.enclosure) < 0.05f
			&& Arrays.equals(outlets, other.outlets) && samePipes(pipes, other.pipes) && samePipes(muffles, other.muffles);
	}

	private static boolean samePipes(float[] a, float[] b) {
		if (a.length != b.length)
			return false;
		for (int i = 0; i < a.length; i++)
			if (Math.abs(a[i] - b[i]) > 0.006f)
				return false;
		return true;
	}

	/** Whether there is anything to sync: a plain engine writes nothing new, so its summary is as it was. */
	public boolean plain() {
		return this == NONE || outlets.length == 0 && !compression && !rotary && enclosure == 0;
	}

	/** As the other {@code held}, with each outlet's muffling too. */
	public static EngineSound held(int[] outlets, int[] pipeHundredths, boolean compression, float precision,
		float enclosure, int[] muffleHundredths) {
		EngineSound shape = held(outlets, pipeHundredths, compression, precision, enclosure);
		float[] muffles = new float[Math.min(muffleHundredths.length, MOST_OUTLETS)];
		for (int i = 0; i < muffles.length; i++)
			muffles[i] = Math.clamp(muffleHundredths[i] / 100f, 0f, 1f);
		return new EngineSound(shape.outlets, shape.pipes, compression, shape.precision, shape.enclosure,
			muffles);
	}

	/** Each outlet's muffling in hundredths, as it is synced. */
	public int[] muffleHundredths() {
		int[] hundredths = new int[muffles.length];
		for (int i = 0; i < muffles.length; i++)
			hundredths[i] = Math.round(muffles[i] * 100);
		return hundredths;
	}

	/**
	 * Each outlet's figure from its cylinders' own, such as their runs' lengths or muffling: their average, in hundredths as
	 * it is synced, so runs that share the last outlet are heard as the mix of them.
	 *
	 * @param outletOf for each cylinder, its outlet
	 * @param values   for each cylinder, its run's figure, or NaN for a cylinder with no exhaust
	 */
	public static float[] perOutlet(int[] outletOf, float[] values, int outlets) {
		float[] sum = new float[outlets];
		int[] count = new int[outlets];
		for (int i = 0; i < outletOf.length; i++)
			if (outletOf[i] < outlets && !Float.isNaN(values[i])) {
				sum[outletOf[i]] += values[i];
				count[outletOf[i]]++;
			}
		for (int o = 0; o < outlets; o++)
			sum[o] = count[o] == 0 ? 0 : Math.round(sum[o] / count[o] * 100) / 100f;
		return sum;
	}

	/** A shape read back from a sync, every value held in range whatever was written. */
	public static EngineSound held(int[] outlets, int[] pipeHundredths, boolean compression, float precision,
		float enclosure) {
		int[] kept = outlets.clone();
		for (int i = 0; i < kept.length; i++)
			kept[i] = Math.clamp(kept[i], 0, MOST_OUTLETS - 1);
		float[] pipes = new float[Math.min(pipeHundredths.length, MOST_OUTLETS)];
		for (int i = 0; i < pipes.length; i++)
			pipes[i] = Math.clamp(pipeHundredths[i] / 100f, 0f, 1f);
		return new EngineSound(kept, pipes, compression, finite(precision),
			finite(enclosure));
	}

	/** Each outlet's run length in hundredths, as it is synced. */
	public int[] pipeHundredths() {
		int[] hundredths = new int[pipes.length];
		for (int i = 0; i < pipes.length; i++)
			hundredths[i] = Math.round(pipes[i] * 100);
		return hundredths;
	}

	private static float finite(float value) {
		return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f;
	}
}
