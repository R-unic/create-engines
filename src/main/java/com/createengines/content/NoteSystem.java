package com.createengines.content;

/**
 * What makes a note one continuous sound and not a row of clicks, as a real exhaust is: set beside recordings, a running
 * engine falls only some 10 dB between firings, where pipes struck one firing at a time fall silent. Two things fill the
 * space. The <i>chambers</i> after the pipes spread each pulse over a few hundredths of a second down paths of unrelated
 * lengths, so no pipe is heard ringing one clean note by itself. The <i>roar</i> is the gas still tearing through the
 * system: uneven low noise that swells with the pulses and is still dying when the next arrives. What is left is then
 * rolled off at the top, as a long exhaust does. Each stream (the open outlets, the muffled ones) has one of its own.
 * Pure Java; the sound thread alone uses it.
 */
final class NoteSystem {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	/** An echo that passes every frequency alike, only later and spread out. */
	private static final class Chamber {
		private static final float SHARE = 0.62f;
		private final float[] line;
		private int at;

		Chamber(double seconds) {
			line = new float[(int) (seconds * RATE)];
		}

		float next(float in) {
			float old = line[at];
			float kept = Denormals.flush(in + SHARE * old);
			line[at] = kept;
			if (++at == line.length)
				at = 0;
			return old - SHARE * kept;
		}

		void clear() {
			java.util.Arrays.fill(line, 0);
		}
	}

	/** How much of the pulse is heard as it left the pipes, and how much by way of the chambers. */
	private static final float DIRECT = 0.8f;
	private static final float SPREAD = 0.45f;

	/** The band most of the roar is in, the higher one that gives it its grain, and how much of that there is. */
	private static final float ROAR_TOP = pole(420);
	private static final float ROAR_FLOOR = pole(70);
	private static final float RASP_TOP = pole(1500);
	private static final float RASP_SHARE = 0.3f;
	/** How fast the roar's strength wavers, and how far: gas does not flow evenly, and an even hiss sounds like one. */
	private static final float FLUTTER = pole(70);
	private static final float FLUTTER_DEPTH = 0.6f;
	/** Brings the filtered noise and its wavering to about the strength of what it follows. */
	private static final float ROAR_GAIN = 2f;
	/** The share of the sound's usual power the roar never falls under, and how long "usual" looks back. */
	private static final float FLOOR_SHARE = 0.1f;
	private static final float USUAL = (float) (1 - Math.exp(-1 / (0.12 * RATE)));
	private static final float FLUTTER_GAIN = 15f;
	/** How many times over the roar's power falls to about a third between two firings, and the times it is held to. */
	private static final double FALLS = 3;
	private static final double SHORTEST = 0.012;
	private static final double LONGEST = 0.2;
	/**
	 * Firings a second up to which they are far apart, and from which they cover the roar between them. Far apart, the roar
	 * is heard bare and its grain and wavering sound like a broken-up recording, so there it is quieter, darker and steadier.
	 */
	private static final float BARE_AT = 10;
	private static final float COVERED_AT = 50;
	private static final float BARE_GAIN = 0.35f;
	private static final float BARE_FLUTTER = 0.15f;
	/** How fast the roar's strength is let move: any faster and it follows each wave of the pulse, which grates. */
	private static final float EASE = (float) (1 - Math.exp(-1 / (0.006 * RATE)));
	/** How fast the roar swells with a pulse. */
	private static final float SWELL = (float) (1 - Math.exp(-1 / (0.004 * RATE)));

	private final Chamber[] chambers = {new Chamber(0.0037), new Chamber(0.0061), new Chamber(0.0103),
		new Chamber(0.0171)};
	private float power;
	private float usual;
	private float strength;
	private float release = 0.998f;
	/** How far the firings cover the roar, 0 (heard bare) to 1. */
	private float covered = 1;
	private float roarTop;
	private float roarFloor;
	private float rasp;
	private float flutter;
	private float topOne;
	private float topTwo;
	private float topAmount = 1;
	private NoteTimbre tuned;
	private float bend = 1;
	/** The roar's band as it is bent: it fills most of what lies between firings, and left still it hid the rest rising. */
	private float roarTopAmount = ROAR_TOP;
	private float roarFloorAmount = ROAR_FLOOR;
	private float raspAmount = RASP_TOP;

	/** Moves the roar and where the top rolls off to {@code times} their usual places ({@link NoteRev}). */
	void bend(float times) {
		if (times != bend) {
			bend = times;
			tuned = null;
			roarTopAmount = pole(420 * times);
			roarFloorAmount = pole(70 * times);
			raspAmount = pole(1500 * times);
		}
	}
	private int random;

	NoteSystem(int seed) {
		random = seed;
	}

	private static float pole(double hz) {
		return (float) (1 - Math.exp(-2 * Math.PI * hz / RATE));
	}

	/** Sets how the roar behaves for firings coming this often. It falls by about the same from one to the next. */
	void pace(float firingsASecond) {
		double apart = Math.clamp(1 / (firingsASecond + 0.01), SHORTEST, LONGEST);
		release = (float) Math.exp(-FALLS / (apart * RATE));
		covered = Math.clamp((firingsASecond - BARE_AT) / (COVERED_AT - BARE_AT), 0f, 1f);
	}

	/** The next sample, from what has left the pipes. */
	float next(float in, NoteTimbre timbre) {
		if (timbre != tuned) {
			tuned = timbre;
			topAmount = pole(timbre.topHz() * bend);
		}
		float spread = in;
		for (Chamber chamber : chambers)
			spread = chamber.next(spread);
		float pulse = in * DIRECT + spread * SPREAD;

		float square = pulse * pulse;
		power = square > power ? power + SWELL * (square - power) : power * release;
		usual += USUAL * (square - usual);
		if (power < Denormals.FLOOR)
			power = 0;
		if (usual < Denormals.FLOOR)
			usual = 0;

		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		float white = random / (float) Integer.MAX_VALUE;
		roarTop += roarTopAmount * (white - roarTop);
		roarFloor += roarFloorAmount * (roarTop - roarFloor);
		rasp += raspAmount * (white - rasp);
		// The same noise read from its other end, so the wavering does not follow the hiss it wavers
		flutter += FLUTTER * ((random << 16) / (float) Integer.MAX_VALUE - flutter);
		float uneven = Math.max(0f,
			1 + (BARE_FLUTTER + (FLUTTER_DEPTH - BARE_FLUTTER) * covered) * FLUTTER_GAIN * flutter);
		float noise = (roarTop - roarFloor + (rasp - roarTop) * RASP_SHARE * covered) * uneven;
		strength = Denormals.flush(strength + EASE * ((float) Math.sqrt(Math.max(power, FLOOR_SHARE * usual)) - strength));
		float roar = noise * strength * ROAR_GAIN * (BARE_GAIN + (1 - BARE_GAIN) * covered) * timbre.roar();

		topOne = Denormals.flush(topOne + topAmount * (pulse + roar - topOne));
		topTwo = Denormals.flush(topTwo + topAmount * (topOne - topTwo));
		return topTwo;
	}

	/** Whether the chambers and the roar have died away. */
	boolean isSilent() {
		return power < 0.01f && usual < 0.01f && strength < 0.1f;
	}

	void clear() {
		for (Chamber chamber : chambers)
			chamber.clear();
		power = usual = strength = roarTop = roarFloor = rasp = flutter = topOne = topTwo = 0;
	}
}
