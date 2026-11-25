package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** The soot trap's bookkeeping and the Maker's Plate's figures and names, under hostile and random use. */
class SootAndPlateEdgesTest {

	private static final double[] VALUES = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e9, -1, 0, 1e-9, 1, 49.999, 50,
		51, 3200, 1e9, Double.MAX_VALUE};

	// --- Soot trap ---

	private static void sound(SootCollector trap) {
		assertTrue(trap.stored() >= 0 && trap.stored() <= SootCollector.CAPACITY, "stored " + trap.stored());
		assertTrue(trap.progress() >= 0 && trap.progress() <= SootCollector.MB_PER_SOOT, "progress " + trap.progress());
		if (trap.full())
			assertEquals(0, trap.progress(), 0);
	}

	@Test
	void aRandomRunOfCatchingTakingAndReloadingNeverBreaksTheTrap() {
		Random random = new Random(99);
		SootCollector trap = new SootCollector();
		for (int step = 0; step < 50000; step++) {
			switch (random.nextInt(4)) {
				case 0 -> trap.add(random.nextInt(8) == 0 ? VALUES[random.nextInt(VALUES.length)] : random.nextDouble() * 80);
				case 1 -> trap.take(random.nextInt(8) == 0 ? random.nextInt() : random.nextInt(80) - 5);
				case 2 -> trap.restore(random.nextInt(8) == 0 ? random.nextInt() : random.nextInt(80), VALUES[random.nextInt(VALUES.length)]);
				default -> trap.add(0);
			}
			sound(trap);
		}
	}

	@Test
	void noSootIsInventedOrLostWhileThereIsRoom() {
		Random random = new Random(3);
		SootCollector trap = new SootCollector();
		double fed = 0;
		int made = 0;
		while (fed < SootCollector.MB_PER_SOOT * (SootCollector.CAPACITY - 2)) {
			double mb = random.nextDouble() * 30;
			fed += mb;
			made += trap.add(mb);
			assertEquals(fed, trap.stored() * SootCollector.MB_PER_SOOT + trap.progress(), 1e-6);
		}
		assertEquals(made, trap.stored());
	}

	@Test
	void oneEnormousCatchFillsTheTrapAndNoMore() {
		SootCollector trap = new SootCollector();
		assertEquals(SootCollector.CAPACITY, trap.add(Double.MAX_VALUE));
		assertTrue(trap.full());
		assertEquals(0, trap.add(1000));
		sound(trap);
	}

	@Test
	void takingEverythingLeavesTheTrapReadyToFillAgain() {
		SootCollector trap = new SootCollector();
		trap.add(1e9);
		assertEquals(SootCollector.CAPACITY, trap.take(Integer.MAX_VALUE));
		assertEquals(0, trap.take(Integer.MAX_VALUE));
		assertEquals(0, trap.take(Integer.MIN_VALUE));
		assertEquals(1, trap.add(SootCollector.MB_PER_SOOT));
	}

	@Test
	void whatEscapesATrapIsNeverNegativeNorMoreThanWasMade() {
		for (double soot : VALUES)
			for (double caught : VALUES) {
				double out = SootCollector.escaped(soot, caught);
				assertTrue(out >= 0 && (!Double.isFinite(soot) || out <= Math.max(0, soot)), soot + " less " + caught + " left " + out);
			}
	}

	@Test
	void aShareOfSootIsFiniteAndNeverMoreThanTheWhole() {
		for (double total : VALUES)
			for (int run : new int[] {-1, 0, 1, 6, 12, Integer.MAX_VALUE})
				for (int all : new int[] {-1, 0, 1, 12, Integer.MAX_VALUE})
					for (float share : new float[] {-1, 0, 0.5f, 1, 40})
						for (int traps : new int[] {-1, 0, 1, 3}) {
							double got = SootCollector.share(total, run, all, share, traps);
							assertTrue(Double.isFinite(got) && got >= 0, "share " + got);
							if (got > 0)
								assertTrue(got <= total, got + " of " + total);
						}
	}

	@Test
	void splittingARunsSootBetweenItsTrapsKeepsTheTotal() {
		for (int traps = 1; traps <= 12; traps++)
			assertEquals(300, SootCollector.share(300, 4, 4, 1, traps) * traps, 1e-9);
	}

	@Test
	void sootFromAnyCycleIsFiniteAndNotNegative() {
		for (double fuel : VALUES)
			for (double unburned : VALUES)
				for (double factor : VALUES) {
					double soot = SootCollector.sootFrom(fuel, unburned, factor);
					assertTrue(Double.isFinite(soot) && soot >= 0);
				}
	}

	// --- Maker's Plate ---

	@Test
	void anyPackedPlateReadsAsNonNegativeFigures() {
		Random random = new Random(11);
		for (int round = 0; round < 20000; round++) {
			int[] packed = {random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()};
			PlateFigures read = PlateFigures.unpack(packed);
			assertTrue(read.peakTorque() >= 0 && read.torqueRpm() >= 0 && read.peakPower() >= 0 && read.powerRpm() >= 0);
		}
	}

	@Test
	void aPlateSaveOfTheWrongShapeIsBlank() {
		for (int length = 0; length <= 10; length++)
			if (length != 4)
				assertEquals(PlateFigures.NONE, PlateFigures.unpack(new int[length]));
		assertEquals(PlateFigures.NONE, PlateFigures.unpack(null));
		assertFalse(PlateFigures.NONE.stamped());
	}

	@Test
	void whateverAPlateIsStampedWithItReadsBackTheSame() {
		Random random = new Random(12);
		for (int round = 0; round < 3000; round++) {
			int points = 1 + random.nextInt(40);
			float[] rpm = new float[points];
			float[] torque = new float[points];
			for (int i = 0; i < points; i++) {
				rpm[i] = 1 + random.nextInt(512);
				torque[i] = random.nextFloat() * 500;
			}
			PlateFigures figures = PlateFigures.of(rpm, torque);
			if (figures == null)
				continue;
			PlateFigures back = PlateFigures.unpack(figures.pack());
			assertArrayEquals(figures.pack(), back.pack());
			assertEquals(figures, back);
		}
	}

	@Test
	void aCurveWithNothingInItIsNotStamped() {
		assertNull(PlateFigures.of(null, new float[] {1}));
		assertNull(PlateFigures.of(new float[] {1}, null));
		assertNull(PlateFigures.of(new float[0], new float[0]));
		assertNull(PlateFigures.of(new float[] {1, 2}, new float[] {1}));
		assertNull(PlateFigures.of(new float[] {10, 20}, new float[] {0, 0}));
		assertNull(PlateFigures.of(new float[] {10, 20}, new float[] {-5, -2}));
		assertNull(PlateFigures.of(new float[] {0}, new float[] {5}), "torque at no speed makes no power");
		assertNull(PlateFigures.of(new float[] {Float.NaN}, new float[] {5}));
		assertNull(PlateFigures.of(new float[] {10}, new float[] {Float.NaN}));
	}

	@Test
	void anyNameFitsThePlateWithNoControlCharactersOrBlankEnds() {
		Random random = new Random(13);
		for (int round = 0; round < 20000; round++) {
			StringBuilder raw = new StringBuilder();
			int length = random.nextInt(80);
			for (int i = 0; i < length; i++)
				raw.append(random.nextInt(3) == 0 ? (char) random.nextInt(33) : (char) random.nextInt(0x700));
			String clean = PlateFigures.cleanName(raw.toString(), 24);
			assertTrue(clean.length() <= 24, "too long: " + clean.length());
			assertFalse(clean.startsWith(" ") || clean.endsWith(" "), "blank end in '" + clean + "'");
			assertFalse(clean.contains("  "), "double blank in '" + clean + "'");
			assertTrue(clean.chars()
				.noneMatch(c -> c < 32 || c == 127), "control character left in");
			assertEquals(clean, PlateFigures.cleanName(clean, 24), "cleaning twice changed it");
		}
	}

	@Test
	void aLimitOfNothingGivesAnEmptyName() {
		assertEquals("", PlateFigures.cleanName("Big Bertha", 0));
		assertEquals("", PlateFigures.cleanName("Big Bertha", -5));
		assertEquals("", PlateFigures.cleanName(null, 24));
		assertEquals("", PlateFigures.cleanName(" \n\t ", 24));
	}

	/** An emoji is two chars. Cutting a name between them leaves half a character, which shows as a box or a question mark. */
	@Test
	void aLongNameIsNeverCutThroughTheMiddleOfAnEmoji() {
		String rocket = new String(Character.toChars(0x1F680));
		for (int before = 20; before <= 24; before++) {
			String clean = PlateFigures.cleanName("a".repeat(before) + rocket + "tail", 24);
			for (int i = 0; i < clean.length(); i++)
				if (Character.isHighSurrogate(clean.charAt(i)))
					assertTrue(i + 1 < clean.length() && Character.isLowSurrogate(clean.charAt(i + 1)),
						"half an emoji left after " + before + " letters");
		}
	}
}
