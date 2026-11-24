package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The masses a pack's or an addon's own flywheel can carry: in range, or refused with a message that says why. */
class FlywheelSpecTest {

	@Test
	void takesAnyMassFromNothingUpToTheServersOwnLimit() {
		assertEquals(0, new FlywheelSpec(0, 0).primaryInertia());
		FlywheelSpec most = new FlywheelSpec(FlywheelSpec.MAX_INERTIA, FlywheelSpec.MAX_INERTIA);
		assertEquals(FlywheelSpec.MAX_INERTIA, most.primaryInertia());
		assertEquals(FlywheelSpec.MAX_INERTIA, most.secondaryInertia());
		assertEquals(100.0, FlywheelSpec.MAX_INERTIA, "the same limit as the server's flywheel settings");
	}

	@Test
	void refusesAMassThatIsNegativeTooBigOrNotANumber() {
		double[] bad = {-0.001, -1, 100.001, 1e300, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (double value : bad) {
			IllegalArgumentException primary = assertThrows(IllegalArgumentException.class, () -> new FlywheelSpec(value, 0));
			assertTrue(primary.getMessage().contains("primary_inertia"), primary.getMessage());
			IllegalArgumentException secondary = assertThrows(IllegalArgumentException.class, () -> new FlywheelSpec(1, value));
			assertTrue(secondary.getMessage().contains("secondary_inertia"), secondary.getMessage());
		}
	}

	@Test
	void noEntryAddsNothing() {
		assertEquals(0, FlywheelSpec.NONE.primaryInertia());
		assertEquals(0, FlywheelSpec.NONE.secondaryInertia());
	}
}
