package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.RefiningColumn.Limit;
import com.createengines.engine.sim.RefiningColumn.Step;

class RefiningColumnTest {

	/** 1000 mB of feed makes 1100 of product, one catalyst item to 1000 mB, full speed: the shape of the diesel route. */
	private static final RefiningRoute DIESEL = new RefiningRoute(1000, 1100, Heat.HEATED, 1000, 1);
	private static final RefiningRoute NO_CATALYST = new RefiningRoute(1000, 1000, Heat.HEATED, 0, 1);
	private static final int PLENTY = 1_000_000;

	/** Runs a column for {@code ticks} with unlimited feed, room and catalyst, and totals what it did. */
	private static long[] run(RefiningColumn column, RefiningRoute route, int width, int height, Heat heat, int ticks) {
		long feed = 0, product = 0, catalyst = 0;
		for (int t = 0; t < ticks; t++) {
			Step s = column.tick(route, width, height, heat, PLENTY, PLENTY, PLENTY);
			feed += s.feedUsed();
			product += s.productMade();
			catalyst += s.catalystUsed();
		}
		return new long[] {feed, product, catalyst};
	}

	@Test
	void aBiggerColumnPassesMoreFeed() {
		assertEquals(0.25, RefiningColumn.ratePerTick(1, 1), 1e-12);
		assertEquals(0.75, RefiningColumn.ratePerTick(1, 3), 1e-12);
		assertEquals(2.25, RefiningColumn.ratePerTick(3, 1), 1e-12);
		assertEquals(4 * RefiningColumn.ratePerTick(1, 5), RefiningColumn.ratePerTick(2, 5), 1e-12);
		assertTrue(RefiningColumn.ratePerTick(2, 6) > RefiningColumn.ratePerTick(2, 5));
	}

