package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * No shipped recording can clip when the game resamples it: each stays under -1 dBFS between its samples too, not only at
 * them. The generator scripts under {@code tools/} hold them there (docs/SOUND-FIT.md).
 */
class SoundTruePeakTest {

	private static final Path SOUNDS_DIR = Path.of("src/main/resources/assets/create_engines/sounds");
	private static final double CEILING = Math.pow(10, -1 / 20.0);
	/** Points looked at between one sample and the next, and the samples either side each is worked out from. */
	private static final int BETWEEN = 4;
	private static final int REACH = 12;

	private static double[][] filters() {
		double[][] taps = new double[BETWEEN - 1][2 * REACH];
		for (int p = 1; p < BETWEEN; p++)
			for (int k = 0; k < 2 * REACH; k++) {
				double from = k - REACH + 1 - p / (double) BETWEEN;
				double window = 0.5 + 0.5 * Math.cos(Math.PI * from / REACH);
				taps[p - 1][k] = Math.sin(Math.PI * from) / (Math.PI * from) * window;
			}
		return taps;
	}

	private static double truePeak(float[] samples) {
		double[][] taps = filters();
		double most = 0;
		for (int i = 0; i < samples.length; i++) {
			most = Math.max(most, Math.abs(samples[i]));
			for (double[] filter : taps) {
				double sum = 0;
				for (int k = 0; k < filter.length; k++) {
					int at = i + k - REACH + 1;
					if (at >= 0 && at < samples.length)
						sum += samples[at] * filter[k];
				}
				most = Math.max(most, Math.abs(sum));
			}
		}
		return most;
	}

	@Test
	void everyRecordingStaysUnderMinusOneDecibelBetweenItsSamples() throws IOException {
		List<String> over = new ArrayList<>();
		List<Path> recordings;
		try (Stream<Path> found = Files.walk(SOUNDS_DIR)) {
			recordings = found.filter(p -> p.toString().endsWith(".ogg")).sorted().toList();
		}
		for (Path path : recordings) {
			double peak = truePeak(VorbisSamples.decode(Files.readAllBytes(path)));
			if (peak > CEILING)
				over.add(path.getFileName() + " reaches " + peak);
		}
		assertEquals(List.of(), over);
	}
}
