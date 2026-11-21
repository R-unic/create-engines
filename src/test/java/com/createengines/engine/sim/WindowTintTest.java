package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** The sight-window tints and the comparator level of a tank. */
class WindowTintTest {

	@Test
	void everyShippedFluidHasItsOwnTintAndAnythingElseIsAmber() {
		for (String fluid : new String[] {"plant_oil", "diesel", "gasoline", "sugar_mash", "ethanol", "glycerin", "used_oil",
			"coolant", "engine_oil", "kerosene"})
			assertNotEquals(WindowTint.AMBER, WindowTint.ofPath(fluid), fluid + " should have a tint of its own");
		assertEquals(WindowTint.AMBER, WindowTint.ofPath("water"));
		assertEquals(WindowTint.AMBER, WindowTint.ofPath("amber"), "amber is the fallback, not a fluid");
		assertEquals(WindowTint.AMBER, WindowTint.ofPath(""));
	}

	@Test
	void tintsAreOpaqueAndDistinct() {
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (WindowTint tint : WindowTint.values()) {
			assertEquals(0xFF, tint.colour() >>> 24, tint + " should be opaque");
			seen.add(tint.colour());
		}
		assertEquals(WindowTint.values().length, seen.size(), "no two tints share a colour");
	}

	@Test
	void anOutOfRangeBlockStateGivesAmberNotACrash() {
		assertEquals(WindowTint.AMBER, WindowTint.byOrdinal(-1));
		assertEquals(WindowTint.AMBER, WindowTint.byOrdinal(999));
		assertEquals(WindowTint.DIESEL, WindowTint.byOrdinal(WindowTint.DIESEL.ordinal()));
	}

	@Test
	void aTankReadsZeroEmptyAndFifteenOnlyFull() {
		assertEquals(0, TankSignal.of(0, 1000));
		assertEquals(1, TankSignal.of(1, 1000));
		assertEquals(8, TankSignal.of(500, 1000));
		assertEquals(14, TankSignal.of(999, 1000));
		assertEquals(15, TankSignal.of(1000, 1000));
		assertEquals(15, TankSignal.of(5000, 1000), "over capacity still reads 15");
		assertEquals(0, TankSignal.of(500, 0), "a zero-capacity tank reads 0, not a division error");
		assertEquals(0, TankSignal.of(-5, 1000));
	}
}
