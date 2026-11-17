package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.createengines.engine.blueprint.FrameSheet.Cell;
import com.createengines.engine.blueprint.FrameSheet.Sheet;

/** Which framed blueprints join into one bigger picture, and which stay apart. */
class FrameSheetTest {

	private static Predicate<Cell> frames(int... uv) {
		Set<Cell> cells = new java.util.HashSet<>();
		for (int i = 0; i < uv.length; i += 2)
			cells.add(new Cell(uv[i], uv[i + 1]));
		return cells::contains;
	}

	private static Predicate<Cell> rectangle(int minU, int minV, int width, int height) {
		return c -> c.u() >= minU && c.u() < minU + width && c.v() >= minV && c.v() < minV + height;
	}

	@Test
	void aFrameOnItsOwnIsASheetOfOne() {
		Sheet sheet = FrameSheet.find(frames(0, 0), c -> true);
		assertEquals(new Sheet(0, 0, 1, 1, new Cell(0, 0)), sheet);
		assertFalse(sheet.joined());
	}

	@Test
	void anEmptyLoneFrameHasNothingToDraw() {
		assertNull(FrameSheet.find(frames(0, 0), c -> false)
			.source());
	}

	@Test
	void twoFramesSideBySideJoinAndEitherSeesTheSameSheet() {
		Predicate<Cell> frame = frames(0, 0, 1, 0);
		Sheet fromLeft = FrameSheet.find(frame, c -> c.u() == 0);
		assertEquals(new Sheet(0, 0, 2, 1, new Cell(0, 0)), fromLeft);
		// Seen from the right frame (cells are relative to it), the same two frames sit at -1 and 0
		Sheet fromRight = FrameSheet.find(frames(-1, 0, 0, 0), c -> c.u() == -1);
		assertEquals(new Sheet(-1, 0, 2, 1, new Cell(-1, 0)), fromRight);
		assertTrue(fromRight.joined());
	}

	@Test
	void aTwoByTwoSheetDrawsTheFirstBlueprintReadingLeftToRightThenDown() {
		Predicate<Cell> frame = rectangle(0, 0, 2, 2);
		// only the bottom two hold one: the left of them is the source
		Sheet sheet = FrameSheet.find(frame, c -> c.v() == 1);
		assertEquals(new Cell(0, 1), sheet.source());
		// the top-right and bottom-left hold one: the top-right comes first reading across
		sheet = FrameSheet.find(frame, c -> c.equals(new Cell(1, 0)) || c.equals(new Cell(0, 1)));
		assertEquals(new Cell(1, 0), sheet.source());
		// the top-left wins over everything
		sheet = FrameSheet.find(frame, c -> true);
		assertEquals(new Cell(0, 0), sheet.source());
	}

	@Test
	void aSheetWhereNoFrameHoldsAnythingStillJoinsButDrawsNothing() {
		Sheet sheet = FrameSheet.find(rectangle(0, 0, 3, 2), c -> false);
		assertEquals(3, sheet.width());
		assertEquals(2, sheet.height());
		assertNull(sheet.source());
	}

	@Test
	void aRaggedGroupStaysSeparateFrames() {
		// an L of three: not a rectangle, so every frame stands alone
		Predicate<Cell> l = frames(0, 0, 1, 0, 0, 1);
		Sheet sheet = FrameSheet.find(l, c -> true);
		assertEquals(new Sheet(0, 0, 1, 1, new Cell(0, 0)), sheet);
		// and a rectangle with a hole in it
		Predicate<Cell> ring = c -> rectangle(0, 0, 3, 3).test(c) && !(c.u() == 1 && c.v() == 1);
		assertEquals(1, FrameSheet.find(ring, c -> true)
			.width());
	}

	@Test
	void framesThatOnlyTouchAtACornerDoNotJoin() {
		assertEquals(1, FrameSheet.find(frames(0, 0, 1, 1), c -> true)
			.width());
	}

	@Test
	void aGapSeparatesTwoFrames() {
		assertEquals(1, FrameSheet.find(frames(0, 0, 2, 0), c -> true)
			.width());
	}

	@Test
	void theLargestSheetIsEightByEightAndNineAcrossIsLeftApart() {
		Sheet big = FrameSheet.find(rectangle(0, 0, FrameSheet.MAX_SIDE, FrameSheet.MAX_SIDE), c -> true);
		assertEquals(FrameSheet.MAX_SIDE, big.width());
		assertEquals(FrameSheet.MAX_SIDE, big.height());
		Sheet tooWide = FrameSheet.find(rectangle(0, 0, FrameSheet.MAX_SIDE + 1, 1), c -> true);
		assertEquals(new Sheet(0, 0, 1, 1, new Cell(0, 0)), tooWide);
		Sheet tooTall = FrameSheet.find(rectangle(0, 0, 1, FrameSheet.MAX_SIDE + 1), c -> true);
		assertEquals(1, tooTall.height());
	}

	@Test
	void aNeverEndingWallOfFramesIsCutOffRatherThanSearchedForever() {
		// every cell is a frame: the search has to give up at the limit, not run away
		Sheet sheet = FrameSheet.find(c -> true, c -> true);
		assertEquals(1, sheet.width());
		assertEquals(1, sheet.height());
	}

	@Test
	void theSheetContainsExactlyItsFrames() {
		Sheet sheet = FrameSheet.find(rectangle(-1, -1, 3, 2), c -> true);
		assertTrue(sheet.contains(new Cell(-1, -1)));
		assertTrue(sheet.contains(new Cell(1, 0)));
		assertFalse(sheet.contains(new Cell(2, 0)));
		assertFalse(sheet.contains(new Cell(0, 1)));
	}
}
