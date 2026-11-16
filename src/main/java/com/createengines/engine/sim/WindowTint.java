package com.createengines.engine.sim;

import java.util.Locale;

/**
 * The colour a refining column's sight windows glow while it works: the colour of the fluid in that part of the column.
 * Our own fluids have their own entry and anything else (another mod's feed or product) glows amber. Pure, so the
 * names and colours are tested without Minecraft.
 */
public enum WindowTint {
	AMBER(0xFFE8A030), PLANT_OIL(0xFF8A5E22), DIESEL(0xFFD9BA3B), GASOLINE(0xFFF0DC84), SUGAR_MASH(0xFFC9A765),
	ETHANOL(0xFFD2F0F4), GLYCERIN(0xFFEBE2AA), USED_OIL(0xFF8E8882), COOLANT(0xFF6CE02E), ENGINE_OIL(0xFFE5A829);

	private final int colour;

	WindowTint(int colour) {
		this.colour = colour;
	}

	/** The glow as ARGB. */
	public int colour() {
		return colour;
	}

	/** The tint for one of our fluids by its registry path ("diesel"), amber for any other. */
	public static WindowTint ofPath(String path) {
		for (WindowTint tint : values())
			if (tint != AMBER && tint.name().toLowerCase(Locale.ROOT).equals(path))
				return tint;
		return AMBER;
	}

	/** The tint with this ordinal, amber for a number out of range (a hand-edited block state). */
	public static WindowTint byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : AMBER;
	}
}
