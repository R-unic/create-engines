package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Naphtha, the light cut other mods (Create: The Factory Must Grow) refine: a spark fuel just under gasoline. */
class NaphthaFuelTest {

	/** {@code CEDataMaps.PILOT_IGNITION_LIMIT}, which a unit test cannot load. */
	private static final double PILOT_IGNITION_LIMIT = 2.0;

	@Test
	void naphthaSitsBetweenDieselAndKeroseneInEnergy() {
		assertTrue(FuelSpec.NAPHTHA.energyPerMb() < FuelSpec.KEROSENE.energyPerMb());
		assertTrue(FuelSpec.NAPHTHA.energyPerMb() > FuelSpec.DIESEL.energyPerMb());
	}

	@Test
	void naphthaNeedsASparkAndIsNoPilotFuel() {
		assertTrue(FuelSpec.NAPHTHA.autoignitionTemp() > PILOT_IGNITION_LIMIT);
		assertTrue(FuelSpec.NAPHTHA.autoignitionTemp() > FuelSpec.GASOLINE.autoignitionTemp());
	}

	@Test
	void naphthaSpillsLightLikeGasoline() {
		assertEquals(FuelSpec.GASOLINE.flammability(), FuelSpec.NAPHTHA.flammability());
	}
}