	@Test
	void aColumnOutsideTheBuildableSizesIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(0, 3));
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(4, 3));
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(2, 0));
	}

	@Test
	void overAMinuteTheColumnPassesItsRateAndNoMoreOrLess() {
		RefiningColumn column = new RefiningColumn();
		long[] done = run(column, NO_CATALYST, 2, 4, Heat.HEATED, 1200);
		// 0.25 * 4 * 4 = 4 mB a tick; the leftover under a mB is the most it can be short
		assertEquals(4800, done[0], 1);
	}

	@Test
	void aRouteSlowerThanFullSpeedPassesProportionallyLess() {
		RefiningRoute slow = new RefiningRoute(1000, 1000, Heat.HEATED, 0, 0.5);
		long[] fast = run(new RefiningColumn(), NO_CATALYST, 1, 4, Heat.HEATED, 2000);
		long[] half = run(new RefiningColumn(), slow, 1, 4, Heat.HEATED, 2000);
		assertEquals(fast[0] / 2.0, half[0], 2);
	}

	@Test
	void aRateUnderOneMbATickStillMovesFeedOverTime() {
		long[] done = run(new RefiningColumn(), NO_CATALYST, 1, 1, Heat.HEATED, 400);
		assertEquals(100, done[0], 1);
	}

	@Test
	void productFollowsTheRouteRatioExactlyOverALongRun() {
		long[] done = run(new RefiningColumn(), DIESEL, 3, 5, Heat.HEATED, 5000);
		// No mB is lost to rounding: the product is the feed times the ratio, to within the one mB still waiting
		assertEquals(done[0] * 1.1, done[1], 1);
	}

	@Test
	void aRatioThatDoesNotDivideLosesNothingToRounding() {
		RefiningRoute odd = new RefiningRoute(300, 100, Heat.HEATED, 0, 1);
		long[] done = run(new RefiningColumn(), odd, 1, 2, Heat.HEATED, 1000);
		assertEquals(done[0] / 3.0, done[1], 1);
	}

	@Test
	void aColdBaseMakesNothing() {
		RefiningColumn column = new RefiningColumn();
		Step s = column.tick(DIESEL, 2, 3, Heat.NONE, PLENTY, PLENTY, PLENTY);
		assertEquals(0, s.feedUsed());
		assertEquals(0, s.productMade());
		assertEquals(Limit.COLD, s.limit());
		assertEquals(Limit.COLD, column.tick(DIESEL, 2, 3, Heat.NONE, PLENTY, PLENTY, PLENTY).limit());
	}

	@Test
	void aHotterBaseThanAskedForRunsTheRoute() {
		Step s = new RefiningColumn().tick(DIESEL, 3, 3, Heat.SUPERHEATED, PLENTY, PLENTY, PLENTY);
		assertTrue(s.feedUsed() > 0);
		assertEquals(Limit.NONE, s.limit());
	}

	@Test
	void aSuperheatedRouteWantsASuperheatedBase() {
		RefiningRoute hot = new RefiningRoute(1000, 340, Heat.SUPERHEATED, 1000, 0.5);
		assertEquals(Limit.COLD, new RefiningColumn().tick(hot, 2, 3, Heat.HEATED, PLENTY, PLENTY, PLENTY).limit());
		assertTrue(new RefiningColumn().tick(hot, 2, 3, Heat.SUPERHEATED, PLENTY, PLENTY, PLENTY).feedUsed() > 0);
	}

	@Test
	void withNoFeedItSaysSoAndMakesNothing() {
		Step s = new RefiningColumn().tick(DIESEL, 2, 3, Heat.HEATED, 0, PLENTY, PLENTY);
		assertEquals(Limit.NO_FEED, s.limit());
		assertEquals(0, s.productMade());
	}

	@Test
	void aTankThatCannotSupplyAFullTickIsDrainedAndNamed() {
		// 2.25 mB a tick wanted at 1x3x... a 3x1 column passes 2 whole mB, and 1 mB is all there is
		Step s = new RefiningColumn().tick(NO_CATALYST, 3, 1, Heat.HEATED, 1, PLENTY, 0);
		assertEquals(1, s.feedUsed());
		assertEquals(Limit.NO_FEED, s.limit());
	}

	@Test
	void aFullProductTankStopsTheColumn() {
		RefiningColumn column = new RefiningColumn();
		Step s = column.tick(DIESEL, 3, 3, Heat.HEATED, PLENTY, 0, PLENTY);
		assertEquals(Limit.PRODUCT_FULL, s.limit());
		assertEquals(0, s.feedUsed());
	}

	@Test
	void theProductNeverOverfillsTheRoomLeft() {
		for (int room = 1; room < 40; room++) {
			RefiningColumn column = new RefiningColumn();
			for (int t = 0; t < 30; t++) {
				Step s = column.tick(DIESEL, 3, 4, Heat.HEATED, PLENTY, room, PLENTY);
				assertTrue(s.productMade() <= room, "room " + room + " got " + s.productMade());
			}
		}
	}

	@Test
	void aTankWithRoomForAFractionOfATickTakesWhatFitsAndNamesTheTank() {
		Step s = new RefiningColumn().tick(NO_CATALYST, 3, 3, Heat.HEATED, PLENTY, 5, PLENTY);
		assertEquals(5, s.productMade());
		assertEquals(5, s.feedUsed());
		assertEquals(Limit.PRODUCT_FULL, s.limit());
	}

	@Test
	void theCatalystIsUsedUpAtItsRateNotOncePerBatch() {
		RefiningColumn column = new RefiningColumn();
		long[] done = run(column, DIESEL, 2, 4, Heat.HEATED, 2500);
		// 4 mB a tick for 2500 ticks is 10000 mB of feed, one coal to the 1000 mB
		assertEquals(10, done[2], 1);
		assertTrue(column.catalystCredit() >= 0 && column.catalystCredit() < 1000);
	}

	@Test
	void aRouteWithNoCatalystNeverAsksForOne() {
		long[] done = run(new RefiningColumn(), NO_CATALYST, 2, 4, Heat.HEATED, 500);
		assertEquals(0, done[2]);
	}

	@Test
	void withoutCatalystAColumnStopsAndNamesIt() {
		RefiningColumn column = new RefiningColumn();
		Step s = column.tick(DIESEL, 2, 3, Heat.HEATED, PLENTY, PLENTY, 0);
		assertEquals(Limit.NO_CATALYST, s.limit());
		assertEquals(0, s.feedUsed());
	}

	@Test
	void aSingleCatalystItemRunsASetAmountOfFeedThenTheColumnStops() {
		RefiningColumn column = new RefiningColumn();
		long feed = 0;
		int items = 1;
		Limit last = Limit.NONE;
		for (int t = 0; t < 5000 && last != Limit.NO_CATALYST; t++) {
			Step s = column.tick(DIESEL, 2, 3, Heat.HEATED, PLENTY, PLENTY, items);
			items -= s.catalystUsed();
			feed += s.feedUsed();
			last = s.limit();
		}
		assertEquals(1000, feed);
		assertEquals(0, items);
		assertEquals(Limit.NO_CATALYST, last);
	}

	@Test
	void theCatalystLoadedIsNeverMoreThanTheFeedNeeds() {
		RefiningColumn column = new RefiningColumn();
		int items = 100;
		for (int t = 0; t < 300; t++)
			items -= column.tick(DIESEL, 1, 2, Heat.HEATED, PLENTY, PLENTY, items).catalystUsed();
		// 0.5 mB a tick for 300 ticks is 150 mB: one coal is enough, and a column does not pull a second early
		assertEquals(99, items);
	}

	@Test
	void whenHeldBackTheColumnKeepsNoMoreThanAMbToCatchUp() {
		RefiningColumn column = new RefiningColumn();
		for (int t = 0; t < 100; t++)
			column.tick(NO_CATALYST, 1, 4, Heat.HEATED, 0, PLENTY, 0);
		Step s = column.tick(NO_CATALYST, 1, 4, Heat.HEATED, PLENTY, PLENTY, 0);
		assertTrue(s.feedUsed() <= 2, "a column that sat idle should not burst: " + s.feedUsed());
	}

	@Test
	void resetForgetsTheCatalystAndTheRemainder() {
		RefiningColumn column = new RefiningColumn();
		column.tick(DIESEL, 3, 3, Heat.HEATED, PLENTY, PLENTY, PLENTY);
		assertTrue(column.catalystCredit() > 0);
		column.reset();
		assertEquals(0, column.catalystCredit());
	}

	@Test
	void aRouteMustHaveSomethingToMake() {
		assertThrows(IllegalArgumentException.class, () -> new RefiningRoute(0, 100, Heat.HEATED, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new RefiningRoute(100, 0, Heat.HEATED, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new RefiningRoute(100, 100, Heat.HEATED, -1, 1));
		assertThrows(IllegalArgumentException.class, () -> new RefiningRoute(100, 100, Heat.HEATED, 0, 0));
	}

	@Test
	void routeCostsReadBackPerBucket() {
		assertEquals(1000 / 1.1, DIESEL.feedPerBucket(), 1e-9);
		assertEquals(1000 / 1.1 / 1000, DIESEL.catalystPerBucket(), 1e-9);
		assertEquals(0, NO_CATALYST.catalystPerBucket(), 0);
	}

	@Test
	void heatNamesRoundTripAndHotterSatisfiesColder() {
		for (Heat heat : Heat.values())
			assertEquals(heat, Heat.fromName(heat.serializedName()));
		assertEquals(null, Heat.fromName("warm"));
		assertTrue(Heat.SUPERHEATED.satisfies(Heat.HEATED) && Heat.HEATED.satisfies(Heat.NONE));
		assertTrue(!Heat.HEATED.satisfies(Heat.SUPERHEATED) && !Heat.NONE.satisfies(Heat.HEATED));
	}

	// A 3 wide, 4 tall column passes exactly 9 mB a tick, so an extra banked mB shows as 10

	@Test
	void aTickWithNoFeedBanksNothingForTheNext() {
		RefiningColumn column = new RefiningColumn();
		Step dry = column.tick(NO_CATALYST, 3, 4, Heat.HEATED, 0, PLENTY, PLENTY);
		assertEquals(Limit.NO_FEED, dry.limit());
		assertEquals(9, column.tick(NO_CATALYST, 3, 4, Heat.HEATED, PLENTY, PLENTY, PLENTY).feedUsed());
	}

	@Test
	void aFullProductTankBanksNothingForTheNext() {
		RefiningColumn column = new RefiningColumn();
		Step full = column.tick(NO_CATALYST, 3, 4, Heat.HEATED, PLENTY, 0, PLENTY);
		assertEquals(Limit.PRODUCT_FULL, full.limit());
		assertEquals(0, full.feedUsed());
		assertEquals(9, column.tick(NO_CATALYST, 3, 4, Heat.HEATED, PLENTY, PLENTY, PLENTY).feedUsed());
	}

	@Test
	void noRoomAtAllPassesNoFeedEvenWhenTheRouteMakesLessThanItTakes() {
		// Ethanol: 1000 in, 170 out. With no room a mB of feed would still need to be refused
		RefiningRoute ethanol = new RefiningRoute(1000, 170, Heat.HEATED, 0, 0.5);
		Step step = new RefiningColumn().tick(ethanol, 3, 4, Heat.HEATED, PLENTY, 0, 0);
		assertEquals(0, step.feedUsed());
		assertEquals(Limit.PRODUCT_FULL, step.limit());
	}

	@Test
	void catalystThatJustCoversTheTickIsNotCalledALimit() {
		// One item serves 9 mB and the tick passes 9: it is used up exactly, and nothing held the column back
		RefiningRoute exact = new RefiningRoute(1000, 1100, Heat.HEATED, 9, 1);
		Step step = new RefiningColumn().tick(exact, 3, 4, Heat.HEATED, PLENTY, PLENTY, 1);
		assertEquals(9, step.feedUsed());
		assertEquals(1, step.catalystUsed());
		assertEquals(Limit.NONE, step.limit());
	}

	@Test
	void catalystIsUsedUpOnlyAsFastAsItIsNeededWhenItDoesNotDivideTheTick() {
		// 12 mB an item against 9 a tick: over 1200 ticks 10800 mB of feed takes exactly 900 items, none wasted
		RefiningRoute twelve = new RefiningRoute(1000, 1100, Heat.HEATED, 12, 1);
		long[] done = run(new RefiningColumn(), twelve, 3, 4, Heat.HEATED, 1200);
		assertEquals(10800, done[0]);
		assertEquals(900, done[2]);
	}
}
