package com.createengines.content;

/**
 * The colours dyed engine casing is drawn in. The casing texture is grey steel, and a block tint multiplies it, so a
 * dye's own colour would come out dark and muddy; the tint is the dye lifted (see {@link #GAMMA}) until the plate reads in
 * the dye's colour while keeping its rivets and shading. Kept free of Minecraft types so the numbers can be tested.
 */
public final class CasingTint {

	/** Minecraft's texture-diffuse colours of the sixteen dyes, in dye order (white to black). */
	private static final int[] DYES = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
		0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

	/**
	 * The exponent that lifts the dye: below 1 it brightens dark dyes the most and leaves white alone, so the order of
	 * the dyes by brightness is kept.
	 */
	private static final double GAMMA = 0.7;

	private CasingTint() {
	}

	/** The number of dyes casing can take. */
	public static int dyes() {
		return DYES.length;
	}

	/** The tint for a dye (0 white to 15 black), as 0xRRGGBB. */
	public static int rgb(int dye) {
		int colour = DYES[dye];
		return channel(colour >> 16) << 16 | channel(colour >> 8) << 8 | channel(colour);
	}

	private static int channel(int value) {
		return (int) Math.min(255, Math.round(255 * Math.pow((value & 0xFF) / 255.0, GAMMA)));
	}
}
