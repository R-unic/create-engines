package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 9, the simulation's share of it: every setting in {@link Tuning} at the ends of its config
 * range, one at a time and in combined extremes, run through the same fuzzed engines and invariants as
 * {@link SimStressTest}. The ranges are the ones {@code CEConfig} defines; {@link #rangesMatchTheConfig} keeps the two in
 * step so a widened range cannot dodge the matrix.
 */
class ConfigMatrixTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);
	private static final int SEEDS = 12;

	/** One numeric setting: how to write it and its config minimum and maximum. */
	private record Knob(String name, Consumer<Double> set, double min, double max) {
	}

	private static final List<Knob> KNOBS = List.of(
		new Knob("precisionBonus", v -> Tuning.precisionBonus = v, 0.0, 2.0),
		new Knob("boostStrength", v -> Tuning.boostStrength = v, 0.0, 2.0),
		new Knob("rotaryPower", v -> Tuning.rotaryPower = v, 0.25, 2.0),
		new Knob("fuelConsumption", v -> Tuning.fuelConsumption = v, 0.05, 20.0),
		new Knob("heatRecovery", v -> Tuning.heatRecovery = v, 0.1, 10.0),
		new Knob("coolingBonus", v -> Tuning.coolingBonus = v, 0.0, 3.0),
		new Knob("torsionalFlex", v -> Tuning.torsionalFlex = v, 0.0, 2.0));

	/** One switch: how to write it. Each is flipped from its default of true. */
	private record Switch(String name, Consumer<Boolean> set) {
	}

	private static final List<Switch> SWITCHES = List.of(
		new Switch("forcedInduction", v -> Tuning.forcedInduction = v),
		new Switch("sparkFouling", v -> Tuning.sparkFouling = v),
		new Switch("oilDilution", v -> Tuning.oilDilution = v),
		new Switch("oilAgeing", v -> Tuning.oilAgeing = v),
		new Switch("engineTemperature", v -> Tuning.engineTemperature = v),
		new Switch("pilotInjection", v -> Tuning.pilotInjection = v),
		new Switch("rotaryEngines", v -> Tuning.rotaryEngines = v),
		new Switch("heatRecoveryEnabled", v -> Tuning.heatRecoveryEnabled = v));

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static void run(String label) {
		for (int seed = 0; seed < SEEDS; seed++) {
			SimStressTest.Rig rig = new SimStressTest.Rig(seed);
			String where = label + ", " + rig.description;
			int tick = 0;
			try {
				for (int i = 0; i < 120; i++, tick++) {
					rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 60 ? 32 : 0), 1);
					SimStressTest.check(rig, MAX_OMEGA, false, tick);
				}
				for (int i = 0; i < 120; i++, tick++) {
					rig.engine.tickFree(i % 40 < 20 ? 2 : 0, MAX_OMEGA);
					SimStressTest.check(rig, MAX_OMEGA, false, tick);
				}
				for (int i = 0; i < 60; i++, tick++) {
					rig.engine.tickDriven(EngineSimulation.rpmToOmega(120), i % 2 == 0 ? 1 : -1);
					SimStressTest.check(rig, Double.MAX_VALUE, true, tick);
				}
			} catch (AssertionError | RuntimeException e) {
				throw new AssertionError("config " + where + ": " + e.getMessage(), e);
			}
		}
	}

	@Test
	void everyNumericSettingAtItsMinimumAndMaximum() {
		for (Knob knob : KNOBS)
			for (double value : new double[] {knob.min, knob.max}) {
				Tuning.reset();
				knob.set.accept(value);
				run(knob.name + " = " + value);
			}
	}

	@Test
	void everySwitchFlippedOff() {
		for (Switch toggle : SWITCHES) {
			Tuning.reset();
			toggle.set.accept(false);
			run(toggle.name + " off");
		}
	}

	@Test
	void combinedExtremes() {
		List<String> labels = new ArrayList<>();
		for (int pattern = 0; pattern < 6; pattern++) {
			Tuning.reset();
			StringBuilder label = new StringBuilder("combined ").append(pattern).append(':');
			Random random = new Random(pattern);
			for (Knob knob : KNOBS) {
				double value = switch (pattern) {
					case 0 -> knob.min;
					case 1 -> knob.max;
					case 2 -> (knob.min + knob.max) / 2;
					default -> random.nextBoolean() ? knob.min : knob.max;
				};
				knob.set.accept(value);
				label.append(' ').append(knob.name).append('=').append(value);
			}
			for (Switch toggle : SWITCHES) {
				boolean on = switch (pattern) {
					case 0 -> false;
					case 1 -> true;
					default -> random.nextBoolean();
				};
				toggle.set.accept(on);
				label.append(' ').append(toggle.name).append(on ? "" : " off");
			}
			labels.add(label.toString());
			run(label.toString());
		}
		assertEquals(6, labels.size());
	}

	/** The matrix is only worth anything if it covers every setting there is. */
	@Test
	void everyTuningFieldIsInTheMatrix() throws Exception {
		int covered = KNOBS.size() + SWITCHES.size();
		int fields = 0;
		for (var field : Tuning.class.getDeclaredFields())
			if (java.lang.reflect.Modifier.isPublic(field.getModifiers()) && java.lang.reflect.Modifier.isStatic(field.getModifiers()))
				fields++;
		assertEquals(fields, covered, "Tuning gained or lost a setting; add it to ConfigMatrixTest");
	}

	/** Reads the min and max out of CEConfig's source, so a range change in the config must be mirrored here. */
	@Test
	void rangesMatchTheConfig() throws Exception {
		String source = java.nio.file.Files.readString(
			java.nio.file.Path.of("src/main/java/com/createengines/config/CEConfig.java"));
		for (Knob knob : KNOBS) {
			var matcher = java.util.regex.Pattern
				.compile("defineInRange\\(\"" + knob.name + "\",\\s*[-0-9.]+,\\s*([-0-9.]+),\\s*([-0-9.]+)\\)")
				.matcher(source);
			if (knob.name.equals("heatRecovery"))
				matcher = java.util.regex.Pattern.compile("defineInRange\\(\"heatRecoveryOutput\",\\s*[-0-9.]+,\\s*([-0-9.]+),\\s*([-0-9.]+)\\)")
					.matcher(source);
			org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), knob.name + " is no longer a ranged config value");
			assertEquals(knob.min, Double.parseDouble(matcher.group(1)), 1e-12, knob.name + " minimum");
			assertEquals(knob.max, Double.parseDouble(matcher.group(2)), 1e-12, knob.name + " maximum");
		}
	}
}
