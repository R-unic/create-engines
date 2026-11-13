package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MechanicalNoiseTest {

	@Test
	void aStillCrankMakesNoNoiseAtAll() {
		assertEquals(0, MechanicalNoise.clatterLevel(0, 0, 1), 0);
		assertEquals(0, MechanicalNoise.clatterLevel(0.5f, 0.4f, 0), 0);
		assertEquals(0, MechanicalNoise.squealLevel(0, 0), 0);
	}

	@Test
	void theValveGearTicksOverQuietlyAndGrowsWithSpeed() {
		float idle = MechanicalNoise.clatterLevel(2, 0, 1);
		float half = MechanicalNoise.clatterLevel(MechanicalNoise.CLATTER_FULL_RPM / 2, 0, 1);
		float full = MechanicalNoise.clatterLevel(MechanicalNoise.CLATTER_FULL_RPM, 0, 1);
		assertEquals(0.3f + 0.5f * 2 / MechanicalNoise.CLATTER_FULL_RPM, idle, 1e-6);
		assertEquals(0.55f, half, 1e-6);
		assertEquals(0.8f, full, 1e-6);
		assertEquals(full, MechanicalNoise.clatterLevel(1000, 0, 1), 1e-6, "no louder past full speed");
	}

	@Test
	void anEngineTurningBackwardsMakesTheSameNoise() {
		assertEquals(MechanicalNoise.clatterLevel(100, 0.1f, 0.5f), MechanicalNoise.clatterLevel(-100, 0.1f, 0.5f), 1e-6);
		assertEquals(MechanicalNoise.squealLevel(30, 0), MechanicalNoise.squealLevel(-30, 0), 1e-6);
		assertEquals(MechanicalNoise.clatterPitch(80), MechanicalNoise.clatterPitch(-80), 1e-6);
		assertEquals(MechanicalNoise.squealPitch(80), MechanicalNoise.squealPitch(-80), 1e-6);
	}

	@Test
	void aRoughEngineRattlesMoreButNeverPastFull() {
		float smooth = MechanicalNoise.clatterLevel(100, 0, 1);
		float rough = MechanicalNoise.clatterLevel(100, 0.4f, 1);
		assertEquals(smooth * 1.4f, rough, 1e-6);
		assertEquals(1, MechanicalNoise.clatterLevel(MechanicalNoise.CLATTER_FULL_RPM, 0.4f, 1), 1e-6);
		assertEquals(smooth, MechanicalNoise.clatterLevel(100, -1, 1), 1e-6, "a negative roughness takes nothing off");
	}

	@Test
	void bearingsWithOilOnThemDoNotSqueal() {
		assertEquals(0, MechanicalNoise.squealLevel(200, 1), 0);
		assertEquals(0, MechanicalNoise.squealLevel(200, MechanicalNoise.DRY_BELOW), 0);
	}

	@Test
	void squealGrowsAsTheOilRunsOutAndAsTheCrankSpeedsUp() {
		assertEquals(1, MechanicalNoise.squealLevel(MechanicalNoise.SQUEAL_FULL_RPM, 0), 1e-6);
		assertEquals(1, MechanicalNoise.squealLevel(500, 0), 1e-6);
		assertEquals(0.5f, MechanicalNoise.squealLevel(MechanicalNoise.SQUEAL_FULL_RPM, MechanicalNoise.DRY_BELOW / 2), 1e-6);
		assertEquals(0.5f, MechanicalNoise.squealLevel(MechanicalNoise.SQUEAL_FULL_RPM / 2, 0), 1e-6);
		assertEquals(1, MechanicalNoise.squealLevel(200, -2), 1e-6, "less than no oil is no oil");
		assertTrue(MechanicalNoise.squealLevel(100, 0.05f) > MechanicalNoise.squealLevel(100, 0.2f));
	}

	@Test
	void drySqueakAddsToTheClatter() {
		float oiled = MechanicalNoise.clatterLevel(MechanicalNoise.SQUEAL_FULL_RPM, 0, 1);
		float dry = MechanicalNoise.clatterLevel(MechanicalNoise.SQUEAL_FULL_RPM, 0, 0);
		assertEquals(oiled + 0.25f, dry, 1e-6);
		assertEquals(1, MechanicalNoise.clatterLevel(MechanicalNoise.CLATTER_FULL_RPM, 0, 0), 1e-6);
	}

	@Test
	void pitchRisesWithSpeedAndStopsAtCreatesUsualLimit() {
		assertEquals(0.6f, MechanicalNoise.clatterPitch(0), 1e-6);
		assertEquals(1.5f, MechanicalNoise.clatterPitch(256), 1e-6);
		assertEquals(1.5f, MechanicalNoise.clatterPitch(1024), 1e-6);
		assertTrue(MechanicalNoise.clatterPitch(128) > MechanicalNoise.clatterPitch(64));
		assertEquals(0.85f, MechanicalNoise.squealPitch(0), 1e-6);
		assertEquals(1.15f, MechanicalNoise.squealPitch(256), 1e-6);
		assertEquals(1.15f, MechanicalNoise.squealPitch(4096), 1e-6);
	}

	@Test
	void levelsStayInZeroToOne() {
		for (float rpm = -600; rpm <= 600; rpm += 7) {
			for (float roughness = 0; roughness <= 0.4f; roughness += 0.1f) {
				for (float oil = 0; oil <= 1; oil += 0.1f) {
					float level = MechanicalNoise.clatterLevel(rpm, roughness, oil);
					assertTrue(level >= 0 && level <= 1, rpm + "/" + roughness + "/" + oil);
					float squeal = MechanicalNoise.squealLevel(rpm, oil);
					assertTrue(squeal >= 0 && squeal <= 1, rpm + "/" + oil);
				}
			}
		}
	}
}
