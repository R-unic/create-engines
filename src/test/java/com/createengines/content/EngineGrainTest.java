package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createengines.engine.EngineSound;

/**
 * The note's sound source (docs/SOUND-PLAN.md, step 2): the grains every firing is struck from, the ring they play through
 * and the modelled exhaust pipe. Pure Java, no Minecraft.
 */
class EngineGrainTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static List<float[]> everyGrain() {
		EngineGrains bank = EngineGrains.bank();
		List<float[]> all = new ArrayList<>();
		for (int take = 0; take < EngineGrains.TAKES; take++) {
			for (int size = 0; size < EngineGrains.SIZES; size++) {
				all.add(bank.crack(size, take));
				for (int load = 0; load < EngineGrains.LOADS; load++) {
					all.add(bank.body(false, size, load, take));
					all.add(bank.body(true, size, load, take));
				}
			}
			all.add(bank.knock(take));
		}
		return all;
	}

	/** A grain that began or ended away from zero would click each time it was struck. */
	@Test
	void everyGrainStartsAndEndsQuietAndIsShortEnough() {
		List<float[]> all = everyGrain();
		assertEquals(176, all.size());
		for (float[] grain : all) {
			float peak = 0;
			for (float sample : grain)
				peak = Math.max(peak, Math.abs(sample));
			assertTrue(peak > 0.05f && peak < 1, "peak " + peak);
			assertTrue(Math.abs(grain[0]) < 0.02f * peak, "starts at " + grain[0]);
			assertTrue(Math.abs(grain[grain.length - 1]) < 0.02f * peak, "ends at " + grain[grain.length - 1]);
			assertTrue(grain.length <= 0.08 * RATE, "longer than 80 ms: " + grain.length);
		}
	}

	@Test
	void theTakesOfASetAllDiffer() {
		Set<String> seen = new HashSet<>();
		for (float[] grain : everyGrain())
			assertTrue(seen.add(Arrays.toString(grain)), "two grains are the same");
	}

	@Test
	void noTakeIsStruckTwiceRunningAndEveryOtherCanFollow() {
		for (int previous = 0; previous < EngineGrains.TAKES; previous++) {
			Set<Integer> next = new HashSet<>();
			for (int i = 0; i <= 1000; i++) {
				int take = EngineNoteSynth.nextTake(previous, i / 1000f);
				assertNotEquals(previous, take);
				assertTrue(take >= 0 && take < EngineGrains.TAKES);
				next.add(take);
			}
			assertEquals(EngineGrains.TAKES - 1, next.size());
		}
	}

	@Test
	void aFileThatIsNotTheseGrainsIsRefused() {
		byte[] wrong = new byte[64];
		assertThrows(IOException.class, () -> EngineGrains.read(new DataInputStream(new ByteArrayInputStream(wrong))));
	}

	@Test
	void overlappingStrikesAddUpAndTheRingEmpties() {
		GrainBus bus = new GrainBus();
		// Far enough round that the ring wraps while grains are in it
		for (int i = 0; i < GrainBus.LONGEST - 2; i++)
			bus.take();
		bus.strike(new float[] {1, 2, 3, 4}, 1);
		assertEquals(1, bus.take());
		bus.strike(new float[] {10, 10}, 0.5f);
		assertEquals(7, bus.take());
		assertEquals(8, bus.take());
		assertEquals(4, bus.take());
		assertTrue(bus.idle());
		assertEquals(0, bus.take());
	}

	private static float[] impulse(ExhaustPipe pipe, int samples) {
		float[] out = new float[samples];
		for (int i = 0; i < samples; i++)
			out[i] = pipe.next(i == 0 ? 1 : 0);
		return out;
	}

	/** The pitch a pipe rings at: the frequency, near its note, that its answer to one pulse holds most of. */
	private static double rings(double note) {
		ExhaustPipe pipe = new ExhaustPipe();
		pipe.tune(note);
		float[] out = impulse(pipe, RATE / 4);
		double best = 0;
		double at = 0;
		for (double hz = note * 0.6; hz <= note * 1.6; hz += 0.5) {
			double re = 0;
			double im = 0;
			for (int i = 0; i < out.length; i++) {
				double turn = 2 * Math.PI * hz * i / RATE;
				re += out[i] * Math.cos(turn);
				im += out[i] * Math.sin(turn);
			}
			if (re * re + im * im > best) {
				best = re * re + im * im;
				at = hz;
			}
		}
		return at;
	}

	@Test
	void aPipeRingsAtTheNoteItIsTunedTo() {
		for (double note : new double[] {65, 86, 130, 190})
			assertEquals(0, 12 * Math.log(rings(note) / note) / Math.log(2), 0.8, "semitones off at " + note);
	}

	/** The run's length is a resonance now: from a stub to a long run, what the pipe rings at falls by over an octave. */
	@Test
	void aPipeRingsLowerTheLongerItsRun() {
		double before = Double.MAX_VALUE;
		double stub = 0;
		for (int blocks : new int[] {1, 2, 4, 13}) {
			double heard = rings(86 * EngineNoteSynth.pitch(0.5f, EngineSound.pipeLength(blocks)));
			assertTrue(heard < before, blocks + " blocks ring at " + heard + ", the shorter at " + before);
			if (blocks == 1)
				stub = heard;
			before = heard;
		}
		assertTrue(12 * Math.log(stub / before) / Math.log(2) > 12, "stub " + stub + " Hz, long " + before + " Hz");
	}

	private static double energy(float[] samples, int from) {
		double sum = 0;
		for (int i = from; i < samples.length; i++)
			sum += samples[i] * samples[i];
		return sum;
	}

	@Test
	void aMufflerInThePipeTakesWhatComesRoundAgain() {
		ExhaustPipe open = new ExhaustPipe();
		ExhaustPipe shut = new ExhaustPipe();
		open.tune(86);
		shut.tune(86);
		shut.muffle(1);
		// Past the first pulse, which leaves the mouth before the muffler's filter has had it
		assertTrue(energy(impulse(shut, RATE / 5), 20) < 0.5 * energy(impulse(open, RATE / 5), 20));
	}

	/** A long ring was heard as reverb: even the shortest run, which goes round most often, has died in a tenth of a second. */
	@Test
	void noPipeRingsOn() {
		for (double note : new double[] {65, 86, 400, 2000}) {
			ExhaustPipe pipe = new ExhaustPipe();
			pipe.tune(note);
			float[] out = impulse(pipe, RATE / 2);
			assertTrue(energy(out, RATE / 10) < 1e-3 * energy(out, 0), "still ringing at " + note);
			assertTrue(pipe.isSilent());
		}
	}

	@Test
	void anyNoteAskedOfAPipeStaysFinite() {
		for (double note : new double[] {0, -5, 1, 1e9}) {
			ExhaustPipe pipe = new ExhaustPipe();
			pipe.tune(note);
			for (float sample : impulse(pipe, 4000))
				assertTrue(Float.isFinite(sample) && Math.abs(sample) < 10, note + " gave " + sample);
		}
	}
}
