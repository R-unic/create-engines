package com.createengines.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChamberLodTest {

	@Test
	void closeIsDetailedMiddleIsCoarseFarIsNothing() {
		assertEquals(ChamberLod.Level.DETAILED, ChamberLod.at(0));
		assertEquals(ChamberLod.Level.DETAILED, ChamberLod.at(ChamberLod.NEAR * ChamberLod.NEAR));
		assertEquals(ChamberLod.Level.COARSE, ChamberLod.at(ChamberLod.NEAR * ChamberLod.NEAR + 1));
		assertEquals(ChamberLod.Level.COARSE, ChamberLod.at(ChamberLod.FAR * ChamberLod.FAR));
		assertNull(ChamberLod.at(ChamberLod.FAR * ChamberLod.FAR + 1));
	}

	@Test
	void hostileDistancesNeverThrowAndDrawNothingUnlessFinite() {
		assertNull(ChamberLod.at(Double.NaN));
		assertNull(ChamberLod.at(Double.POSITIVE_INFINITY));
		assertNull(ChamberLod.at(Double.MAX_VALUE));
		assertEquals(ChamberLod.Level.DETAILED, ChamberLod.at(-1));
	}

	@Test
	void everyStepFurtherDrawsNoMoreVertices() {
		int last = Integer.MAX_VALUE;
		for (double d = 0; d < 60; d += 0.5) {
			ChamberLod.Level level = ChamberLod.at(d * d);
			int vertices = level == null ? 0 : level.worstCase();
			assertTrue(vertices <= last, "vertices rose at " + d + " blocks");
			last = vertices;
		}
		assertEquals(0, last);
	}

	@Test
	void theCoarseLevelIsAtLeastHalfTheCost() {
		ChamberLod.Level detailed = ChamberLod.Level.DETAILED;
		ChamberLod.Level coarse = ChamberLod.Level.COARSE;
		assertTrue(coarse.worstCase() * 2 <= detailed.worstCase(),
			coarse.worstCase() + " against " + detailed.worstCase());
	}

	@Test
	void theDetailedLevelIsTheShapeThatShippedAndNothingIsDegenerate() {
		ChamberLod.Level detailed = ChamberLod.Level.DETAILED;
		assertEquals(12, detailed.sides);
		assertEquals(7, detailed.rings);
		assertEquals(6, detailed.bands);
		assertEquals(5, detailed.tongues);
		for (ChamberLod.Level level : ChamberLod.Level.values()) {
			assertTrue(level.sides >= 3 && level.rings >= 1 && level.bands >= 2 && level.tongues >= 1, level.name());
		}
	}
}
