package com.createengines.content;

/**
 * What is heard of a slow engine between its firings, so that turning over it is one continuous machine: a steady bed (gas
 * moving in the pipes, the rumble of bearings and oil, the mesh of gears and chain) and over it the other strokes of the
 * cycle ({@link NoteStrokes}). Its level hangs from how loud the engine's own firings have been over the last cycle, so it
 * holds however long the gap is, and is as muffled, as far off and as hard-worked as they are. It is steady and dull on
 * purpose: sparse wavering noise was heard as a broken recording. Where firings crowd it is not needed and goes, and it
 * goes with the burning and the turning, so a stall still ends in silence. Pure Java; the sound thread alone uses it.
 */
final class NoteBed {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	/** Firings a second up to which all of the bed is heard, and from which none of it. */
	private static final float ALL_BELOW = 9;
	private static final float NONE_FROM = 24;
	/** The speed from which it is at its full level. */
	private static final float FULL_RPM = 24;
	/** The bed and the strokes, as shares of the loudest hundredth of a second of the last cycle. */
	private static final float BED = 0.13f;
	private static final float BREATH = 0.09f;
	private static final float PUFF = 0.07f;
	private static final float SWELL = 0.07f;
	/** Where the bed's top rolls off beside an open engine, and behind mufflers, casing or distance. */
	private static final float CLEAR = pole(900);
	private static final float DULLED = pole(300);

	private static final float HEARD = pole(16);
	private static final float EASE = pole(8);
	/** The strokes' tables step and a lock may move the cycle: neither is heard as a click. */
	private static final float STROKE_EASE = pole(35);
	/** The gas moves in the pipes, so its band sits round the pipe's note, from and to these many times it. */
	private static final double GAS_TOP = 4;
	private static final double GAS_FLOOR = 0.8;
	private static final float RUMBLE = pole(140);
	/** Brings each filtered noise to a mean square of about one. */
	private static final float GAS_GAIN = 5.5f;
	/** The pipe's note that gain was set for. */
	private static final double MIDDLING_HZ = 140;
	private static final float RUMBLE_GAIN = 12f;
	/** Teeth passing each turn of the crank, two meshes unlike enough not to make a chord. */
	private static final double[] TEETH = {37, 83};
	private static final float MESH_GAIN = 0.7f;

	/** How long the bed stays out of a firing's way. */
	private static final float DUCK_FALL = (float) Math.exp(-1 / (0.02 * RATE));

	private float ducked;
	private float heard;
	private float loudest;
	private float lastLoudest;
	private float level;
	private double tunedTo;
	private float topPole;
	private float floorPole;
	private float gasGain;
	private float gasTop;
	private float gasFloor;
	private float rumbleOne;
	private float rumbleTwo;
	private float breath;
	private float puff;
	private float swell;
	private float toneOne;
	private float toneTwo;
	private final double[] mesh = new double[TEETH.length];
	private int random = 0x51ED270B;

	/** Hertz to the share of the way a one-pole filter moves each sample. */
	private static float pole(double hz) {
		return (float) (1 - Math.exp(-2 * Math.PI * hz / RATE));
	}

	/** The cycle has come round: what was loudest in the one before the last is forgotten. */
	void turned() {
		lastLoudest = loudest;
		loudest = 0;
	}

	/**
	 * @param note   the note's own sample, which sets the level
	 * @param cycle  where in its 720 degree cycle the crank is
	 * @param firings firings a second
	 * @param dulled  how far mufflers, casing or distance take the top off the note, 0 to 1
	 * @return what to add to the note
	 */
	float next(float note, NoteStrokes strokes, double cycle, float rpm, float combustion, float firings, float dulled,
			   double pipeHz) {
		heard = Denormals.flush(heard + HEARD * (note * note - heard));
		loudest = Math.max(loudest, heard);
		float share = Math.clamp((NONE_FROM - firings) / (NONE_FROM - ALL_BELOW), 0f, 1f)
			* Math.min(1f, combustion * 2) * Math.min(1f, rpm / FULL_RPM);
		float wanted = share <= 0 ? 0 : (float) Math.sqrt(Math.max(loudest, lastLoudest)) * share;
		level = Denormals.flush(level + EASE * (wanted - level));
		if (level < 1e-5f)
			return 0;

		if (pipeHz != tunedTo) {
			tunedTo = pipeHz;
			topPole = pole(GAS_TOP * pipeHz);
			floorPole = pole(GAS_FLOOR * pipeHz);
			// A narrower band lets less noise through, made up so a long pipe's bed is as loud as a stub's
			gasGain = GAS_GAIN * (float) Math.sqrt(MIDDLING_HZ / pipeHz);
		}
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		float white = random / (float) Integer.MAX_VALUE;
		gasTop += topPole * (white - gasTop);
		gasFloor += floorPole * (gasTop - gasFloor);
		float gas = (gasTop - gasFloor) * gasGain;
		// The same noise read from its other end, so the rumble is not the hiss again
		rumbleOne += RUMBLE * ((random << 16) / (float) Integer.MAX_VALUE - rumbleOne);
		rumbleTwo += RUMBLE * (rumbleOne - rumbleTwo);
		float rumble = rumbleTwo * RUMBLE_GAIN;
		float gears = 0;
		for (int i = 0; i < TEETH.length; i++) {
			mesh[i] += 2 * Math.PI * TEETH[i] * rpm / 60 / RATE;
			if (mesh[i] > 2 * Math.PI)
				mesh[i] -= 2 * Math.PI;
			gears += NoteMath.sin(mesh[i]);
		}

		breath = Denormals.flush(breath + STROKE_EASE * (strokes.exhaust(cycle) - breath));
		puff = Denormals.flush(puff + STROKE_EASE * (strokes.intake(cycle) - puff));
		swell = Denormals.flush(swell + STROKE_EASE * (strokes.compression(cycle) - swell));
		float bed = (0.85f * gas + 0.18f * rumble + 0.25f * gears * MESH_GAIN) * BED;
		float strokeSound = breath * BREATH * gas + puff * PUFF * 0.5f * (rumble + gas)
			+ swell * SWELL * (0.8f * gas + 0.3f * rumble);
		// While the firing itself is loud there is no gap to fill, and noise added to it would only blur one against the next
		ducked = Denormals.flush(Math.max(Math.abs(note), ducked * DUCK_FALL));
		float gap = Math.max(0f, 1 - ducked / ((float) Math.sqrt(Math.max(loudest, lastLoudest)) + 1e-9f));
		float tone = CLEAR + (DULLED - CLEAR) * dulled;
		toneOne = Denormals.flush(toneOne + tone * ((bed + strokeSound) * level * gap - toneOne));
		toneTwo = Denormals.flush(toneTwo + tone * (toneOne - toneTwo));
		return toneTwo;
	}

	boolean isSilent() {
		return level < 1e-4f;
	}

	void clear() {
		heard = loudest = lastLoudest = level = gasTop = gasFloor = rumbleOne = rumbleTwo = breath = puff = swell = 0;
		toneOne = toneTwo = ducked = 0;
		java.util.Arrays.fill(mesh, 0);
	}
}
