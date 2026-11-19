package com.createengines.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 2.0 regrouped the config once; no setting resets. */
class ConfigCarryOverTest {

	private static List<String> keys(String file) throws IOException {
		return Files.readAllLines(Path.of("src/test/resources/compat/" + file))
			.stream()
			.filter(line -> !line.isBlank())
			.toList();
	}

	@Test
	void everyServerKeyTheFirstVersionShippedHasANewHome() throws IOException {
		for (String key : keys("config-keys-1x-server.txt"))
			assertTrue(ConfigCarryOver.SERVER.containsKey(key), key + " would reset");
		assertEquals(keys("config-keys-1x-server.txt").size(), ConfigCarryOver.SERVER.size());
	}

	@Test
	void everyClientKeyTheFirstVersionShippedHasANewHome() throws IOException {
		for (String key : keys("config-keys-1x-client.txt"))
			assertTrue(ConfigCarryOver.CLIENT.containsKey(key), key + " would reset");
		assertEquals(keys("config-keys-1x-client.txt").size(), ConfigCarryOver.CLIENT.size());
	}

	@Test
	void noTwoOldKeysShareANewOne() {
		Set<String> seen = new HashSet<>();
		for (String now : ConfigCarryOver.SERVER.values())
			assertTrue(seen.add(now), now);
		seen.clear();
		for (String now : ConfigCarryOver.CLIENT.values())
			assertTrue(seen.add(now), now);
	}

	@Test
	void valuesFollowTheirKeysAndKeepTheirTypes() {
		Map<String, Object> old = new LinkedHashMap<>();
		old.put("engine.stressPerTorque", 40.0);
		old.put("engine.maxCrankSegments", 128);
		old.put("balance.fuelConsumption", 0.6);
		old.put("features.sparkFouling", false);
		ConfigCarryOver.Result result = ConfigCarryOver.carry(ConfigCarryOver.SERVER, old);
		assertEquals(40.0, result.moved().get("calibration.stressPerTorque"));
		assertEquals(128, result.moved().get("limits.maxCrankSegments"));
		assertEquals(0.6, result.moved().get("balance.fuelConsumption"));
		assertEquals(false, result.moved().get("features.sparkFouling"));
		assertTrue(result.skipped().isEmpty());
	}

	@Test
	void anUnknownKeyIsLeftBehindAndSaid() {
		Map<String, Object> old = new LinkedHashMap<>();
		old.put("engine.somethingElse", 1);
		old.put("balance.fuelConsumption", 2.0);
		ConfigCarryOver.Result result = ConfigCarryOver.carry(ConfigCarryOver.SERVER, old);
		assertEquals(List.of("engine.somethingElse"), result.skipped());
		assertEquals(1, result.moved().size());
	}

	@Test
	void anEmptyOrNullFileCarriesNothing() {
		assertTrue(ConfigCarryOver.carry(ConfigCarryOver.CLIENT, Map.of()).moved().isEmpty());
		Map<String, Object> old = new LinkedHashMap<>();
		old.put("effects.bangVolume", null);
		assertFalse(ConfigCarryOver.carry(ConfigCarryOver.CLIENT, old).moved().containsKey("volume.bang"));
	}

	@Test
	void theNewNamesAreTheGroupedOnes() {
		assertEquals("volume.bang", ConfigCarryOver.CLIENT.get("effects.bangVolume"));
		assertEquals("effects.particles", ConfigCarryOver.CLIENT.get("effects.engineParticles"));
		assertEquals("limits.maxCouplingWalk", ConfigCarryOver.SERVER.get("engine.maxCouplingWalk"));
	}
}
