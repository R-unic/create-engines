package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The dry bearings' loop is a warning that can be lived with: once a shriek held near 2.4 kHz, it may not go back to one. */
class BearingSquealSoundTest {

	private static final int RATE = 44100;
	private static final int SIZE = 32768;

	/** The loop's power in each bin of its first three quarters of a second or so. */
	private static double[] spectrum() throws IOException {
		float[] samples = VorbisSamples.decode(Files.readAllBytes(
			Path.of("src/main/resources/assets/create_engines/sounds/bearing_squeal.ogg")));
		double[] re = new double[SIZE];
		double[] im = new double[SIZE];
		for (int i = 0; i < SIZE; i++)
			re[i] = samples[i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / SIZE));
		transform(re, im);
		double[] power = new double[SIZE / 2];
		for (int i = 0; i < power.length; i++)
			power[i] = re[i] * re[i] + im[i] * im[i];
		return power;
	}

	private static void transform(double[] re, double[] im) {
		for (int i = 1, j = 0; i < SIZE; i++) {
			int bit = SIZE >> 1;
			for (; (j & bit) != 0; bit >>= 1)
				j ^= bit;
			j |= bit;
			if (i < j) {
				double held = re[i];
				re[i] = re[j];
				re[j] = held;
			}
		}
		for (int length = 2; length <= SIZE; length <<= 1)
			for (int start = 0; start < SIZE; start += length)
				for (int k = 0; k < length / 2; k++) {
					double angle = -2 * Math.PI * k / length;
					int a = start + k;
					int b = a + length / 2;
					double x = re[b] * Math.cos(angle) - im[b] * Math.sin(angle);
					double y = re[b] * Math.sin(angle) + im[b] * Math.cos(angle);
					re[b] = re[a] - x;
					im[b] = im[a] - y;
					re[a] += x;
					im[a] += y;
				}
	}

	@Test
	void theLoopDoesNotPierceOrHoldOneTone() throws IOException {
		double[] power = spectrum();
		double whole = 0;
		double high = 0;
		int loudest = 1;
		for (int i = 1; i < power.length; i++) {
			whole += power[i];
			if (i * (double) RATE / SIZE > 2000)
				high += power[i];
			if (power[i] > power[loudest])
				loudest = i;
		}
		double line = 0;
		for (int i = Math.max(1, loudest - 4); i <= loudest + 4; i++)
			line += power[i];
		assertTrue(high / whole < 0.1, "share of the squeal above 2 kHz: " + high / whole);
		assertTrue(line / whole < 0.3, "share of the squeal in its strongest tone: " + line / whole);
	}
}
