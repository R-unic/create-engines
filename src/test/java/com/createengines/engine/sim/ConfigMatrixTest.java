package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		new Switch("heatRecoveryEnabled", v -> Tuning.heatRecoveryEnabled = v),
		new Switch("altitudeThinning", v -> Tuning.altitudeThinning = v));

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

	/**
	 * Settings of {@code CEConfig} that are not {@link Tuning} fields, each with where its extremes are run. A setting added
	 * to the config but to neither the matrix nor this list fails {@link #everyConfigSettingIsInTheMatrixOrNamedHere}.
	 */
	private static final java.util.Map<String, String> OUTSIDE_TUNING = java.util.Map.ofEntries(
		java.util.Map.entry("journalInertia", "sim: inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound"),
		java.util.Map.entry("throwInertia", "sim: inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound"),
		java.util.Map.entry("flywheelInertia", "sim: inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound"),
		java.util.Map.entry("castFlywheelInertia", "sim: inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound"),
		java.util.Map.entry("bearingFriction", "sim: inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound"),
		java.util.Map.entry("stressPerTorque", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("maxCrankSegments", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("maxManifoldBlocks", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("maxCoupledLines", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("maxCouplingWalk", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("alternatorEnergy", "GameTest: SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane"),
		java.util.Map.entry("pollution", "no effect on the sim: it only reports gas to another mod"),
		java.util.Map.entry("pollutionRate", "no effect on the sim: it only reports gas to another mod"),
		java.util.Map.entry("carriedOver", "bookkeeping of the 1.x file carry-over, ConfigCarryOverTest"));

	/** The matrix is only worth anything if it covers every setting the config file offers, not just the Tuning fields. */
	@Test
	void everyConfigSettingIsInTheMatrixOrNamedHere() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/createengines/config/CEConfig.java"));
		var matcher = java.util.regex.Pattern.compile("\\.define(?:InRange)?\\(\"(\\w+)\"").matcher(source);
		java.util.Set<String> inMatrix = new java.util.HashSet<>();
		for (Knob knob : KNOBS)
			inMatrix.add(knob.name.equals("heatRecovery") ? "heatRecoveryOutput" : knob.name);
		for (Switch toggle : SWITCHES)
			inMatrix.add(toggle.name.equals("heatRecoveryEnabled") ? "heatRecovery" : toggle.name);
		List<String> unaccounted = new ArrayList<>();
		int found = 0;
		while (matcher.find()) {
			found++;
			String name = matcher.group(1);
			if (!inMatrix.contains(name) && !OUTSIDE_TUNING.containsKey(name))
				unaccounted.add(name);
		}
		assertTrue(found >= 30, "only " + found + " settings were found in the config source; the pattern no longer reads it");
		assertEquals(List.of(), unaccounted, "settings the matrix neither runs nor names: add each to the matrix or to OUTSIDE_TUNING");
		for (String name : OUTSIDE_TUNING.keySet())
			assertTrue(source.contains("\"" + name + "\""), name + " is listed in OUTSIDE_TUNING but is no longer a config setting");
	}

	/**
	 * The inertia the engine controller hands the sim is the sum of the config's per-segment and flywheel values (each 0 to
	 * 100, over up to 1024 segments) and its friction is the bearing friction (0 to 10) per segment: so from nothing at all to
	 * a hundred thousand, and friction from none to ten thousand. Every corner is run, with the cylinder state the dynamometer
	 * starts from.
	 */
	@Test
	void inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound() {
		for (double inertia : new double[] {0, 0.001, 0.15, 5, 100, 102_400})
			for (double friction : new double[] {0, 0.01, 10, 10_240}) {
				Tuning.reset();
				List<CylinderModel> cylinders = new ArrayList<>();
				Camshaft cam = new Camshaft();
				for (int i = 0; i < 4; i++) {
					double fire = i * 180;
					CylinderModel c = new CylinderModel(CylinderSpec.build(true, true, true, true), CamProfile.BASIC, fire % 360, cam, fire / 2);
					c.metering = 2;
					c.airMatch = 1;
					c.ignition = IgnitionSource.UNLIMITED;
					c.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
					cylinders.add(c);
				}
				EngineSimulation engine = new EngineSimulation(cylinders, inertia, friction);
				for (CylinderModel c : cylinders)
					c.resetGas(0);
				String where = "inertia " + inertia + ", friction " + friction;
				for (int i = 0; i < 160; i++) {
					engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 80 ? 32 : 0), 1);
					double[] figures = {engine.omega(), engine.netTorque(), engine.indicatedTorque(), engine.fuelRate(),
						engine.availableTorque(), engine.frictionTorque()};
					for (double figure : figures)
						assertTrue(Double.isFinite(figure), where + ": a figure is " + figure + " at tick " + i);
					assertTrue(engine.rpm() <= 256 + 1e-6, where + ": ran to " + engine.rpm() + " RPM past the ceiling at tick " + i);
				}
			}
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
