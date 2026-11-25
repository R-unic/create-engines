package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Framed blueprints on thousands of random walls, and the plain helpers beside them under hostile input. */
class FrameWallRandomTest {

	private record Wall(Set<FrameSheet.Cell> frames, Set<FrameSheet.Cell> filled) {

		/** The sheet seen from the frame at {@code from}, given back in wall coordinates. */
		FrameSheet.Sheet from(FrameSheet.Cell from) {
			FrameSheet.Sheet sheet = FrameSheet.find(c -> frames.contains(shift(c, from.u(), from.v())),
				c -> filled.contains(shift(c, from.u(), from.v())));
			return new FrameSheet.Sheet(sheet.minU() + from.u(), sheet.minV() + from.v(), sheet.width(), sheet.height(),
				sheet.source() == null ? null : shift(sheet.source(), from.u(), from.v()));
		}
	}

	private static FrameSheet.Cell shift(FrameSheet.Cell cell, int u, int v) {
		return new FrameSheet.Cell(cell.u() + u, cell.v() + v);
	}

	private static Wall randomWall(Random random) {
		Set<FrameSheet.Cell> frames = new HashSet<>();
		Set<FrameSheet.Cell> filled = new HashSet<>();
		// A few rectangles, which sometimes touch or overlap into ragged shapes
		for (int r = 0, count = 1 + random.nextInt(4); r < count; r++) {
			int u = random.nextInt(14), v = random.nextInt(14);
			int width = 1 + random.nextInt(random.nextInt(5) == 0 ? 11 : 4), height = 1 + random.nextInt(4);
			for (int du = 0; du < width; du++)
				for (int dv = 0; dv < height; dv++) {
					FrameSheet.Cell cell = new FrameSheet.Cell(u + du, v + dv);
					frames.add(cell);
					if (random.nextInt(3) == 0)
						filled.add(cell);
				}
		}
		return new Wall(frames, filled);
	}

	@Test
	void everySheetIsAWholeRectangleOfFramesNoBiggerThanTheLimitThatHoldsTheFrameAsked() {
		Random random = new Random(1);
		for (int round = 0; round < 3000; round++) {
			Wall wall = randomWall(random);
			for (FrameSheet.Cell frame : wall.frames) {
				FrameSheet.Sheet sheet = wall.from(frame);
				assertTrue(sheet.contains(frame), "the frame asked is on its own sheet");
				assertTrue(sheet.width() <= FrameSheet.MAX_SIDE && sheet.height() <= FrameSheet.MAX_SIDE);
				for (int u = sheet.minU(); u < sheet.minU() + sheet.width(); u++)
					for (int v = sheet.minV(); v < sheet.minV() + sheet.height(); v++)
						assertTrue(wall.frames.contains(new FrameSheet.Cell(u, v)), "a hole in a sheet");
			}
		}
	}

	@Test
	void everyFrameOfAJoinedSheetAgreesWhatTheSheetIs() {
		Random random = new Random(2);
		for (int round = 0; round < 3000; round++) {
			Wall wall = randomWall(random);
			for (FrameSheet.Cell frame : wall.frames) {
				FrameSheet.Sheet sheet = wall.from(frame);
				if (!sheet.joined())
					continue;
				for (FrameSheet.Cell other : wall.frames)
					if (sheet.contains(other))
						assertEquals(sheet, wall.from(other), "frames " + frame + " and " + other + " disagree");
			}
		}
	}

	@Test
	void aSheetsPictureComesFromAFilledFrameOnItAndIsMissingOnlyWhenNoneIs() {
		Random random = new Random(3);
		for (int round = 0; round < 3000; round++) {
			Wall wall = randomWall(random);
			for (FrameSheet.Cell frame : wall.frames) {
				FrameSheet.Sheet sheet = wall.from(frame);
				boolean any = wall.filled.stream()
					.anyMatch(sheet::contains);
				if (!any)
					assertNull(sheet.source());
				else
					assertTrue(sheet.source() != null && sheet.contains(sheet.source()) && wall.filled.contains(sheet.source()));
			}
		}
	}

	@Test
	void anEndlessWallOfFramesIsLeftAloneAndFinishes() {
		FrameSheet.Sheet sheet = FrameSheet.find(c -> true, c -> true);
		assertEquals(new FrameSheet.Sheet(0, 0, 1, 1, new FrameSheet.Cell(0, 0)), sheet);
	}

	// --- Paper turns ---

	@Test
	void anySavedTurnIsOneOfFourAndFourMoreQuartersComeRound() {
		int[] saved = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -5, -1, 0, 1, 2, 3, 4, 7, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
		for (int turn : saved) {
			int now = PaperTurn.normalise(turn);
			assertTrue(now >= 0 && now < 4);
			int next = PaperTurn.next(now);
			assertEquals((now + 1) % 4, next);
			int round = now;
			for (int i = 0; i < 4; i++)
				round = PaperTurn.next(round);
			assertEquals(now, round);
		}
	}

	/** Using a wrench on the largest number a damaged save can hold must still be one quarter further round. */
	@Test
	void theNextTurnFromAHostileSaveIsStillOneQuarterOn() {
		for (int turn : new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, -1, 4001})
			assertEquals((PaperTurn.normalise(turn) + 1) % 4, PaperTurn.next(turn), "from " + turn);
	}

	@Test
	void turningNeverChangesHowManyFramesTheDrawingCovers() {
		for (int turn = -8; turn <= 8; turn++)
			for (int w = 1; w <= 8; w++)
				for (int h = 1; h <= 8; h++)
					assertEquals(w * h, PaperTurn.layoutWidth(w, h, turn) * PaperTurn.layoutHeight(w, h, turn));
	}

	// --- File names ---

	@Test
	void noBlueprintNameCanMakeAFileNameThatLeavesItsFolder() {
		Random random = new Random(4);
		String nasty = "../\\:*?\"<>|. \t\n\u0000abcXYZ019_-éİK";
		for (int round = 0; round < 20000; round++) {
			StringBuilder name = new StringBuilder();
			for (int i = 0, length = random.nextInt(40); i < length; i++)
				name.append(random.nextBoolean() ? nasty.charAt(random.nextInt(nasty.length())) : (char) random.nextInt(0xFFFF));
			String file = BlueprintNames.fileName(name.toString());
			assertTrue(file.matches("[a-z0-9]+(_[a-z0-9]+)*"), "'" + file + "'");
			assertEquals(file, BlueprintNames.fileName(file), "a clean name stays as it is");
		}
	}

	@Test
	void aFileNameDoesNotDependOnTheMachinesLanguage() {
		Locale was = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertEquals("big_inline_i6", BlueprintNames.fileName("BIG INLINE I6"));
		} finally {
			Locale.setDefault(was);
		}
	}

	@Test
	void anEmptyOrAllPunctuationNameIsCalledEngine() {
		for (String name : new String[] {"", " ", "...", "///", "___", "éè"})
			assertEquals("engine", BlueprintNames.fileName(name), "for '" + name + "'");
	}
}
