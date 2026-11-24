package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Fuel character in 2.1: what each shipped fuel is for, the spill-fire figures and the pure arithmetic behind them. */
class FuelCharacterTest {

	private static final List<FuelSpec> SHIPPED = List.of(FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.BIODIESEL, FuelSpec.GASOLINE,
		FuelSpec.ETHANOL, FuelSpec.KEROSENE, FuelSpec.METHANOL);

	@Test
	void aFuelThatNamesNoFlammabilityBurnsInASpillLikeEveryFuelDidBefore() {
		FuelSpec plain = new FuelSpec(30, 1.7, 1.6, 1.0, 0, 1);
		assertEquals(1, plain.flammability(), 0);
		assertEquals(SpillFire.BASE_DELAY, SpillFire.delay(plain.flammability()));
		assertEquals(1, SpillFire.catchChance(plain.flammability()), 0);
		assertEquals(1, new FuelSpec(30, 1.7, 1.6, 1.0, 0, 1, 1.7, 1).flammability(), 0);
	}

	@Test
	void everyShippedFuelPassesTheFuelCheckWithItsFlammability() {
		for (FuelSpec fuel : SHIPPED)
			assertTrue(FuelCheck.problems(fuel).isEmpty(), fuel + " " + FuelCheck.problems(fuel));
		assertFalse(FuelCheck.problems(FuelSpec.DIESEL.withFlammability(0)).isEmpty(), "zero flammability is reported");
		assertFalse(FuelCheck.problems(FuelSpec.DIESEL.withFlammability(Double.NaN)).isEmpty());
	}

	@Test
	void volatileFuelsLightFastAndDieselClassFuelsAreSlow() {
		for (FuelSpec fast : List.of(FuelSpec.GASOLINE, FuelSpec.METHANOL, FuelSpec.ETHANOL))
			assertTrue(SpillFire.delay(fast.flammability()) < SpillFire.BASE_DELAY, "fast: " + fast);
		for (FuelSpec slow : List.of(FuelSpec.DIESEL, FuelSpec.BIODIESEL, FuelSpec.KEROSENE, FuelSpec.PLANT_OIL)) {
			assertTrue(SpillFire.delay(slow.flammability()) > SpillFire.BASE_DELAY, "slow: " + slow);
			assertTrue(SpillFire.catchChance(slow.flammability()) < 1, "does not always catch: " + slow);
		}
		assertEquals(1, SpillFire.catchChance(FuelSpec.GASOLINE.flammability()), 0);
		assertTrue(FuelSpec.GASOLINE.flammability() > FuelSpec.ETHANOL.flammability());
	}

	@Test
	void theSpillArithmeticIsFiniteAndBoundedForAnyNumber() {
		for (double flammability : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -0.0, -3, 1e-300,
			Double.MIN_VALUE, 1e300, Double.MAX_VALUE}) {
			int delay = SpillFire.delay(flammability);
			double chance = SpillFire.catchChance(flammability);
			assertTrue(delay >= SpillFire.MIN_DELAY && delay <= SpillFire.MAX_DELAY, flammability + " delay " + delay);
			assertTrue(chance > 0 && chance <= 1, flammability + " chance " + chance);
		}
		assertEquals(SpillFire.BASE_DELAY, SpillFire.delay(Double.NaN), "an unreadable figure counts as an ordinary fuel");
	}

	@Test
	void flammabilityIsKeptWhenAFuelIsMadeUsable() {
		assertEquals(FuelSpec.GASOLINE.flammability(), FuelSpec.GASOLINE.withFlammability(2).usable().flammability(), 0);
		FuelSpec broken = new FuelSpec(Double.NaN, 1.7, 0, Double.NaN, 0, 1, 1.7, 1, 0.5);
		assertEquals(0.5, broken.usable().flammability(), 0);
	}

	@Test
	void biodieselIsTheCleanDieselAtAPriceInEnergy() {
		FuelSpec bio = FuelSpec.BIODIESEL;
		FuelSpec diesel = FuelSpec.DIESEL;
		assertTrue(bio.sootFactor() < diesel.sootFactor(), "less soot");
		assertEquals(0, bio.sulfur(), 0, "no sulfur");
		assertTrue(bio.energyPerMb() < diesel.energyPerMb(), "lower energy");
		assertEquals(diesel.autoignitionTemp(), bio.autoignitionTemp(), 0, "lights like diesel");
		assertEquals(diesel.coldSensitivity(), bio.coldSensitivity(), 0, "no gelling punishment");
		assertEquals(diesel.airPerMb(), bio.airPerMb(), 0);
		assertFalse(bio.needsSpark());
	}

	@Test
	void plantOilIsTheTolerantDirtyOne() {
		FuelSpec oil = FuelSpec.PLANT_OIL;
		assertTrue(oil.coldSensitivity() <= FuelSpec.DIESEL.coldSensitivity(), "a cold block costs it no more than diesel");
		assertTrue(oil.sootFactor() > FuelSpec.DIESEL.sootFactor(), "dirtier");
		assertTrue(oil.sulfur() > FuelSpec.DIESEL.sulfur(), "and sulfurous");
		assertTrue(oil.energyPerMb() < FuelSpec.DIESEL.energyPerMb(), "and thin in energy");
		assertFalse(oil.needsSpark());
	}

	@Test
	void theOtherFuelsKeepTheirNumbers() {
		assertEquals(36, FuelSpec.DIESEL.energyPerMb(), 0);
		assertEquals(1, FuelSpec.DIESEL.sootFactor(), 0);
		assertEquals(40, FuelSpec.GASOLINE.energyPerMb(), 0);
		assertEquals(0.8, FuelSpec.GASOLINE.coldSensitivity(), 0);
		assertEquals(38, FuelSpec.KEROSENE.energyPerMb(), 0);
		assertEquals(27, FuelSpec.ETHANOL.energyPerMb(), 0);
		assertEquals(22, FuelSpec.METHANOL.energyPerMb(), 0);
		assertEquals(0.17, FuelSpec.METHANOL.chargeCooling(), 0);
	}
}
