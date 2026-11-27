package com.createengines.content;

/**
 * The low boom of one exhaust outlet's whole system: a slow resonance well under its pipe's note, which each firing sets
 * going and which is still going when the next lands, so a big engine rumbles instead of thudding one firing at a time.
 * How much of it a build has is {@link EngineNoteVoicing#depth}. Pure Java; the sound thread alone uses it.
 */
final class NoteBoom {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	/** Seconds in which the boom falls to about a third: longer than a V8's idling firings are apart on one bank. */
	private static final double RING = 0.07;
	private static final double FALL = Math.exp(-1 / (RING * RATE));
	/** The pressure that sets it going rises over a few thousandths of a second, so no firing clicks. */
	private static final double RISE = Math.exp(-1 / (0.004 * RATE));
	private static final double SMALLEST_HZ = 30;
	private static final double HIGHEST_HZ = 120;

	private double turn;
	private double drive;
	private double impulse;
	private double pulse;
	private double last;
	private double before;

	/** Makes it boom at {@code hz}. */
	void tune(double hz) {
		double radians = 2 * Math.PI * Math.clamp(hz, SMALLEST_HZ, HIGHEST_HZ) / RATE;
		turn = 2 * FALL * Math.cos(radians);
		// One firing of strength 1 swings it to about 1, whatever its note
		drive = Math.sin(radians);
	}

	/** A firing of this strength. */
	void strike(float strength) {
		impulse += strength;
	}

	/** The next sample. */
	float next() {
		pulse = RISE * pulse + (1 - RISE) * impulse;
		impulse = 0;
		double out = turn * last - FALL * FALL * before + drive * pulse;
		// A tail too small to hear would run into subnormal numbers, which cost many times more to add up
		if (Math.abs(out) < 1e-18 && pulse < 1e-18)
			out = pulse = 0;
		before = last;
		last = out;
		return (float) out;
	}

	/** Whether there is nothing in it at all, so its next sample is nothing too: most small engines never strike it. */
	boolean isStill() {
		return impulse == 0 && pulse == 0 && last == 0 && before == 0;
	}

	boolean isSilent() {
		return pulse < 1e-5 && Math.abs(last) < 0.001 && Math.abs(before) < 0.001;
	}

	void clear() {
		impulse = pulse = last = before = 0;
	}
}
