package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Dyed casing is grey steel under a tint: every dye must give its own colour, bright enough to read, and in range. */
class CasingTintTest {

	@Test
	void everyDyeHasItsOwnTint() {
		assertEquals(16, CasingTint.dyes());
		Set<Integer> seen = new HashSet<>();
		for (int dye = 0; dye < CasingTint.dyes(); dye++) {
			int rgb = CasingTint.rgb(dye);
			assertTrue(rgb >= 0 && rgb <= 0xFFFFFF, "a colour in 24 bits: " + Integer.toHexString(rgb));
			assertTrue(seen.add(rgb), "dye " + dye + " repeats another dye's tint");
		}
	}

	@Test
	void aTintIsLiftedSoThePlateIsNotMuddy() {
		// Steel multiplies by the tint, so an unlifted dark dye would come out nearly black
		int red = CasingTint.rgb(14);
		assertTrue(red >> 16 > 0xB0, "red is lifted past the dye's own 0xB0");
		assertTrue((red >> 16) > ((red >> 8) & 0xFF) && (red >> 16) > (red & 0xFF), "and stays red");
		int black = CasingTint.rgb(15);
		assertNotEquals(0, black, "even black casing keeps its detail");
		assertTrue((black & 0xFF) < 0x80, "while staying dark");
	}

	@Test
	void whiteIsTheBrightestAndBlackTheDarkest() {
		int white = CasingTint.rgb(0);
		assertTrue(white >> 16 > 0xF0, "white is near full strength");
		for (int dye = 1; dye < CasingTint.dyes(); dye++)
			assertTrue(brightness(CasingTint.rgb(dye)) <= brightness(white), "dye " + dye + " outshines white");
		for (int dye = 0; dye < 15; dye++)
			assertTrue(brightness(CasingTint.rgb(dye)) >= brightness(CasingTint.rgb(15)), "dye " + dye + " is darker than black");
	}

	private static int brightness(int rgb) {
		return (rgb >> 16) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
	}
}
