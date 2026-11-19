package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The retuning a pack can give a cylinder: nothing by default, exactly what it names otherwise. */
class PartStatsTest {

	@Test
	void noEntryHandsBackTheSameSpec() {
		for (CylinderSpec spec : new CylinderSpec[] { CylinderSpec.CRUDE, CylinderSpec.ROTARY, CylinderSpec.build(true, true, true, true) })
			assertSame(spec, PartStats.NONE.apply(spec));
		assertTrue(PartStats.NONE.isNone());
	}

	@Test
	void aScaleChangesOnlyItsOwnStat() {
		CylinderSpec base = CylinderSpec.CRUDE;
		PartStats stats = new PartStats(1, 1.5, 1, 1, 1, 1, 1, 1, 1, 1, 1);
		CylinderSpec tuned = stats.apply(base);
		assertEquals(base.intakeFlow() * 1.5, tuned.intakeFlow(), 1e-12);
		assertEquals(base.compressionRatio(), tuned.compressionRatio(), 1e-12);
		assertEquals(base.exhaustFlow(), tuned.exhaustFlow(), 1e-12);
		assertEquals(base.maxFuelPerShot(), tuned.maxFuelPerShot(), 1e-12);
		assertEquals(base.rodRatio(), tuned.rodRatio(), 1e-12);
		assertEquals(base.displacement(), tuned.displacement(), 1e-12);
		assertEquals(base.injectorFlow(), tuned.injectorFlow(), 1e-12);
		assertEquals(base.inertia(), tuned.inertia(), 1e-12);
	}

	@Test
	void everyScaleReachesItsStat() {
		CylinderSpec base = CylinderSpec.build(true, true, true, true);
		CylinderSpec tuned = new PartStats(2, 3, 4, 5, 6, 7, 8, 9, 10, 0.5, 0.25).apply(base);
		assertEquals(base.compressionRatio() * 2, tuned.compressionRatio(), 1e-9);
		assertEquals(base.intakeFlow() * 3, tuned.intakeFlow(), 1e-9);
		assertEquals(base.exhaustFlow() * 4, tuned.exhaustFlow(), 1e-9);
		assertEquals(base.maxFuelPerShot() * 5, tuned.maxFuelPerShot(), 1e-9);
		assertEquals(base.ignitionDelay() * 6, tuned.ignitionDelay(), 1e-9);
		assertEquals(base.burnDuration() * 7, tuned.burnDuration(), 1e-9);
		assertEquals(base.frictionStatic() * 8, tuned.frictionStatic(), 1e-9);
		assertEquals(base.frictionViscous() * 9, tuned.frictionViscous(), 1e-9);
		assertEquals(base.inertia() * 10, tuned.inertia(), 1e-9);
		assertEquals(base.displacement() * 0.5, tuned.displacement(), 1e-9);
		assertEquals(base.injectorFlow() * 0.25, tuned.injectorFlow(), 1e-9);
	}

	@Test
	void aRotaryChamberTakesTheScalesOnTopOfItsInjectorAndPowerSetting() {
		Tuning.reset();
		try {
			Tuning.rotaryPower = 2;
			CylinderSpec built = CylinderSpec.buildRotary(true);
			CylinderSpec tuned = new PartStats(1, 1.25, 1, 1, 1, 0.5, 1, 1, 1, 1, 1).apply(built);
			assertEquals(built.intakeFlow() * 1.25, tuned.intakeFlow(), 1e-12);
			assertEquals(built.burnDuration() * 0.5, tuned.burnDuration(), 1e-12);
			assertEquals(built.displacement(), tuned.displacement(), 0);
			assertEquals(built.rodRatio(), tuned.rodRatio(), 0);
			assertSame(built, PartStats.NONE.apply(built));
		} finally {
			Tuning.reset();
		}
	}

	@Test
	void compressionCannotBeScaledBelowARunningEngine() {
		CylinderSpec tuned = new PartStats(PartStats.MIN_SCALE, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1).apply(CylinderSpec.CRUDE);
		assertEquals(PartStats.MIN_COMPRESSION, tuned.compressionRatio(), 0);
	}

	@Test
	void everyExtremeOfEveryScaleLeavesAUsableSpec() {
		double[] extremes = { PartStats.MIN_SCALE, PartStats.MAX_SCALE };
		CylinderSpec[] bases = { CylinderSpec.CRUDE, CylinderSpec.build(true, true, true, true) };
		for (CylinderSpec base : bases)
			for (int field = 0; field < 11; field++)
				for (double value : extremes) {
					double[] scales = new double[11];
					java.util.Arrays.fill(scales, 1);
					scales[field] = value;
					CylinderSpec tuned = new PartStats(scales[0], scales[1], scales[2], scales[3], scales[4], scales[5], scales[6],
						scales[7], scales[8], scales[9], scales[10]).apply(base);
					for (double stat : new double[] { tuned.compressionRatio(), tuned.rodRatio(), tuned.intakeFlow(), tuned.exhaustFlow(),
						tuned.maxFuelPerShot(), tuned.ignitionDelay(), tuned.burnDuration(), tuned.frictionStatic(),
						tuned.frictionViscous(), tuned.inertia(), tuned.displacement(), tuned.injectorFlow() })
						assertTrue(Double.isFinite(stat) && stat > 0, "field " + field + " at " + value + " gave " + stat);
				}
	}
}
