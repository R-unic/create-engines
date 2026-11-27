package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.effects.Pitches;

/** The one-offs an engine note plays itself: backfires, spits and a stalled crank rocking to rest. */
class NoteBangsTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static EngineNoteSynth running(int seed, float rpm) {
		EngineNoteSynth synth = new EngineNoteSynth(seed);
		synth.set(new EngineNoteSynth.Params(rpm, new float[] {10}, 1, 0.5f, 1));
		for (int i = 0; i < RATE * 2; i++)
			synth.next();
		return synth;
	}

	private static float[] play(NoteBangs bangs, int samples) {
		float[] out = new float[samples];
		for (int i = 0; i < samples; i++)
			out[i] = bangs.next();
		return out;
	}

	private static int firstSound(float[] samples) {
		for (int i = 0; i < samples.length; i++)
			if (Math.abs(samples[i]) > 0.001f)
				return i;
		return -1;
	}

	/** The sample at which a single's cycle next passes its firing, of a twin that is asked for nothing. */
	private static int nextFiring(EngineNoteSynth twin) {
		double before = twin.cycleDegrees();
		for (int i = 0; i < RATE; i++) {
			twin.next();
			double now = twin.cycleDegrees();
			if ((10 - before + 720) % 720 < (now - before + 720) % 720)
				return i;
			before = now;
		}
		return -1;
	}

	@Test
	void aBackfireInTheNoteLandsOnItsFiring() {
		// 1500 rpm: a single fires every 80 ms, inside the longest a bang waits
		EngineNoteSynth plain = running(3, 1500);
		EngineNoteSynth banged = running(3, 1500);
		int firing = nextFiring(running(3, 1500));
		assertTrue(firing > 100, "the check needs a firing a little way off, not " + firing);
		banged.bang(NoteBangs.Kind.POP, 1, 1);
		for (int i = 0; i < firing + 400; i++) {
			float without = plain.next();
			float with = banged.next();
			if (without != with) {
				assertTrue(i >= firing - 1 && i <= firing + 64, "heard at sample " + i + ", the firing is at " + firing);
				return;
			}
		}
		throw new AssertionError("the backfire was never heard");
	}

	@Test
	void aBackfireLeavesTheNoteItselfAsItWas() {
		EngineNoteSynth plain = running(5, 900);
		EngineNoteSynth banged = running(5, 900);
		banged.bang(NoteBangs.Kind.POP, 1, 1);
		for (int i = 0; i < RATE; i++) {
			plain.next();
			banged.next();
		}
		for (int i = 0; i < RATE; i++)
			assertEquals(plain.next(), banged.next(), "once it has rung out, sample " + i);
	}

	@Test
	void withNothingFiringABangGoesAfterAMomentNotNever() {
		NoteBangs bangs = new NoteBangs();
		bangs.ask(NoteBangs.Kind.SPIT, 1, 1);
		assertFalse(bangs.idle(), "waiting counts as busy");
		float[] out = play(bangs, RATE);
		int first = firstSound(out);
		assertTrue(first >= NoteBangs.MOST_WAIT - 1 && first < NoteBangs.MOST_WAIT + 200, "first heard at " + first);
		assertTrue(bangs.idle(), "and rings out");
	}

	/** Of what leaves the mouth, the share below about 300 Hz. */
	private static double lowShare(float[] samples) {
		double held = 0;
		double low = 0;
		double all = 0;
		double share = 1 - Math.exp(-2 * Math.PI * 100.0 / RATE);
		for (float sample : samples) {
			held += share * (sample - held);
			low += held * held;
			all += sample * sample;
		}
		return low / all;
	}

	private static float[] backfireDown(double note) {
		NoteBangs bangs = new NoteBangs();
		bangs.ask(NoteBangs.Kind.POP, 0.3f, 1);
		bangs.fired(note, 0);
		return play(bangs, RATE / 2);
	}

	@Test
	void aBackfireFromALongRunSoundsLikeThatRun() {
		float[] longRun = backfireDown(EngineNoteSynth.pitch(0.5f, 1) * 86);
		float[] stub = backfireDown(EngineNoteSynth.pitch(0.5f, 0) * 86);
		assertTrue(lowShare(longRun) > lowShare(stub) * 1.15, lowShare(longRun) + " against " + lowShare(stub));
	}

	@Test
	void aStalledCrankRocksToRestWithNoGapAndNoOverlap() {
		EngineNoteSynth synth = running(9, 600);
		synth.perform(EngineNoteGesture.Kind.LUGGED_STALL);
		synth.set(EngineNoteSynth.Params.SILENT);
		while (synth.dying()) {
			assertFalse(synth.resting(), "the crank does not rock while it still turns");
			synth.next();
		}
		assertTrue(synth.resting(), "the rocking starts as the winding ends");
		double power = 0;
		int soon = RATE / 20;
		for (int i = 0; i < soon; i++) {
			float sample = synth.next();
			power += sample * sample;
		}
		assertTrue(Math.sqrt(power / soon) > 0.01, "something is heard straight after the crank stops");
		for (int i = 0; i < RATE * 3; i++)
			synth.next();
		assertFalse(synth.resting());
		assertTrue(synth.isSilent());
	}

	@Test
	void anEngineTurnedAgainStopsRocking() {
		EngineNoteSynth synth = running(9, 600);
		synth.perform(EngineNoteGesture.Kind.STALL);
		while (synth.dying())
			synth.next();
		synth.endGesture();
		assertFalse(synth.resting());
	}

	@Test
	void ofTwoBangsWaitingTheStrongerIsKept() {
		NoteBangs both = new NoteBangs();
		both.ask(NoteBangs.Kind.POP, 1, 1);
		both.ask(NoteBangs.Kind.SPIT, 0.2f, 1);
		NoteBangs one = new NoteBangs();
		one.ask(NoteBangs.Kind.POP, 1, 1);
		both.fired(86, 0);
		one.fired(86, 0);
		float[] expected = play(one, RATE / 4);
		float[] got = play(both, RATE / 4);
		for (int i = 0; i < got.length; i++)
			assertEquals(expected[i], got[i]);
	}

	@Test
	void nonsenseIsRefusedAndTheOutputStaysInRange() {
		NoteBangs bangs = new NoteBangs();
		bangs.ask(NoteBangs.Kind.POP, Float.NaN, 1);
		bangs.ask(NoteBangs.Kind.POP, 1, Float.POSITIVE_INFINITY);
		bangs.ask(NoteBangs.Kind.POP, -1, 1);
		assertTrue(bangs.idle());
		for (int n = 0; n < 200; n++) {
			bangs.ask(n % 2 == 0 ? NoteBangs.Kind.POP : NoteBangs.Kind.SPIT, 1e9f, n % 3 == 0 ? 1e-9f : 1e9f);
			bangs.fired(n * 37 % 4000, (n % 5) / 4f);
			if (n % 7 == 0)
				bangs.rest(2, 1);
			for (float sample : play(bangs, 500))
				assertTrue(Float.isFinite(sample) && Math.abs(sample) <= 1);
		}
	}

	@Test
	void aOneOffStraysNoMoreThanThreeHundredthsFromItsPitch() {
		assertEquals(0.97f, Pitches.near(1, 0), 1e-6);
		assertEquals(1, Pitches.near(1, 0.5f), 1e-6);
		assertEquals(1.2875f, Pitches.near(1.25f, 1), 1e-6);
	}
}
