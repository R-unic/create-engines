package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The battery's rules as numbers ({@link BatteryChemistry}): what a spark costs, how a cold battery gives less, how a
 * starter's draw sags, and the levels a comparator and the lamp strip show. Each test pins one edge a mutation would move.
 */
class BatteryChemistryTest {

	private static final double EPS = 1e-9;

	@Test
	void aWarmEngineSparkCostsOneAndAColdOneCostsMore() {
		assertEquals(1, BatteryChemistry.sparkCost(0, true), EPS);
		assertEquals(2, BatteryChemistry.sparkCost(1, true), EPS);
		assertEquals(1.5, BatteryChemistry.sparkCost(0.5, true), EPS);
	}

	@Test
	void sparkCostIsClampedAndSwitchedOff() {
		assertEquals(2, BatteryChemistry.sparkCost(7, true), EPS, "colder than the coldest is the coldest");
		assertEquals(1, BatteryChemistry.sparkCost(-3, true), EPS, "warmer than warm is warm");
		assertEquals(1, BatteryChemistry.sparkCost(1, false), EPS, "with the feature off a cold engine costs the same");
	}

	@Test
	void aBiomeIsColdBelowComfortableAndFreezingAtZero() {
		assertEquals(0, BatteryChemistry.ambientCold(0.8), EPS, "plains");
		assertEquals(0, BatteryChemistry.ambientCold(BatteryChemistry.COMFORTABLE_BIOME), EPS, "the edge is not cold");
		assertEquals(0.5, BatteryChemistry.ambientCold(0.25), EPS, "taiga");
		assertEquals(1, BatteryChemistry.ambientCold(0), EPS, "snowy plains");
		assertEquals(1, BatteryChemistry.ambientCold(-0.7), EPS, "frozen peaks");
		assertEquals(0, BatteryChemistry.ambientCold(2), EPS, "desert");
		assertEquals(0, BatteryChemistry.ambientCold(Double.NaN), EPS, "a biome that gives no number is not cold");
	}

	@Test
	void aHeatSourceBesideABatteryTakesColdOff() {
		assertEquals(1, BatteryChemistry.coldness(0, 0), EPS);
		assertEquals(0.6, BatteryChemistry.coldness(0, 1), EPS, "smouldering");
		assertEquals(0.2, BatteryChemistry.coldness(0, 2), EPS, "kindled");
		assertEquals(0, BatteryChemistry.coldness(0, 3), EPS, "seething");
		assertEquals(0, BatteryChemistry.coldness(0.8, 0), EPS, "nothing to take off in a warm biome");
		assertEquals(1, BatteryChemistry.coldness(0, -4), EPS, "a level below none is none");
		assertEquals(0, BatteryChemistry.coldness(0, 9), EPS, "a level above seething is seething");
	}

	@Test
	void warmthRisesWithTheHeatLevel() {
		double last = -1;
		for (int level = 0; level <= 3; level++) {
			double warmth = BatteryChemistry.warmth(level);
			assertTrue(warmth > last, "level " + level);
			last = warmth;
		}
		assertEquals(1, BatteryChemistry.warmth(3), EPS);
	}

	@Test
	void aColdBatteryKeepsItsChargeButGivesOnlyPartOfIt() {
		assertEquals(200, BatteryChemistry.deliverable(200, 200, 0, true), EPS, "warm: all of it");
		assertEquals(100, BatteryChemistry.deliverable(200, 200, 1, true), EPS, "as cold as it gets: half");
		assertEquals(150, BatteryChemistry.deliverable(200, 200, 0.5, true), EPS);
		assertEquals(40, BatteryChemistry.deliverable(40, 200, 1, true), EPS, "a nearly flat one gives what it has, cold or not");
		assertEquals(200, BatteryChemistry.deliverable(200, 200, 1, false), EPS, "with the feature off the cold keeps nothing back");
		assertEquals(0, BatteryChemistry.deliverable(-5, 200, 0, true), EPS, "never less than nothing");
	}

	@Test
	void theColdLimitIsTheCapacityLessTheShareTheColdKeeps() {
		assertEquals(100, BatteryChemistry.deliverableLimit(200, 1, true), EPS);
		assertEquals(200, BatteryChemistry.deliverableLimit(200, 1, false), EPS);
		assertEquals(200, BatteryChemistry.deliverableLimit(200, -1, true), EPS, "colder is clamped, not negative");
		assertEquals(100, BatteryChemistry.deliverableLimit(200, 5, true), EPS);
	}

