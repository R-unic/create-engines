package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;
import com.createengines.engine.EngineSound;

/**
 * The build shapes the note (the 2.4 sound rework): engines with the same number of cylinders sound different when their banks
 * go out of different outlets, a longer pipe rings lower, a diesel knocks, nothing clicks between firings, and no two firings
 * are alike. Pure synth, no Minecraft.
 */
class EngineNoteVoiceTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	/** A crossplane V8 fires 1-8-4-3-6-5-7-2 every 90 degrees; cylinders 1 to 4 are the left bank, so its banks fire unevenly. */
	private static final int[] CROSSPLANE = {0, 1, 0, 0, 1, 1, 1, 0};
	/** A flat-plane V8 fires the banks in turn. */
	private static final int[] FLATPLANE = {0, 1, 0, 1, 0, 1, 0, 1};

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static Voice voice(int[] outlet, float[] pipes, Ignition ignition, float precision) {
		return new Voice(outlet, pipes, ignition, precision);
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

	private static Params engine(float rpm, float[] angles, float load, float size, float roughness, Voice voice) {
		return new Params(rpm, angles, 1, load, 1, size, 0.5f, roughness, voice);
	}

	private static double rms(float[] samples) {
		double sum = 0;
		for (float sample : samples)
			sum += sample * sample;
		return Math.sqrt(sum / samples.length);
	}

	/** How alike the loudness is at two moments {@code lag} samples apart, -1 to 1. */
	private static double envelopeCorrelation(float[] samples, int lag) {
		double mean = 0;
		for (float sample : samples)
			mean += Math.abs(sample);
		mean /= samples.length;
		double top = 0;
		double bottom = 0;
		for (int i = 0; i + lag < samples.length; i++) {
			double a = Math.abs(samples[i]) - mean;
			double b = Math.abs(samples[i + lag]) - mean;
			top += a * b;
			bottom += a * a;
		}
		return top / bottom;
	}

	/** The level of what is left under {@code cutoff} Hz, through two gentle low-pass stages. */
	private static double lowRms(float[] samples, double cutoff) {
		double pole = Math.exp(-2 * Math.PI * cutoff / RATE);
		double first = 0;
		double second = 0;
		double sum = 0;
		for (float sample : samples) {
			first = pole * first + (1 - pole) * sample;
			second = pole * second + (1 - pole) * first;
			sum += second * second;
		}
		return Math.sqrt(sum / samples.length);
	}

	/** The level of what is above {@code cutoff} Hz. */
	private static double highRms(float[] samples, double cutoff) {
		double pole = Math.exp(-2 * Math.PI * cutoff / RATE);
		double first = 0;
		double second = 0;
		double sum = 0;
		for (float sample : samples) {
			first = pole * first + (1 - pole) * sample;
			second = pole * second + (1 - pole) * first;
			double high = sample - second;
			sum += high * high;
		}
		return Math.sqrt(sum / samples.length);
	}

	@Test
	void aCrossplaneV8WithABankToEachOutletDoesNotRepeatAtEachFiring() {
		// 600 RPM: 40 firings a second. Through one outlet every firing is like the last; with each bank out of its own outlet the
		// banks' uneven rhythm is heard, so the sound repeats only once a cycle
		Voice one = voice(new int[0], new float[0], Ignition.SPARK, 1);
		Voice split = voice(CROSSPLANE, new float[] {0.5f, 0.5f}, Ignition.SPARK, 1);
		int firing = RATE / 40;
		double together = envelopeCorrelation(render(engine(600, even(8), 0.8f, 0.25f, 0, one), 3), firing);
		double apart = envelopeCorrelation(render(engine(600, even(8), 0.8f, 0.25f, 0, split), 3), firing);
		assertTrue(together > apart + 0.05, "Split banks should break up the even rhythm: " + together + " against " + apart);
	}

	@Test
	void aCrossplaneAndAFlatPlaneV8SoundDifferent() {
		// Same cylinders, same speed, same two outlets: only which bank fires when differs
		float[] cross = render(engine(600, even(8), 0.8f, 0.25f, 0,
			voice(CROSSPLANE, new float[] {0.5f, 0.5f}, Ignition.SPARK, 1)), 3);
		float[] flat = render(engine(600, even(8), 0.8f, 0.25f, 0,
			voice(FLATPLANE, new float[] {0.5f, 0.5f}, Ignition.SPARK, 1)), 3);
		// A flat-plane's banks alternate, so it repeats every two firings; a crossplane's do not
		int two = RATE * 2 / 40;
		double flatRepeat = envelopeCorrelation(flat, two);
		double crossRepeat = envelopeCorrelation(cross, two);
		assertTrue(flatRepeat > crossRepeat + 0.03, "A flat-plane V8 repeats every two firings, a crossplane does not: " + flatRepeat
			+ " against " + crossRepeat);
	}

	@Test
	void aLongerPipeAndABiggerCylinderRingLower() {
		assertTrue(EngineNoteSynth.pitch(0.5f, 1) < EngineNoteSynth.pitch(0.5f, 0));
		assertTrue(EngineNoteSynth.pitch(1, 0.5f) < EngineNoteSynth.pitch(0, 0.5f));
		assertEquals(1, EngineNoteSynth.pitch(0.5f, 0.5f), 1e-9, "The middling pipe rings where the note always did");
		float[] longRun = render(engine(900, even(4), 0.8f, 0.5f, 0,
			voice(new int[0], new float[] {1}, Ignition.SPARK, 1)), 3);
		float[] stub = render(engine(900, even(4), 0.8f, 0.5f, 0,
			voice(new int[0], new float[] {0}, Ignition.SPARK, 1)), 3);
		assertTrue(lowRms(longRun, 110) / rms(longRun) > lowRms(stub, 110) / rms(stub),
			"A long run should have more of its sound low down");
	}

	@Test
	void theFirstFewPipesChangeTheNoteClearly() {
		// Each block added to a short run moves it two semitones or more, so a few manifolds are heard, not only a dozen
		for (int blocks = 1; blocks < 4; blocks++)
			assertTrue(semitonesBetween(blocks, blocks + 1) >= 2, "From " + blocks + " blocks to " + (blocks + 1));
		assertTrue(semitonesBetween(1, 13) >= 16, "A stub and a long run should be well over an octave apart");
	}

	/** Taken lower, a long run's boom dropped under what is heard and the note sounded crushed (the author, 2026-11-26). */
	@Test
	void aLongRunStaysHighEnoughToHear() {
		assertTrue(EngineNoteSynth.pitch(0.5f, 1) >= 0.7, "A long run on a middling cylinder: " + EngineNoteSynth.pitch(0.5f, 1));
	}

	private static double semitonesBetween(int shorter, int longer) {
		return 12 * Math.log(EngineNoteSynth.pitch(0.5f, com.createengines.engine.EngineSound.pipeLength(shorter))
			/ EngineNoteSynth.pitch(0.5f, com.createengines.engine.EngineSound.pipeLength(longer))) / Math.log(2);
	}

	@Test
	void aDieselKnocksAndASparkEngineDoesNot() {
		// At idle, where a diesel's knock is loudest
		Voice spark = voice(new int[0], new float[0], Ignition.SPARK, 1);
		Voice diesel = voice(new int[0], new float[0], Ignition.COMPRESSION, 1);
		float[] quiet = render(engine(300, even(4), 0.1f, 0.5f, 0, spark), 3);
		float[] knocking = render(engine(300, even(4), 0.1f, 0.5f, 0, diesel), 3);
		assertTrue(highRms(knocking, 2000) > highRms(quiet, 2000) * 1.3,
			"A diesel should rap above 2 kHz: " + highRms(knocking, 2000) + " against " + highRms(quiet, 2000));
	}

	@Test
	void noTwoFiringsAreAlikeAndARoughEngineLeastOfAll() {
		double smooth = firingSpread(0);
		double rough = firingSpread(0.8f);
		assertTrue(smooth > 0.005, "Even a smooth engine's firings differ a little: " + smooth);
		assertTrue(rough > smooth * 1.5, "A rough engine's firings differ more: " + rough + " against " + smooth);
	}

	/** How much the loudest moment of each firing of a slow single varies from one to the next, as a share of their mean. */
	private static double firingSpread(float roughness) {
		float rpm = 240;
		float[] samples = render(engine(rpm, even(1), 0.8f, 0.5f, roughness, Voice.PLAIN), 6);
		int cycle = Math.round(RATE * 120f / rpm);
		int firings = samples.length / cycle;
		double[] peaks = new double[firings];
		double mean = 0;
		for (int f = 0; f < firings; f++) {
			for (int i = f * cycle; i < (f + 1) * cycle; i++)
				peaks[f] = Math.max(peaks[f], Math.abs(samples[i]));
			mean += peaks[f];
		}
		mean /= firings;
		double spread = 0;
		for (double peak : peaks)
			spread += (peak - mean) * (peak - mean);
		return Math.sqrt(spread / firings) / mean;
	}

	private static float[] renderMuffled(Params params, float muffling) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(params);
		synth.setMuffling(muffling);
		for (int i = 0; i < RATE; i++)
			synth.next();
		float[] out = new float[4 * RATE];
		for (int i = 0; i < out.length; i++)
			out[i] = synth.next();
		return out;
	}

	/** How much the signal changes sample to sample, against its level: higher is brighter. */
	private static double brightness(float[] samples) {
		double sum = 0;
		for (int i = 1; i < samples.length; i++) {
			double step = samples[i] - samples[i - 1];
			sum += step * step;
		}
		return Math.sqrt(sum / samples.length) / rms(samples);
	}

	@Test
	void aMuffledExhaustIsQuieterAndDullerAndMoreSoTheMoreGoesThrough() {
		for (float rpm : new float[] {80, 900, 3000}) {
			Params params = engine(rpm, even(4), 0.7f, 0.5f, 0, Voice.PLAIN);
			float[] open = renderMuffled(params, 0);
			float[] half = renderMuffled(params, 0.5f);
			float[] full = renderMuffled(params, 1);
			assertTrue(rms(half) < rms(open) && rms(full) < rms(half), "quieter at " + rpm);
			assertTrue(rms(full) < 0.5 * rms(open), "far quieter fully muffled at " + rpm + ": " + rms(full) / rms(open));
			assertTrue(brightness(full) < 0.8 * brightness(open),
				"duller at " + rpm + ": " + brightness(full) / brightness(open));
		}
	}

	/** At the recorded sounds' muffled volume, a fifth, a slow single could not be heard standing beside it. */
	@Test
	void aFullyMuffledSlowSingleCanStillBeHeard() {
		Params params = engine(173, new float[] {0}, 0.2f, 1, 0, Voice.PLAIN);
		double share = rms(renderMuffled(params, 1)) / rms(renderMuffled(params, 0));
		assertTrue(share > 0.35, "a muffled single keeps this much of its level: " + share);
	}

	/** The cam turns steadily, so any variation in the gaps between a single's firings is the note's own. */
	@Test
	void aRoughIdlingSingleStaysInTime() {
		float[] out = render(engine(64, new float[] {0}, 0, 1, 1,
			voice(new int[1], new float[] {0}, Ignition.SPARK, 0)), 40);
		float peak = 0;
		for (float sample : out)
			peak = Math.max(peak, Math.abs(sample));
		double cycle = RATE * 120.0 / 64;
		int last = -1;
		int heard = 0;
		for (int i = 0; i < out.length; i++) {
			if (Math.abs(out[i]) < 0.2f * peak || last >= 0 && i - last < cycle / 2)
				continue;
			if (last >= 0)
				assertEquals(cycle, i - last, 0.011 * RATE, "the gap before the firing at sample " + i);
			last = i;
			heard++;
		}
		assertTrue(heard >= 20, "firings heard: " + heard);
	}

	/** A sync brings the same firings in a new array; one still waiting its turn must not be skipped. */
	@Test
	void aSyncEveryFewSamplesDropsNoFiring() {
		EngineNoteSynth synced = new EngineNoteSynth(7);
		EngineNoteSynth left = new EngineNoteSynth(7);
		synced.set(engine(300, even(1), 0, 0.5f, 1, Voice.PLAIN));
		left.set(engine(300, even(1), 0, 0.5f, 1, Voice.PLAIN));
		for (int i = 0; i < 10 * RATE; i++) {
			if (i % 20 == 0)
				synced.set(engine(300, even(1), 0, 0.5f, 1, Voice.PLAIN));
			assertEquals(left.next(), synced.next(), 1e-6f, "sample " + i);
		}
	}

	@Test
	void aCladEngineIsHeardDulled() {
		Voice open = new Voice(new int[0], new float[0], Ignition.SPARK, 0.5f, 0);
		Voice clad = new Voice(new int[0], new float[0], Ignition.SPARK, 0.5f, 1);
		float[] bare = render(engine(300, even(4), 0.7f, 0.5f, 0, open), 3);
		float[] cased = render(engine(300, even(4), 0.7f, 0.5f, 0, clad), 3);
		assertTrue(highRms(cased, 2000) / rms(cased) < highRms(bare, 2000) / rms(bare) * 0.7,
			"Casing takes the top off: " + highRms(cased, 2000) / rms(cased) + " against " + highRms(bare, 2000) / rms(bare));
	}

	@Test
	void farOffOnlyTheBoomCarries() {
		Voice voice = voice(new int[0], new float[0], Ignition.COMPRESSION, 0.3f);
		float[] near = render(engine(300, even(4), 0.4f, 0.5f, 0, voice), 3);
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(engine(300, even(4), 0.4f, 0.5f, 0, voice));
		synth.setDistance(1);
		for (int i = 0; i < RATE * 2; i++)
			synth.next();
		float[] far = new float[RATE * 3];
		for (int i = 0; i < far.length; i++)
			far[i] = synth.next();
		assertTrue(highRms(far, 2000) / rms(far) < highRms(near, 2000) / rms(near) * 0.6,
			"Far off the knock fades first: " + highRms(far, 2000) / rms(far) + " against " + highRms(near, 2000) / rms(near));
	}

	@Test
	void theServersWordForTheBuildBecomesTheSynthsVoice() {
		Voice made = Voice.of(new EngineSound(new int[] {0, 1}, new float[] {0.25f, 0.75f}, false, 0.4f, 0.6f));
		assertEquals(Ignition.SPARK, made.ignition());
		assertArrayEquals(new int[] {0, 1}, made.outlet());
		assertArrayEquals(new float[] {0.25f, 0.75f}, made.pipeLength());
		assertEquals(0.4f, made.precision());
		assertEquals(0.6f, made.enclosure());
		assertEquals(Ignition.COMPRESSION,
			Voice.of(new EngineSound(new int[0], new float[0], true, 1, 0)).ignition());
		assertSame(Voice.PLAIN, Voice.of(EngineSound.NONE), "An older world's engine has the plain note");
		assertSame(Voice.PLAIN, Voice.of(null));
	}

	/** The valve sounds are gone (author, 2026-11-26): between a slow single's firings there is no click, however crude. */
	@Test
	void nothingClicksBetweenTheFirings() {
		float rpm = 240;
		float[] samples = render(engine(rpm, even(1), 0.2f, 0.5f, 0,
			voice(new int[0], new float[0], Ignition.SPARK, 0)), 6);
		int cycle = Math.round(RATE * 120f / rpm);
		double pole = Math.exp(-2 * Math.PI * 2000 / RATE);
		double first = 0;
		double second = 0;
		double before = 0;
		double seating = 0;
		for (int i = 0; i < samples.length; i++) {
			first = pole * first + (1 - pole) * samples[i];
			second = pole * second + (1 - pole) * first;
			double high = samples[i] - second;
			double degrees = (i % cycle) * 720.0 / cycle;
			// A valve used to seat 470 degrees after the firing: as many degrees either side of it
			if (degrees >= 410 && degrees < 470)
				before += high * high;
			else if (degrees >= 470 && degrees < 530)
				seating += high * high;
		}
		assertTrue(seating < before * 1.5, "Nothing starts where a valve seated: " + seating + " against " + before);
	}

	@Test
	void aDieselsKnockIsLoudestAtIdle() {
		double atIdle = knockShareOfTheNote(300);
		double atSpeed = knockShareOfTheNote(2400);
		assertTrue(atIdle > atSpeed * 1.15, "The knock should fade as the diesel speeds up: " + atIdle + " against " + atSpeed);
	}

	/** How much more there is above 2 kHz, for the level of the note, from a diesel than from a spark engine at the same point. */
	private static double knockShareOfTheNote(float rpm) {
		float[] spark = render(engine(rpm, even(4), 0.1f, 0.5f, 0,
			voice(new int[0], new float[0], Ignition.SPARK, 1)), 3);
		float[] diesel = render(engine(rpm, even(4), 0.1f, 0.5f, 0,
			voice(new int[0], new float[0], Ignition.COMPRESSION, 1)), 3);
		return (highRms(diesel, 2000) / rms(diesel)) / (highRms(spark, 2000) / rms(spark));
	}

	/**
	 * A firing is moved a few degrees late, so one at the very end of the cycle is the one at risk of falling off it. Roughest
	 * and most crude, a single firing there is still heard in every cycle.
	 */
	@Test
	void aLateFiringAtTheEndOfTheCycleIsNeverLost() {
		float rpm = 240;
		float[] samples = render(engine(rpm, new float[] {719.9f}, 0, 0.5f, 1,
			voice(new int[0], new float[0], Ignition.SPARK, 0)), 10);
		int cycle = Math.round(RATE * 120f / rpm);
		double[] peaks = new double[samples.length / cycle - 1];
		double loudest = 0;
		// Windows half a cycle off the firing, so each holds one firing whole
		for (int f = 0; f < peaks.length; f++) {
			for (int i = f * cycle + cycle / 2; i < (f + 1) * cycle + cycle / 2 && i < samples.length; i++)
				peaks[f] = Math.max(peaks[f], Math.abs(samples[i]));
			loudest = Math.max(loudest, peaks[f]);
		}
		for (int f = 0; f < peaks.length; f++)
			assertTrue(peaks[f] > loudest * 0.25, "Cycle " + f + " lost its firing: " + peaks[f] + " against " + loudest);
	}

	@Test
	void anOutletListThatDoesNotMatchTheFiringsIsHeardThroughOneOutlet() {
		float[] pipes = {0.2f, 0.9f};
		float[] mismatched = render(engine(600, even(8), 0.8f, 0.25f, 0,
			voice(new int[] {1, 1, 1}, pipes, Ignition.SPARK, 1)), 1);
		float[] one = render(engine(600, even(8), 0.8f, 0.25f, 0,
			voice(new int[0], pipes, Ignition.SPARK, 1)), 1);
		assertArrayEquals(one, mismatched);
	}

	@Test
	void theSameBuildSoundsTheSameEachTime() {
		Params params = engine(500, even(6), 0.3f, 0.35f, 0.4f,
			voice(new int[] {0, 1, 0, 1, 0, 1}, new float[] {0.2f, 0.9f}, Ignition.COMPRESSION, 0.2f));
		float[] first = render(params, 1);
		float[] second = render(params, 1);
		for (int i = 0; i < first.length; i++)
			assertEquals(first[i], second[i]);
	}

	@Test
	void aHostileVoiceStaysFiniteAndInRange() {
		Voice[] hostile = {
			null,
			new Voice(null, null, null, Float.NaN),
			new Voice(new int[] {99, -4}, new float[] {Float.NaN, Float.POSITIVE_INFINITY, -3, 7}, Ignition.COMPRESSION,
				-1),
			new Voice(new int[] {0, 1, 2}, new float[] {0.5f}, Ignition.SPARK, 2),
		};
		for (Voice voice : hostile)
			for (float rpm : new float[] {0, 60, 900, 20000})
				for (float sample : render(new Params(rpm, even(2), 1, 0.7f, 1, 0.5f, 0.5f, Float.NaN, voice), 0.3f)) {
					assertFalse(Float.isNaN(sample), "NaN at " + rpm + " with " + voice);
					assertTrue(Math.abs(sample) <= 1, "Out of range at " + rpm + " with " + voice);
				}
	}

	@Test
	void aFullVoiceStaysAudibleAndNeverDeafeningAcrossTheRange() {
		Voice full = voice(CROSSPLANE, new float[] {0.4f, 0.7f}, Ignition.COMPRESSION, 0);
		double quietest = Double.MAX_VALUE;
		double loudest = 0;
		for (float rpm : new float[] {60, 150, 300, 600, 1200, 2400, 4000}) {
			double level = rms(render(engine(rpm, even(8), 0.8f, 0.25f, 0.3f, full), 1));
			quietest = Math.min(quietest, level);
			loudest = Math.max(loudest, level);
		}
		assertTrue(quietest > 0.01, "Too quiet to hear: " + quietest);
		assertTrue(loudest < 0.6, "Too loud: " + loudest);
		assertTrue(loudest / quietest < 6, "Loudness swings too far with speed: " + quietest + " to " + loudest);
	}

	/**
	 * The reference set the roadmap names, written to {@code build/engine-notes/reference} as WAV files for the author to listen
	 * to beside real recordings: each engine at idle, mid and high speed. Not a check; it costs a moment and proves the files
	 * can be made. The final numbers are the author's, by ear.
	 */
	@Test
	void rendersTheReferenceSet() throws IOException {
		Path folder = Path.of("build", "engine-notes", "reference");
		Files.createDirectories(folder);
		reference(folder, "inline4", even(4), 0.5f, voice(new int[0], new float[0], Ignition.SPARK, 0.5f));
		reference(folder, "inline6", even(6), 0.35f, voice(new int[0], new float[0], Ignition.SPARK, 0.5f));
		reference(folder, "v8_crossplane", even(8), 0.25f,
			voice(CROSSPLANE, new float[] {0.5f, 0.5f}, Ignition.SPARK, 0.5f));
		reference(folder, "v8_flatplane", even(8), 0.25f,
			voice(FLATPLANE, new float[] {0.5f, 0.5f}, Ignition.SPARK, 0.5f));
		// Unequal headers: the two sides' runs differ, which is the flat four's rumble
		reference(folder, "flat4", even(4), 0.5f,
			voice(new int[] {0, 1, 0, 1}, new float[] {0.3f, 0.8f}, Ignition.SPARK, 0.5f));
		reference(folder, "v_twin_45", new float[] {0, 405}, 0.85f,
			voice(new int[0], new float[0], Ignition.SPARK, 0.3f));
		reference(folder, "v12", even(12), 0.1f,
			voice(new int[] {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1}, new float[] {0.6f, 0.6f}, Ignition.SPARK, 0.8f));
		reference(folder, "w16", even(16), 0f,
			voice(new int[] {0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3}, new float[] {0.5f, 0.6f, 0.5f, 0.6f}, Ignition.SPARK,
				1));
		reference(folder, "rotary_twin", new float[] {0, 180, 360, 540}, 0.5f,
			voice(new int[0], new float[] {0.2f}, Ignition.SPARK, 0.5f));
		reference(folder, "diesel_inline6", even(6), 0.45f,
			voice(new int[0], new float[] {0.7f}, Ignition.COMPRESSION, 0.3f));
	}

	private static void reference(Path folder, String name, float[] angles, float size, Voice voice) throws IOException {
		// Idle is lighter and rougher; high speed is under full load
		write(folder.resolve(name + "_idle.wav"), render(engine(160, angles, 0.1f, size, 0.3f, voice), 4));
		write(folder.resolve(name + "_mid.wav"), render(engine(384, angles, 0.6f, size, 0.1f, voice), 4));
		write(folder.resolve(name + "_high.wav"), render(engine(1024, angles, 1f, size, 0, voice), 4));
	}

	private static void write(Path file, float[] samples) throws IOException {
		ByteBuffer data = ByteBuffer.allocate(samples.length * 2)
			.order(ByteOrder.LITTLE_ENDIAN);
		for (float sample : samples)
			data.putShort((short) Math.round(Math.clamp(sample, -1f, 1f) * 30000));
		ByteBuffer header = ByteBuffer.allocate(44)
			.order(ByteOrder.LITTLE_ENDIAN);
		header.put("RIFF".getBytes())
			.putInt(36 + data.capacity())
			.put("WAVEfmt ".getBytes())
			.putInt(16)
			.putShort((short) 1)
			.putShort((short) 1)
			.putInt(RATE)
			.putInt(RATE * 2)
			.putShort((short) 2)
			.putShort((short) 16)
			.put("data".getBytes())
			.putInt(data.capacity());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(header.array());
		out.write(data.array());
		Files.write(file, out.toByteArray());
	}
}
