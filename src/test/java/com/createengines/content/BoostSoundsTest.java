package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.content.effects.Pitches;
import com.createengines.content.effects.VorbisSamples;

/**
 * What the forced-induction sounds are made of: the turbo's whistle, its blow-off takes and the supercharger's whine, read
 * from the recordings and from the live voices an engine's note plays. It cannot say they sound good, only that they are
 * what they are meant to be: tones and not static, the two copies of a loop alike, and a blow-off that is fluttering air.
 */
class BoostSoundsTest {

	private static final Path SOUNDS = Path.of("src/main/resources/assets/create_engines/sounds");
	private static final int RECORDED = 44100;
	private static final int LIVE = EngineNoteSynth.SAMPLE_RATE;
	private static final String[] TAKES = {"turbo_blowoff", "turbo_blowoff2", "turbo_blowoff3"};

	private static float[] recording(String name) throws IOException {
		return VorbisSamples.decode(Files.readAllBytes(SOUNDS.resolve(name + ".ogg")));
	}

	private static float[] live(BoostWhine.Kind kind, float seconds) {
		BoostWhine voice = new BoostWhine(kind, 7);
		float[] out = new float[(int) (seconds * LIVE)];
		for (int i = 0; i < out.length; i++)
			out[i] = voice.next(1);
		return out;
	}

	/** The share of {@code samples}' power between {@code low} and {@code high} hertz. */
	private static double share(float[] samples, int rate, double low, double high) {
		double[] power = spectrum(samples, 0, samples.length);
		double in = 0;
		double all = 0;
		for (int k = 1; k < power.length; k++) {
			double hz = k * rate / (2.0 * power.length);
			all += power[k];
			if (hz >= low && hz < high)
				in += power[k];
		}
		return in / all;
	}

