package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeanSpeedTest {

	private static final double DT = EngineSimulation.TICK;

	/** A mean speed already settled at {@code rpm}. */
	private static MeanSpeed settled(double rpm) {
		MeanSpeed speed = new MeanSpeed();
		speed.update(rpm, 0, 0, DT);
		speed.update(rpm, rpm, 0.5, DT);
		return speed;
	}

	@Test
	void aConstantSpeedGivesItself() {
		MeanSpeed speed = settled(120);
		for (int i = 0; i < 100; i++) {
			speed.update(120, 120, 0.5, DT);
			assertEquals(120, speed.rpm(), 0);
		}
	}

	@Test
	void aRippleGivesItsMeanWhereverTheTickLandsOnIt() {
		for (double ripple : new double[] {0.02, 0.15, 0.6}) {
			for (double surgesPerTick : new double[] {0.07, 0.31, 0.77, 2.4}) {
				MeanSpeed speed = settled(100);
				for (int i = 0; i < 400; i++) {
					double instant = 100 * (1 + ripple / 2 * Math.sin(2 * Math.PI * surgesPerTick * i));
					speed.update(instant, 100, DT / surgesPerTick, DT);
					assertEquals(100, speed.rpm(), 1e-9, "ripple " + ripple);
				}
			}
		}
	}

	@Test
	void aNewMeanIsReachedWithinOneCycleAndNeverPassed() {
		for (double cycle : new double[] {2, 0.5, 0.12, 0.01}) {
			for (double next : new double[] {200, 40}) {
				MeanSpeed speed = settled(100);
				double last = 100;
				int ticks = (int) Math.ceil(cycle / DT - 1e-9);
				for (int i = 0; i < ticks + 20; i++) {
					// The instant speed is far off, to show that it has no say
					speed.update(7, next, cycle, DT);
					assertTrue(next > 100 ? speed.rpm() >= last && speed.rpm() <= next : speed.rpm() <= last && speed.rpm() >= next,
						"cycle " + cycle + ": " + last + " then " + speed.rpm() + " toward " + next);
					if (i >= ticks - 1)
						assertEquals(next, speed.rpm(), 1e-9, "cycle " + cycle + " tick " + i);
					last = speed.rpm();
				}
			}
		}
	}

	@Test
	void aNewMeanMidRampCarriesOnFromWhereItIs() {
		MeanSpeed speed = settled(100);
		speed.update(100, 200, 1, DT);
		assertEquals(105, speed.rpm(), 1e-9);
		speed.update(100, 85, 1, DT);
		assertEquals(104, speed.rpm(), 1e-9);
	}

	@Test
	void withNoWholeCycleItIsTheInstantSpeed() {
		MeanSpeed speed = settled(100);
		speed.update(33, 0, 0, DT);
		assertEquals(33, speed.rpm());
		// A mean with no length, or a length with no mean, is not a whole cycle
		speed.update(34, 100, 0, DT);
		assertEquals(34, speed.rpm());
		speed.update(35, 0, 0.5, DT);
		assertEquals(35, speed.rpm());
		speed.update(0, 0, 0, DT);
		assertEquals(0, speed.rpm());
		// A stopped crank has no mean, whatever the last cycle's was
		speed.update(0, 100, 0.5, DT);
		assertEquals(0, speed.rpm());
	}

	@Test
	void nothingThatIsNotANumberGetsIn() {
		double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -1};
		for (double instant : bad)
			for (double mean : bad)
				for (double cycle : bad) {
					MeanSpeed speed = settled(100);
					speed.update(instant, mean, cycle, DT);
					assertTrue(Double.isFinite(speed.rpm()), instant + " " + mean + " " + cycle);
					speed.update(100, 100, 0.5, DT);
					assertTrue(Double.isFinite(speed.rpm()));
				}
		MeanSpeed speed = settled(100);
		speed.update(50, 100, Double.NaN, DT);
		assertEquals(50, speed.rpm(), "a cycle with no length is none to go by");
	}
}
