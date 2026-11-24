package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.ChargePriority.Use;

/** Exact edges of the charge rules: zero rooms and zero deliveries, NaN, the constants, and the per-cycle counts. */
class ChargeEdgesTest {

	private static final float EPS = 1e-5f;

	@Test
	void aReserveWithNothingToGiveBesideAnOpenBatteryTakesNoShareAndMakesNoNaN() {
		assertArrayEquals(new float[] {0, 5}, ChargePriority.split(new float[] {0, 10}, new boolean[] {true, false}, 5, Use.ESSENTIAL), EPS);
	}

	@Test
	void anOpenBatteryWithNothingToGiveBesideAReserveTakesNoShareAndMakesNoNaN() {
		assertArrayEquals(new float[] {5, 0}, ChargePriority.split(new float[] {10, 0}, new boolean[] {true, false}, 5, Use.ESSENTIAL), EPS);
	}

	@Test
	void anEssentialDrawEmptiesTheOpenOnesThenTheReserveForWhatIsLeft() {
		assertArrayEquals(new float[] {6, 4}, ChargePriority.split(new float[] {10, 4}, new boolean[] {true, false}, 10, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {0, 4}, ChargePriority.split(new float[] {10, 4}, new boolean[] {true, false}, 4, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {10, 0}, ChargePriority.split(new float[] {10, 0}, new boolean[] {true, false}, 99, Use.ESSENTIAL), EPS);
	}

	@Test
	void nothingAskedOrNothingThereGivesNothingWhateverTheAsk() {
		boolean[] both = {false, true};
		assertArrayEquals(new float[] {0, 0}, ChargePriority.split(new float[] {5, 5}, both, 0, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.split(new float[] {5, 5}, both, -3, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.split(new float[] {5, 5}, both, Float.NaN, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.split(new float[] {0, 0}, both, 4, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.fillSplit(new float[] {5, 5}, both, 0), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.fillSplit(new float[] {5, 5}, both, -3), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.fillSplit(new float[] {5, 5}, both, Float.NaN), EPS);
		assertArrayEquals(new float[] {0, 0}, ChargePriority.fillSplit(new float[] {0, 0}, both, 4), EPS);
	}

	@Test
	void aFullReserveBesideAnOpenBatteryTakesNothingAndMakesNoNaN() {
		assertArrayEquals(new float[] {0, 4}, ChargePriority.fillSplit(new float[] {0, 10}, new boolean[] {true, false}, 4), EPS);
	}

	@Test
	void aFullOpenBatteryBesideAReserveTakesNothingAndMakesNoNaN() {
		assertArrayEquals(new float[] {4, 0}, ChargePriority.fillSplit(new float[] {10, 0}, new boolean[] {true, false}, 4), EPS);
		assertArrayEquals(new float[] {3, 2}, ChargePriority.fillSplit(new float[] {3, 6}, new boolean[] {true, false}, 5), EPS);
	}

	@Test
	void energyAndSparksConvertExactlyAtTheEdges() {
		assertEquals(0, BatteryEnergy.toEnergy(0));
		assertEquals(0, BatteryEnergy.toEnergy(-5));
		assertEquals(0, BatteryEnergy.toEnergy(Double.NaN));
		assertEquals(0, BatteryEnergy.toEnergy(0.004));
		assertEquals(200, BatteryEnergy.toEnergy(1));
		assertEquals(Integer.MAX_VALUE, BatteryEnergy.toEnergy(1e12));
		assertEquals(0, BatteryEnergy.toSparks(0), 0);
		assertEquals(0, BatteryEnergy.toSparks(-5), 0);
		assertEquals(2, BatteryEnergy.toSparks(400), 1e-12);
		assertEquals(0.005, BatteryEnergy.toSparks(1), 1e-12);
	}

	@Test
	void theNoneAndUnlimitedPartsSayWhatTheyAre() {
		assertFalse(InjectorAssist.NONE.installed());
		assertFalse(InjectorAssist.NONE.powered());
		assertFalse(InjectorAssist.NONE.fire());
		assertTrue(InjectorAssist.UNLIMITED.installed());
		assertTrue(InjectorAssist.UNLIMITED.powered());
		assertTrue(InjectorAssist.UNLIMITED.fire());
		assertFalse(IgnitionCoil.NONE.installed());
		assertFalse(IgnitionCoil.NONE.powered());
		assertFalse(IgnitionCoil.NONE.fire());
		assertTrue(IgnitionCoil.UNLIMITED.installed());
		assertTrue(IgnitionCoil.UNLIMITED.powered());
		assertTrue(IgnitionCoil.UNLIMITED.fire());
	}

	private static CylinderModel one(FuelSpec fuel, InjectorAssist assist, IgnitionCoil coil) {
		CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		model.fuelSupply = MixtureTest.unlimited(fuel);
		model.ignition = IgnitionSource.UNLIMITED;
		model.assist = assist;
		model.coil = coil;
		return model;
	}

	/** The lowest and highest per-cycle count seen while the engine runs: a count never goes below zero or past one a cycle. */
	private static int[] counts(CylinderModel model, ToIntFunction<CycleStats> which) {
		List<CylinderModel> models = new ArrayList<>(List.of(model));
		EngineSimulation sim = new EngineSimulation(models, 0.35 + 1, 3 * 0.01);
		model.resetGas(0);
		sim.restore(0, 0, 1);
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int t = 0; t < 600; t++) {
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
			int n = which.applyAsInt(model.lastCycle());
			lo = Math.min(lo, n);
			hi = Math.max(hi, n);
		}
		return new int[] {lo, hi};
	}

	private static final class Spender implements InjectorAssist, IgnitionCoil {
		final boolean charged;

		Spender(boolean charged) {
			this.charged = charged;
		}

		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return charged;
		}

		@Override
		public boolean fire() {
			return charged;
		}
	}

	@Test
	void aChargedAssistCountsOneShotACycleAndNeverANegativeNumber() {
		int[] seen = counts(one(FuelSpec.GASOLINE, new Spender(true), IgnitionCoil.NONE), c -> c.assistedShots);
		assertEquals(0, seen[0]);
		assertEquals(1, seen[1]);
		int[] dead = counts(one(FuelSpec.GASOLINE, new Spender(true), IgnitionCoil.NONE), c -> c.deadAssists);
		assertEquals(0, dead[0]);
		assertEquals(0, dead[1]);
	}

	@Test
	void aFlatAssistCountsOneDeadShotACycleAndNeverANegativeNumber() {
		int[] dead = counts(one(FuelSpec.GASOLINE, new Spender(false), IgnitionCoil.NONE), c -> c.deadAssists);
		assertEquals(0, dead[0]);
		assertEquals(1, dead[1]);
		int[] seen = counts(one(FuelSpec.GASOLINE, new Spender(false), IgnitionCoil.NONE), c -> c.assistedShots);
		assertEquals(0, seen[1]);
	}

	@Test
	void aChargedCoilCountsOneSparkACycleAndAFlatOneCountsItDead() {
		int[] coiled = counts(one(FuelSpec.GASOLINE, InjectorAssist.NONE, new Spender(true)), c -> c.coiledSparks);
		assertEquals(0, coiled[0]);
		assertEquals(1, coiled[1]);
		int[] dead = counts(one(FuelSpec.GASOLINE, InjectorAssist.NONE, new Spender(false)), c -> c.deadCoils);
		assertEquals(0, dead[0]);
		assertEquals(1, dead[1]);
		int[] none = counts(one(FuelSpec.GASOLINE, InjectorAssist.NONE, new Spender(false)), c -> c.coiledSparks);
		assertEquals(0, none[1]);
	}

	@Test
	void anAssistedBoostedMethanolCylinderOffersMoreWorkAndAFlatOneOffersTheSame() {
		double none = work(InjectorAssist.NONE);
		double flat = work(new Spender(false));
		double assisted = work(new Spender(true));
		assertEquals(none, flat, 1e-9);
		assertTrue(assisted > none * 1.01, "assisted " + assisted + " against " + none);
	}

	private static double work(InjectorAssist assist) {
		CylinderModel model = one(FuelSpec.METHANOL, assist, IgnitionCoil.NONE);
		model.airMatch = 1;
		model.intakePressure = 2.5;
		counts(model, c -> c.assistedShots);
		return model.availableWork();
	}
}
