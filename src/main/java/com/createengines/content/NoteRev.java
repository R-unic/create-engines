package com.createengines.content;

/**
 * How far an engine's whole voice has risen in pitch with its speed. Engines here mostly turn at 256 RPM at the most, so
 * even many cylinders fire too few times a second to be heard as a note: what is heard as pitch is where the pipes and
 * resonances ring. Held still, revving only made the same sound come faster. So they all sit lower in a slow engine, rise
 * together as it revs, and fall again as it slows or stalls. The firings themselves keep the crank's time. Pure Java, so
 * it is tested.
 */
final class NoteRev {

	/** At this speed and under, the voice is as low as it goes. */
	static final float LOWEST_RPM = 64;
	/** At Create's usual speed limit the voice is where the build puts it. */
	static final float FULL_RPM = 256;
	/** Past this speed, which only a pack or a setting allows, the voice rises no further. */
	static final float HIGHEST_RPM = 2048;
	/** Semitones the voice rises each time the speed doubles, up to the usual limit. */
	static final double PER_DOUBLING = 4;
	/** The same past it, gentler: by then the firings come fast enough to carry some of the pitch themselves. */
	static final double PAST_PER_DOUBLING = 2;
	/** The rise moves in steps of this share of a semitone, too small to hear, so nothing is retuned for less. */
	private static final double STEPS = 16;

	private NoteRev() {
	}

	/** What the voice's frequencies are multiplied by at {@code rpm}: under 1 below {@link #FULL_RPM}, over 1 above. */
	static float of(float rpm) {
		return of(rpm, FULL_RPM);
	}

	/**
	 * As {@link #of(float)}, for a voice that takes up the rise past the usual limit only from {@code pastFrom} RPM: uneven
	 * banks, which until then are opening up with speed by themselves ({@link NoteOpening}). Raised on top of that, a
	 * crossplane V8 could not be told from a flat-plane.
	 *
	 * @param pastFrom {@link #FULL_RPM} or more
	 */
	static float of(float rpm, float pastFrom) {
		if (!Float.isFinite(rpm))
			return 1;
		float held = Math.clamp(rpm, LOWEST_RPM, HIGHEST_RPM);
		double semitones = 0;
		if (held < FULL_RPM)
			semitones = PER_DOUBLING * doublings(held, FULL_RPM);
		else if (held > pastFrom)
			semitones = PAST_PER_DOUBLING * doublings(held, pastFrom);
		return (float) Math.pow(2, Math.round(semitones * STEPS) / (STEPS * 12));
	}

	private static double doublings(double speed, double from) {
		return Math.log(speed / from) / Math.log(2);
	}
}
