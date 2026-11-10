package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.content.effects.ExhaustLook.Engine;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.Thermal;

/** An engine's smoke says how it is burning, and its machinery is as loud as it is fast and dry. */
class ExhaustLookTest {

	private static final float WARM = (float) Thermal.NORMAL;
	/** The faintest a particle can be and still be drawn: the game's particle shader throws away anything fainter. */
	private static final float VISIBLE = 0.1f;

	private static Engine fired(boolean rich, boolean lean, float mixture, float load, float temperature) {
		return new Engine(CycleResult.FIRED, rich, lean, mixture, load, 0.5f, temperature, 0, true, false);
	}

	private static float brightness(ExhaustLook look) {
		return (look.red() + look.green() + look.blue()) / 3;
	}

	@Test
	void nothingComesOutOfACylinderThatBurnedNothing() {
		for (CycleResult result : new CycleResult[] {CycleResult.NONE, CycleResult.NO_FUEL, CycleResult.NO_AIR})
			assertEquals(0, ExhaustLook.of(new Engine(result, false, false, Float.NaN, 0.5f, 0.5f, WARM, 0, true, false))
				.puffs(), result + " leaves nothing to see");
	}

	@Test
	void aRichMixtureSmokesBlackerAndThickerTheRicherItIs() {
		ExhaustLook clean = ExhaustLook.of(fired(false, false, 1, 0.8f, WARM));
		ExhaustLook rich = ExhaustLook.of(fired(true, false, 1.4f, 0.8f, WARM));
		ExhaustLook drowned = ExhaustLook.of(fired(true, false, 2, 0.8f, WARM));
		assertTrue(brightness(rich) < brightness(clean), "rich smoke is darker than clean");
		assertTrue(brightness(drowned) < brightness(rich), "and darker the richer it runs");
		assertTrue(rich.alpha() > clean.alpha() && drowned.alpha() >= rich.alpha(), "and thicker");
		assertTrue(drowned.puffs() > clean.puffs(), "and there is more of it");
	}

	@Test
	void aCleanEngineShowsAHazeThatGrowsWithLoadButNeverVanishes() {
		ExhaustLook idle = ExhaustLook.of(fired(false, false, 1, 0, WARM));
		ExhaustLook loaded = ExhaustLook.of(fired(false, false, 1, 1, WARM));
		assertTrue(idle.puffs() >= 1 && idle.alpha() > 2 * VISIBLE, "an idling engine still shows it is running");
		assertTrue(loaded.alpha() > idle.alpha() && loaded.scale() > idle.scale() && loaded.speed() > idle.speed(),
			"a working engine pushes out more, faster");
		ExhaustLook lean = ExhaustLook.of(fired(false, true, 0.7f, 0.5f, WARM));
		ExhaustLook balanced = ExhaustLook.of(fired(false, false, 1, 0.5f, WARM));
		assertTrue(brightness(lean) > brightness(balanced) && lean.alpha() < balanced.alpha(),
			"a lean one burns cleanest of all");
	}

	@Test
	void aColdEngineBlowsWhiteVapourUntilItWarms() {
		ExhaustLook cold = ExhaustLook.of(fired(false, false, 1, 0.5f, 0));
		ExhaustLook warm = ExhaustLook.of(fired(false, false, 1, 0.5f, WARM));
		assertTrue(brightness(cold) > 0.75f && brightness(cold) > brightness(warm), "condensation is white");
		assertTrue(cold.lifetime() > warm.lifetime() && cold.lift() < warm.lift(), "and hangs about where hot gas climbs");
		ExhaustLook justWarm = ExhaustLook.of(fired(false, false, 1, 0.5f, (float) Thermal.COLD_BELOW));
		assertEquals(brightness(warm), brightness(justWarm), 1e-4, "it is gone once the block is no longer cold");
	}

	@Test
	void fuelInTheOilBurnsBlue() {
		ExhaustLook thinned = ExhaustLook.of(new Engine(CycleResult.FIRED, false, false, 1, 0.5f, 0.5f, WARM, 0.4f, true, false));
		ExhaustLook sound = ExhaustLook.of(fired(false, false, 1, 0.5f, WARM));
		assertTrue(thinned.blue() > thinned.red() + 0.1f, "a blue cast");
		assertEquals(sound.blue(), sound.red(), 1e-6, "which clean oil does not give");
	}

