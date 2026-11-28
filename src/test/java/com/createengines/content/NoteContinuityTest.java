package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Ignition;
import com.createengines.content.EngineNoteSynth.Params;
import com.createengines.content.EngineNoteSynth.Voice;

/**
 * Engine audio never sounds choppy and never clips (docs/SOUND-FIT.md), held at the speeds the game runs: 32 to 256 RPM.
 * Fall is the spread of a 20 ms loudness envelope above 30 Hz (95th less 5th percentile); quiet is the share of time more
 * than 25 dB under the loud parts. Pure synth, no Minecraft.
 */
class NoteContinuityTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final float[] SPEEDS = {32, 64, 128, 256};

	private record Build(String name, float[] angles, int[] outlets, boolean diesel, boolean rotary) {
		Params at(float rpm, float load) {
			int cylinders = angles.length;
			return new Params(rpm, angles, 1, load, 1, EngineNoteVoicing.size(cylinders), 0.5f, 0.05f,
				new Voice(outlets, new float[] {0.5f, 0.5f}, diesel ? Ignition.COMPRESSION : Ignition.SPARK, 0.5f, 0,
					new float[0], rotary));
		}
	}

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static final Build[] BUILDS = {
		new Build("single", even(1), new int[1], false, false),
		new Build("V-twin", new float[] {0, 315}, new int[2], false, false),
		new Build("inline four", even(4), new int[4], false, false),
		new Build("diesel six", even(6), new int[6], true, false),
		new Build("crossplane V8", even(8), new int[] {0, 1, 0, 0, 1, 1, 1, 0}, false, false),
		new Build("V12", even(12), new int[12], false, false),
		new Build("two rotors", even(4), new int[4], false, true),
	};

	/** Long enough to take in several cycles of the slowest engine. */
	private static float[] render(Params params, float muffling) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(params);
		synth.setMuffling(muffling);
		int cycle = (int) (RATE * 120 / params.rpm());
		for (int i = 0; i < Math.max(2 * RATE, 2 * cycle); i++)
			synth.next();
		float[] out = new float[Math.max(8 * RATE, 4 * cycle)];
		for (int i = 0; i < out.length; i++)
			out[i] = synth.next();
		return out;
	}

	/** {@code {fall in dB, share of quiet time}}. */
	private static double[] measure(float[] samples) {
		double pole = Math.exp(-2 * Math.PI * 30 / RATE);
		double low = 0;
		double lower = 0;
		double[] heard = new double[samples.length];
		for (int i = 0; i < samples.length; i++) {
			low = pole * low + (1 - pole) * samples[i];
			double high = samples[i] - low;
			lower = pole * lower + (1 - pole) * high;
			heard[i] = high - lower;
		}
		int window = RATE / 50;
		int hop = RATE / 200;
		double[] level = new double[(heard.length - window) / hop];
		for (int w = 0; w < level.length; w++) {
			double sum = 0;
			for (int i = w * hop; i < w * hop + window; i++)
				sum += heard[i] * heard[i];
			level[w] = 10 * Math.log10(sum / window + 1e-14);
		}
		double[] sorted = level.clone();
		Arrays.sort(sorted);
		double loud = sorted[(int) (sorted.length * 0.95)];
		int quiet = 0;
		for (double l : level)
			if (l < loud - 25)
				quiet++;
		return new double[] {loud - sorted[(int) (sorted.length * 0.05)], quiet / (double) level.length};
	}

	@Test
	void aTurningEngineNeverFallsSilentBetweenItsFirings() {
		List<String> wrong = new ArrayList<>();
		for (Build build : BUILDS)
			for (float rpm : SPEEDS)
				for (float load : new float[] {0.1f, 1}) {
					double[] found = measure(render(build.at(rpm, load), 0));
					double firings = build.angles.length * rpm / 120;
					double most = firings >= 5 ? 20 : 25;
					String line = String.format("%s at %.0f RPM, load %.1f: %.1f firings/s, fall %.1f dB, quiet %.1f%%",
						build.name, rpm, load, firings, found[0], found[1] * 100);
					System.out.println("FIT " + line);
					if (found[0] > most || found[1] > 0)
						wrong.add(line);
				}
		assertEquals(List.of(), wrong);
	}

	@Test
	void aMuffledEngineIsAsContinuousAsAnOpenOne() {
		for (float rpm : SPEEDS) {
			double[] found = measure(render(BUILDS[2].at(rpm, 0.6f), 1));
			assertTrue(found[0] <= 25 && found[1] == 0, rpm + " RPM muffled: fall " + found[0] + ", quiet " + found[1]);
		}
	}

	@Test
	void noSampleReachesFullScaleEvenWithABangOnALoudNote() {
		for (float rpm : SPEEDS) {
			EngineNoteSynth synth = new EngineNoteSynth(7);
			synth.set(BUILDS[4].at(rpm, 1));
			for (int i = 0; i < 8 * RATE; i++) {
				if (i % RATE == RATE / 2)
					synth.bang(NoteBangs.Kind.values()[0], 1, 1);
				assertTrue(Math.abs(synth.next()) < 1, "full scale at " + rpm);
			}
		}
	}

	/** Blower whine is added after the synthesizer's own limit: once it was cut off flat at the 16-bit limit. */
	@Test
	void aStreamWithBlowersNeverReachesThe16BitLimit() {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(BUILDS[4].at(256, 1));
		EngineNoteMix mix = new EngineNoteMix(synth);
		mix.setGain(1);
		for (int slot = 0; slot < EngineNoteMix.WHINES; slot++)
			mix.setWhine(slot, BoostWhine.Kind.values()[slot % BoostWhine.Kind.values().length], 0.6f, 1);
		short[] out = new short[EngineNotePcm.CHUNK];
		int loudest = 0;
		for (int chunk = 0; chunk < 100; chunk++) {
			if (chunk % 20 == 10)
				synth.bang(NoteBangs.Kind.values()[0], 1, 1);
			mix.render(out, 0, out.length);
			for (short sample : out)
				loudest = Math.max(loudest, Math.abs(sample));
		}
		assertTrue(loudest > 20000, "the blowers were not heard: " + loudest);
		assertTrue(loudest <= 30000, "past the limiter: " + loudest);
	}

	@Test
	void theBedGoesWithTheTurning() {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(BUILDS[0].at(32, 0.5f));
		for (int i = 0; i < 8 * RATE; i++)
			synth.next();
		synth.set(Params.SILENT);
		for (int i = 0; i < 3 * RATE; i++)
			synth.next();
		assertTrue(synth.isSilent());
		for (int i = 0; i < RATE / 10; i++)
			assertTrue(Math.abs(synth.next()) < 0.001f);
	}

	@Test
	void theStrokesSitWhereTheCamPutsThem() {
		NoteStrokes single = NoteStrokes.of(new float[] {0});
		assertTrue(single.exhaust(270) > 0.9f && single.exhaust(90) == 0 && single.exhaust(450) == 0);
		assertTrue(single.intake(450) > 0.9f && single.intake(270) == 0);
		assertTrue(single.compression(710) > 0.85f && single.compression(560) < 0.1f && single.compression(10) == 0);
		NoteStrokes many = NoteStrokes.of(even(24));
		for (int degrees = 0; degrees < 720; degrees += 2)
			assertTrue(many.exhaust(degrees) <= 1 && many.compression(degrees) <= 1);
		assertEquals(0, NoteStrokes.NONE.exhaust(100));
	}
}
