package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** A fuel a pack writes is looked over for impossible numbers by the key the pack maker wrote, and nothing is changed. */
class FuelCheckTest {

	private static FuelSpec with(double energy, double ignition, double air, double cold, double cooling, double soot,
		double preignition, double sulfur) {
		return new FuelSpec(energy, ignition, air, cold, cooling, soot, preignition, sulfur);
	}

	private static final FuelSpec SOUND = with(36, 1.55, 1.7, 1, 0, 1, 1.55, 1);

	@Test
	void everyShippedFuelIsSoundAndUsual() {
		for (FuelSpec fuel : List.of(FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL)) {
			assertEquals(List.of(), FuelCheck.problems(fuel), fuel.toString());
			assertEquals(List.of(), FuelCheck.unusual(fuel), fuel.toString());
		}
	}

	@Test
	void eachImpossibleNumberIsNamedByItsDataMapKey() {
		assertTrue(FuelCheck.problems(with(0, 1.55, 1.7, 1, 0, 1, 1.55, 1)).get(0).startsWith("energy_per_mb"));
		assertTrue(FuelCheck.problems(with(-3, 1.55, 1.7, 1, 0, 1, 1.55, 1)).get(0).startsWith("energy_per_mb"));
		assertTrue(FuelCheck.problems(with(36, 0, 1.7, 1, 0, 1, 1.55, 1)).get(0).startsWith("autoignition_temperature"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 0, 1, 0, 1, 1.55, 1)).get(0).startsWith("air_per_mb"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 1.7, -1, 0, 1, 1.55, 1)).get(0).startsWith("cold_sensitivity"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 1.7, 1, -0.1, 1, 1.55, 1)).get(0).startsWith("charge_cooling"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 1.7, 1, 0, -1, 1.55, 1)).get(0).startsWith("soot_factor"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 1.7, 1, 0, 1, 0, 1)).get(0).startsWith("preignition_temperature"));
		assertTrue(FuelCheck.problems(with(36, 1.55, 1.7, 1, 0, 1, 1.55, -1)).get(0).startsWith("sulfur"));
	}

	@Test
	void zeroIsFineWhereTheSimulationAllowsIt() {
		// Ethanol has no soot; a fuel may be cold-proof, not cool its charge and have no sulfur
		assertEquals(List.of(), FuelCheck.problems(with(36, 1.55, 1.7, 0, 0, 0, 1.55, 0)));
	}

	@Test
	void aNumberThatIsNotANumberOrIsInfiniteIsAProblem() {
		assertEquals(1, FuelCheck.problems(with(Double.NaN, 1.55, 1.7, 1, 0, 1, 1.55, 1)).size());
		assertEquals(1, FuelCheck.problems(with(36, 1.55, Double.POSITIVE_INFINITY, 1, 0, 1, 1.55, 1)).size());
		assertEquals(1, FuelCheck.problems(with(36, 1.55, 1.7, Double.NaN, 0, 1, 1.55, 1)).size());
	}

	@Test
	void everyProblemIsReportedAtOnce() {
		assertEquals(3, FuelCheck.problems(with(0, 0, 0, 1, 0, 1, 1, 1)).size());
	}

	@Test
	void aSlipOfADecimalPlaceIsUnusualButNotAProblem() {
		FuelSpec thin = with(360, 1.55, 1.7, 1, 0, 1, 1.55, 1);
		assertEquals(List.of(), FuelCheck.problems(thin));
		assertEquals(1, FuelCheck.unusual(thin).size());
		assertTrue(FuelCheck.unusual(thin).get(0).startsWith("energy_per_mb"));
		assertEquals(1, FuelCheck.unusual(with(36, 1.55, 17, 1, 0, 1, 1.55, 1)).size());
		assertEquals(List.of(), FuelCheck.unusual(SOUND));
	}

	@Test
	void theUnusualRangesIncludeTheirEdges() {
		for (double energy : new double[] { FuelCheck.UNUSUAL_ENERGY_LOW, FuelCheck.UNUSUAL_ENERGY_HIGH, 36 })
			assertEquals(List.of(), FuelCheck.unusual(with(energy, 1.55, 1.7, 1, 0, 1, 1.55, 1)), "energy " + energy);
		for (double energy : new double[] { FuelCheck.UNUSUAL_ENERGY_LOW - 0.01, FuelCheck.UNUSUAL_ENERGY_HIGH + 0.01 })
			assertEquals(1, FuelCheck.unusual(with(energy, 1.55, 1.7, 1, 0, 1, 1.55, 1)).size(), "energy " + energy);
		for (double air : new double[] { FuelCheck.UNUSUAL_AIR_LOW, FuelCheck.UNUSUAL_AIR_HIGH, 1.7 })
			assertEquals(List.of(), FuelCheck.unusual(with(36, 1.55, air, 1, 0, 1, 1.55, 1)), "air " + air);
		for (double air : new double[] { FuelCheck.UNUSUAL_AIR_LOW - 0.01, FuelCheck.UNUSUAL_AIR_HIGH + 0.01 })
			assertEquals(1, FuelCheck.unusual(with(36, 1.55, air, 1, 0, 1, 1.55, 1)).size(), "air " + air);
		// Zero is a problem, not an oddity
		assertEquals(List.of(), FuelCheck.unusual(with(0, 1.55, 1.7, 1, 0, 1, 1.55, 1)));
		assertEquals(List.of(), FuelCheck.unusual(with(36, 1.55, 0, 1, 0, 1, 1.55, 1)));
	}

	@Test
	void anImpossibleNumberIsNotAlsoCalledUnusual() {
		assertEquals(List.of(), FuelCheck.unusual(with(-3, 1.55, 0, 1, 0, 1, 1.55, 1)));
	}
}