	@Test
	void unburnedFuelPopsOnlyInAHotPipe() {
		for (CycleResult result : new CycleResult[] {CycleResult.NO_SPARK, CycleResult.TOO_RICH, CycleResult.TOO_LEAN,
			CycleResult.FOULED, CycleResult.NO_IGNITION}) {
			ExhaustLook hot = ExhaustLook.of(new Engine(result, false, false, 1.6f, 0.5f, 0.5f, WARM, 0, true, false));
			assertTrue(hot.puffs() > 0 && hot.pop() > 0.1f, result + ": fuel meets a hot pipe");
			ExhaustLook cranking = ExhaustLook.of(new Engine(result, false, false, 1.6f, 0.5f, 0.2f, WARM, 0, false, false));
			assertEquals(0, cranking.pop(), result + ": nothing else is firing, so nothing lights it");
			ExhaustLook cold = ExhaustLook.of(new Engine(result, false, false, 1.6f, 0.5f, 0.5f, 0.1f, 0, true, false));
			assertEquals(0, cold.pop(), result + ": a cold pipe lights nothing");
		}
	}

	@Test
	void onlyARichEngineWorkingHardThrowsFlame() {
		assertEquals(0, ExhaustLook.of(fired(false, false, 1, 1, WARM))
			.flame(), "a clean burn leaves nothing to light");
		assertEquals(0, ExhaustLook.of(fired(true, false, 1.8f, 0.1f, WARM))
			.flame(), "an idling one hasn't the heat");
		ExhaustLook flaming = ExhaustLook.of(fired(true, false, 1.8f, 1, WARM));
		assertTrue(flaming.flame() > 0.1f && flaming.pop() > 0 && flaming.pop() < flaming.flame(),
			"rich and flat out: flame often, a crack now and then");
	}

	@Test
	void anEngineThatHasJustCaughtCoughsOutADarkCloud() {
		ExhaustLook running = ExhaustLook.of(fired(false, false, 1, 0.2f, 0.1f));
		ExhaustLook catching = ExhaustLook.of(new Engine(CycleResult.FIRED, false, false, 1, 0.2f, 0.5f, 0.1f, 0, true, true));
		assertTrue(catching.puffs() >= running.puffs() + 3 && catching.scale() > running.scale()
			&& catching.alpha() >= 0.8f && brightness(catching) < brightness(running), "more, bigger, thicker, darker");
	}

	@Test
	void everyLookStaysInRange() {
		for (CycleResult result : CycleResult.values())
			for (float load : new float[] {0, 0.5f, 1.3f})
				for (float temperature : new float[] {0, 0.3f, 0.7f, 1.2f})
					for (float mixture : new float[] {Float.NaN, 0.5f, 1, 3})
						for (int flags = 0; flags < 8; flags++) {
							ExhaustLook look = ExhaustLook.of(new Engine(result, (flags & 1) != 0, (flags & 2) != 0, mixture,
								load, 2, temperature, 0.5f, true, (flags & 4) != 0));
							if (look.puffs() == 0)
								continue;
							for (float part : new float[] {look.red(), look.green(), look.blue(), look.flame(), look.pop()})
								assertTrue(part >= 0 && part <= 1, "out of range: " + look);
							assertTrue(look.alpha() > VISIBLE && look.alpha() <= 1 && look.puffs() <= 7 && look.lifetime() > 0
								&& look.speed() > 0 && look.speed() < 0.3f && look.scale() > 0.5f && look.scale() < 3,
								"out of range: " + look);
						}
	}

	@Test
	void valveGearTicksWithTheCrankAndRattlesWhenRoughOrDry() {
		assertEquals(0, MechanicalNoise.clatterLevel(0, 0, 1), "a stopped engine is silent");
		float idle = MechanicalNoise.clatterLevel(16, 0, 1);
		float fast = MechanicalNoise.clatterLevel(200, 0, 1);
		assertTrue(idle > 0.2f && fast > idle && fast <= 1, "it ticks over, and louder at speed");
		assertTrue(MechanicalNoise.clatterLevel(100, 0.3f, 1) > MechanicalNoise.clatterLevel(100, 0, 1), "rougher is louder");
		assertTrue(MechanicalNoise.clatterLevel(100, 0, 0) > MechanicalNoise.clatterLevel(100, 0, 1), "so is dry");
		assertTrue(MechanicalNoise.clatterPitch(256) > MechanicalNoise.clatterPitch(16), "and it ticks faster");
		assertEquals(MechanicalNoise.clatterLevel(-100, 0, 1), MechanicalNoise.clatterLevel(100, 0, 1), "either way round");
	}

	@Test
	void bearingsSquealOnlyWhenDryAndTurning() {
		assertEquals(0, MechanicalNoise.squealLevel(100, 1));
		assertEquals(0, MechanicalNoise.squealLevel(100, MechanicalNoise.DRY_BELOW), "oiled enough");
		assertEquals(0, MechanicalNoise.squealLevel(0, 0), "nothing turning");
		assertEquals(1, MechanicalNoise.squealLevel(100, 0), 1e-6, "bone dry at speed");
		assertTrue(MechanicalNoise.squealLevel(100, 0.15f) < MechanicalNoise.squealLevel(100, 0.05f), "worse the drier");
		assertTrue(MechanicalNoise.squealLevel(10, 0) < MechanicalNoise.squealLevel(100, 0), "and the faster");
	}
}
