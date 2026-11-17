package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Worked-out figures for {@link Thermal}, added after mutation testing showed its arithmetic was barely pinned. */
class ThermalExactTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** A coolant of a given quality that hands over the share of what is asked that it is told to, and records the request. */
	private static final class Tank implements CoolantSupply {
		final double quality;
		final double delivers;
		double asked;

		Tank(double quality, double delivers) {
			this.quality = quality;
			this.delivers = delivers;
		}

		@Override
		public double draw(double mb) {
			asked = mb;
			return mb * delivers;
		}

		@Override
		public double quality() {
			return quality;
		}
	}

	@Test
	void coldCostsABurnItsShareAndNeverMoreThanHalf() {
		assertEquals(0.88, Thermal.burnFactor(1, 1), 1e-12);
		assertEquals(1, Thermal.burnFactor(0, 1), 1e-12);
		assertEquals(0.88, Thermal.burnFactor(0.5, 2), 1e-12);
		assertEquals(1, Thermal.burnFactor(1, 0), 1e-12);
		assertEquals(0.5, Thermal.burnFactor(1, 10), 1e-12);
	}

	@Test
	void aStoppedBlockIsCoolingFromExactlyTheCoolingThreshold() {
		assertTrue(Thermal.isCooling(Thermal.COOLING_FROM, false));
		assertFalse(Thermal.isCooling(Math.nextDown(Thermal.COOLING_FROM), false));
		assertFalse(Thermal.isCooling(0.5, true));
		assertTrue(Thermal.isCooling(0.5, false));
	}

	@Test
	void theGlowRisesFromHalfToTheTopOfItsSpan() {
		assertEquals(0, Thermal.glow(0.4), 0);
		assertEquals(0, Thermal.glow(Thermal.GLOW_FROM), 0);
		assertEquals(0.5, Thermal.glow(Thermal.GLOW_FROM + Thermal.GLOW_SPAN / 2), 1e-12);
		assertEquals(1, Thermal.glow(Thermal.GLOW_FROM + Thermal.GLOW_SPAN), 1e-12);
		assertEquals(1, Thermal.glow(3), 0);
	}

	@Test
	void theContractionTickChanceScalesWithHeat() {
		assertEquals(0, Thermal.tickChance(Math.nextDown(Thermal.COOLING_FROM)), 0);
		assertEquals(0.004, Thermal.tickChance(Thermal.COOLING_FROM), 1e-12);
		assertEquals(0.004 + 0.045 * 0.5, Thermal.tickChance((Thermal.COOLING_FROM + Thermal.NORMAL) / 2), 1e-12);
		assertEquals(0.049, Thermal.tickChance(Thermal.NORMAL), 1e-12);
		assertEquals(0.049, Thermal.tickChance(5), 1e-12);
	}

	@Test
	void heatIntoTheBlockIsTheBlendOfExhaustAndFriction() {
		assertEquals(10 * 0.65 + 20 * 0.35, Thermal.heatIn(10, 20), 1e-12);
		assertEquals(0, Thermal.heatIn(0, 0), 0);
		assertEquals(6.5, Thermal.heatIn(10, 0), 1e-12);
		assertEquals(7, Thermal.heatIn(0, 20), 1e-12);
	}

	@Test
	void anOutsideHeatSourceHasFourLevelsAndIsClamped() {
		Thermal thermal = new Thermal(0);
		double[] expected = {0, 0.4, 0.9, 1.6};
		for (int level = 0; level < expected.length; level++) {
			thermal.setPreheat(level);
			assertEquals(expected[level], thermal.preheat(), 0, "level " + level);
		}
		thermal.setPreheat(-3);
		assertEquals(0, thermal.preheat(), 0);
		thermal.setPreheat(9);
		assertEquals(1.6, thermal.preheat(), 0);
	}

	@Test
	void theStartersAssistIsAShareAndTheTemperatureNeverGoesBelowZero() {
		Thermal thermal = new Thermal(-5);
		assertEquals(0, thermal.temperature(), 0);
		thermal.setTemperature(-1);
		assertEquals(0, thermal.temperature(), 0);
		thermal.setTemperature(0.4);
		assertEquals(0.4, thermal.temperature(), 0);
		thermal.setCrankAssist(0.25);
		assertEquals(0.25, thermal.crankAssist(), 0);
		thermal.setCrankAssist(7);
		assertEquals(1, thermal.crankAssist(), 0);
		thermal.setCrankAssist(-7);
		assertEquals(0, thermal.crankAssist(), 0);
	}

	@Test
	void anEngineOnASteadyLoadStaysAtNormalAndAColdOneWarmsAtTheDocumentedRate() {
		Thermal steady = new Thermal(Thermal.NORMAL);
		steady.update(2.5, 1, 1, CoolantSupply.NONE);
		assertEquals(Thermal.NORMAL, steady.temperature(), 1e-9);

		// Stone cold, one cylinder sending 1 out of the exhaust: light = 1 - 1/2.5, assist 0.8, no loss yet
		Thermal cold = new Thermal(0);
		cold.update(1, 1, 1, CoolantSupply.NONE);
		assertEquals(1 * (1 + 0.8 * 1 * 0.6) / 60, cold.temperature(), 1e-12);

		// Two cylinders sharing the same heat per cylinder warm at the same rate per cylinder
		Thermal twin = new Thermal(0);
		twin.update(2, 2, 1, CoolantSupply.NONE);
		assertEquals(2 * (1 + 0.8 * 0.6) / 120, twin.temperature(), 1e-12);

		// A block with no cylinders still counts one, and a source touching it warms it as well
		Thermal hot = new Thermal(0);
		hot.setPreheat(3);
		hot.update(0, 0, 1, CoolantSupply.NONE);
		assertEquals(1.6 / 60, hot.temperature(), 1e-12);
		Thermal pair = new Thermal(0);
		pair.setPreheat(3);
		pair.update(0, 2, 1, CoolantSupply.NONE);
		assertEquals(1.6 * 2 / 120, pair.temperature(), 1e-12);

		// A hot engine with nothing coming in sheds heat to the air, faster the hotter it is
		Thermal cooling = new Thermal(0.6);
		cooling.update(0, 1, 1, CoolantSupply.NONE);
		assertEquals(0.6 - Thermal.PASSIVE * 0.36 / 60, cooling.temperature(), 1e-12);
		Thermal flat = new Thermal(0.01);
		flat.update(0, 1, 1000, CoolantSupply.NONE);
		assertEquals(0, flat.temperature() < 0.01 ? 0 : 1, 0);
		assertTrue(flat.temperature() >= 0);
	}

	@Test
	void coolantCarriesHeatAwayAndIsSpentForIt() {
		Tank full = new Tank(1, 1);
		Thermal thermal = new Thermal(0.6);
		thermal.update(0, 1, 1, full);
		double carried = 100 * 1 * 1 * (0.6 - Thermal.THERMOSTAT);
		assertEquals(carried * 0.15 / 1 * 1, full.asked, 1e-12);
		assertEquals(0.6 - (Thermal.PASSIVE * 0.36 + carried) / 60, thermal.temperature(), 1e-12);
		assertEquals(carried * 0.15, thermal.coolantRate(), 1e-12);
		assertEquals(1, thermal.coolantQuality(), 0);

		// A tank that gives half of what is asked carries half the heat; the rate is what it actually gave
		Tank half = new Tank(1, 0.5);
		Thermal partial = new Thermal(0.6);
		partial.update(0, 1, 1, half);
		assertEquals(0.6 - (Thermal.PASSIVE * 0.36 + carried * 0.5) / 60, partial.temperature(), 1e-12);
		assertEquals(carried * 0.15 * 0.5, partial.coolantRate(), 1e-12);
		assertEquals(1, partial.coolantQuality(), 0);

		// A dry tank carries nothing and reports no coolant at work
		Tank dry = new Tank(1, 0);
		Thermal parched = new Thermal(0.6);
		parched.update(0, 1, 1, dry);
		assertEquals(0.6 - Thermal.PASSIVE * 0.36 / 60, parched.temperature(), 1e-12);
		assertEquals(0, parched.coolantRate(), 0);
		assertEquals(0, parched.coolantQuality(), 0);

		// Poorer coolant (quality 0.5) carries half the heat and costs the same per unit carried, over a longer stretch of time
		Tank water = new Tank(0.5, 1);
		Thermal wet = new Thermal(0.6);
		wet.update(0, 1, 2, water);
		double carriedWater = 100 * 1 * 0.5 * 0.2;
		assertEquals(carriedWater * 0.15 / 0.5 * 2, water.asked, 1e-12);
		assertEquals(0.5, wet.coolantQuality(), 0);
		assertEquals(carriedWater * 0.15 / 0.5, wet.coolantRate(), 1e-12);

		// At or below the thermostat nothing is drawn at all
		Tank idle = new Tank(1, 1);
		Thermal mild = new Thermal(Thermal.THERMOSTAT);
		mild.update(0, 1, 1, idle);
		assertEquals(0, idle.asked, 0);
		assertEquals(0, mild.coolantRate(), 0);
		assertEquals(0, mild.coolantQuality(), 0);
	}

	@Test
	void coldDragAndTheCoolingBonusFollowTheTemperature() {
		assertEquals(1, new Thermal(0).cold(), 1e-12);
		assertEquals(0.5, new Thermal(0.15).cold(), 1e-12);
		assertEquals(0, new Thermal(Thermal.COLD_BELOW).cold(), 1e-12);
		assertEquals(0, new Thermal(0.9).cold(), 0);

		Thermal chilly = new Thermal(0);
		chilly.setCrankAssist(0.5);
		assertEquals(0.5, chilly.felt(), 1e-12);
		assertEquals(1 + 0.25 * 0.5, chilly.drag(), 1e-12);
		chilly.setCrankAssist(0);
		assertEquals(1.25, chilly.drag(), 1e-12);
		assertEquals(1, new Thermal(0.7).drag(), 1e-12);

		assertEquals(0, new Thermal(Thermal.COOL_NONE).cool(), 1e-12);
		assertEquals(1, new Thermal(Thermal.COOL_FULL).cool(), 1e-12);
		assertEquals(0.5, new Thermal(0.55).cool(), 1e-12);
		assertEquals(0.5, new Thermal(0.35).cool(), 1e-12);
		assertEquals(0, new Thermal(Thermal.COLD_BELOW).cool(), 1e-12);
		assertEquals(0, new Thermal(0.9).cool(), 0);

		Thermal best = new Thermal(Thermal.COOL_FULL);
		assertEquals(0.97, best.chargeFactor(), 1e-12);
		Tuning.coolingBonus = 0;
		assertEquals(1, best.chargeFactor(), 1e-12);
		Tuning.coolingBonus = 2;
		assertEquals(0.94, best.chargeFactor(), 1e-12);
	}

	@Test
	void withoutEngineTemperatureNothingIsColdOrCool() {
		Tuning.engineTemperature = false;
		assertEquals(0, new Thermal(0).cold(), 0);
		assertEquals(0, new Thermal(0).felt(), 0);
		assertEquals(1, new Thermal(0).drag(), 0);
		assertEquals(0, new Thermal(Thermal.COOL_FULL).cool(), 0);
		assertEquals(1, new Thermal(Thermal.COOL_FULL).chargeFactor(), 0);
	}
}
