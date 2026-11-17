package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins what a spread of random engines do, tick by tick, against {@code src/test/resources/compat/trajectories.txt}.
 * <p>
 * Added after mutation testing: {@link EngineSimulation} and {@link CylinderModel} are long chains of arithmetic, and a
 * changed sign or a swapped operator in one of them moves a run by a few percent without crossing any of the
 * invariants the other tests assert. A trajectory records the speed, torque, fuel rate, temperature, oil film, cylinder
 * work and boost of one seeded rig every few ticks, so the same change moves a number.
 * <p>
 * This is the same contract as {@link BehaviourBaselineTest}: in 1.x a failure means existing builds run differently, and
 * the answer is a switch for the new behaviour, not new numbers. A major release may regenerate the file by hand.
 * Trajectories for new seeds are appended with {@code COMPAT_REGEN=1}; recorded lines are never rewritten by it.
 * <p>
 * The recorded numbers came from one machine's JVM. Math functions may differ in the last bit between JVMs and CPUs, so
 * the comparison allows a relative difference of 1e-6, which is far below what any change to the arithmetic makes.
 */
class SimTrajectoryTest {

	private static final Path FILE = Path.of("").toAbsolutePath().resolve("src/test/resources/compat/trajectories.txt");
	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);
	private static final int SEEDS = 40;
	private static final int TICKS = 300;
	private static final int EVERY = 6;

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static String trajectory(int seed) {
		Tuning.reset();
		SimStressTest.Rig rig = new SimStressTest.Rig(seed);
		EngineSimulation e = rig.engine;
		e.trackSwing();
		StringBuilder out = new StringBuilder();
		for (int tick = 0; tick < TICKS; tick++) {
			// Things that happen to a running engine: it is turned round, rebuilt from saved motion, and its oil is changed
			switch (seed % 4) {
				case 1 -> {
					if (tick == 100)
						e.setDirection(-1);
				}
				case 2 -> {
					if (tick == 150)
						e.restore(123.4, 20, 1);
					if (tick == 200)
						e.setDirection(-1);
				}
				case 3 -> {
					if (tick == 80)
						e.ageOil(true);
					if (tick == 180)
						e.lubrication().refresh(300);
					if (tick == 230)
						e.restore(-77.7, 5, -1);
				}
				default -> {
				}
			}
			if (tick < 120)
				e.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(tick < 60 ? 32 : 0), 1);
			else if (tick < 240)
				e.tickFree(tick % 40 < 20 ? 2 : 0, MAX_OMEGA);
			else
				e.tickDriven(EngineSimulation.rpmToOmega(120), tick % 2 == 0 ? 1 : -1);
			if (tick % EVERY != EVERY - 1)
				continue;
			double work = 0, burned = 0, peak = 0, boost = 0, blower = 0;
			for (CylinderModel c : e.cylinders()) {
				CycleStats s = c.lastCycle();
				work += s.work;
				burned += s.fuelBurned;
				peak = Math.max(peak, s.peakPressure);
			}
			for (Turbocharger t : e.turbos())
				boost += t.boost() + t.spool() + t.backPressure();
			for (Supercharger b : e.superchargers())
				blower += b.boost();
			for (double v : new double[] {e.omega(), e.netTorque(), e.indicatedTorque(), e.fuelRate(), e.availableTorque(),
				e.frictionTorque(), e.ripple(), e.thermal().temperature(), e.lubrication().film(), e.lubrication().dilution(),
				e.lubrication().condition(), work, burned, peak, boost, blower, e.angle(), e.rotaryAngle(), e.predictedRipple(),
				e.crankingTorque(), e.inertia(), e.direction(), e.isSustaining() ? 1 : 0, e.isStopped() ? 1 : 0})
				out.append(String.format(Locale.ROOT, "%.9e ", v));
		}
		return out.toString().trim();
	}

	private static Map<String, String> measure() {
		Map<String, String> out = new TreeMap<>();
		for (int seed = 0; seed < SEEDS; seed++)
			out.put("seed" + seed, trajectory(seed));
		return out;
	}

	private static Map<String, String> read() throws IOException {
		Map<String, String> out = new TreeMap<>();
		if (!Files.exists(FILE))
			return out;
		for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
			int space = line.indexOf(' ');
			if (line.isBlank() || line.startsWith("#") || space < 0)
				continue;
			out.put(line.substring(0, space), line.substring(space + 1));
		}
		return out;
	}

	/** First difference between two trajectories, or null if they agree to the tolerance. */
	private static String firstDifference(String shipped, String now) {
		String[] a = shipped.split(" ");
		String[] b = now.split(" ");
		if (a.length != b.length)
			return "lengths differ: " + a.length + " against " + b.length;
		String[] names = {"omega", "netTorque", "indicatedTorque", "fuelRate", "availableTorque", "frictionTorque", "ripple",
			"temperature", "film", "dilution", "condition", "work", "burned", "peakPressure", "boost", "blower", "angle", "rotaryAngle",
			"predictedRipple", "crankingTorque", "inertia", "direction", "sustaining", "stopped"};
		for (int i = 0; i < a.length; i++) {
			double x = Double.parseDouble(a[i]);
			double y = Double.parseDouble(b[i]);
			if (Math.abs(x - y) > 1e-6 * Math.max(Math.abs(x), Math.abs(y)) + 1e-9)
				return "sample " + i / names.length + " (tick " + (i / names.length * EVERY + EVERY - 1) + ") " + names[i % names.length]
					+ ": recorded " + a[i] + ", now " + b[i];
		}
		return null;
	}

	@Test
	void seededEnginesRunAsTheyDid() throws IOException {
		Map<String, String> shipped = read();
		Map<String, String> now = measure();
		List<String> problems = new ArrayList<>();
		List<String> added = new ArrayList<>();
		for (String key : now.keySet()) {
			String recorded = shipped.get(key);
			if (recorded == null) {
				added.add(key);
				continue;
			}
			String difference = firstDifference(recorded, now.get(key));
			if (difference != null)
				problems.add(key + ": " + difference);
		}
		if (System.getenv("COMPAT_REGEN") != null && !added.isEmpty()) {
			Map<String, String> merged = new TreeMap<>(shipped);
			for (String key : added)
				merged.put(key, now.get(key));
			StringBuilder text = new StringBuilder("# Seeded engine trajectories (see SimTrajectoryTest). Recorded lines are never rewritten in 1.x.\n");
			merged.forEach((k, v) -> text.append(k).append(' ').append(v).append('\n'));
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, text.toString(), StandardCharsets.UTF_8);
			return;
		}
		assertTrue(added.isEmpty(), "trajectories are not recorded for " + added + " (run once with COMPAT_REGEN=1)");
		assertTrue(problems.isEmpty(), "existing engines run differently now:\n" + String.join("\n", problems));
	}
}
