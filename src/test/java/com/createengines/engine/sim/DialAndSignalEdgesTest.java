package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The ends of the dials, comparator signals and small curves the blocks read: every int a save or a pack could hand them. */
class DialAndSignalEdgesTest {

	private static final int[] HOSTILE_INTS = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1000, -1, 0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 1000,
		Integer.MAX_VALUE - 1, Integer.MAX_VALUE};

	// --- Alternator charge rate ---

	@Test
	void everyChargeRateNumberIsOneOfTheFourSettings() {
		for (int setting : HOSTILE_INTS) {
			int held = ChargeRate.clamp(setting);
			assertTrue(held >= ChargeRate.MIN_SETTING && held <= ChargeRate.MAX_SETTING, "held " + held);
			assertEquals(ChargeRate.percent(held), ChargeRate.percent(setting));
			assertEquals(ChargeRate.percent(setting) / 100.0, ChargeRate.share(setting), 0);
		}
	}

	@Test
	void eachChargeRateSettingIsMoreThanTheOneBelowAndTheDefaultIsFull() {
		for (int s = ChargeRate.MIN_SETTING + 1; s <= ChargeRate.MAX_SETTING; s++)
			assertTrue(ChargeRate.share(s) > ChargeRate.share(s - 1));
		assertEquals(1, ChargeRate.share(ChargeRate.DEFAULT_SETTING), 0);
	}

	// --- Dual-mass spring pack ---

	@Test
	void everySpringPackNumberStaysWithinTheDialsRatios() {
		for (int setting : HOSTILE_INTS) {
			assertTrue(Math.abs(SpringPack.position(setting)) <= 1);
			assertTrue(SpringPack.resonanceFactor(setting) >= 1 / SpringPack.RESONANCE_RATIO - 1e-12
				&& SpringPack.resonanceFactor(setting) <= SpringPack.RESONANCE_RATIO + 1e-12);
			assertTrue(SpringPack.massFactor(setting) >= 1 / SpringPack.MASS_RATIO - 1e-12
				&& SpringPack.massFactor(setting) <= SpringPack.MASS_RATIO + 1e-12);
		}
	}

	@Test
	void theMiddleSpringPackIsExactlyTheOldPartAndTheEndsMirrorIt() {
		assertEquals(1.0, SpringPack.resonanceFactor(SpringPack.DEFAULT_SETTING), 0);
		assertEquals(1.0, SpringPack.massFactor(SpringPack.DEFAULT_SETTING), 0);
		for (int step = 0; step <= 4; step++) {
			assertEquals(1, SpringPack.resonanceFactor(4 - step) * SpringPack.resonanceFactor(4 + step), 1e-12);
			assertEquals(1, SpringPack.massFactor(4 - step) * SpringPack.massFactor(4 + step), 1e-12);
		}
	}

	// --- Comparator off a tank ---

	@Test
	void anEmptyOrImpossibleTankReadsZero() {
		for (int amount : HOSTILE_INTS)
			for (int capacity : HOSTILE_INTS)
				if (amount <= 0 || capacity <= 0)
					assertEquals(0, TankSignal.of(amount, capacity), amount + " of " + capacity);
	}

	@Test
	void anyAmountInAnyTankReadsOneToFifteenAndFifteenOnlyWhenFull() {
		for (int amount : HOSTILE_INTS)
			for (int capacity : HOSTILE_INTS)
				if (amount > 0 && capacity > 0) {
					int signal = TankSignal.of(amount, capacity);
					assertTrue(signal >= 1 && signal <= 15, signal + " for " + amount + " of " + capacity);
					assertEquals(amount >= capacity, signal == 15, amount + " of " + capacity);
				}
	}

	@Test
	void theSignalNeverFallsAsATankFills() {
		for (int capacity : new int[] {1, 2, 13, 14, 15, 250, 1000, 4000}) {
			int last = 0;
			for (int amount = 0; amount <= capacity; amount++) {
				int signal = TankSignal.of(amount, capacity);
				assertTrue(signal >= last, "fell at " + amount + " of " + capacity);
				last = signal;
			}
			assertEquals(15, last);
		}
	}

	// --- Spilled fuel catching ---

	@Test
	void anyFlammabilityGivesADelayAndChanceAFireCanUse() {
		double[] values = {Double.NaN, Double.NEGATIVE_INFINITY, -5, 0, Double.MIN_VALUE, 1e-9, 0.15, 0.5, 1, 2, 3, 100, 1e9,
			Double.MAX_VALUE, Double.POSITIVE_INFINITY};
		for (double f : values) {
			assertTrue(SpillFire.delay(f) >= SpillFire.MIN_DELAY && SpillFire.delay(f) <= SpillFire.MAX_DELAY, "delay " + f);
			assertTrue(SpillFire.catchChance(f) > 0 && SpillFire.catchChance(f) <= 1, "chance " + f);
		}
	}

	@Test
	void anUnreadableFlammabilityBurnsAsAnOrdinaryFuel() {
		for (double f : new double[] {Double.NaN, 0, -1, Double.NEGATIVE_INFINITY}) {
			assertEquals(SpillFire.delay(1), SpillFire.delay(f));
			assertEquals(1, SpillFire.catchChance(f), 0);
		}
		assertEquals(SpillFire.BASE_DELAY, SpillFire.delay(1));
	}

	@Test
	void aMoreFlammableFuelNeverSpreadsSlower() {
		int delay = Integer.MAX_VALUE;
		double chance = 0;
		for (int i = 1; i <= 5000; i++) {
			double f = i / 100.0;
			assertTrue(SpillFire.delay(f) <= delay);
			assertTrue(SpillFire.catchChance(f) >= chance);
			delay = SpillFire.delay(f);
			chance = SpillFire.catchChance(f);
		}
	}

	// --- Throttle ---

	@Test
	void anyRedstoneNumberOpensTheValveBetweenIdleAndWideOpen() {
		for (double idle : new double[] {0, 0.05, 0.25, 0.9, 1})
			for (double wide : new double[] {-1, 0, 0.5, 1, 1.5, 100})
				for (int signal : HOSTILE_INTS) {
					double open = Throttle.opening(idle, wide, signal);
					double top = Math.max(idle, Math.min(Throttle.MAX_WIDE_OPEN, wide));
					assertTrue(open >= idle - 1e-12 && open <= top + 1e-12, idle + " " + wide + " " + signal + " gave " + open);
				}
	}

	@Test
	void theValveNeverClosesAsTheSignalRises() {
		for (double idle : new double[] {0, 0.2, 1}) {
			double last = -1;
			for (int signal = 0; signal <= Throttle.FULL_SIGNAL; signal++) {
				double open = Throttle.opening(idle, signal);
				assertTrue(open >= last);
				last = open;
			}
		}
	}

	@Test
	void theFuelTrimStaysAShareAndRisesWithTheValve() {
		double last = -1;
		for (int i = -200; i <= 400; i++) {
			double trim = Throttle.fuelTrim(i / 200.0);
			assertTrue(trim >= 0 && trim <= 1);
			assertTrue(trim >= last);
			last = trim;
		}
		assertEquals(0, Throttle.fuelTrim(0), 0);
		assertEquals(1, Throttle.fuelTrim(1), 1e-12);
	}

	@Test
	void throttlesInSeriesTakeTheMostClosedInAnyOrder() {
		assertEquals(1, Throttle.inSeries(List.of()), 0);
		assertEquals(0.2, Throttle.inSeries(List.of(1.4, 0.2, 0.9)), 0);
		assertEquals(0.2, Throttle.inSeries(List.of(0.2, 0.9, 1.4)), 0);
		assertEquals(1.2, Throttle.inSeries(List.of(1.5, 1.2)), 0);
	}

	// --- Muffling ---

	@Test
	void anyMixOfExhaustRunsIsMuffledBetweenNotAndFully() {
		Random random = new Random(7);
		for (int round = 0; round < 5000; round++) {
			int runs = random.nextInt(9);
			int[] heads = new int[runs];
			float[] muffled = new float[runs];
			for (int i = 0; i < runs; i++) {
				heads[i] = random.nextInt(13);
				muffled[i] = Muffling.run(random.nextBoolean(), random.nextBoolean());
			}
			float total = Muffling.engine(heads, muffled);
			assertTrue(total >= 0 && total <= 1.0001f, "muffled " + total);
			float volume = Muffling.volume(total);
			assertTrue(volume >= 1 - Muffling.QUIET - 1e-6f && volume <= 1);
		}
	}

	@Test
	void aMufflerEveryPathGoesThroughCountsFullyWhateverElseTheRunHas() {
		assertEquals(1, Muffling.run(true, false), 0);
		assertEquals(1, Muffling.run(true, true), 0);
		assertEquals(Muffling.BYPASSED, Muffling.run(false, true), 0);
		assertEquals(0, Muffling.run(false, false), 0);
		assertEquals(0, Muffling.engine(new int[] {0, 0}, new float[] {1, 1}), 0, "no heads, nothing to quiet");
	}

	@Test
	void aVolumeOutsideTheRangeIsHeld() {
		assertEquals(1, Muffling.volume(-3), 0);
		assertEquals(1 - Muffling.QUIET, Muffling.volume(7), 1e-6f);
	}

	// --- Reserve lines ---

	@Test
	void everyCombinationOfReserveGogglesNamesTheSwitchFirst() {
		for (boolean reserve : new boolean[] {false, true})
			for (int reserves : new int[] {Integer.MIN_VALUE, -1, 0, 1, 99})
				for (boolean sneaking : new boolean[] {false, true}) {
					List<String> keys = ReserveLines.keys(reserve, reserves, sneaking);
					assertEquals(reserve ? "goggles.battery.reserve" : "goggles.battery.reserve_off", keys.getFirst());
					assertEquals(1 + (reserves > 0 ? 1 : 0) + (sneaking ? 1 : 0), keys.size());
				}
	}
}
