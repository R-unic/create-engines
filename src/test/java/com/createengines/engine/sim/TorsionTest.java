package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TorsionTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void aCrankCostsNothingUpToEightThrowsAndThenLosesByTheThrow() {
		for (int throwsOnLine = 0; throwsOnLine <= Torsion.FREE_THROWS; throwsOnLine++)
			assertEquals(0, Torsion.lineLoss(throwsOnLine), "throws " + throwsOnLine);
		assertEquals(Torsion.LOSS_PER_THROW, Torsion.lineLoss(9), 1e-12);
		assertEquals(4 * Torsion.LOSS_PER_THROW, Torsion.lineLoss(12), 1e-12);
		assertEquals(Torsion.MAX_LOSS, Torsion.lineLoss(100), "there is a floor to what it can cost");
	}

	@Test
	void aStraightTwelveLosesAboutAFifthAndAVeeOrFlatTwelveNothing() {
		double straight = Torsion.loss(new int[] {12});
		assertTrue(straight > 0.15 && straight < 0.22, "straight twelve loses " + straight);
		// A vee or a flat puts two cylinders on a throw: twelve cylinders are six throws
		assertEquals(0, Torsion.loss(new int[] {6}));
		assertEquals(0, Torsion.loss(new int[] {8}), "even a vee sixteen is free");
		double straightSixteen = Torsion.loss(new int[] {16});
		assertTrue(straightSixteen > 0.3 && straightSixteen < 0.4, "straight sixteen loses " + straightSixteen);
	}

	@Test
	void crankLinesGearedTogetherFlexOnTheirOwn() {
		double one = Torsion.loss(new int[] {16});
		double two = Torsion.loss(new int[] {8, 8});
		assertTrue(one > 0.3);
		assertEquals(0, two, "two straight eights coupled lose nothing");
		// An uneven pair is weighted by the work each line carries, so the short line does not hide the long one
		double lopsided = Torsion.loss(new int[] {12, 4});
		assertTrue(lopsided > 0 && lopsided < Torsion.loss(new int[] {12}), "a short line dilutes the loss: " + lopsided);
		assertEquals(Torsion.loss(new int[] {12}) * 12 / 16, lopsided, 1e-12);
	}

	@Test
	void linesAreCountedFromWhereEachBeginsAndOnlyThrowsCount() {
		// journal, throw, throw, journal | journal, throw | throw
		boolean[] isThrow = {false, true, true, false, false, true, true};
		boolean[] startsLine = {false, false, false, false, true, false, true};
		assertArrayEquals(new int[] {2, 1, 1}, Torsion.throwsPerLine(isThrow, startsLine));
		assertArrayEquals(new int[0], Torsion.throwsPerLine(new boolean[0], new boolean[0]));
		assertEquals(0, Torsion.loss(new int[0]));
		assertEquals(0, Torsion.loss(new int[] {0}), "a crank with no throws has nothing to flex");
	}

	@Test
	void stiffnessIsWhatTheRatingKeepsAndTheServerCanScaleOrRemoveIt() {
		double loss = Torsion.loss(new int[] {12});
		assertEquals(1 - loss, Torsion.stiffness(loss), 1e-12);
		assertEquals(1, Torsion.stiffness(0));
		Tuning.torsionalFlex = 0;
		assertEquals(1, Torsion.stiffness(loss), "off");
		Tuning.torsionalFlex = 2;
		assertEquals(1 - 2 * loss, Torsion.stiffness(loss), 1e-12, "doubled");
		assertEquals(1 - 0.8, Torsion.stiffness(Torsion.MAX_LOSS), 1e-12, "never takes more than four fifths");
	}
}