	/** The power spectrum of a stretch, averaged over half-overlapping windows. */
	private static double[] spectrum(float[] samples, int from, int to) {
		int size = 2048;
		double[] power = new double[size / 2];
		for (int start = from; start + size <= to; start += size / 2) {
			double[] real = new double[size];
			double[] imaginary = new double[size];
			for (int i = 0; i < size; i++)
				real[i] = samples[start + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / size));
			transform(real, imaginary);
			for (int k = 0; k < power.length; k++)
				power[k] += real[k] * real[k] + imaginary[k] * imaginary[k];
		}
		return power;
	}

	/** A radix-2 Fourier transform, in place. */
	private static void transform(double[] real, double[] imaginary) {
		int size = real.length;
		for (int i = 1, j = 0; i < size; i++) {
			int bit = size >> 1;
			for (; (j & bit) != 0; bit >>= 1)
				j ^= bit;
			j ^= bit;
			if (i < j) {
				double r = real[i];
				real[i] = real[j];
				real[j] = r;
			}
		}
		for (int length = 2; length <= size; length <<= 1)
			for (int start = 0; start < size; start += length)
				for (int k = 0; k < length / 2; k++) {
					double turn = -2 * Math.PI * k / length;
					int a = start + k;
					int b = a + length / 2;
					double r = real[b] * Math.cos(turn) - imaginary[b] * Math.sin(turn);
					double i = real[b] * Math.sin(turn) + imaginary[b] * Math.cos(turn);
					real[b] = real[a] - r;
					imaginary[b] = imaginary[a] - i;
					real[a] += r;
					imaginary[a] += i;
				}
	}

	@Test
	void theLiveVoicesAreTheRecordings() throws IOException {
		double[] edges = {40, 400, 850, 1150, 1700, 2300, 3500, 6000, 15000};
		for (BoostWhine.Kind kind : BoostWhine.Kind.values()) {
			float[] recorded = recording(kind == BoostWhine.Kind.TURBO ? "turbo_whistle" : "supercharger_whine");
			float[] made = live(kind, 4);
			int compared = 0;
			for (int band = 0; band + 1 < edges.length; band++) {
				double there = share(recorded, RECORDED, edges[band], edges[band + 1]);
				if (there < 0.02)
					continue;
				double here = share(made, LIVE, edges[band], edges[band + 1]);
				assertEquals(0, 10 * Math.log10(here / there), 2.5,
					kind + " from " + edges[band] + " to " + edges[band + 1] + " Hz, dB");
				compared++;
			}
			assertTrue(compared >= 3, kind + " compared only " + compared + " bands");
		}
	}

	/** The whistle keeps a bright top over its tone: the overtones and the wide rush of air. */
	@Test
	void theTurbosHighEndReachesToTheTop() {
		float[] made = live(BoostWhine.Kind.TURBO, 3);
		double high = share(made, LIVE, 3500, LIVE / 2.0);
		assertTrue(high > 0.1, "Only " + high + " of the turbo is above 3.5 kHz");
		double top = share(made, LIVE, 8000, LIVE / 2.0);
		assertTrue(top > 0.012, "Only " + top + " of the turbo is above 8 kHz");
		double whistle = share(made, LIVE, 1900, 2100);
		assertTrue(whistle > 0.4, "Only " + whistle + " of the turbo is at its whistle");
	}

	/** The blower is heard for as long as its shaft turns, so it stays rounded: very little of it is up where it bites. */
	@Test
	void theBlowersWhineIsRounded() {
		float[] made = live(BoostWhine.Kind.SUPERCHARGER, 3);
		double high = share(made, LIVE, 2500, LIVE / 2.0);
		assertTrue(high < 0.03, "The blower has " + high + " of its power above 2.5 kHz");
		double whine = share(made, LIVE, 480, 560);
		assertTrue(whine > 0.25, "Only " + whine + " of the blower is at its lobe tone");
	}

	/** Two tones a few hertz apart beat, which is heard as a wobble: the blower's whine holds steady instead. */
	@Test
	void theBlowersToneDoesNotWobble() throws IOException {
		float[] sound = recording("supercharger_whine");
		// Three turns of the 48 Hz between the tone and its growl, so the growl does not leak into the measure
		int window = RECORDED * 3 / 48;
		double least = Double.MAX_VALUE;
		double most = 0;
		for (int start = 0; start + window <= sound.length; start += window / 2) {
			double real = 0;
			double imaginary = 0;
			for (int i = 0; i < window; i++) {
				double turn = 2 * Math.PI * 520 * (start + i) / RECORDED;
				real += sound[start + i] * Math.cos(turn);
				imaginary += sound[start + i] * Math.sin(turn);
			}
			double level = Math.hypot(real, imaginary);
			least = Math.min(least, level);
			most = Math.max(most, level);
		}
		assertTrue(least > 0.6 * most, "The lobe tone falls to " + least / most + " of its loudest");
	}

	private static double loudness(float[] sound, int start, int length) {
		double sum = 0;
		for (int i = start; i < start + length; i++)
			sum += sound[i] * sound[i];
		return Math.sqrt(sum / length);
	}

	/** Between two ways of being wrong: a thud like a kick drum, with its weight at the bottom, and a thin shaker of hiss. */
	@Test
	void theBlowOffIsFullButHasNoThud() throws IOException {
		for (String take : TAKES) {
			float[] sound = recording(take);
			double thud = share(sound, RECORDED, 0, 200);
			assertTrue(thud < 0.02, take + " has " + thud + " of its power below 200 Hz");
			double body = share(sound, RECORDED, 300, 1500);
			assertTrue(body > 0.45, take + " has only " + body + " of its power in its body, 300 to 1500 Hz");
			double top = share(sound, RECORDED, 1500, RECORDED / 2.0);
			assertTrue(top > 0.25, take + " has only " + top + " of its power above 1.5 kHz, and is not a rush of air");
		}
	}

	/**
	 * Air releasing, not parts striking each other: between one swell of the flutter and the next the stream thins but is
	 * never shut off, and each swell takes a while to come.
	 */
	@Test
	void theFlutterIsOneStreamOfAirThatSwellsSoftly() throws IOException {
		int hop = RECORDED / 250;
		for (String take : TAKES) {
			float[] sound = recording(take);
			List<Double> at = chuffs(sound);
			for (int i = 0; i + 1 < at.size(); i++) {
				int from = (int) Math.round(at.get(i) * RECORDED);
				int to = (int) Math.round(at.get(i + 1) * RECORDED);
				double least = Double.MAX_VALUE;
				int leastAt = from;
				for (int start = from; start + hop <= to; start += hop) {
					double level = loudness(sound, start, hop);
					if (level < least) {
						least = level;
						leastAt = start;
					}
				}
				double smaller = Math.min(loudness(sound, from, hop), loudness(sound, to, hop));
				assertTrue(least > 0.07 * smaller, take + " all but stops after " + at.get(i) + " s: " + least / smaller);
				assertTrue((to - leastAt) / (double) RECORDED >= 0.012,
					take + " strikes at " + at.get(i + 1) + " s instead of swelling");
			}
		}
	}

	/**
	 * The wheel is heard running down: a tone that falls in pitch stands out of the air, and only a little of one, so that
	 * it is not a whistle. The three takes whine alike, so they are looked at together, which evens out the noise.
	 */
	@Test
	void eachBlowOffHasALittleWhineThatFalls() throws IOException {
		int size = 2048;
		for (double moment : new double[] {0.35, 0.5, 0.65}) {
			double[] power = new double[size / 2];
			for (String take : TAKES) {
				int from = (int) (moment * RECORDED) - size / 2;
				double[] one = spectrum(recording(take), from, from + size);
				double all = 0;
				for (double bin : one)
					all += bin;
				for (int k = 0; k < power.length; k++)
					power[k] += one[k] / all / TAKES.length;
			}
			// The pitch the generator gives the wheel then (boost_sounds.py)
			double wheel = 1500 + 1800 * Math.exp(-moment / 0.45);
			double bin = RECORDED / (double) size;
			int peak = (int) (0.9 * wheel / bin);
			for (int k = peak; k * bin < 1.1 * wheel; k++)
				if (power[k] > power[peak])
					peak = k;
			assertEquals(wheel, peak * bin, 0.03 * wheel, "At " + moment + " s the loudest tone near the wheel's");
			int reach = (int) (400 / bin);
			double[] around = java.util.Arrays.copyOfRange(power, peak - reach, peak + reach + 1);
			java.util.Arrays.sort(around);
			double stands = power[peak] / around[reach];
			assertTrue(stands > 5, "At " + moment + " s the whine stands only " + stands + " times over the air");
			double itsShare = 0;
			for (int k = peak - 2; k <= peak + 2; k++)
				itsShare += power[k];
			assertTrue(itsShare < 0.25, "At " + moment + " s the whine is " + itsShare + " of all that is heard");
		}
	}

	/** The compressor surging: separate chuffs of air, one after another, further apart as the wheel runs down. */
	@Test
	void eachBlowOffChuffsSlowerAndSlower() throws IOException {
		for (String take : TAKES) {
			List<Double> at = chuffs(recording(take));
			assertTrue(at.size() >= 6, take + " has chuffs only at " + at);
			// From when every take's flutter is fully formed, and by halves: noise moves any one peak by a few milliseconds,
			// and while the flutter is still forming a swell can be missed
			List<Double> formed = at.stream().filter(when -> when > 0.19).toList();
			int middle = (formed.size() - 1) / 2;
			double first = (formed.get(middle) - formed.get(0)) / middle;
			double last = (formed.get(formed.size() - 1) - formed.get(middle)) / (formed.size() - 1 - middle);
			assertTrue(last > 1.15 * first, take + " does not slow: " + at);
		}
	}

	/** When each chuff peaks, in seconds: a rise of 8 dB or more out of a dip, in the loudness of 4 ms at a time. */
	private static List<Double> chuffs(float[] sound) {
		int hop = RECORDED / 250;
		List<Double> found = new ArrayList<>();
		double dip = Double.MAX_VALUE;
		double peak = 0;
		double peakAt = 0;
		boolean rising = false;
		for (int start = 0; start + hop <= sound.length; start += hop) {
			double sum = 0;
			for (int i = start; i < start + hop; i++)
				sum += sound[i] * sound[i];
			double level = Math.sqrt(sum / hop);
			if (!rising) {
				dip = Math.min(dip, level);
				if (level > 2.5 * dip && level > 0.01) {
					rising = true;
					peak = level;
					peakAt = start / (double) RECORDED;
				}
			} else if (level > peak) {
				peak = level;
				peakAt = start / (double) RECORDED;
			} else if (level < 0.4 * peak) {
				// The crack of the valve opening is not a chuff
				if (peakAt > 0.06)
					found.add(peakAt);
				rising = false;
				dip = level;
			}
		}
		return found;
	}

	@Test
	void theBlowOffHasThreeTakesThatDiffer() throws IOException {
		String sounds = Files.readString(Path.of("src/main/resources/assets/create_engines/sounds.json"));
		for (String take : TAKES)
			assertTrue(sounds.contains("\"create_engines:" + take + "\""), take + " is not played");
		assertTrue(chuffs(recording(TAKES[0])).size() != chuffs(recording(TAKES[1])).size());
		assertTrue(chuffs(recording(TAKES[1])).size() != chuffs(recording(TAKES[2])).size());
	}

	@Test
	void theWhistleClimbsWellOverAnOctaveAsTheWheelSpools() {
		float last = 0;
		for (float spool = 0; spool <= 1.2f; spool += 0.05f) {
			float pitch = Pitches.turboWhistle(spool);
			assertTrue(pitch > last);
			last = pitch;
		}
		assertTrue(Pitches.turboWhistle(1) > 2.1f * Pitches.turboWhistle(0.15f));
		assertEquals(Pitches.turboWhistle(1.2f), Pitches.turboWhistle(5), 0);
		assertTrue(Pitches.turboWhistle(1.2f) <= 2 && Pitches.turboWhistle(0) >= BoostWhine.LOWEST_PITCH);
	}
}
