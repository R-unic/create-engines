package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Params;

/**
 * The engine note is rendered on the sound engine's thread for every engine in earshot, 22050 samples a second each, so
 * its cost per sample is multiplied by the number of voices. The measured numbers are printed on every run; the budget is
 * set well above them so a busy build agent does not trip it, but below what a reintroduced per-sample allocation or
 * transcendental pile-up costs. The checksum line lets a before and after run show whether a change moved the sound.
 */
class EngineNotePerfTest {

	/** Nanoseconds a sample may take, one voice, any scenario. A real-time voice has 45000 ns a sample available. */
	private static final double MAX_NS_PER_SAMPLE = 600;

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static final int BLOCK = 2205;

	/**
	 * Renders every scenario before any is timed, so the compiler has finished with the synth and every path through it. Without
	 * this the first scenarios were timed while it was still being compiled: on a shared build agent one cylinder, timed first,
	 * cost twice what sixteen did, and went over the budget on some runs.
	 */
	private static void warmUp(Object[][] scenarios) {
		short[] out = new short[BLOCK];
		for (int pass = 0; pass < 3; pass++)
			for (Object[] scenario : scenarios) {
				EngineNoteSynth synth = new EngineNoteSynth(7);
				synth.set((Params) scenario[1]);
				for (int i = 0; i < 100; i++)
					synth.render(out, 0, BLOCK);
			}
	}

	private static double bestNanosPerSample(Params params, long[] checksum) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(params);
		short[] out = new short[BLOCK];
		for (int i = 0; i < 40; i++)
			synth.render(out, 0, BLOCK);
		double best = Double.MAX_VALUE;
		long sum = 0;
		for (int round = 0; round < 9; round++) {
			long start = System.nanoTime();
			for (int block = 0; block < 20; block++) {
				synth.render(out, 0, BLOCK);
				if (round == 0)
					for (short s : out)
						sum = sum * 31 + s;
			}
			best = Math.min(best, (System.nanoTime() - start) / (20.0 * BLOCK));
		}
		checksum[0] = sum;
		return best;
	}

	@Test
	void aVoiceStaysUnderItsBudget() {
		Object[][] scenarios = {
			{ "1 cyl @ 90 rpm", new Params(90, even(1), 1, 0.5f, 1) },
			{ "4 cyl @ 1500 rpm", new Params(1500, even(4), 1, 0.6f, 1) },
			{ "8 cyl @ 3000 rpm", new Params(3000, even(8), 0.9f, 0.8f, 1, 0.7f, 0.8f) },
			{ "16 cyl @ 8000 rpm", new Params(8000, even(16), 1, 1, 1) },
			{ "idle coast @ 400 rpm", new Params(400, even(6), 1, 0, 0.25f) },
		};
		warmUp(scenarios);
		for (Object[] scenario : scenarios) {
			long[] checksum = new long[1];
			double ns = bestNanosPerSample((Params) scenario[1], checksum);
			System.out.printf("PERF note %s: %.1f ns/sample (budget %.0f) checksum %x%n", scenario[0], ns, MAX_NS_PER_SAMPLE,
				checksum[0]);
			assertTrue(ns < MAX_NS_PER_SAMPLE, scenario[0] + " takes " + ns + " ns a sample");
		}
	}
}
