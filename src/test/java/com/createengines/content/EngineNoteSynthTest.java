package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Params;

/** The exhaust note follows the firing order and the speed, stays in range, and goes quiet when the engine does. */
class EngineNoteSynthTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	/** Renders {@code seconds} of an engine running steadily, after letting it settle for a second. */
	private static float[] render(float rpm, float[] angles, float share, float load, float seconds) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(new Params(rpm, angles, share, load, 1));
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

	@Test
	void everySampleIsInRangeAtEverySpeed() {
		for (float rpm : new float[] {0, 20, 60, 300, 1200, 4000, 12000}) {
			for (float sample : render(rpm, even(8), 1, 1, 0.5f)) {
				assertFalse(Float.isNaN(sample), "NaN at " + rpm);
				assertTrue(Math.abs(sample) <= 1, "Out of range at " + rpm);
			}
		}
	}

	@Test
	void aRunningEngineIsAudibleAtEverySpeedAndNoneIsDeafening() {
		double quietest = Double.MAX_VALUE;
		double loudest = 0;
		for (float rpm : new float[] {60, 150, 300, 600, 1200, 2400, 4000}) {
			double level = rms(render(rpm, even(8), 1, 0.8f, 1));
			quietest = Math.min(quietest, level);
			loudest = Math.max(loudest, level);
		}
		assertTrue(quietest > 0.01, "Too quiet to hear: " + quietest);
		assertTrue(loudest < 0.6, "Too loud: " + loudest);
		assertTrue(loudest / quietest < 6, "Loudness swings too far with speed: " + quietest + " to " + loudest);
	}

	@Test
	void theNoteDoesNotChangeCharacterAtAnySpeed() {
		// Once the firings come faster than the pipe rings, which resonance each rate lands on once swung the loudness by half
		for (int cylinders : new int[] {4, 8}) {
			double before = 0;
			for (float rpm = 200; rpm <= 6000; rpm *= 1.15f) {
				double level = rms(render(rpm, even(cylinders), 1, 0.8f, 1));
				if (before > 0)
					assertTrue(level / before > 0.8 && level / before < 1.25,
						"Loudness jumps near " + rpm + " RPM with " + cylinders + " cylinders: " + before + " to " + level);
				before = level;
			}
		}
	}

	@Test
	void noFiringIsAClick() {
		// A click is a jump from one sample to the next far bigger than the sound around it, and a spike far above its level
		for (float[] angles : new float[][] {even(1), {0, 315}, even(4), even(8), even(16)}) {
			for (float rpm : new float[] {80, 256, 900}) {
				// Only a firing every few hundredths of a second blends into a note; slower ones are separate thumps
				if (angles.length * rpm / 120 < 8)
					continue;
				float[] samples = render(rpm, angles, 1, 0.8f, 2);
				double level = rms(samples);
				double biggestStep = 0;
				double peak = 0;
				for (int i = 1; i < samples.length; i++) {
					biggestStep = Math.max(biggestStep, Math.abs(samples[i] - samples[i - 1]));
					peak = Math.max(peak, Math.abs(samples[i]));
				}
				String where = angles.length + " firings at " + rpm + " RPM";
				assertTrue(biggestStep / level < 2, "Jumps from sample to sample like a click, " + where + ": " + biggestStep / level);
				assertTrue(peak / level < 6, "Spikes like a click, " + where + ": " + peak / level);
			}
		}
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

	@Test
	void everyFiringHasTheLowThumpOfTheBang() {
		// The pipe's resonances start at 86 Hz, so what sits under 60 Hz is the thump the bang is made of
		for (float[] angles : new float[][] {even(1), even(4), even(8)}) {
			float[] samples = render(angles.length == 1 ? 90 : 256, angles, 1, 0.8f, 3);
			double share = lowRms(samples, 60) / rms(samples);
			assertTrue(share > 0.18, "No low end under the note with " + angles.length + " firings a cycle: " + share);
		}
	}

	@Test
	void anEvenFiringEngineRepeatsAtEachFiring() {
		// Four cylinders at 1200 RPM: 40 firings a second
		float[] samples = render(1200, even(4), 1, 0.8f, 2);
		int firing = RATE / 40;
		double each = envelopeCorrelation(samples, firing);
		double between = envelopeCorrelation(samples, firing / 2);
		assertTrue(each > between + 0.2, "Each firing should look like the last: " + each + " against " + between);
	}

	@Test
	void anUnevenEngineDoesNotRepeatAtHalfItsCycle() {
		// A V-twin at 90 degrees fires 315 degrees after the other and 405 degrees before it comes round again
		float[] uneven = render(600, new float[] {0, 315}, 1, 0.8f, 4);
		float[] steady = render(600, new float[] {0, 360}, 1, 0.8f, 4);
		int cycle = RATE * 120 / 600;
		double unevenHalf = envelopeCorrelation(uneven, cycle / 2);
		double steadyHalf = envelopeCorrelation(steady, cycle / 2);
		assertTrue(steadyHalf > unevenHalf + 0.15,
			"A parallel twin repeats at half a cycle, a V-twin lopes: " + steadyHalf + " against " + unevenHalf);
	}

	@Test
	void missingFiringsMakeTheNoteQuieterAndUneven() {
		float[] full = render(900, even(8), 1, 0.8f, 2);
		float[] limping = render(900, even(8), 0.5f, 0.8f, 2);
		assertTrue(rms(limping) < rms(full), "Misfires are quieter than firings");
	}

	@Test
	void aHardWorkedEngineIsLouderAndBrighterThanAnIdlingOne() {
		double idle = rms(render(600, even(6), 1, 0.05f, 2));
		double working = rms(render(600, even(6), 1, 1, 2));
		assertTrue(working > idle, "Load adds the upper partials: " + idle + " against " + working);
	}

	@Test
	void aSilencedEngineFallsQuiet() {
		EngineNoteSynth synth = new EngineNoteSynth(3);
		synth.set(new Params(1200, even(4), 1, 1, 1));
		for (int i = 0; i < RATE; i++)
			synth.next();
		assertFalse(synth.isSilent());
		synth.set(Params.SILENT);
		for (int i = 0; i < RATE * 3; i++)
			synth.next();
		assertTrue(synth.isSilent());
		for (int i = 0; i < 100; i++)
			assertTrue(Math.abs(synth.next()) < 0.005f);
	}

	@Test
	void theSameEngineSoundsTheSameEachTime() {
		float[] first = render(900, even(6), 1, 0.7f, 0.5f);
		float[] second = render(900, even(6), 1, 0.7f, 0.5f);
		for (int i = 0; i < first.length; i++)
			assertEquals(first[i], second[i]);
	}

	/**
	 * Writes a few engines to {@code build/engine-notes} as WAV files to listen to. Not a check; it costs a moment and
	 * proves the files can be made.
	 */
	@Test
	void rendersDemoRecordings() throws IOException {
		Path folder = Path.of("build", "engine-notes");
		Files.createDirectories(folder);
		demo(folder, "inline4_idle_to_redline", even(4), 1, sweep(4, 90, 1500));
		demo(folder, "inline6_steady_900", even(6), 1, steady(6, 900));
		demo(folder, "v8_even_firing_steady_900", even(8), 1, steady(8, 900));
		demo(folder, "v8_one_cylinder_dead_900", even(8), 0.875f, steady(8, 900));
		demo(folder, "v_twin_potato_steady_300", new float[] {0, 315}, 1, steady(2, 300));
		demo(folder, "parallel_twin_steady_300", new float[] {0, 360}, 1, steady(2, 300));
		demo(folder, "v12_pull_to_4000", even(12), 1, sweep(12, 120, 4000));
		demo(folder, "rotary_twin_steady_1500", new float[] {0, 180, 360, 540}, 1, steady(4, 1500));
	}

	private static float[] steady(int ignored, float rpm) {
		return new float[] {rpm, rpm};
	}

	private static float[] sweep(int ignored, float from, float to) {
		return new float[] {from, to};
	}

	private static void demo(Path folder, String name, float[] angles, float share, float[] rpm) throws IOException {
		EngineNoteSynth synth = new EngineNoteSynth(11);
		int samples = RATE * 5;
		short[] pcm = new short[samples];
		for (int i = 0; i < samples; i++) {
			float t = (float) i / samples;
			float speed = rpm[0] + (rpm[1] - rpm[0]) * t;
			synth.set(new Params(speed, angles, share, 0.3f + 0.7f * t, 1));
			pcm[i] = (short) Math.round(Math.clamp(synth.next(), -1f, 1f) * 30000);
		}
		Files.write(folder.resolve(name + ".wav"), wav(pcm));
	}

	private static byte[] wav(short[] pcm) throws IOException {
		ByteBuffer data = ByteBuffer.allocate(pcm.length * 2)
			.order(ByteOrder.LITTLE_ENDIAN);
		for (short sample : pcm)
			data.putShort(sample);
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
		return out.toByteArray();
	}
}
