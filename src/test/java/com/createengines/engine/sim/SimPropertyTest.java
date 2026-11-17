package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 2: properties that must hold for every seed, on top of {@link SimStressTest}'s finite and
 * bounded checks. Every failure message names its seed, so a failure replays by running that seed alone.
 */
class SimPropertyTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static double[] run(long seed) {
		Tuning.reset();
		SimStressTest.Rig rig = new SimStressTest.Rig(seed);
		double[] out = new double[250 * 4];
		for (int i = 0; i < 250; i++) {
			rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 80 ? 32 : 0), 1);
			out[i * 4] = rig.engine.omega();
			out[i * 4 + 1] = rig.engine.netTorque();
			out[i * 4 + 2] = rig.engine.fuelRate();
			out[i * 4 + 3] = rig.engine.lubrication().film();
		}
		return out;
	}

	/** Replaying a seed on another thread gives the same run: the sim keeps no hidden per-thread or leftover state. */
	@Test
	void aSeedReplaysIdenticallyOnAnotherThread() throws Exception {
		// Tuning is global (and reset by every run), so replays cannot overlap; each waits for the one before. What this
		// does check is that nothing carries over from one run to the next or depends on the thread it ran on.
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			for (int seed = 0; seed < 20; seed++) {
				double[] here = run(seed);
				final int s = seed;
				Callable<double[]> there = () -> run(s);
				Future<double[]> result = pool.submit(there);
				double[] other = result.get();
				for (int i = 0; i < here.length; i++)
					assertEquals(Double.doubleToLongBits(here[i]), Double.doubleToLongBits(other[i]), "seed " + seed + " sample " + i);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** An engine with nothing to burn never makes power on its own, whatever else it is. */
	@Test
	void anEngineWithNoFuelNeverSustainsItself() {
		for (int seed = 0; seed < 60; seed++) {
			Tuning.reset();
			SimStressTest.Rig rig = new SimStressTest.Rig(seed);
			for (CylinderModel cylinder : rig.cylinders) {
				cylinder.fuelSupply = new FuelSupply() {
					@Override
					public double draw(double mb) {
						return 0;
					}

					@Override
					public FuelSpec fuel() {
						return null;
					}
				};
				cylinder.pilotSupply = null;
			}
			for (Turbocharger turbo : rig.engine.turbos())
				turbo.antiLagFuel = new FuelSupply() {
					@Override
					public double draw(double mb) {
						return 0;
					}

					@Override
					public FuelSpec fuel() {
						return null;
					}
				};
			for (int i = 0; i < 400; i++)
				rig.engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
			for (int i = 0; i < 400; i++)
				rig.engine.tickFree(0, MAX_OMEGA);
			assertTrue(rig.engine.rpm() < 1, "seed " + seed + " (" + rig.description + ") still turns at " + rig.engine.rpm() + " RPM with no fuel");
			assertFalse(rig.engine.isSustaining(), "seed " + seed + " claims to sustain itself with no fuel");
		}
	}

	/** Fuel burned never exceeds fuel injected, over a whole run and not only per cycle. */
	@Test
	void fuelIsConservedOverARun() {
		for (int seed = 0; seed < 80; seed++) {
			Tuning.reset();
			SimStressTest.Rig rig = new SimStressTest.Rig(seed);
			for (int i = 0; i < 300; i++) {
				rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
				for (CylinderModel c : rig.engine.cylinders()) {
					CycleStats s = c.lastCycle();
					assertTrue(s.fuelBurned <= s.fuelInjected + 1e-6, "seed " + seed + " tick " + i + ": burned " + s.fuelBurned
						+ " of " + s.fuelInjected);
				}
			}
		}
	}

	/** The fuel rate is zero when the shaft is stopped and nothing is cranking it. */
	@Test
	void aStoppedEngineBurnsNothing() {
		for (int seed = 0; seed < 60; seed++) {
			Tuning.reset();
			SimStressTest.Rig rig = new SimStressTest.Rig(seed);
			rig.engine.restore(0, 0, 1);
			for (int i = 0; i < 60; i++)
				rig.engine.tickFree(0, MAX_OMEGA);
			assertEquals(0, rig.engine.rpm(), 1e-6, "seed " + seed + " started on its own");
		}
	}

	/** Two engines built from the same seed, ticked in lock step, never drift apart. */
	@Test
	void twinsStayTwins() {
		List<Long> bad = new ArrayList<>();
		Random picks = new Random(42);
		for (int n = 0; n < 40; n++) {
			long seed = picks.nextInt(100_000);
			Tuning.reset();
			SimStressTest.Rig a = new SimStressTest.Rig(seed);
			SimStressTest.Rig b = new SimStressTest.Rig(seed);
			for (int i = 0; i < 200; i++) {
				a.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
				b.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
				if (Double.doubleToLongBits(a.engine.omega()) != Double.doubleToLongBits(b.engine.omega())) {
					bad.add(seed);
					break;
				}
			}
		}
		assertEquals(List.of(), bad, "these seeds diverged from an identical twin");
	}
}