	@Test
	void aBankIsLowBelowTheSagLineAndANonNumberIsLow() {
		assertTrue(!BatteryChemistry.isLow(1));
		assertTrue(!BatteryChemistry.isLow(BatteryChemistry.SAG_BELOW), "at the line it is not yet low");
		assertTrue(BatteryChemistry.isLow(BatteryChemistry.SAG_BELOW - 1e-6));
		assertTrue(BatteryChemistry.isLow(0));
		assertTrue(BatteryChemistry.isLow(Double.NaN), "a reading that is not a number is low");
	}

	@Test
	void crankingDrawsMoreFromAColdEngine() {
		assertEquals(BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(0, 1), EPS);
		assertEquals(2 * BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(1, 1), EPS);
		assertEquals(2 * BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(4, 1), EPS, "clamped");
		assertEquals(BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(-1, 1), EPS, "clamped");
	}

	@Test
	void theStarterTakesLessAsTheBankRunsDownAndNothingFromAnEmptyOne() {
		assertEquals(BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(0, BatteryChemistry.SAG_BELOW), EPS, "at the sag line it takes all it does");
		assertEquals(BatteryChemistry.CRANK_DRAW / 2, BatteryChemistry.crankDraw(0, BatteryChemistry.SAG_BELOW / 2), EPS);
		assertEquals(0, BatteryChemistry.crankDraw(1, 0), EPS, "nothing from an empty bank, so an alternator can fill it");
		assertEquals(0, BatteryChemistry.crankDraw(1, -1), EPS);
		assertEquals(0, BatteryChemistry.crankDraw(1, Double.NaN), EPS);
		assertEquals(BatteryChemistry.CRANK_DRAW, BatteryChemistry.crankDraw(0, 9), EPS, "a share above 1 is clamped");
	}

	@Test
	void aComparatorReadsZeroOnlyWhenEmptyAndFifteenOnlyWhenFull() {
		assertEquals(0, BatteryChemistry.signal(0));
		assertEquals(0, BatteryChemistry.signal(-0.2));
		assertEquals(0, BatteryChemistry.signal(Double.NaN));
		assertEquals(1, BatteryChemistry.signal(1e-6), "any charge at all reads at least 1");
		assertEquals(14, BatteryChemistry.signal(0.999), "almost full does not read as full");
		assertEquals(15, BatteryChemistry.signal(1));
		assertEquals(15, BatteryChemistry.signal(3));
		assertEquals(8, BatteryChemistry.signal(0.5));
	}

	@Test
	void theSignalNeverFallsAsTheChargeRises() {
		int last = 0;
		for (int i = 0; i <= 1000; i++) {
			int signal = BatteryChemistry.signal(i / 1000.0);
			assertTrue(signal >= last, "at " + i + "/1000");
			assertTrue(signal >= 0 && signal <= BatteryChemistry.MAX_SIGNAL);
			last = signal;
		}
		assertEquals(BatteryChemistry.MAX_SIGNAL, last);
	}

	@Test
	void theLampStripIsDarkOnlyWhenFlatAndLitOnlyWhenFull() {
		assertEquals(0, BatteryChemistry.gaugeStage(0));
		assertEquals(0, BatteryChemistry.gaugeStage(Double.NaN));
		assertEquals(1, BatteryChemistry.gaugeStage(1e-6), "a little charge lights the first lamp");
		assertEquals(1, BatteryChemistry.gaugeStage(0.32));
		assertEquals(2, BatteryChemistry.gaugeStage(0.5));
		assertEquals(3, BatteryChemistry.gaugeStage(0.7));
		assertEquals(3, BatteryChemistry.gaugeStage(0.999), "almost full is not full");
		assertEquals(BatteryChemistry.GAUGE_STAGES, BatteryChemistry.gaugeStage(1));
		assertEquals(BatteryChemistry.GAUGE_STAGES, BatteryChemistry.gaugeStage(4));
	}

