package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerfSamplerTest {

	@Test
	void percentilesFindTheStutterTheMeanHides() {
		PerfSampler sampler = new PerfSampler();
		for (int i = 0; i < 99; i++)
			sampler.record(10_000_000L); // 10 ms
		sampler.record(500_000_000L); // one half-second hitch
		assertEquals(10, sampler.percentileMillis(0.5), 1e-9);
		assertEquals(10, sampler.percentileMillis(0.95), 1e-9);
		assertEquals(500, sampler.percentileMillis(1.0), 1e-9);
		assertEquals(500, sampler.maxMillis(), 1e-9);
		assertTrue(sampler.meanMillis() < 20);
	}

	@Test
	void anEmptySamplerReportsZeroes() {
		PerfSampler sampler = new PerfSampler();
		assertEquals(0, sampler.percentileMillis(0.99));
		assertEquals(0, sampler.maxMillis());
		assertEquals(0, sampler.meanMillis());
	}

	@Test
	void growsPastItsInitialCapacity() {
		PerfSampler sampler = new PerfSampler();
		for (int i = 0; i < 5000; i++)
			sampler.record(i);
		assertEquals(5000, sampler.count());
	}

	@Test
	void aSteadilyClimbingSeriesIsALeak() {
		PerfSampler.Series heap = new PerfSampler.Series();
		for (int i = 0; i < 20; i++)
			heap.add(500 + i * 10);
		assertTrue(PerfSampler.climbing(heap, 50));
	}

	@Test
	void aFlatSeriesWithNoiseIsNot() {
		PerfSampler.Series heap = new PerfSampler.Series();
		double[] noise = {500, 503, 498, 505, 497, 502, 499, 504, 500, 501};
		for (double v : noise)
			heap.add(v);
		assertFalse(PerfSampler.climbing(heap, 50));
	}

	@Test
	void anEarlySpikeThatSettlesIsNotALeak() {
		PerfSampler.Series heap = new PerfSampler.Series();
		for (double v : new double[] {500, 900, 700, 520, 505, 500, 502, 499})
			heap.add(v);
		assertFalse(PerfSampler.climbing(heap, 100));
	}

	@Test
	void tooFewSamplesCannotAccuseAnything() {
		PerfSampler.Series series = new PerfSampler.Series();
		series.add(1);
		series.add(1000);
		assertFalse(PerfSampler.climbing(series, 1));
		assertEquals(0, PerfSampler.slope(java.util.List.of(5.0)));
	}

	@Test
	void slopeIsPerSample() {
		assertEquals(2, PerfSampler.slope(java.util.List.of(0.0, 2.0, 4.0, 6.0)), 1e-9);
	}
}
