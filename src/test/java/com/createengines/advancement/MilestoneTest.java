package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MilestoneTest {

	@Test
	void anIdIsTheLowerCaseName() {
		assertEquals("engine_running", Milestone.ENGINE_RUNNING.id());
		assertEquals("cranking", Milestone.CRANKING.id());
	}

	@Test
	void idsAreUniqueAndSafeForAResourcePath() {
		Set<String> seen = new HashSet<>();
		for (Milestone milestone : Milestone.values()) {
			String id = milestone.id();
			assertTrue(id.matches("[a-z0-9_]+"), id);
			assertTrue(seen.add(id), "duplicate id " + id);
		}
		assertEquals(Milestone.values().length, seen.size());
	}
}
