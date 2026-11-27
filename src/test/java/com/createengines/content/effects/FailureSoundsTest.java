package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The wear mode's failures are metal and gas, not whistles: sweeping and held tones made the first recordings sound like a
 * cartoon (a theremin for a dry piston, a slide whistle for a dead turbo). This cannot tell whether a recording is good,
 * only that it has not gone back to being mostly a few clean tones.
 */
class FailureSoundsTest {

	private static final Path SOUNDS = Path.of("src/main/resources/assets/create_engines/sounds");
	private static final List<String> FAILURES =
		List.of("rod_thrown", "gasket_blow", "seize_warning", "piston_seize", "apex_fail", "head_warp", "turbo_fail");

	private static final int FRAME = 2048;
	/** A frame is tonal when its three strongest peaks, five bins each, hold this much of it. */
	private static final double TONAL = 0.7;
	/** The whistling takes were tonal in over nine frames in ten; these are in under three. */
	private static final double MOST_TONAL_FRAMES = 0.5;

	@Test
	void noFailureIsMostlyAWhistle() throws IOException {
		List<String> problems = new ArrayList<>();
		for (String failure : FAILURES) {
			for (int take = 1; take <= 2; take++) {
				String name = failure + take;
				double share = tonalShare(VorbisSamples.decode(Files.readAllBytes(SOUNDS.resolve(name + ".ogg"))));
				if (share > MOST_TONAL_FRAMES)
					problems.add(name + " is a few clean tones in " + Math.round(100 * share) + "% of its frames");
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void theMeasureTellsASweepFromNoise() {
		float[] sweep = new float[44100];
		float[] noise = new float[44100];
		java.util.Random random = new java.util.Random(1);
		double phase = 0;
		for (int i = 0; i < sweep.length; i++) {
			phase += 2 * Math.PI * (1500 + 1500.0 * i / sweep.length) / 44100;
			sweep[i] = (float) Math.sin(phase);
			noise[i] = (float) random.nextGaussian() * 0.2f;
		}
		assertTrue(tonalShare(sweep) > 0.9);
		assertTrue(tonalShare(noise) < 0.1);
	}

	/** The share of the audible frames that are tonal, judged from 500 Hz to 9 kHz, where a whistle sits. */
	private static double tonalShare(float[] samples) {
		int low = 500 * FRAME / 44100;
		int high = 9000 * FRAME / 44100;
		List<double[]> frames = new ArrayList<>();
		double loudest = 0;
		for (int start = 0; start + FRAME < samples.length; start += FRAME / 2) {
			double[] power = Arrays.copyOfRange(power(samples, start), low, high);
			frames.add(power);
			loudest = Math.max(loudest, Arrays.stream(power).sum());
		}
		int heard = 0;
		int tonal = 0;
		for (double[] power : frames) {
			double total = Arrays.stream(power).sum();
			// Thirty decibels under the loudest frame is a tail, not what the sound is heard as
			if (total <= loudest * 1e-3)
				continue;
			heard++;
			double held = 0;
			for (int peak = 0; peak < 3; peak++) {
				int at = 0;
				for (int i = 1; i < power.length; i++)
					if (power[i] > power[at])
						at = i;
				for (int i = Math.max(0, at - 2); i < Math.min(power.length, at + 3); i++) {
					held += power[i];
					power[i] = 0;
				}
			}
			if (held > TONAL * total)
				tonal++;
		}
		return heard == 0 ? 0 : tonal / (double) heard;
	}

	/** The power spectrum of one Hann-windowed frame (radix-2 FFT). */
	private static double[] power(float[] samples, int start) {
		double[] re = new double[FRAME];
		double[] im = new double[FRAME];
		for (int i = 0; i < FRAME; i++) {
			int reversed = Integer.reverse(i) >>> (32 - Integer.numberOfTrailingZeros(FRAME));
			re[reversed] = samples[start + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME));
		}
		for (int size = 2; size <= FRAME; size *= 2) {
			for (int block = 0; block < FRAME; block += size) {
				for (int k = 0; k < size / 2; k++) {
					double angle = -2 * Math.PI * k / size;
					double c = Math.cos(angle);
					double s = Math.sin(angle);
					int a = block + k;
					int b = a + size / 2;
					double x = re[b] * c - im[b] * s;
					double y = re[b] * s + im[b] * c;
					re[b] = re[a] - x;
					im[b] = im[a] - y;
					re[a] += x;
					im[a] += y;
				}
			}
		}
		double[] power = new double[FRAME / 2];
		for (int i = 0; i < power.length; i++)
			power[i] = re[i] * re[i] + im[i] * im[i];
		return power;
	}
}
