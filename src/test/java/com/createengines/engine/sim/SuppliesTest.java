package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SuppliesTest {

	@Test
	void noOilSupplyDeliversNothingHoweverMuchIsAsked() {
		assertEquals(0, OilSupply.NONE.draw(0), 0);
		assertEquals(0, OilSupply.NONE.draw(1e9), 0);
	}

	@Test
	void noFuelSupplyDeliversNothingAndHasNoFuel() {
		assertEquals(0, FuelSupply.NONE.draw(50), 0);
		assertNull(FuelSupply.NONE.fuel());
	}

	@Test
	void noCoolantSupplyDeliversNothingAndCoolsNothing() {
		assertEquals(0, CoolantSupply.NONE.draw(50), 0);
		assertEquals(0, CoolantSupply.NONE.quality(), 0);
	}
}
