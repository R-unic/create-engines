package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.PartEffects.Effect;
import com.createengines.engine.sim.PartEffects.Part;

/**
 * Verification track items 8 and 9, parts as data: a pack may scale any of the eight figures of any part anywhere in
 * {@code [MIN, MAX]}, so every one of them at both ends, and all of them at once, must leave engines running soundly. The
 * same fuzzed engines and invariants as {@link SimStressTest} are used, so a figure that goes NaN, a speed that runs
 * away, or a supply asked for a negative amount fails with the seed that found it.
 */
class PartEffectsExtremesTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);
	private static final String[] FIELDS = {"compression", "breathing", "ignitionDelay", "burn", "frictionStatic", "frictionViscous",
		"inertia", "injectorFlow"};

	@AfterEach
	void reset() {
		PartEffects.reset();
		Tuning.reset();
	}

	private static Effect effect(int field, double value) {
		double[] scales = {1, 1, 1, 1, 1, 1, 1, 1};
		if (field >= 0)
			scales[field] = value;
		else
			java.util.Arrays.fill(scales, value);
		return new Effect(scales[0], scales[1], scales[2], scales[3], scales[4], scales[5], scales[6], scales[7]);
	}

	/** Runs a few seeds under the effects now set; returns what went wrong, or null. */
	private static String run(String label) {
		for (int seed = 0; seed < 8; seed++) {
			Tuning.reset();
			SimStressTest.Rig rig = new SimStressTest.Rig(seed);
			int tick = 0;
			try {
				for (int i = 0; i < 100; i++, tick++) {
					rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 50 ? 32 : 0), 1);
					SimStressTest.check(rig, MAX_OMEGA, false, tick);
				}
				for (int i = 0; i < 30; i++, tick++) {
					rig.engine.tickDriven(EngineSimulation.rpmToOmega(120), 1);
					SimStressTest.check(rig, Double.MAX_VALUE, true, tick);
				}
			} catch (AssertionError | RuntimeException e) {
				return label + ", " + rig.description + ": " + e.getMessage();
			}
		}
		return null;
	}

	@Test
	void everyFigureOfEveryPartAtItsMinimumAndMaximumLeavesEnginesSound() {
		List<String> problems = new ArrayList<>();
		for (Part part : Part.values())
			for (int field = 0; field < FIELDS.length; field++)
				for (double value : new double[] {Effect.MIN, Effect.MAX}) {
					Map<Part, Effect> effects = new EnumMap<>(Part.class);
					effects.put(part, effect(field, value));
					PartEffects.set(effects);
					String bad = run(part + "." + FIELDS[field] + " = " + value);
					if (bad != null)
						problems.add(bad);
				}
		assertEquals(List.of(), problems, "a part effect inside its allowed range left an engine unsound");
	}

	@Test
	void everyPartWithEveryFigureAtOnceAtTheEndsLeavesEnginesSound() {
		List<String> problems = new ArrayList<>();
		for (double value : new double[] {Effect.MIN, Effect.MAX}) {
			Map<Part, Effect> effects = new EnumMap<>(Part.class);
			for (Part part : Part.values())
				effects.put(part, effect(-1, value));
			PartEffects.set(effects);
			String bad = run("every figure of every part = " + value);
			if (bad != null)
				problems.add(bad);
		}
		// Opposite ends in one pack: the parts disagree about which way is good
		Map<Part, Effect> mixed = new EnumMap<>(Part.class);
		int n = 0;
		for (Part part : Part.values())
			mixed.put(part, effect(-1, n++ % 2 == 0 ? Effect.MIN : Effect.MAX));
		PartEffects.set(mixed);
		String bad = run("parts alternating between the two ends");
		if (bad != null)
			problems.add(bad);
		assertEquals(List.of(), problems, "part effects inside their allowed range left an engine unsound");
	}

	/**
	 * Found 2026-11-19: every part's compression scale is range-checked alone, but a cylinder takes the product of every part
	 * fitted to it, and a product that took the ratio under 1 turned the sim's gas work NaN (two parts at 0.1, three at 0.3,
	 * four at 0.5). {@code CylinderSpec.build} now holds the ratio at {@link PartStats#MIN_COMPRESSION}, as a part_stats
	 * retuning already did.
	 */
	@Test
	void compressionScalesThatMultiplyBelowOneAreHeldAtTheFloorAndEnginesStaySound() {
		Part[] parts = Part.values();
		for (int fitted = 1; fitted <= parts.length; fitted++)
			for (double scale : new double[] {0.1, 0.3, 0.5}) {
				PartEffects.set(compressionOn(fitted, scale));
				assertNull(run(fitted + " parts at " + scale + " compression"), fitted + " parts at " + scale + " compression");
			}
		PartEffects.set(compressionOn(parts.length, Effect.MIN));
		assertEquals(PartStats.MIN_COMPRESSION, CylinderSpec.build(true, true, true, true).compressionRatio(), 0,
			"four parts at the minimum take the ratio to the floor, not under it");
		PartEffects.reset();
		assertEquals(CylinderSpec.CRUDE.compressionRatio(), CylinderSpec.build(false, false, false, false).compressionRatio(), 0);
		assertTrue(CylinderSpec.build(true, true, true, true).compressionRatio() > PartStats.MIN_COMPRESSION,
			"the shipped precision cylinder is nowhere near the floor");
	}

	private static Map<Part, Effect> compressionOn(int fitted, double scale) {
		Map<Part, Effect> effects = new EnumMap<>(Part.class);
		Part[] parts = Part.values();
		for (int i = 0; i < fitted; i++)
			effects.put(parts[i], new Effect(scale, 1, 1, 1, 1, 1, 1, 1));
		return effects;
	}
}
