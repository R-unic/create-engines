package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.PartEffects.Effect;
import com.createengines.engine.sim.PartEffects.Part;

/**
 * Verification track item 6, past the sound thread. An engine is simulated on one thread, but the numbers it reads are
 * shared: {@link Tuning} is rewritten when a config file reloads, {@link PartEffects} when a data pack reloads, and the
 * tables behind a cylinder or a cam are built by whichever thread asks first (the server tick or the client's render
 * thread). These tests change each from another thread while engines run, start many threads at a cold cache, and run
 * the same seeds on many threads at once, which is what would show a shared scratch buffer or a half-built table.
 * <p>
 * Timing cannot be seeded, so the seeds fix what is asked for and the schedule is left to the machine; a failure names
 * the thread and what it saw.
 */
class SharedStateConcurrencyTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void reset() {
		Tuning.reset();
		PartEffects.reset();
	}

	private static void join(List<Thread> threads, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		for (Thread thread : threads) {
			thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
			if (thread.isAlive())
				fail("thread " + thread.getName() + " did not finish in " + millis + " ms; deadlocked, or stuck in a loop");
		}
	}

	/** A sim thread: builds a new engine from a seed now and then, runs it and checks every figure, until told to stop. */
	private static Thread simThread(String name, long firstSeed, AtomicBoolean stop, AtomicLong ticks, ConcurrentLinkedQueue<Throwable> thrown,
									CountDownLatch go) {
		return new Thread(() -> {
			try {
				go.await();
				long seed = firstSeed;
				while (!stop.get()) {
					SimStressTest.Rig rig = new SimStressTest.Rig(seed++);
					for (int i = 0; i < 80 && !stop.get(); i++) {
						rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 40 ? 32 : 0), 1);
						SimStressTest.check(rig, MAX_OMEGA, false, i);
						ticks.incrementAndGet();
					}
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, name);
	}

	/** A config reload rewrites every setting while engines on other threads are running and being built. */
	@Test
	void tuningRewrittenFromAnotherThreadNeverBreaksAnEngine() throws Exception {
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		AtomicBoolean stop = new AtomicBoolean();
		AtomicLong ticks = new AtomicLong();
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 3; i++)
			threads.add(simThread("sim-" + i, 1000L * i, stop, ticks, thrown, go));
		threads.add(new Thread(() -> {
			Random random = new Random(5);
			try {
				go.await();
				while (!stop.get()) {
					// Every value is one the config allows, at its edges as often as in between: a reload is a player's edit
					Tuning.precisionBonus = fromPool(random, 0, 2);
					Tuning.boostStrength = edge(random, 0, 2);
					Tuning.rotaryPower = fromPool(random, 0.25, 2);
					Tuning.fuelConsumption = edge(random, 0.05, 20);
					Tuning.torsionalFlex = edge(random, 0, 2);
					Tuning.coolingBonus = edge(random, 0, 3);
					Tuning.heatRecovery = edge(random, 0.1, 10);
					Tuning.forcedInduction = random.nextBoolean();
					Tuning.sparkFouling = random.nextBoolean();
					Tuning.oilDilution = random.nextBoolean();
					Tuning.oilAgeing = random.nextBoolean();
					Tuning.engineTemperature = random.nextBoolean();
					Tuning.pilotInjection = random.nextBoolean();
					Tuning.rotaryEngines = random.nextBoolean();
					Tuning.heatRecoveryEnabled = random.nextBoolean();
					Tuning.altitudeThinning = random.nextBoolean();
					Thread.yield();
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, "config-reload"));
		for (Thread thread : threads)
			thread.start();
		go.countDown();
		Thread.sleep(2500);
		stop.set(true);
		join(threads, 15_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
		assertTrue(ticks.get() > 1000, "only " + ticks.get() + " ticks ran in the whole test; the sim threads were starved");
	}

	/**
	 * The two settings a cylinder's shape is made from (precision bonus, rotary power) come from a few set values, the edges
	 * of the range among them, and not from anywhere in it: every distinct value makes new cylinder shapes, and the table for
	 * each shape is kept for good, so a value picked anew thousands of times a second filled the heap (it ran a mutant test
	 * run out of memory). Every other setting here still takes any value in its range.
	 */
	private static double fromPool(Random random, double min, double max) {
		return switch (random.nextInt(3)) {
			case 0 -> min;
			case 1 -> max;
			default -> min + (max - min) * (1 + random.nextInt(5)) / 6;
		};
	}

	private static double edge(Random random, double min, double max) {
		return switch (random.nextInt(3)) {
			case 0 -> min;
			case 1 -> max;
			default -> min + random.nextDouble() * (max - min);
		};
	}

	/** A data pack reload swaps the part effects while cylinders are being built from them and run. */
	@Test
	void partEffectsSwappedFromAnotherThreadNeverBreaksAnEngine() throws Exception {
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		AtomicBoolean stop = new AtomicBoolean();
		AtomicLong ticks = new AtomicLong();
		AtomicLong swaps = new AtomicLong();
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 3; i++)
			threads.add(simThread("sim-" + i, 7000L + 1000L * i, stop, ticks, thrown, go));
		threads.add(new Thread(() -> {
			Random random = new Random(9);
			try {
				go.await();
				while (!stop.get()) {
					if (random.nextInt(5) == 0) {
						PartEffects.reset();
					} else {
						// One effect for every part (or for none): a dozen distinct pack states, so the spec cache stays small
						Map<Part, Effect> effects = new EnumMap<>(Part.class);
						Effect chosen = randomEffect(random);
						for (Part part : Part.values())
							effects.put(part, chosen);
						PartEffects.set(effects);
					}
					swaps.incrementAndGet();
					Thread.yield();
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, "data-reload"));
		for (Thread thread : threads)
			thread.start();
		go.countDown();
		Thread.sleep(2500);
		stop.set(true);
		join(threads, 15_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
		assertTrue(ticks.get() > 1000, "only " + ticks.get() + " ticks ran");
		assertTrue(swaps.get() > 100, "only " + swaps.get() + " reloads happened");
	}

	/**
	 * A fixed pool, not a fresh random effect each time: every distinct set of effects makes distinct cylinder specs, and each
	 * spec's tables are cached for the life of the JVM. A pack reload makes a handful of specs; a test that made thousands ran
	 * the heap out, which says something about the test and nothing about threads.
	 */
	private static final List<Effect> POOL = poolOfEffects();

	private static List<Effect> poolOfEffects() {
		Random random = new Random(1234);
		List<Effect> pool = new ArrayList<>();
		for (int n = 0; n < 12; n++) {
			double[] scales = new double[8];
			for (int i = 0; i < scales.length; i++)
				scales[i] = edge(random, Effect.MIN, Effect.MAX);
			pool.add(new Effect(scales[0], scales[1], scales[2], scales[3], scales[4], scales[5], scales[6], scales[7]));
		}
		return pool;
	}

	private static Effect randomEffect(Random random) {
		return POOL.get(random.nextInt(POOL.size()));
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> staticMap(Class<?> owner, String field) throws Exception {
		Field f = owner.getDeclaredField(field);
		f.setAccessible(true);
		return (Map<Object, Object>) f.get(null);
	}

	/**
	 * The tables behind a cylinder shape and a cam profile are built by whichever thread first asks. Emptied, then asked
	 * for by many threads at once, every thread must be handed the same finished table.
	 */
	@Test
	void coldCachesAreBuiltOnceAndSharedWhenManyThreadsAskAtOnce() throws Exception {
		Map<Object, Object> geometries = staticMap(CylinderSpec.class, "GEOMETRIES");
		Map<Object, Object> curves = staticMap(CamProfile.class, "CURVES");
		List<CylinderSpec> specs = List.of(CylinderSpec.CRUDE, CylinderSpec.build(true, false, false, false),
			CylinderSpec.build(false, true, true, false), CylinderSpec.build(true, true, true, true),
			CylinderSpec.build(false, false, false, true));
		List<CamProfile> cams = List.of(CamProfile.BASIC, CamProfile.ROTARY);
		for (int round = 0; round < 40; round++) {
			geometries.clear();
			curves.clear();
			int threads = 8;
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch go = new CountDownLatch(1);
			ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
			Object[][] seen = new Object[threads][specs.size() + cams.size() * 2];
			List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final int id = t;
				Thread worker = new Thread(() -> {
					try {
						ready.countDown();
						go.await();
						int slot = 0;
						// Each thread walks the list from a different place, so they meet at different tables first
						for (int k = 0; k < specs.size(); k++) {
							int at = (k + id) % specs.size();
							seen[id][at] = specs.get(at).geometry();
						}
						for (int k = 0; k < cams.size(); k++) {
							int at = (k + id) % cams.size();
							seen[id][specs.size() + at * 2] = cams.get(at).intakeCurve();
							seen[id][specs.size() + at * 2 + 1] = cams.get(at).exhaustCurve();
						}
					} catch (Throwable e) {
						thrown.add(e);
					}
				}, "cold-" + t);
				workers.add(worker);
				worker.start();
			}
			ready.await();
			go.countDown();
			join(workers, 15_000);
			assertNull(thrown.peek(), "round " + round + ": a thread threw: " + thrown.peek());
			for (int slot = 0; slot < seen[0].length; slot++)
				for (int t = 1; t < threads; t++) {
					assertTrue(seen[0][slot] != null && seen[t][slot] != null, "round " + round + " slot " + slot + " came back empty");
					assertSame(seen[0][slot], seen[t][slot], "round " + round + ": threads 0 and " + t + " built different tables for slot " + slot);
				}
		}
	}

	/** The same seed, run on many threads at once, gives the one answer. A shared scratch buffer would give several. */
	@Test
	void theSameSeedsRunOnManyThreadsAtOnceGiveOneAnswer() throws Exception {
		int seeds = 24;
		double[][] expected = new double[seeds][];
		for (int seed = 0; seed < seeds; seed++)
			expected[seed] = trace(seed);
		int threads = 8;
		CountDownLatch go = new CountDownLatch(1);
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		List<Thread> workers = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int id = t;
			Thread worker = new Thread(() -> {
				try {
					go.await();
					for (int round = 0; round < 3; round++)
						for (int k = 0; k < seeds; k++) {
							int seed = (k + id * 5) % seeds;
							double[] got = trace(seed);
							for (int i = 0; i < got.length; i++)
								if (Double.doubleToLongBits(got[i]) != Double.doubleToLongBits(expected[seed][i]))
									throw new AssertionError("thread " + id + ", seed " + seed + ", sample " + i + ": " + got[i]
										+ " where one thread alone gave " + expected[seed][i]);
						}
				} catch (Throwable e) {
					thrown.add(e);
				}
			}, "twin-" + t);
			workers.add(worker);
			worker.start();
		}
		go.countDown();
		join(workers, 60_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
	}

	private static double[] trace(long seed) {
		SimStressTest.Rig rig = new SimStressTest.Rig(seed);
		double[] out = new double[120 * 3];
		for (int i = 0; i < 120; i++) {
			rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 60 ? 32 : 0), 1);
			out[i * 3] = rig.engine.omega();
			out[i * 3 + 1] = rig.engine.netTorque();
			out[i * 3 + 2] = rig.engine.fuelRate();
		}
		return out;
	}

	/** Sanity of the harness itself: the traces the threads compare are not all the same number. */
	@Test
	void theTracesTheTestComparesAreNotEmpty() {
		double[] a = trace(1);
		double[] b = trace(2);
		boolean differ = false;
		boolean moves = false;
		for (int i = 0; i < a.length; i++) {
			differ |= a[i] != b[i];
			moves |= a[i] != 0;
		}
		assertTrue(moves, "seed 1 never moved the crank, so the replay test would pass on nothing");
		assertTrue(differ, "two different seeds gave the same trace");
		assertEquals(a.length, b.length);
	}
}
