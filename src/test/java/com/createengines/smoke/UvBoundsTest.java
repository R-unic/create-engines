package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UvBoundsTest {

	private static final double EPS = 1e-9;

	@Test
	void aPointInsideTheSpriteIsNotOutside() {
		assertEquals(0, UvBounds.outside(0.25f, 0.75f, 0f, 0.5f, 0.5f, 1f), EPS);
		assertEquals(0, UvBounds.outside(0.1f, 0.6f, 0f, 0.5f, 0.5f, 1f), EPS);
		assertEquals(0, UvBounds.outside(0f, 0.5f, 0f, 0.5f, 0.5f, 1f), EPS);
		assertEquals(0, UvBounds.outside(0.5f, 1f, 0f, 0.5f, 0.5f, 1f), EPS);
	}

	@Test
	void aPointPastAnEdgeIsMeasuredInSpritePixels() {
		// a sprite spanning u 0..0.5: a whole sprite width is 16 pixels, so 1/16 of 0.5 is one pixel
		assertEquals(1, UvBounds.outside(0.5f + 0.5f / 16, 0.75f, 0f, 0.5f, 0.5f, 1f), 1e-5);
		assertEquals(2, UvBounds.outside(-2 * 0.5f / 16, 0.75f, 0f, 0.5f, 0.5f, 1f), 1e-5);
		assertEquals(3, UvBounds.outside(0.25f, 0.5f - 3 * 0.5f / 16, 0f, 0.5f, 0.5f, 1f), 1e-5);
		assertEquals(4, UvBounds.outside(0.25f, 1f + 4 * 0.5f / 16, 0f, 0.5f, 0.5f, 1f), 1e-5);
	}

	@Test
	void theWorseOfTheTwoAxesCounts() {
		assertEquals(5, UvBounds.outside(0.5f + 5 * 0.5f / 16, 1f + 1 * 0.5f / 16, 0f, 0.5f, 0.5f, 1f), 1e-5);
	}

	@Test
	void aSpriteWithNoAreaHasNothingToRunOutOf() {
		assertEquals(0, UvBounds.outside(9f, 9f, 0.5f, 0.5f, 0.5f, 1f), EPS);
		assertEquals(0, UvBounds.outside(9f, 9f, 0f, 0.5f, 1f, 1f), EPS);
	}

	@Test
	void aQuadIsJudgedByItsWorstVertex() {
		int[] vertices = new int[UvBounds.VERTEX_STRIDE * 4];
		float[][] uvs = {{0.1f, 0.6f}, {0.4f, 0.6f}, {0.4f, 0.9f}, {0.5f + 2 * 0.5f / 16, 0.9f}};
		for (int i = 0; i < 4; i++) {
			vertices[i * UvBounds.VERTEX_STRIDE + UvBounds.U_OFFSET] = Float.floatToRawIntBits(uvs[i][0]);
			vertices[i * UvBounds.VERTEX_STRIDE + UvBounds.V_OFFSET] = Float.floatToRawIntBits(uvs[i][1]);
		}
		assertEquals(2, UvBounds.worstOverrun(vertices, 0f, 0.5f, 0.5f, 1f), 1e-5);
		assertEquals(0, UvBounds.worstOverrun(new int[0], 0f, 0.5f, 0.5f, 1f), EPS);
	}

	@Test
	void theToleranceIsHalfAPixelAndNotMore() {
		assertFalse(UvBounds.exceeds(0));
		assertFalse(UvBounds.exceeds(0.3));
		assertFalse(UvBounds.exceeds(0.5));
		assertTrue(UvBounds.exceeds(0.5001));
		assertTrue(UvBounds.exceeds(4.5));
	}

	@Test
	void theDefaultPatchOfAFaceFollowsTheElementsCorners() {
		double[] from = {2, 3, 4};
		double[] to = {10, 11, 13};
		assertArrayEquals(new double[] {2, 4, 10, 13}, UvBounds.defaultPatch(from, to, "up"), EPS);
		assertArrayEquals(new double[] {2, 3, 10, 12}, UvBounds.defaultPatch(from, to, "down"), EPS);
		assertArrayEquals(new double[] {6, 5, 14, 13}, UvBounds.defaultPatch(from, to, "north"), EPS);
		assertArrayEquals(new double[] {2, 5, 10, 13}, UvBounds.defaultPatch(from, to, "south"), EPS);
		assertArrayEquals(new double[] {4, 5, 13, 13}, UvBounds.defaultPatch(from, to, "west"), EPS);
		assertArrayEquals(new double[] {3, 5, 12, 13}, UvBounds.defaultPatch(from, to, "east"), EPS);
		assertThrows(IllegalArgumentException.class, () -> UvBounds.defaultPatch(from, to, "sideways"));
	}

	@Test
	void anElementPokingOutOfTheBlockRunsOutsideTheTexture() {
		// the rocker: z from 14 to 20.5, so its east and west faces run to 20.5 in u: 4.5 pixels past the sprite
		double[] from = {6.75, 3.25, 14};
		double[] to = {9.25, 4.75, 20.5};
		assertEquals(4.5, UvBounds.patchOverrun(UvBounds.defaultPatch(from, to, "west")), EPS);
		assertEquals(0, UvBounds.patchOverrun(UvBounds.defaultPatch(new double[] {2, 2, 2}, new double[] {14, 14, 14}, "north")), EPS);
		assertEquals(3.25, UvBounds.patchOverrun(new double[] {0, -3.25, 4, 2}), EPS);
		assertEquals(0, UvBounds.patchOverrun(new double[] {0, 0, 16, 16}), EPS);
	}
}
