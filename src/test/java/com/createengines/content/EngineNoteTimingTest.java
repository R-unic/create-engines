package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;
import com.createengines.engine.EngineSound;

/**
 * The note stays in time with a cam seen at uneven moments, each exhaust is muffled on its own, a run's length is heard, and
 * the nearest engines hold the notes. Pure Java.
 */
class EngineNoteTimingTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final int CHUNK = EngineNotePcm.CHUNK;
	private static final double SECONDS = CHUNK / (double) RATE;

	/**
	 * Reads the clock as the game does: four chunks at once when the stream starts, then one at the first tick after each
	 * has played. Returns the clock's worst error, in seconds, over chunks {@code from} to {@code to}.
	 */
	private static double worstAsTheGameReads(EngineNoteClock clock, int from, int to) {
		Random random = new Random(1);
		double worst = 0;
		for (int chunk = 0; chunk < to; chunk++) {
			// Ticks come every twentieth of a second, at a moment that wanders with the frames
			double read = chunk < EngineNoteClock.PRIMED_CHUNKS ? 0
				: (chunk - 3) * 0.05 + random.nextDouble() * 0.017;
			long sample = EngineNotePcm.samplesBefore(chunk);
			double error = Math.abs(clock.heardAt(sample, (long) (read * 1e9)) * 1e-9 - sample / (double) RATE);
			if (chunk >= from)
				worst = Math.max(worst, error);
		}
		return worst;
	}

	/** A throttle step reaches what is heard within the queue and the note's own follow, not four tenths of a second on. */
	@Test
	void aThrottleStepIsHeardWithinTheQueuedTime() {
		float[] angles = {0, 180, 360, 540};
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(600, angles, 1, 0.5f, 1));
		for (int i = 0; i < RATE * 2; i++)
			synth.next();
		synth.set(new Params(1200, angles, 1, 0.5f, 1));
		// The chunks already queued were rendered before the step; it is in the first one rendered after it
		double queued = EngineNoteClock.LEAD;
		double heard = Double.NaN;
		double before = synth.cycleDegrees();
		for (int i = 0; i < RATE; i++) {
			synth.next();
			double now = synth.cycleDegrees();
			double rpm = (now - before + 720) % 720 * RATE / 6;
			before = now;
			if (rpm >= 900) {
				heard = queued + i / (double) RATE;
				break;
			}
		}
		assertTrue(heard < 0.25, "Half of the step was heard after " + heard + " s");
	}

	@Test
	void theClockIsRightFromTheFirstChunkAndStaysRight() {
		double worst = worstAsTheGameReads(new EngineNoteClock(), 0, 2400);
		assertTrue(worst < 0.012, "The clock was out by " + worst + " s");
	}

	@Test
	void theClockStartsAgainAfterAStall() {
		EngineNoteClock clock = new EngineNoteClock();
		worstAsTheGameReads(clock, 0, 20);
		long later = 30_000_000_000L;
		assertEquals(later + (long) (EngineNoteClock.LEAD * 1e9), clock.heardAt(EngineNotePcm.samplesBefore(20), later));
	}

	@Test
	void aNewNoteLocksToTheCamAtTheEnginesFullSpeed() {
		float rpm = 174;
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(rpm, new float[] {0}, 1, 0.5f, 1));
		synth.sight(100, 0);
		synth.applyLock(0);
		// The first sample is heard as it is read, so it belongs where the cam is now, then it runs at full speed
		assertEquals(100, synth.cycleDegrees(), 1);
	}

	@Test
	void sightingsReadAtUnevenMomentsKeepTheCycleSteady() {
		float rpm = 450;
		double perSample = rpm * 6.0 / RATE;
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(rpm, new float[] {0, 180, 360, 540}, 1, 0.5f, 1));
		Random random = new Random(2);
		short[] out = new short[CHUNK];
		double least = Double.MAX_VALUE;
		double most = -Double.MAX_VALUE;
		double first = Double.NaN;
		for (int chunk = 0; chunk < 600; chunk++) {
			double now = chunk * SECONDS + random.nextDouble() * 0.05;
			double seenAt = now - random.nextDouble() * 0.05;
			synth.sight((rpm * 6.0 * seenAt) % 720, (long) (seenAt * 1e9));
			synth.applyLock((long) (now * 1e9));
			for (int i = 0; i < out.length; i++)
				synth.next();
			if (chunk <= 200)
				continue;
			// How far the cycle is from an even run at the engine's speed
			double drift = synth.cycleDegrees() - ((long) (chunk + 1) * CHUNK * perSample) % 720;
			if (Double.isNaN(first))
				first = drift;
			double from = ((drift - first) % 720 + 1080) % 720 - 360;
			least = Math.min(least, from);
			most = Math.max(most, from);
		}
		// A twentieth of a second at this speed is 135 degrees, which is how far the uncorrected readings wander
		assertTrue(most - least < 35, "The cycle wanders by " + (most - least) + " degrees");
	}

	/** Four cylinders in turn from two outlets, one behind the other. */
	private static float[] renderTwoBanks(float[] muffle) {
		Voice voice = new Voice(new int[] {0, 1, 0, 1}, new float[] {0.5f, 0.5f}, Ignition.SPARK, 1, 0, muffle);
		return render(new Params(240, new float[] {0, 180, 360, 540}, 1, 0.6f, 1, 0.5f, 0.5f, 0, voice), 8);
	}

	private static float[] render(Params params, float seconds) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(params);
		for (int i = 0; i < RATE; i++)
			synth.next();
		float[] out = new float[(int) (seconds * RATE)];
		for (int i = 0; i < out.length; i++)
			out[i] = synth.next();
		return out;
	}

	private static double rms(float[] samples) {
		double sum = 0;
		for (float sample : samples)
			sum += sample * sample;
		return Math.sqrt(sum / samples.length);
	}

	/** The level in the stretch after each firing, for the even firings and for the odd ones. */
	private static double[] byBank(float[] samples) {
		// 240 RPM: a cycle takes half a second and a firing comes every eighth of a second
		int firing = RATE / 8;
		double[] sum = new double[2];
		for (int i = 0; i < samples.length; i++)
			sum[(i / firing) & 1] += samples[i] * samples[i];
		return new double[] {Math.sqrt(sum[0]), Math.sqrt(sum[1])};
	}

	private static double apart(double[] banks) {
		return Math.max(banks[0], banks[1]) / Math.min(banks[0], banks[1]);
	}

	@Test
	void aMufflerOnOneBankQuietensThatBankAlone() {
		float[] open = renderTwoBanks(new float[] {0, 0});
		float[] one = renderTwoBanks(new float[] {1, 0});
		float[] both = renderTwoBanks(new float[] {1, 1});
		assertTrue(rms(one) < rms(open) * 0.9, "A muffled bank is quieter: " + rms(one) + " against " + rms(open));
		assertTrue(rms(both) < rms(one) * 0.7, "Both muffled is quieter still: " + rms(both) + " against " + rms(one));
		assertTrue(apart(byBank(one)) > apart(byBank(open)) * 1.4,
			"The muffled bank's firings should stand apart from the open one's: " + apart(byBank(one)) + " against "
				+ apart(byBank(open)));
	}

	@Test
	void aLongRunSoundsUnlikeAShortOne() {
		float[][] notes = new float[2][];
		for (int i = 0; i < 2; i++) {
			Voice voice = new Voice(new int[] {0}, new float[] {i}, Ignition.SPARK, 1);
			notes[i] = render(new Params(300, new float[] {0}, 1, 0.6f, 1, 0.5f, 0.5f, 0, voice), 6);
		}
		double[] crossings = new double[2];
		for (int i = 0; i < 2; i++) {
			// How often the note under a few hundred hertz changes sign: its pitch
			double low = 0;
			double lower = 0;
			double pole = Math.exp(-2 * Math.PI * 400 / RATE);
			double before = 0;
			for (float sample : notes[i]) {
				low = pole * low + (1 - pole) * sample;
				lower = pole * lower + (1 - pole) * low;
				if (lower > 0 != before > 0)
					crossings[i]++;
				before = lower;
			}
		}
		assertTrue(crossings[1] < crossings[0] * 0.85,
			"A long run should sound clearly lower than a stub: " + crossings[1] + " against " + crossings[0]);
	}

	@Test
	void theNoteKeepsStepWithTheDrawnCrankUnlessItIsCapped() {
		assertEquals(448, EngineChorus.noteRpm(452.8f, 448), 1e-3);
		assertEquals(448, EngineChorus.noteRpm(452.8f, -448), 1e-3);
		assertTrue(EngineChorus.followsDrawing(452.8f, 448));
		assertEquals(452.8f, EngineChorus.noteRpm(452.8f, 256), 1e-3, "Create caps the shaft: the engine's own speed is heard");
		assertFalse(EngineChorus.followsDrawing(452.8f, 256));
		assertFalse(EngineChorus.followsDrawing(300, 0));
	}

	@Test
	void aMuchNearerEngineTakesTheNoteAndOneAboutAsFarDoesNot() {
		assertTrue(EngineChorus.shouldYield(30, 4));
		assertFalse(EngineChorus.shouldYield(10, 8));
		assertFalse(EngineChorus.shouldYield(4, 30));
	}

	@Test
	void eachOutletTakesTheMixOfItsCylindersRuns() {
		float[] muffles = EngineSound.perOutlet(new int[] {0, 1, 0, 1, 1}, new float[] {1, 0, 1, 1, Float.NaN}, 2);
		assertEquals(1, muffles[0], 1e-6);
		assertEquals(0.5f, muffles[1], 1e-6);
	}

	@Test
	void eachOutletsMufflingIsSyncedHeldInRangeAndToldApart() {
		EngineSound open = EngineSound.held(new int[] {0, 1}, new int[] {10, 20}, false, 1, 0, new int[] {0, 0});
		EngineSound wild = EngineSound.held(new int[] {0, 1}, new int[] {10, 20}, false, 1, 0, new int[] {900, -5});
		assertEquals(1, wild.muffles()[0], 1e-6);
		assertEquals(0, wild.muffles()[1], 1e-6);
		assertFalse(open.sameAs(wild), "A muffler fitted to one bank is a change to send");
		assertEquals(100, wild.muffleHundredths()[0]);
	}
}
