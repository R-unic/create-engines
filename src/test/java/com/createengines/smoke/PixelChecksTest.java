package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class PixelChecksTest {

	private static int[] flat(int w, int h, int argb) {
		int[] pixels = new int[w * h];
		Arrays.fill(pixels, argb);
		return pixels;
	}

	/** A noisy greyscale frame standing in for a rendered scene. */
	private static int[] scene(int w, int h, long seed) {
		Random random = new Random(seed);
		int[] pixels = new int[w * h];
		for (int i = 0; i < pixels.length; i++) {
			int v = 40 + random.nextInt(180);
			pixels[i] = 0xFF000000 | v << 16 | v << 8 | v;
		}
		return pixels;
	}

	@Test
	void aFlatFrameIsBlank() {
		assertTrue(PixelChecks.analyse(flat(64, 64, 0xFF87CEEB), 64, 64)
			.blank());
		assertTrue(PixelChecks.analyse(flat(64, 64, 0xFF000000), 64, 64)
			.nearlyBlack());
	}

	@Test
	void aRenderedSceneIsNotBlank() {
		var stats = PixelChecks.analyse(scene(64, 64, 1), 64, 64);
		assertFalse(stats.blank());
		assertFalse(stats.nearlyBlack());
		assertFalse(stats.hasMissingTexture());
	}

	@Test
	void missingTextureMagentaIsSpottedEvenWhenSmall() {
		int[] pixels = scene(100, 100, 2);
		// An 8x8 checker square in the missing-texture pink, 0.64% of the frame
		for (int y = 0; y < 8; y++)
			for (int x = 0; x < 8; x++)
				pixels[y * 100 + x] = 0xFF000000 | PixelChecks.MISSING_MAGENTA;
		assertTrue(PixelChecks.analyse(pixels, 100, 100)
			.hasMissingTexture());
	}

	@Test
	void aFewStrayPinkPixelsAreTolerated() {
		int[] pixels = scene(200, 200, 3);
		pixels[5] = 0xFFF800F8;
		assertFalse(PixelChecks.analyse(pixels, 200, 200)
			.hasMissingTexture());
	}

	@Test
	void minecraftAbgrPixelsAreSwappedToArgb() {
		// ABGR 0xFF F800F8 happens to be symmetric; use an asymmetric red instead
		int[] argb = PixelChecks.fromAbgr(new int[] {0xFF0000FF});
		assertEquals(0xFFFF0000, argb[0]);
		assertTrue(PixelChecks.analyse(PixelChecks.fromAbgr(flat(10, 10, 0xFFF800F8)), 10, 10)
			.hasMissingTexture());
	}

	@Test
	void identicalFramesHaveNoChangeAndDifferentOnesDo() {
		int[] a = scene(50, 50, 4);
		assertEquals(0, PixelChecks.changedFraction(a, a.clone(), 8));
		assertTrue(PixelChecks.changedFraction(a, scene(50, 50, 5), 8) > 0.5);
	}

	@Test
	void smallNoiseUnderTheToleranceIsNotChange() {
		int[] a = flat(10, 10, 0xFF808080);
		int[] b = flat(10, 10, 0xFF818181);
		assertEquals(0, PixelChecks.changedFraction(a, b, 8));
	}

	@Test
	void cropClampsToTheImage() {
		int[] pixels = scene(10, 10, 6);
		assertEquals(25, PixelChecks.crop(pixels, 10, 10, 5, 5, 99, 99).length);
		assertEquals(0, PixelChecks.crop(pixels, 10, 10, 20, 20, 30, 30).length);
	}

	@Test
	void mismatchedSizesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> PixelChecks.analyse(new int[3], 10, 10));
		assertThrows(IllegalArgumentException.class, () -> PixelChecks.changedFraction(new int[3], new int[4], 1));
	}
}
