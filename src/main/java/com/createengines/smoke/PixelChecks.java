package com.createengines.smoke;

/**
 * Judges a screenshot without a person looking at it: is it a flat colour (nothing rendered), does it hold Minecraft's
 * missing-texture magenta, is it nearly black, did anything change since the last frame. Works on plain ARGB pixel
 * arrays so it needs no game and can be unit tested with synthetic images.
 *
 * <p>These are blunt instruments by design. They cannot say a picture looks <em>right</em>; they catch the failures
 * that make a picture obviously wrong, which is where unattended runs earn their keep.
 */
public final class PixelChecks {

	/** The pink of Minecraft's missing texture. */
	public static final int MISSING_MAGENTA = 0xF800F8;

	public record Stats(int width, int height, double dominantFraction, double magentaFraction, double meanLuma,
		double lumaDeviation, int distinctColours) {

		/** Nothing but one colour covers the frame: a black screen, a sky, or a world that never drew. */
		public boolean blank() {
			return dominantFraction > 0.995;
		}

		/** More than a speck of the frame is the missing-texture pink. */
		public boolean hasMissingTexture() {
			return magentaFraction > 0.0004;
		}

		public boolean nearlyBlack() {
			return meanLuma < 6;
		}
	}

	private PixelChecks() {
	}

	/** Pixel colour as {@code 0xRRGGBB} ignoring alpha, so {@code argb} and {@code abgr} callers can both use it. */
	private static int rgb(int pixel) {
		return pixel & 0xFFFFFF;
	}

	/**
	 * Analyses {@code pixels} (row major, {@code width * height} of them, {@code 0xAARRGGBB}). Callers holding
	 * Minecraft's {@code NativeImage} ABGR values should swap red and blue first ({@link #fromAbgr}).
	 */
	public static Stats analyse(int[] pixels, int width, int height) {
		if (width <= 0 || height <= 0 || pixels.length < width * height)
			throw new IllegalArgumentException("pixel array does not match " + width + "x" + height);
		int total = width * height;
		java.util.HashMap<Integer, int[]> histogram = new java.util.HashMap<>();
		int magenta = 0;
		double luma = 0;
		double lumaSquares = 0;
		for (int i = 0; i < total; i++) {
			int colour = rgb(pixels[i]);
			// Quantise to 4 bits a channel so noise and anti-aliasing do not count as new colours
			int bucket = (colour >> 20 & 0xF) << 8 | (colour >> 12 & 0xF) << 4 | (colour >> 4 & 0xF);
			histogram.computeIfAbsent(bucket, b -> new int[1])[0]++;
			int r = colour >> 16 & 0xFF, g = colour >> 8 & 0xFF, b = colour & 0xFF;
			if (r >= 0xE0 && g <= 0x20 && b >= 0xE0)
				magenta++;
			double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
			luma += y;
			lumaSquares += y * y;
		}
		int dominant = 0;
		for (int[] count : histogram.values())
			dominant = Math.max(dominant, count[0]);
		double mean = luma / total;
		double variance = Math.max(0, lumaSquares / total - mean * mean);
		return new Stats(width, height, (double) dominant / total, (double) magenta / total, mean, Math.sqrt(variance),
			histogram.size());
	}

	/** Converts Minecraft's native ABGR pixels to ARGB. */
	public static int[] fromAbgr(int[] abgr) {
		int[] out = new int[abgr.length];
		for (int i = 0; i < abgr.length; i++) {
			int p = abgr[i];
			out[i] = (p & 0xFF00FF00) | (p << 16 & 0xFF0000) | (p >> 16 & 0xFF);
		}
		return out;
	}

	/**
	 * The fraction of pixels that differ visibly between two frames of the same size. A running engine seen twice a few
	 * ticks apart should differ a little; two identical frames mean the animation, or the game, has stopped.
	 */
	public static double changedFraction(int[] a, int[] b, int tolerance) {
		if (a.length != b.length)
			throw new IllegalArgumentException("frames differ in size");
		if (a.length == 0)
			return 0;
		int changed = 0;
		for (int i = 0; i < a.length; i++) {
			int x = rgb(a[i]), y = rgb(b[i]);
			int d = Math.abs((x >> 16 & 0xFF) - (y >> 16 & 0xFF)) + Math.abs((x >> 8 & 0xFF) - (y >> 8 & 0xFF))
				+ Math.abs((x & 0xFF) - (y & 0xFF));
			if (d > tolerance)
				changed++;
		}
		return (double) changed / a.length;
	}

	/** Crops a rectangle out of an image (clamped to it), for judging one part of the frame. */
	public static int[] crop(int[] pixels, int width, int height, int x0, int y0, int x1, int y1) {
		x0 = Math.max(0, x0);
		y0 = Math.max(0, y0);
		x1 = Math.min(width, x1);
		y1 = Math.min(height, y1);
		int w = Math.max(0, x1 - x0), h = Math.max(0, y1 - y0);
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++)
			System.arraycopy(pixels, (y0 + y) * width + x0, out, y * w, w);
		return out;
	}
}
