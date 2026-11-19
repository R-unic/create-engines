package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** {@link FuelSpec#usable()}: what stands between a pack's fuel numbers and the simulation. */
class FuelSpecUsableTest {

	@Test
	void everyShippedFuelIsUsableAsItIs() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL})
			assertSame(fuel, fuel.usable(), "a sound fuel is returned itself, so it runs exactly as before");
	}

	@Test
	void oddButFiniteNumbersAreLeftAlone() {
		FuelSpec odd = new FuelSpec(-1, 1e-12, -1, -3, -2, 1e300, 0, 1e9);
		assertSame(odd, odd.usable(), "a negative or tiny number runs as written; only what turns the figures NaN is replaced");
		FuelSpec infiniteAir = new FuelSpec(36, 1.5, Double.POSITIVE_INFINITY);
		assertSame(infiniteAir, infiniteAir.usable());
	}

	@Test
	void energyThatIsNotANumberOrOverflowsIsReplaced() {
		assertEquals(0, new FuelSpec(Double.NaN, 1.5, 1.7).usable().energyPerMb(), 0);
		assertEquals(0, new FuelSpec(Double.POSITIVE_INFINITY, 1.5, 1.7).usable().energyPerMb(), 0);
		assertEquals(0, new FuelSpec(Double.NEGATIVE_INFINITY, 1.5, 1.7).usable().energyPerMb(), 0);
		assertEquals(FuelSpec.MAX_USABLE_ENERGY, new FuelSpec(Double.MAX_VALUE, 1.5, 1.7).usable().energyPerMb(), 0);
		assertEquals(FuelSpec.MAX_USABLE_ENERGY, new FuelSpec(FuelSpec.MAX_USABLE_ENERGY, 1.5, 1.7).usable().energyPerMb(), 0);
	}

	@Test
	void airThatIsZeroOrNotANumberIsReplaced() {
		assertEquals(FuelSpec.MIN_USABLE_AIR, new FuelSpec(36, 1.5, 0).usable().airPerMb(), 0);
		assertEquals(FuelSpec.MIN_USABLE_AIR, new FuelSpec(36, 1.5, Double.NaN).usable().airPerMb(), 0);
		assertEquals(FuelSpec.MIN_USABLE_AIR, new FuelSpec(36, 1.5, Double.NEGATIVE_INFINITY).usable().airPerMb(), 0);
	}

	@Test
	void coldSensitivityAndChargeCoolingThatAreNotFiniteBecomeNoEffect() {
		for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			assertEquals(0, new FuelSpec(36, 1.5, 1.7, bad).usable().coldSensitivity(), 0);
			assertEquals(0, new FuelSpec(36, 1.5, 1.7, 1, bad, 1).usable().chargeCooling(), 0);
		}
	}

	@Test
	void theOtherFiguresAreNeverTouchedAndTheOriginalIsNotChanged() {
		FuelSpec bad = new FuelSpec(Double.NaN, 2.5, 0, Double.NaN, Double.NaN, 0.7, 3.1, 0.4);
		FuelSpec fixed = bad.usable();
		assertEquals(2.5, fixed.autoignitionTemp(), 0);
		assertEquals(0.7, fixed.sootFactor(), 0);
		assertEquals(3.1, fixed.preignitionTemp(), 0);
		assertEquals(0.4, fixed.sulfur(), 0);
		assertFalse(FuelCheck.problems(bad).isEmpty(), "FuelCheck still sees what the pack wrote");
		assertEquals(Double.NaN, bad.energyPerMb(), 0);
	}
}
