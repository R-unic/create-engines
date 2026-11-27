package com.createengines.content;

/**
 * What lies under an engine note's pipes: a low thump on each firing, a faint mechanical bed that turns with the crank and,
 * where firings run together, a tone at their rate. Pure Java; the sound thread alone uses it.
 */
final class NoteUndertone {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	/** The firing's pressure behind the thump and the tone rises over about a thousandth of a second, never all at once. */
	private static final float RISE = (float) Math.exp(-1 / (0.001 * RATE));
	/** Undoes the smoothing's loss of height, so a smoothed firing counts as much as a sharp one would. */
	private static final double PULSE_GAIN = 1 / (1 - RISE);
	/**
	 * The weight under the grain, on the firing it belongs to: a sine that drops from about 150 Hz to 46 Hz in a few
	 * hundredths of a second, the thump of the pressure, which dies away in about a tenth of a second.
	 */
	private static final double THUMP_LOW = 46;
	private static final double THUMP_DROP = 110;
	private static final float THUMP_SWEEP_FALL = (float) Math.exp(-1 / (0.02 * RATE));
	private static final float THUMP_FALL = (float) Math.exp(-1 / (0.036 * RATE));
	private static final float THUMP_WEIGHT = 0.35f;
	/** The thump swells over a few thousandths of a second: its wave is wherever it happens to be when a firing lands. */
	private static final float THUMP_SWELL = (float) (1 - Math.exp(-1 / (0.004 * RATE)));
	/** The mechanical bed: dull noise that swells twice a turn of the crank, quiet under everything else. */
	private static final float BED_POLE = (float) Math.exp(-2 * Math.PI * 180 / RATE);
	private static final float BED_WEIGHT = 0.02f;
	/** The speed from which the bed is at its full level. */
	private static final float BED_FULL_RPM = 200;
	/**
	 * Above a hundred firings a second the pulses come faster than the pipes ring, and which of them a firing
	 * rate happens to land on decides the loudness: the note would drop away in steps as the engine sped up. So the firings
	 * themselves, softened to below a few hundred hertz, are heard as a tone at the firing rate that grows in as the rate
	 * climbs, and a slow gain follows the level so a fast engine stays as loud as a slow one.
	 */
	private static final float TONE_POLE = (float) Math.exp(-2 * Math.PI * 600 / RATE);
	private static final float TONE_DC_POLE = (float) Math.exp(-2 * Math.PI * 15 / RATE);
	private static final float TONE_WEIGHT = 3f;

	private double impulse;
	private double pulse;
	private float thumpEnvelope;
	private float thumpSweep;
	private float thumpSwelled;
	private float thumpPitch = 1;
	private double thumpPhase;
	private float bedNoise;
	private float bedLow;
	private float toneA;
	private float toneB;
	private float toneDc;

	/** A firing of this strength, lit or not: the pressure behind the thump and the tone. */
	void pressure(float strength) {
		impulse += strength;
	}

	/** A lit firing restarts the thump's drop, at {@code pitch} times its usual pitch. */
	void thump(float pitch) {
		thumpSweep = 1;
		thumpPitch = pitch;
	}

	/**
	 * The next sample of the thump, the bed and the tone together.
	 *
	 * @param noise      white noise, -1 to 1, one new value a sample
	 * @param cycle      where in its 720 degree cycle the crank is
	 * @param pileUp     how far overlapping thumps are thinned
	 * @param toneShare  how far firings have run together into a tone, 0 to 1
	 */
	float next(float noise, double cycle, float rpm, float combustion, float pileUp, float toneShare) {
		// The firing as a pressure that rises and falls, not a single sample
		pulse = RISE * pulse + (1 - RISE) * impulse;
		impulse = 0;
		// Between a slow engine's firings these filters die away into subnormal numbers, which cost many times more to add
		// up, and a float one that gets there stays there. So each is dropped to nothing long after it is too small to hear
		if (pulse < Denormals.FLOOR)
			pulse = 0;
		// Every firing restarts the thump's drop; its phase carries on, so the pitch jumps but the wave never does
		thumpSweep = Denormals.flush(thumpSweep * THUMP_SWEEP_FALL);
		thumpPhase += 2 * Math.PI * (THUMP_LOW + THUMP_DROP * thumpSweep) * thumpPitch / RATE;
		if (thumpPhase > 2 * Math.PI)
			thumpPhase -= 2 * Math.PI;
		thumpEnvelope = thumpEnvelope * THUMP_FALL + (float) (pulse * PULSE_GAIN);
		thumpSwelled += THUMP_SWELL * (thumpEnvelope - thumpSwelled);
		if (thumpEnvelope < Denormals.FLOOR && thumpSwelled < Denormals.FLOOR)
			thumpEnvelope = thumpSwelled = 0;
		// Thumps that overlap pile up, so a fast engine's are thinned until it hums instead of roaring
		float thump = NoteMath.sin(thumpPhase) * thumpSwelled * THUMP_WEIGHT * pileUp;

		bedNoise = BED_POLE * bedNoise + (1 - BED_POLE) * noise;
		bedLow = BED_POLE * bedLow + (1 - BED_POLE) * bedNoise;
		float bed = 0;
		if (combustion > 0.001f)
			bed = bedLow * (1 + 0.6f * NoteMath.sin(cycle * Math.PI / 180)) * BED_WEIGHT * combustion
				* Math.min(1f, rpm / BED_FULL_RPM);

		toneA = TONE_POLE * toneA + (1 - TONE_POLE) * (float) (pulse * PULSE_GAIN);
		toneB = TONE_POLE * toneB + (1 - TONE_POLE) * toneA;
		// Together, as a firing's rise reaches the first before the second has left nothing
		if (toneA < Denormals.FLOOR && toneB < Denormals.FLOOR)
			toneA = toneB = 0;
		toneDc = Denormals.flush(TONE_DC_POLE * toneDc + (1 - TONE_DC_POLE) * toneB);
		float tone = (toneB - toneDc) * TONE_WEIGHT * toneShare;
		return thump + bed + tone;
	}

	/** Whether the last firing's pressure and thump have died away. */
	boolean isSilent() {
		return pulse < 0.001 && thumpEnvelope < 0.001f;
	}

	void clear() {
		impulse = pulse = 0;
		thumpEnvelope = thumpSweep = thumpSwelled = bedNoise = bedLow = toneA = toneB = toneDc = 0;
		thumpPhase = 0;
	}
}
