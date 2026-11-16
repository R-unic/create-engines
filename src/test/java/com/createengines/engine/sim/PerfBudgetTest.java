package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 7, the simulation's share: limits that fail the build. Cost per tick per engine and bytes
 * allocated per tick on the hot path. Baseline measured 2026-11-16: 0.075 ms (16 cylinders, turbo, worst speed), 0.019 ms
 * (4 cylinders), 0 bytes allocated. The budgets are about 7x the baseline (the measured numbers
 * are printed on every run, to set the next baseline from) so a busy build agent does not trip them, and well below
 * what a real regression costs: a reintroduced per-step allocation or an accidental quadratic loop is a factor of ten.
 * The 2.0 optimization pass tightens them.
 */
class PerfBudgetTest {

	/** Milliseconds a 16-cylinder turbocharged engine may take for one tick, at its worst speed. */
	private static final double MAX_MS_PER_TICK_16_CYL = 0.5;
	/** Milliseconds a 4-cylinder engine may take for one tick. */
	private static final double MAX_MS_PER_TICK_4_CYL = 0.15;
	/** Bytes the hot path may allocate for each tick of one 8-cylinder engine. */
	private static final long MAX_BYTES_PER_TICK_8_CYL = 4 * 1024;

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static EngineSimulation engine(int cylinderCount, boolean turbo) {
		List<CylinderModel> cylinders = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < cylinderCount; i++) {
			double tdc = i * 720.0 / cylinderCount;
			CylinderModel c = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 % 180);
			c.fuelSupply = new FuelSupply() {
				@Override
				public double draw(double mb) {
					return mb;
				}

				@Override
				public FuelSpec fuel() {
					return FuelSpec.DIESEL;
				}
			};
			cylinders.add(c);
		}
		EngineSimulation engine = new EngineSimulation(cylinders, 2, 0.02);
		if (turbo)
			engine.addTurbo(new Turbocharger(cylinders, cylinders, 1));
		engine.setThermalLive(true);
		return engine;
	}

	/** The best of several timed runs, which is what the code costs once the machine's noise is out of it. */
	private static double bestMillisPerTick(EngineSimulation engine, double rpm) {
		double omega = EngineSimulation.rpmToOmega(rpm);
		for (int i = 0; i < 300; i++)
			engine.tickDriven(omega, 1);
		double best = Double.MAX_VALUE;
		for (int round = 0; round < 7; round++) {
			int ticks = 200;
			long start = System.nanoTime();
			for (int i = 0; i < ticks; i++)
				engine.tickDriven(omega, 1);
			best = Math.min(best, (System.nanoTime() - start) / 1e6 / ticks);
		}
		return best;
	}

	@Test
	void aSixteenCylinderTurboEngineStaysUnderItsBudget() {
		for (double rpm : new double[] {64, 256, 1024, 4096}) {
			double ms = bestMillisPerTick(engine(16, true), rpm);
			System.out.printf("PERF 16 cyl turbo @ %.0f rpm: %.3f ms/tick (budget %.1f)%n", rpm, ms, MAX_MS_PER_TICK_16_CYL);
			assertTrue(ms < MAX_MS_PER_TICK_16_CYL, "16 cylinders at " + rpm + " RPM take " + ms + " ms a tick");
		}
	}

	@Test
	void aFourCylinderEngineStaysUnderItsBudget() {
		for (double rpm : new double[] {64, 256, 1024, 4096}) {
			double ms = bestMillisPerTick(engine(4, false), rpm);
			System.out.printf("PERF 4 cyl @ %.0f rpm: %.3f ms/tick (budget %.1f)%n", rpm, ms, MAX_MS_PER_TICK_4_CYL);
			assertTrue(ms < MAX_MS_PER_TICK_4_CYL, "4 cylinders at " + rpm + " RPM take " + ms + " ms a tick");
		}
	}

	@Test
	void theHotPathAllocatesLittle() {
		var bean = ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean sun) || !sun.isThreadAllocatedMemorySupported()) {
			System.out.println("PERF allocation budget skipped: this JVM cannot measure per-thread allocation");
			return;
		}
		EngineSimulation engine = engine(8, true);
		double omega = EngineSimulation.rpmToOmega(1024);
		for (int i = 0; i < 500; i++)
			engine.tickDriven(omega, 1);
		long id = Thread.currentThread().threadId();
		long worst = 0;
		for (int round = 0; round < 5; round++) {
			int ticks = 200;
			long before = sun.getThreadAllocatedBytes(id);
			for (int i = 0; i < ticks; i++)
				engine.tickDriven(omega, 1);
			worst = Math.max(worst, (sun.getThreadAllocatedBytes(id) - before) / ticks);
		}
		System.out.printf("PERF 8 cyl allocates %d bytes/tick (budget %d)%n", worst, MAX_BYTES_PER_TICK_8_CYL);
		assertTrue(worst < MAX_BYTES_PER_TICK_8_CYL, "an 8-cylinder tick allocates " + worst + " bytes");
	}
}
