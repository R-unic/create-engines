package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How far the drawing on a framed blueprint is turned, and how it is laid out across the sheet when it is on its side. */
class PaperTurnTest {

	@Test
	void fourQuarterTurnsMakeAWholeOneAndNeverGoOutOfRange() {
		int turn = 0;
		for (int i = 1; i <= 8; i++) {
			turn = PaperTurn.next(turn);
			assertEquals(i % 4, turn);
		}
	}

	@Test
	void savedDataThatHoldsAnyNumberIsReadAsATurnFromZeroToThree() {
		assertEquals(0, PaperTurn.normalise(0));
		assertEquals(3, PaperTurn.normalise(-1));
		assertEquals(1, PaperTurn.normalise(5));
		assertEquals(0, PaperTurn.normalise(Integer.MIN_VALUE));
		assertEquals(3, PaperTurn.normalise(Integer.MAX_VALUE));
		for (int turn = -300; turn <= 300; turn++) {
			int n = PaperTurn.normalise(turn);
			assertTrue(n >= 0 && n <= 3, "turn " + turn);
		}
	}

	@Test
	void aQuarterAndThreeQuartersPutTheDrawingOnItsSide() {
		assertFalse(PaperTurn.sideways(0));
		assertTrue(PaperTurn.sideways(1));
		assertFalse(PaperTurn.sideways(2));
		assertTrue(PaperTurn.sideways(3));
	}

	@Test
	void aSidewaysDrawingIsLaidOutAcrossTheSheetTurnedToo() {
		assertEquals(3, PaperTurn.layoutWidth(3, 2, 0));
		assertEquals(2, PaperTurn.layoutHeight(3, 2, 0));
		assertEquals(2, PaperTurn.layoutWidth(3, 2, 1));
		assertEquals(3, PaperTurn.layoutHeight(3, 2, 1));
		assertEquals(3, PaperTurn.layoutWidth(3, 2, 2));
		assertEquals(2, PaperTurn.layoutWidth(3, 2, 3));
		// The sheet's own area is the same however it is turned
		for (int turn = 0; turn < 4; turn++)
			assertEquals(6, PaperTurn.layoutWidth(3, 2, turn) * PaperTurn.layoutHeight(3, 2, turn));
	}

	@Test
	void theAngleIsAQuarterTurnForEachStep() {
		assertEquals(0f, PaperTurn.radians(0), 1e-6f);
		assertEquals((float) (Math.PI / 2), PaperTurn.radians(1), 1e-6f);
		assertEquals((float) Math.PI, PaperTurn.radians(2), 1e-6f);
		assertEquals((float) (3 * Math.PI / 2), PaperTurn.radians(3), 1e-6f);
		assertEquals(PaperTurn.radians(1), PaperTurn.radians(5), 1e-6f);
	}
}
