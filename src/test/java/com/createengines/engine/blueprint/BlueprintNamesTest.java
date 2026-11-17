package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class BlueprintNamesTest {

	@Test
	void anOrdinaryNameBecomesALowerCaseStem() {
		assertEquals("little_six", BlueprintNames.fileName("Little Six"));
		assertEquals("v12_2", BlueprintNames.fileName("V12 #2"));
		assertEquals("a_b", BlueprintNames.fileName("  a --- b  "));
	}

	@Test
	void nothingUsableLeavesTheDefault() {
		for (String name : new String[] { "", "   ", "---", "\u00e9\u00e8", "\n\t", "../.." })
			assertEquals("engine", BlueprintNames.fileName(name), "name '" + name + "'");
	}

	@Test
	void noNameCanReachOutOfItsFolder() {
		for (String name : new String[] { "../../etc/passwd", "..\\..\\windows\\system32", "a/b", "c:\\x", "x\u0000y", "con:", "~/.ssh/key" }) {
			String stem = BlueprintNames.fileName(name);
			assertFalse(stem.contains("/") || stem.contains("\\") || stem.contains(".") || stem.contains(":")
				|| stem.contains("~") || stem.contains("\u0000"), "stem '" + stem + "' from '" + name + "'");
			assertEquals(stem, stem.toLowerCase());
		}
	}

	@Test
	void aVeryLongNameStaysAName() {
		String stem = BlueprintNames.fileName("x".repeat(10_000));
		assertEquals(10_000, stem.length());
	}
}