	@Test
	void theLampStripHoldsAtAnEdgeInsteadOfFlickering() {
		// The edge between stages 1 and 2 is a third of the way up
		double edge = 1.0 / 3;
		assertEquals(1, BatteryChemistry.gaugeStage(edge + 0.005, 1), "just over the edge: stays at 1");
		assertEquals(2, BatteryChemistry.gaugeStage(edge + 0.03, 1), "clearly over: moves up");
		assertEquals(2, BatteryChemistry.gaugeStage(edge - 0.005, 2), "just under: stays at 2");
		assertEquals(1, BatteryChemistry.gaugeStage(edge - 0.03, 2), "clearly under: moves down");
		assertEquals(0, BatteryChemistry.gaugeStage(0, 1), "flat always goes dark");
		assertEquals(BatteryChemistry.GAUGE_STAGES, BatteryChemistry.gaugeStage(1, 3), "full always lights the last lamp");
		assertEquals(3, BatteryChemistry.gaugeStage(0.7, 3), "no change is no change");
		assertEquals(0, BatteryChemistry.gaugeStage(0.0, 0));
	}

	@Test
	void theLampStripMovesExactlyWhenTheChargeIsTheHoldPastTheEdge() {
		// GAUGE_HOLD is a power of two, so these sums are exact and the boundary is tested at the boundary
		double edge = 1.0 / 3;
		assertEquals(2, BatteryChemistry.gaugeStage(edge + BatteryChemistry.GAUGE_HOLD, 1), "exactly the hold past the edge moves up");
		assertEquals(1, BatteryChemistry.gaugeStage(edge + BatteryChemistry.GAUGE_HOLD / 2, 1), "half the hold past it stays");
		assertEquals(1, BatteryChemistry.gaugeStage(edge - BatteryChemistry.GAUGE_HOLD, 2), "exactly the hold under the edge moves down");
		assertEquals(2, BatteryChemistry.gaugeStage(edge - BatteryChemistry.GAUGE_HOLD / 2 + 0.0, 2), "half the hold under it stays");
		double upper = 2.0 / 3;
		assertEquals(3, BatteryChemistry.gaugeStage(upper + BatteryChemistry.GAUGE_HOLD, 2), "the same at the next edge");
		assertEquals(3, BatteryChemistry.gaugeStage(upper - BatteryChemistry.GAUGE_HOLD / 2, 3), "and coming down from it, half the hold stays");
		assertEquals(2, BatteryChemistry.gaugeStage(upper - BatteryChemistry.GAUGE_HOLD, 3), "and the whole hold moves down");
	}

	@Test
	void theEndsOfTheLampStripNeverHold() {
		assertEquals(1, BatteryChemistry.gaugeStage(0.001, 0), "a little charge lights the first lamp at once from flat");
		assertEquals(0, BatteryChemistry.gaugeStage(0, 1), "flat darkens a lit strip at once");
		assertEquals(3, BatteryChemistry.gaugeStage(0.995, BatteryChemistry.GAUGE_STAGES), "just under full leaves the full stage at once");
		assertEquals(BatteryChemistry.GAUGE_STAGES, BatteryChemistry.gaugeStage(1, BatteryChemistry.GAUGE_STAGES - 1), "full lights the last lamp at once");
		assertEquals(3, BatteryChemistry.gaugeStage(0.7, 1), "a jump of two stages is not held");
		assertEquals(1, BatteryChemistry.gaugeStage(0.2, 3), "a fall of two stages is not held");
	}

	@Test
	void timeLeftIsTheChargeOverTheRate() {
		assertEquals(Double.POSITIVE_INFINITY, BatteryChemistry.ticksLeft(50, 1e-9), "a rate of exactly the smallest counted is still nothing drawn");
		assertTrue(BatteryChemistry.ticksLeft(50, 2e-9) < Double.POSITIVE_INFINITY, "twice that is counted");
		assertEquals(100, BatteryChemistry.ticksLeft(50, 0.5), EPS);
		assertEquals(Double.POSITIVE_INFINITY, BatteryChemistry.ticksLeft(50, 0), "nothing drawn: for ever");
		assertEquals(Double.POSITIVE_INFINITY, BatteryChemistry.ticksLeft(50, -1), "a negative rate is nothing drawn");
		assertEquals(0, BatteryChemistry.ticksLeft(-3, 1), EPS, "no charge: none left");
	}
}
